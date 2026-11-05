/**
 * RideAlong push relay (Cloudflare Worker).
 *
 * POST /  with  Authorization: Bearer <Firebase ID token>
 *           body {"kind": "call" | "notification", "id": "<document id>"}
 *
 * The relay never trusts the request body: it verifies the caller's Firebase ID token, then reads
 * the referenced Firestore document with the service account and only pushes if the signed-in user
 * is that document's sender (calls/{id}.callerId or notifications/{id}.senderId). The push is a
 * data-only FCM message holding ids, never message text; the app fetches and renders it itself.
 */

const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const SCOPES = 'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging';
const ID_RE = /^[A-Za-z0-9_-]{1,200}$/;

let jwksCache = { keys: null, exp: 0 };
let tokenCache = { token: null, exp: 0 };

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') return json({ error: 'POST only' }, 405);
    const auth = request.headers.get('Authorization') || '';
    if (!auth.startsWith('Bearer ')) return json({ error: 'unauthenticated' }, 401);

    let sa;
    try {
      sa = JSON.parse(env.SERVICE_ACCOUNT_JSON);
    } catch (e) {
      return json({ error: 'relay not configured' }, 500);
    }
    const projectId = env.FIREBASE_PROJECT_ID || sa.project_id;

    let uid;
    try {
      uid = await verifyIdToken(auth.slice(7), projectId);
    } catch (e) {
      return json({ error: 'invalid token' }, 401);
    }

    let body;
    try {
      body = await request.json();
    } catch (e) {
      return json({ error: 'bad json' }, 400);
    }
    const { kind, id } = body || {};
    if (!ID_RE.test(id || '')) return json({ error: 'bad id' }, 400);

    const access = await accessToken(sa);
    let recipient;
    let data;
    let ttl;
    if (kind === 'call') {
      const f = await getDoc(projectId, access, `calls/${id}`);
      if (!f) return json({ error: 'not found' }, 404);
      if (f.callerId !== uid) return json({ error: 'forbidden' }, 403);
      if (f.status !== 'RINGING') return json({ sent: false, reason: 'not ringing' });
      recipient = f.calleeId;
      data = { kind: 'call', callId: id };
      ttl = '45s'; // a call push that arrives later than this is useless
    } else if (kind === 'notification') {
      const f = await getDoc(projectId, access, `notifications/${id}`);
      if (!f) return json({ error: 'not found' }, 404);
      if (f.senderId !== uid) return json({ error: 'forbidden' }, 403);
      recipient = f.userId;
      data = { kind: 'notification', notificationId: id };
      ttl = '86400s';
    } else {
      return json({ error: 'bad kind' }, 400);
    }

    const tokenDoc = recipient ? await getDoc(projectId, access, `pushTokens/${recipient}`) : null;
    if (!tokenDoc || !tokenDoc.token) return json({ sent: false, reason: 'recipient has no device token' });

    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${access}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: { token: tokenDoc.token, data, android: { priority: 'HIGH', ttl } } }),
    });
    if (!res.ok) return json({ sent: false, reason: `fcm ${res.status}` }, 502);
    return json({ sent: true });
  },
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { 'Content-Type': 'application/json' } });
}

// ---------- Firebase ID token verification (RS256, Google public keys) ----------

function b64urlToBytes(s) {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlJson(s) {
  return JSON.parse(new TextDecoder().decode(b64urlToBytes(s)));
}

async function googleKeys() {
  const now = Date.now();
  if (jwksCache.keys && jwksCache.exp > now) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  const body = await res.json();
  const maxAge = /max-age=(\d+)/.exec(res.headers.get('Cache-Control') || '');
  jwksCache = { keys: body.keys, exp: now + (maxAge ? Number(maxAge[1]) * 1000 : 3600_000) };
  return body.keys;
}

export async function verifyIdToken(token, projectId, keysOverride) {
  const [h, p, s] = token.split('.');
  if (!h || !p || !s) throw new Error('malformed');
  const header = b64urlJson(h);
  const payload = b64urlJson(p);
  if (header.alg !== 'RS256') throw new Error('alg');
  const keys = keysOverride || (await googleKeys());
  const jwk = keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error('kid');
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, b64urlToBytes(s), new TextEncoder().encode(`${h}.${p}`));
  if (!ok) throw new Error('signature');
  const now = Math.floor(Date.now() / 1000);
  if (payload.aud !== projectId) throw new Error('aud');
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new Error('iss');
  if (!(payload.exp > now)) throw new Error('expired');
  if (payload.iat > now + 300) throw new Error('iat');
  if (!payload.sub) throw new Error('sub');
  return payload.sub;
}

// ---------- Service-account OAuth token ----------

function pemToPkcs8(pem) {
  const b64 = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

function bytesToB64url(bytes) {
  let bin = '';
  for (const b of new Uint8Array(bytes)) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function accessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  if (tokenCache.token && tokenCache.exp > now + 60) return tokenCache.token;
  const enc = (o) => bytesToB64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc({ alg: 'RS256', typ: 'JWT' })}.${enc({
    iss: sa.client_email, scope: SCOPES, aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600,
  })}`;
  const key = await crypto.subtle.importKey('pkcs8', pemToPkcs8(sa.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${bytesToB64url(sig)}`,
    }),
  });
  const body = await res.json();
  if (!body.access_token) throw new Error('oauth failed');
  tokenCache = { token: body.access_token, exp: now + (body.expires_in || 3600) };
  return body.access_token;
}

// ---------- Firestore REST ----------

async function getDoc(projectId, access, path) {
  const res = await fetch(`https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${path}`, {
    headers: { Authorization: `Bearer ${access}` },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`firestore ${res.status}`);
  const doc = await res.json();
  const out = {};
  for (const [k, v] of Object.entries(doc.fields || {})) {
    out[k] = v.stringValue ?? v.booleanValue ?? v.integerValue ?? v.timestampValue ?? null;
  }
  return out;
}