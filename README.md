# RideAlong

> **Your Route. Your People. Your Ride.**

A passenger-to-passenger ride-sharing and cost-sharing Android app for students, university
commuters and office workers in Bangladesh. Anyone can **post** a ride (pickup, destination, time,
cost, seats) or **join** one. Accepted riders chat in real time, call each other through the phone
dialer, share live location, use an SOS screen, and rate each other after the ride.

Everything runs on **free tiers**: Firebase Spark plan (Auth + Firestore only), OpenStreetMap,
OSRM and Nominatim. No Cloud Functions, no Firebase Storage, no Google Maps billing.

Setup steps are in **[SETUP_GUIDE.md](SETUP_GUIDE.md)**; build status and limitations are in
**[PROJECT_STATUS.md](PROJECT_STATUS.md)**.

---

## Features

| Area | What works |
|---|---|
| Auth | Email/password, Google Sign-In, password-reset **link** by email, logout, delete account (with re-auth) |
| Profile | Name, phone, organization, gender, photo (camera/gallery → center-crop 256×256 JPEG < 100 KB → Base64), 1–5 emergency contacts, rating & completed rides |
| Post ride | Map pin / place search for pickup & destination, date/time, transport (CNG/rickshaw/car/bus), seats 1–8, cost, max detour, comment, route preview, **fare estimate** (labelled as estimate) |
| Discover | Geohash-based nearby feed (radius in Settings), destination / time window / seats / max-cost filters, **compatibility score** + detour per ride, list & map views |
| Requests | Send / cancel / accept / decline / leave. Duplicates, overbooking and self-joining are blocked in the app **and** in `firestore.rules`. Accept and leave are **transactions** |
| Ride states | `OPEN → FULL → IN_PROGRESS → COMPLETED`, `CANCELLED`, defined in one place (`RideStateMachine`) and mirrored in rules |
| Chat | One chat per ride (members only), real-time, full history (Room cache offline), unread counts, call events logged ("Zed called Rafi") |
| Calling | `ACTION_DIAL` with the co-rider's phone, from a member-only contact card (no `CALL_PHONE` permission) |
| Live location | Foreground service (`location` type), consent dialog, throttled writes, members-only markers, auto-stop on completion/cancellation |
| Geofencing | Pickup proximity + arrival alerts via the Geofencing API; falls back to in-app distance checks without background location |
| SOS | Big button → confirmation → location → dialer for an emergency contact + SMS with an OpenStreetMap link. Never claims anything was sent. 999 notice and button. Local + Firestore log |
| Ratings | 1–5 stars + review after completion, one per (ride, reviewer, reviewee), immutable; rating counters updated atomically with the review and verified by rules |
| History | Posted / joined rides, cached in Room, read-only offline |
| Safety | Block (hides rides both ways, rules stop requests/notifications) and report |
| Notifications | In-app notification list + local Android notifications from Firestore listeners; optional background-sync service |
| Offline | Firestore persistence, Room caches, offline banner, sync on reconnect, "Cached" labels |
| UI | Material 3, brand colors, reusable ride card / avatar / rating / empty / loading / error components, English + partial Bangla |

## Algorithms (pure Java, unit-tested)

* **GeoHash** (`domain/algorithm/GeoHash.java`): encode/decode/neighbours/radius query bounds, no dependency.
* **Haversine** and **point-to-segment** distance (`Haversine`, `GeometryUtils`).
* **Dijkstra** (`DijkstraRouter`): graph from the route polyline (densified) plus the passenger's pickup and drop
  nodes, joined by connector edges to the nearest route nodes and their exact perpendicular projections. It
  computes the **shared distance**, the **detour** `d(start→P→D→end) − d(start→end)`, the **fare distance**
  and the direction of travel.
* **RouteMatcher**: weighted 0–100 score (pickup 25%, time 25%, route overlap 35%, destination 15%, all
  configurable), with hard rejections for seats, pickup distance, time, detour, destination and wrong direction.
* **FareCalculator**: Bangladesh rates per transport type, split per rider, proportional passenger share.

## Architecture

MVVM + Repository with manual DI (`ServiceLocator`).

```
com.example.ridealong
  data/      local (Room), remote (FirebaseProvider, Firestore LiveData), repository, model
  domain/    algorithm, calculator, config (AppConfig/MatchingConfig/FareConfig), model, validator
  map/       MapProvider / RoutingProvider / GeocodingProvider; osm, osrm, nominatim, ors, google
  ui/        auth, onboarding, home, rides, requests, chat, tracking, profile, settings, emergency, notifications, common
  service/   LocationShareService, GeofenceManager/Receiver, RealtimeNotificationListener, RealtimeSyncService
  utils/     permissions, images, date/time, intents, network, constants
```

Configurable values live in `AppConfig`, `MatchingConfig` and `FareConfig`. They can be overridden
without a release through the Firestore document `config/app` (see SETUP_GUIDE).

## Tests

```bash
./gradlew testDebugUnitTest          # 73 JVM tests: GeoHash, Haversine, point-to-segment, polyline,
                                      # Dijkstra, RouteMatcher, FareCalculator, validators, seat rules, state machine
cd firestore-tests && npm install && npm test   # 26 security-rules tests on the Firestore emulator
```

## Known limitations (honest)

* **No push when the app is killed.** Server push needs FCM + Cloud Functions, which require the paid
  Blaze plan. Notifications arrive while the process is alive. The optional *Background sync* setting
  keeps a foreground service running, but Android may still stop it (battery savers, Android 15
  `dataSync` time limits).
* **Calls and SMS are never automatic.** Android doesn't allow normal apps to place calls or send SMS
  silently. RideAlong opens the dialer / SMS composer and the user presses Call / Send.
* **Profile photo cropping** is an automatic center-square crop (no manual crop UI), so no extra library
  is needed.
* The public **OSRM / Nominatim** servers are fair-use services. For heavy use, add an OpenRouteService
  key or self-host. Routing falls back to a straight-line estimate when they're unreachable.
* `completedRides` / rating counters are maintained by rules-validated client batches (no server code),
  so they're tamper-resistant but not computed by a trusted backend.
