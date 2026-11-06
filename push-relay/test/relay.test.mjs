// node --test test/relay.test.mjs   (Node 18+: global WebCrypto)
import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyIdToken, groupRecipients } from '../src/index.js';

const PROJECT = 'demo-ridealong';
const b64url = (bytes) => Buffer.from(bytes).toString('base64url');

async function setup() {
  const { publicKey, privateKey } = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true, ['sign', 'verify']);
  const jwk = { ...(await crypto.subtle.exportKey('jwk', publicKey)), kid: 'k1', alg: 'RS256', use: 'sig' };
  async function sign(payload, kid = 'k1') {
    const h = b64url(JSON.stringify({ alg: 'RS256', kid, typ: 'JWT' }));
    const p = b64url(JSON.stringify(payload));
    const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', privateKey, new TextEncoder().encode(`${h}.${p}`));
    return `${h}.${p}.${b64url(new Uint8Array(sig))}`;
  }
  return { keys: [jwk], sign };
}

const now = () => Math.floor(Date.now() / 1000);
const good = () => ({ aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`, sub: 'alice', iat: now(), exp: now() + 3600 });

test('accepts a valid Firebase ID token and returns the uid', async () => {
  const { keys, sign } = await setup();
  assert.equal(await verifyIdToken(await sign(good()), PROJECT, keys), 'alice');
});

test('rejects tampered, wrong-project, expired and unknown-key tokens', async () => {
  const { keys, sign } = await setup();
  const t = await sign(good());
  const [h, , s] = t.split('.');
  const forged = `${h}.${b64url(JSON.stringify({ ...good(), sub: 'mallory' }))}.${s}`;
  await assert.rejects(verifyIdToken(forged, PROJECT, keys));
  await assert.rejects(verifyIdToken(await sign({ ...good(), aud: 'other-project' }), PROJECT, keys));
  await assert.rejects(verifyIdToken(await sign({ ...good(), iss: 'https://evil.example' }), PROJECT, keys));
  await assert.rejects(verifyIdToken(await sign({ ...good(), exp: now() - 10 }), PROJECT, keys));
  await assert.rejects(verifyIdToken(await sign(good(), 'unknown'), PROJECT, keys));
});

test('group call rings every invited member except the host, once each', () => {
  assert.deepEqual(groupRecipients({ invited: ['alice', 'bob', 'carol', 'bob'] }, 'alice'), ['bob', 'carol']);
  assert.deepEqual(groupRecipients({}, 'alice'), []);
  assert.equal(groupRecipients({ invited: Array.from({ length: 20 }, (_, i) => `u${i}`) }, 'x').length, 8);
});
