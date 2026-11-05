/*
 * Firestore security-rules tests for RideAlong.
 * Run:  cd firestore-tests && npm install && npm test
 * (npm test starts the Firestore emulator via firebase-tools; Java 11+ is required.)
 */
const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment, assertFails, assertSucceeds,
} = require('@firebase/rules-unit-testing');
const firebase = require('firebase/compat/app');
require('firebase/compat/firestore');

const FieldValue = firebase.firestore.FieldValue;
const Timestamp = firebase.firestore.Timestamp;

let env;
const ALICE = 'alice'; // ride owner
const BOB = 'bob';     // passenger
const CAROL = 'carol'; // unrelated user
const RIDE = 'ride1';

function db(uid) { return env.authenticatedContext(uid).firestore(); }
function anon() { return env.unauthenticatedContext().firestore(); }
function inHours(h) { return Timestamp.fromMillis(Date.now() + h * 3600 * 1000); }

function rideData(overrides = {}) {
  return Object.assign({
    ownerId: ALICE, ownerName: 'Alice', status: 'OPEN',
    pickupLat: 23.7808, pickupLng: 90.2792, pickupAddress: 'Mirpur 10', pickupGeohash: 'wh0r3x5c1',
    dropLat: 23.7461, dropLng: 90.3742, dropAddress: 'Dhanmondi', dropGeohash: 'wh0qcj7e1',
    departureTime: inHours(3), costPerPerson: 80, totalSeats: 3, seatsAvailable: 3,
    maxDetourKm: 2, comment: '', transportType: 'CNG', memberIds: [ALICE],
  }, overrides);
}
function profile(uid) {
  return { uid, fullName: uid, ratingSum: 0, ratingCount: 0, completedRides: 0, profileComplete: true };
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ridealong',
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8'),
      host: '127.0.0.1', port: 8080,
    },
  });
});
after(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const a = ctx.firestore();
    for (const u of [ALICE, BOB, CAROL]) await a.doc(`users/${u}`).set(profile(u));
    await a.doc(`rides/${RIDE}`).set(rideData());
  });
});

async function seedPendingRequest(seats = 1) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`rideRequests/${RIDE}_${BOB}`).set({
      rideId: RIDE, rideOwnerId: ALICE, requesterId: BOB, requesterName: 'Bob',
      seats, status: 'PENDING', message: '',
    });
  });
}
async function acceptBob(seats = 1) {
  const d = db(ALICE);
  const b = d.batch();
  b.update(d.doc(`rideRequests/${RIDE}_${BOB}`), { status: 'ACCEPTED', updatedAt: FieldValue.serverTimestamp() });
  b.update(d.doc(`rides/${RIDE}`), {
    seatsAvailable: 3 - seats, memberIds: [ALICE, BOB], status: seats === 3 ? 'FULL' : 'OPEN',
    lastRequestId: `${RIDE}_${BOB}`, updatedAt: FieldValue.serverTimestamp(),
  });
  return b.commit();
}

describe('users', () => {
  it('rejects unauthenticated reads', async () => {
    await assertFails(anon().doc(`users/${ALICE}`).get());
  });
  it('lets a user edit their own profile but not their counters', async () => {
    await assertSucceeds(db(BOB).doc(`users/${BOB}`).update({ fullName: 'Bob B' }));
    await assertFails(db(BOB).doc(`users/${BOB}`).update({ ratingSum: 50, ratingCount: 10 }));
    await assertFails(db(BOB).doc(`users/${BOB}`).update({ completedRides: 99 }));
  });
  it('blocks editing someone else\'s profile', async () => {
    await assertFails(db(CAROL).doc(`users/${BOB}`).update({ fullName: 'hacked' }));
  });
  it('keeps private info private', async () => {
    await assertSucceeds(db(BOB).doc(`users/${BOB}/private/info`).set({ phone: '01700000000', emergencyContacts: [] }));
    await assertFails(db(CAROL).doc(`users/${BOB}/private/info`).get());
  });
});

describe('rides', () => {
  it('allows a valid ride and rejects invalid ones', async () => {
    await assertSucceeds(db(BOB).doc('rides/new1').set(rideData({ ownerId: BOB, memberIds: [BOB] })));
    await assertFails(db(BOB).doc('rides/new2').set(rideData({ ownerId: BOB, memberIds: [BOB], seatsAvailable: 1 })));
    await assertFails(db(BOB).doc('rides/new3').set(rideData({ ownerId: BOB, memberIds: [BOB], totalSeats: 9, seatsAvailable: 9 })));
    await assertFails(db(BOB).doc('rides/new4').set(rideData({ ownerId: BOB, memberIds: [BOB], costPerPerson: -1 })));
    await assertFails(db(BOB).doc('rides/new5').set(rideData({ ownerId: ALICE })));
  });
  it('only the owner changes status, and only along allowed transitions', async () => {
    await assertFails(db(BOB).doc(`rides/${RIDE}`).update({ status: 'CANCELLED' }));
    await assertFails(db(ALICE).doc(`rides/${RIDE}`).update({ status: 'COMPLETED' }));
    await assertSucceeds(db(ALICE).doc(`rides/${RIDE}`).update({ status: 'IN_PROGRESS' }));
    await assertSucceeds(db(ALICE).doc(`rides/${RIDE}`).update({ status: 'COMPLETED' }));
    await assertFails(db(ALICE).doc(`rides/${RIDE}`).update({ status: 'OPEN' }));
  });
  it('rejects arbitrary seat changes', async () => {
    await assertFails(db(ALICE).doc(`rides/${RIDE}`).update({ seatsAvailable: 0 }));
    await assertFails(db(BOB).doc(`rides/${RIDE}`).update({ seatsAvailable: 2, memberIds: [ALICE, BOB] }));
  });
});

describe('join requests', () => {
  const reqDoc = (d, id = `${RIDE}_${BOB}`) => d.doc(`rideRequests/${id}`);
  const req = (o = {}) => Object.assign({
    rideId: RIDE, rideOwnerId: ALICE, requesterId: BOB, requesterName: 'Bob', seats: 1, status: 'PENDING', message: '',
  }, o);

  it('allows a valid request', async () => {
    await assertSucceeds(reqDoc(db(BOB)).set(req()));
  });
  it('prevents self-join, overbooking and forged ids', async () => {
    await assertFails(reqDoc(db(ALICE), `${RIDE}_${ALICE}`).set(req({ requesterId: ALICE })));
    await assertFails(reqDoc(db(BOB)).set(req({ seats: 4 })));
    await assertFails(reqDoc(db(BOB), `${RIDE}_someoneelse`).set(req()));
    await assertFails(reqDoc(db(BOB)).set(req({ status: 'ACCEPTED' })));
  });
  it('only the requester can cancel; only the owner can accept/reject', async () => {
    await seedPendingRequest();
    await assertFails(reqDoc(db(CAROL)).update({ status: 'CANCELLED' }));
    await assertFails(reqDoc(db(BOB)).update({ status: 'ACCEPTED' }));
    await assertFails(reqDoc(db(CAROL)).update({ status: 'REJECTED' }));
    await assertSucceeds(reqDoc(db(ALICE)).update({ status: 'REJECTED' }));
  });
  it('requester can cancel their own pending request', async () => {
    await seedPendingRequest();
    await assertSucceeds(reqDoc(db(BOB)).update({ status: 'CANCELLED' }));
  });
  it('owner accepts atomically with the seat update', async () => {
    await seedPendingRequest();
    await assertSucceeds(acceptBob());
  });
  it('rejects accepting without a matching seat decrement', async () => {
    await seedPendingRequest();
    const d = db(ALICE);
    const b = d.batch();
    b.update(d.doc(`rideRequests/${RIDE}_${BOB}`), { status: 'ACCEPTED' });
    b.update(d.doc(`rides/${RIDE}`), { seatsAvailable: 3, memberIds: [ALICE, BOB], status: 'OPEN', lastRequestId: `${RIDE}_${BOB}` });
    await assertFails(b.commit());
  });
  it('accepted participant can leave atomically', async () => {
    await seedPendingRequest();
    await acceptBob();
    const d = db(BOB);
    const b = d.batch();
    b.update(d.doc(`rideRequests/${RIDE}_${BOB}`), { status: 'LEFT', updatedAt: FieldValue.serverTimestamp() });
    b.update(d.doc(`rides/${RIDE}`), {
      seatsAvailable: 3, memberIds: [ALICE], status: 'OPEN', lastRequestId: `${RIDE}_${BOB}`,
      updatedAt: FieldValue.serverTimestamp(),
    });
    await assertSucceeds(b.commit());
  });
  it('blocked users cannot request', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`blocks/${ALICE}_${BOB}`).set({ blockerId: ALICE, blockedId: BOB });
    });
    await assertFails(reqDoc(db(BOB)).set(req()));
  });
});

describe('contacts, request lookups and notifications', () => {
  it('owner can create a ride and its contact card in one batch', async () => {
    const d = db(BOB);
    const b = d.batch();
    b.set(d.doc('rides/r2'), rideData({ ownerId: BOB, memberIds: [BOB] }));
    b.set(d.doc(`rides/r2/contacts/${BOB}`), { name: 'Bob', phone: '01711111111' });
    await assertSucceeds(b.commit());
  });
  it('requester can check a request that does not exist yet', async () => {
    await assertSucceeds(db(BOB).doc(`rideRequests/${RIDE}_${BOB}`).get());
  });
  it('requester publishes their contact together with the request; outsiders cannot read it', async () => {
    const d = db(BOB);
    const b = d.batch();
    b.set(d.doc(`rideRequests/${RIDE}_${BOB}`), {
      rideId: RIDE, rideOwnerId: ALICE, requesterId: BOB, requesterName: 'Bob', seats: 1, status: 'PENDING', message: '',
    });
    b.set(d.doc(`rides/${RIDE}/contacts/${BOB}`), { name: 'Bob', phone: '01711111111' });
    await assertSucceeds(b.commit());
    await assertSucceeds(db(ALICE).doc(`rides/${RIDE}/contacts/${BOB}`).get()); // owner may call a requester
    await assertFails(db(CAROL).doc(`rides/${RIDE}/contacts/${BOB}`).get());
    await assertFails(db(CAROL).doc(`rides/${RIDE}/contacts/${CAROL}`).set({ name: 'C', phone: '0170' }));
  });
  it('unrelated users cannot send notifications', async () => {
    const n = (sender) => ({
      userId: ALICE, senderId: sender, type: 'JOIN_REQUEST', title: 't', body: 'b', rideId: RIDE, read: false,
      createdAt: FieldValue.serverTimestamp(),
    });
    await assertFails(db(CAROL).collection('notifications').add(n(CAROL)));
    await assertFails(db(CAROL).collection('notifications').add(n(BOB))); // impersonation
  });
});

describe('private chat between poster and requester', () => {
  const CHAT = `${RIDE}_${BOB}`;
  const chat = () => ({
    rideId: RIDE, ownerId: ALICE, ownerName: 'Alice', requesterId: BOB, requesterName: 'Bob',
    participants: [ALICE, BOB], lastMessage: '', lastSenderId: '', lastReadAt: {},
    lastMessageAt: FieldValue.serverTimestamp(), createdAt: FieldValue.serverTimestamp(),
  });
  const msg = (uid, text = 'hello') => ({ senderId: uid, senderName: uid, text, type: 'TEXT', createdAt: FieldValue.serverTimestamp() });

  it('requester creates the chat together with the request (pending, not accepted)', async () => {
    const d = db(BOB);
    const b = d.batch();
    b.set(d.doc(`rideRequests/${CHAT}`), {
      rideId: RIDE, rideOwnerId: ALICE, requesterId: BOB, requesterName: 'Bob', seats: 1, status: 'PENDING', message: 'hi',
    });
    b.set(d.doc(`directChats/${CHAT}`), Object.assign(chat(), { lastMessage: 'hi', lastSenderId: BOB }));
    b.set(d.doc(`directChats/${CHAT}/messages/m1`), msg(BOB, 'hi'));
    await assertSucceeds(b.commit());
    await assertSucceeds(db(ALICE).doc(`directChats/${CHAT}/messages/m1`).get());
    await assertSucceeds(db(ALICE).collection('directChats').where('participants', 'array-contains', ALICE).get());
  });

  it('cannot create a chat without a request, or for someone else', async () => {
    await assertFails(db(BOB).doc(`directChats/${CHAT}`).set(chat()));
    await seedPendingRequest();
    await assertFails(db(CAROL).doc(`directChats/${CHAT}`).set(chat()));
    await assertFails(db(BOB).doc(`directChats/${RIDE}_${CAROL}`).set(Object.assign(chat(), { requesterId: CAROL, participants: [ALICE, CAROL] })));
  });

  it('both sides chat; outsiders cannot read, write or impersonate', async () => {
    await seedPendingRequest();
    await assertSucceeds(db(ALICE).doc(`directChats/${CHAT}`).set(chat())); // poster can open it too
    await assertSucceeds(db(ALICE).collection(`directChats/${CHAT}/messages`).add(msg(ALICE)));
    await assertSucceeds(db(BOB).collection(`directChats/${CHAT}/messages`).add(msg(BOB)));
    await assertFails(db(BOB).collection(`directChats/${CHAT}/messages`).add(msg(ALICE)));
    await assertFails(db(CAROL).collection(`directChats/${CHAT}/messages`).get());
    await assertFails(db(CAROL).collection(`directChats/${CHAT}/messages`).add(msg(CAROL)));
    await assertFails(db(CAROL).doc(`directChats/${CHAT}`).get());
  });

  it('participants update the preview and only their own read marker', async () => {
    await seedPendingRequest();
    await db(ALICE).doc(`directChats/${CHAT}`).set(chat());
    await assertSucceeds(db(BOB).doc(`directChats/${CHAT}`).update({
      lastMessage: 'yo', lastSenderId: BOB, lastMessageAt: FieldValue.serverTimestamp(),
      [`lastReadAt.${BOB}`]: FieldValue.serverTimestamp(),
    }));
    await assertFails(db(BOB).doc(`directChats/${CHAT}`).update({ [`lastReadAt.${ALICE}`]: FieldValue.serverTimestamp() }));
    await assertFails(db(BOB).doc(`directChats/${CHAT}`).update({ lastSenderId: ALICE }));
    await assertFails(db(BOB).doc(`directChats/${CHAT}`).update({ participants: [BOB, CAROL] }));
  });

  it('blocked users cannot message', async () => {
    await seedPendingRequest();
    await db(ALICE).doc(`directChats/${CHAT}`).set(chat());
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`blocks/${ALICE}_${BOB}`).set({ blockerId: ALICE, blockedId: BOB });
    });
    await assertFails(db(BOB).collection(`directChats/${CHAT}/messages`).add(msg(BOB)));
  });

  it('requester can read the poster phone while pending, not after being declined', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`rides/${RIDE}/contacts/${ALICE}`).set({ name: 'Alice', phone: '01711111111' });
    });
    await seedPendingRequest();
    await assertSucceeds(db(BOB).doc(`rides/${RIDE}/contacts/${ALICE}`).get());
    await assertFails(db(CAROL).doc(`rides/${RIDE}/contacts/${ALICE}`).get());
    await db(ALICE).doc(`rideRequests/${CHAT}`).update({ status: 'REJECTED' });
    await assertFails(db(BOB).doc(`rides/${RIDE}/contacts/${ALICE}`).get());
  });

  it('direct-message notifications are allowed between the two', async () => {
    await seedPendingRequest();
    await assertSucceeds(db(BOB).collection('notifications').add({
      userId: ALICE, senderId: BOB, type: 'DIRECT_MESSAGE', title: 'Bob', body: 'hi', rideId: RIDE, read: false,
      args: ['Bob', 'hi', CHAT], createdAt: FieldValue.serverTimestamp(),
    }));
  });
});

describe('chat and live location', () => {
  beforeEach(async () => { await seedPendingRequest(); await acceptBob(); });
  const msg = (uid) => ({ senderId: uid, senderName: uid, text: 'hi', type: 'TEXT', createdAt: FieldValue.serverTimestamp() });

  it('members can chat, outsiders cannot read or write', async () => {
    await assertSucceeds(db(BOB).collection(`chats/${RIDE}/messages`).add(msg(BOB)));
    await assertSucceeds(db(ALICE).collection(`chats/${RIDE}/messages`).get());
    await assertFails(db(CAROL).collection(`chats/${RIDE}/messages`).get());
    await assertFails(db(CAROL).collection(`chats/${RIDE}/messages`).add(msg(CAROL)));
  });
  it('cannot impersonate a sender', async () => {
    await assertFails(db(BOB).collection(`chats/${RIDE}/messages`).add(msg(ALICE)));
  });
  it('live location is members-only', async () => {
    await db(ALICE).doc(`rides/${RIDE}`).update({ status: 'IN_PROGRESS' });
    await assertSucceeds(db(BOB).doc(`liveLocations/${RIDE}/members/${BOB}`).set({ lat: 23.78, lng: 90.28 }));
    await assertFails(db(CAROL).doc(`liveLocations/${RIDE}/members/${BOB}`).get());
    await assertFails(db(BOB).doc(`liveLocations/${RIDE}/members/${ALICE}`).set({ lat: 1, lng: 1 }));
  });
});

describe('reviews and counters', () => {
  beforeEach(async () => {
    await seedPendingRequest(); await acceptBob();
    await db(ALICE).doc(`rides/${RIDE}`).update({ status: 'IN_PROGRESS' });
  });
  const reviewId = `${RIDE}_${BOB}_${ALICE}`;
  const review = () => ({
    rideId: RIDE, reviewerId: BOB, reviewerName: 'Bob', revieweeId: ALICE, rating: 5, comment: 'Great',
    createdAt: FieldValue.serverTimestamp(),
  });

  it('cannot review before completion', async () => {
    await assertFails(db(BOB).doc(`reviews/${reviewId}`).set(review()));
  });
  it('owner completion increments all members once', async () => {
    const d = db(ALICE);
    const b = d.batch();
    b.update(d.doc(`rides/${RIDE}`), { status: 'COMPLETED' });
    b.update(d.doc(`users/${ALICE}`), { completedRides: 1, lastCompletedRideId: RIDE });
    b.update(d.doc(`users/${BOB}`), { completedRides: 1, lastCompletedRideId: RIDE });
    await assertSucceeds(b.commit());
    // A second increment after the ride is already completed must fail.
    await assertFails(db(ALICE).doc(`users/${BOB}`).update({ completedRides: 2, lastCompletedRideId: RIDE }));
  });
  it('review + rating counters are atomic, unique and immutable', async () => {
    await db(ALICE).doc(`rides/${RIDE}`).update({ status: 'COMPLETED' });
    const d = db(BOB);
    const b = d.batch();
    b.set(d.doc(`reviews/${reviewId}`), review());
    b.update(d.doc(`users/${ALICE}`), { ratingSum: 5, ratingCount: 1, lastReviewId: reviewId });
    await assertSucceeds(b.commit());

    await assertFails(d.doc(`reviews/${reviewId}`).set(review()));               // duplicate
    await assertFails(d.doc(`reviews/${reviewId}`).update({ rating: 1 }));        // immutable
    await assertFails(d.doc(`users/${ALICE}`).update({ ratingSum: 10, ratingCount: 2, lastReviewId: reviewId }));
  });
  it('outsiders cannot review', async () => {
    await db(ALICE).doc(`rides/${RIDE}`).update({ status: 'COMPLETED' });
    await assertFails(db(CAROL).doc(`reviews/${RIDE}_${CAROL}_${ALICE}`).set(
      Object.assign(review(), { reviewerId: CAROL })));
  });
});
