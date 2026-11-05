/*
 * EMULATOR-ONLY seed data for manual testing. The app itself never contains sample data.
 *
 *   firebase emulators:start --only auth,firestore --project demo-ridealong
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 npm run seed
 *
 * The script refuses to run unless both emulator host variables are set, so it can never
 * write to a real Firebase project.
 */
if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  console.error('Refusing to seed: FIRESTORE_EMULATOR_HOST and FIREBASE_AUTH_EMULATOR_HOST must point at the emulator.');
  process.exit(1);
}

const admin = require('firebase-admin');
admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT || 'demo-ridealong' });
const auth = admin.auth();
const db = admin.firestore();

const users = [
  { email: 'tester1@ridealong.test', password: 'test1234', name: 'Test Rider One', phone: '01700000001' },
  { email: 'tester2@ridealong.test', password: 'test1234', name: 'Test Rider Two', phone: '01700000002' },
];

(async () => {
  const uids = [];
  for (const u of users) {
    let rec;
    try { rec = await auth.getUserByEmail(u.email); } catch (_) {
      rec = await auth.createUser({ email: u.email, password: u.password, displayName: u.name });
    }
    uids.push(rec.uid);
    await db.doc(`users/${rec.uid}`).set({
      uid: rec.uid, fullName: u.name, fullNameLower: u.name.toLowerCase(), organization: 'Emulator University',
      gender: '', ratingSum: 0, ratingCount: 0, completedRides: 0, profileComplete: true,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    await db.doc(`users/${rec.uid}/private/info`).set({
      phone: u.phone, email: u.email,
      emergencyContacts: [{ name: 'Emulator Contact', phone: '01700000099' }],
    });
  }
  const owner = uids[0];
  const ride = await db.collection('rides').add({
    ownerId: owner, ownerName: users[0].name, status: 'OPEN',
    pickupLat: 23.8069, pickupLng: 90.3687, pickupAddress: 'Mirpur 10 Circle, Dhaka', pickupGeohash: 'wh0r9q0kh',
    dropLat: 23.7389, dropLng: 90.3950, dropAddress: 'Shahbag, Dhaka', dropGeohash: 'wh0qcw2u7',
    departureTime: admin.firestore.Timestamp.fromMillis(Date.now() + 2 * 3600 * 1000),
    costPerPerson: 70, totalSeats: 3, seatsAvailable: 3, maxDetourKm: 2, comment: 'Emulator seed ride',
    transportType: 'CNG', memberIds: [owner], routePolyline: '', routeDistanceKm: 9.2, routeDurationMin: 30,
    routeApproximate: true,
    createdAt: admin.firestore.FieldValue.serverTimestamp(), updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await db.doc(`rides/${ride.id}/contacts/${owner}`).set({ name: users[0].name, phone: users[0].phone });
  console.log('Seeded emulator: users', uids.join(', '), 'ride', ride.id);
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
