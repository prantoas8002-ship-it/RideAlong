# RideAlong push relay

Android only wakes a **closed** app for a push message (FCM), and FCM must be sent by a server.
Firebase's own server code (Cloud Functions) needs the paid Blaze plan, so RideAlong uses a tiny
**Cloudflare Worker** instead (free: 100,000 requests/day).

What it does: the app tells the relay "I created call X" or "I created notification Y". The relay
1. verifies the sender's Firebase ID token,
2. reads that Firestore document with a service account and checks the sender really created it,
3. sends a **data-only** FCM push (ids only, never message text) to the recipient's device token
   (`pushTokens/{uid}`).

The phone then wakes up, shows the incoming-call screen or the notification, even when RideAlong
was fully closed.

## Setup (about 5 minutes)
1. **Firebase service-account key:** Firebase console → ⚙ Project settings → *Service accounts* →
   **Generate new private key**. A JSON file downloads. Keep it private and never commit it.
2. **Cloudflare account:** free sign-up at <https://dash.cloudflare.com/sign-up> (no card needed).
3. In PowerShell:
   ```powershell
   cd "D:\2 - 2\RideAlong\push-relay"
   .\deploy.ps1 -KeyFile "$env:USERPROFILE\Downloads\<the-downloaded-file>.json"
   ```
   A browser window asks you to allow Wrangler once.
4. Put the printed URL into `local.properties`:
   `PUSH_RELAY_URL=https://ridealong-push.<your-subdomain>.workers.dev`
5. Rebuild the app (or ask Claude to rebuild the shareable APK).

Without `PUSH_RELAY_URL` the app still works; calls and notifications just arrive only while the
app is running.

## Tests
`npm test`: verifies the ID-token check rejects forged, expired and wrong-project tokens.