# RideAlong: Project Status

> *Your Route. Your People. Your Ride.*
> The original specification is preserved in [`RideAlong_Master_Prompt.md`](RideAlong_Master_Prompt.md).

## Verification summary
| Check | Result |
|---|---|
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL with the real `app/google-services.json` (project `ridealong-f946d`) on the Dolphin toolchain |
| `./gradlew testDebugUnitTest` | ✅ 73 / 73 JVM tests pass |
| Firestore rules tests (`firestore-tests`, emulator) | ✅ 26 / 26 pass |
| `./gradlew lintDebug` | ✅ 0 errors (remaining warnings: intentionally pinned dependency versions, minor style) |
| Emulator seed script | ✅ verified against the local emulator; refuses to run without emulator env vars |
| **Running the app on a device/emulator** | ⚠️ **Not verified here.** The Android emulator can't start on this machine (hypervisor driver not installed) and no device was attached. All screens compile and were reviewed statically, but nothing has been exercised at runtime |

## Environment
| Tool | Version |
|---|---|
| Target IDE | Android Studio Dolphin 2021.3.1 |
| JDK (Gradle runtime) | 11 (Studio's bundled JDK); verified here with JDK 17 |
| Gradle wrapper / AGP | 7.5.1 / 7.3.1 |
| compileSdk / targetSdk / minSdk | 33 / 33 / 24 |
| Firebase BoM | 32.7.0 (Auth, Firestore) |
| Others | Material 1.9.0, Navigation 2.5.3, Room 2.5.2, Lifecycle 2.6.2, AndroidX Core 1.10.1, osmdroid 6.1.20, Glide 4.16.0, Play Services Location 21.0.1 / Auth 20.7.0 / Maps 18.2.0 |

## Roadmap
| # | Phase | Status |
|---|---|---|
| 1 | Project init, version catalog, wrapper, `.gitignore` | ✅ |
| 2 | Firebase files, `firestore.rules`, indexes, emulator config, rules tests, emulator-only seed | ✅ |
| 3 | Theme, design system, reusable components, navigation | ✅ |
| 4 | Auth (email, Google, reset link) + profile, photo, emergency contacts | ✅ |
| 5 | Map abstraction (OSM/OSRM/Nominatim, optional Google/ORS) + location picker | ✅ |
| 6 | Post ride + geohash discovery with filters | ✅ |
| 7 | Algorithms + unit tests | ✅ |
| 8 | Join requests, transactions, participants | ✅ |
| 9 | Chat, call logging, notifications | ✅ |
| 10 | Live location service, tracking map, geofencing | ✅ |
| 11 | Ratings, history, block/report | ✅ |
| 12 | SOS / emergency | ✅ |
| 13 | Room offline support + sync | ✅ |
| 14 | Tests, lint, docs | ✅ (runtime testing pending, see above) |

## Key architectural decisions
* **Spark plan only.** Multi-document workflows run as client transactions/batches, and the rules
  validate them by comparing `get()` (before) with `getAfter()` (after):
  * accept: request `PENDING→ACCEPTED`, seats decrease by exactly the requested seats, requester appended to `memberIds`
  * leave: request `ACCEPTED→LEFT`, seats released, member removed
  * review: review doc created and the reviewee's `ratingSum`/`ratingCount` increased by exactly that rating
  * complete: ride `IN_PROGRESS→COMPLETED` and each member's `completedRides` +1, once
* **Deterministic ids** (`rideId_uid` requests, `rideId_reviewer_reviewee` reviews, `blocker_blocked`
  blocks) make duplicates impossible.
* **Privacy:** phone and emergency contacts live in `users/{uid}/private/info` (owner only). The phone a
  co-rider needs is published per ride in `rides/{id}/contacts/{uid}` (members only).
* **Profile photos** are stored as Base64 (≤ ~100 KB) in the user doc instead of Storage.
* **Maps** sit behind interfaces with one setting. The free stack is the default, with a Haversine fallback.
* **Notifications** use Firestore + local notifications. Recipients render text in their own language
  from `type` + `args`.

## Required external configuration
1. Firebase project (Spark), Android app `com.example.ridealong`, debug **SHA-1**.
2. Enable Email/Password and Google sign-in.
3. Create Firestore, then `firebase deploy --only firestore:rules,firestore:indexes`.
4. Place `google-services.json` in `app/`.
5. *(Optional)* `MAPS_API_KEY`, `ORS_API_KEY` in `local.properties`.
6. Run on an emulator with Google Play services, or on a device.

See [SETUP_GUIDE.md](SETUP_GUIDE.md). Emulator-only mode (no Firebase project) is also documented there.

## Recent additions
* **Private chat** poster <-> requester, **Home chat icon** with unread badge, Messages inbox.
* **Message privacy:** notifications never contain message text.
* **In-app voice calls** (WebRTC `com.dafruits:webrtc:123.0.0`, Firestore signaling, rules-protected
  `calls` collection, foreground `microphone` service, full-screen incoming call). Rules tests: 38 passing.
* **Dark mode** with Light / Dark / System setting.
* **Video calls** in the private chat (camera optional; falls back to voice-only sending if the camera is denied).
* ⚠️ Voice calls have not been tested between two real phones from this machine (no device attached).

## Known limitations
* In-app calls between two phones on mobile data usually need a TURN relay (configure `TURN_*`, see SETUP_GUIDE).
* No push to a killed app (needs FCM + Functions → Blaze). An optional background-sync foreground service is available.
* Calls/SMS require the user to press Call/Send (an Android restriction). The app never claims an alert was sent.
* Automatic center-square crop for avatars (no manual crop UI).
* Public OSRM/Nominatim are fair-use; add an ORS key or self-host for scale.
* Bangla translation is partial (main screens); the rest falls back to English.
