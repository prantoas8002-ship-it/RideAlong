# RideAlong: Project Status

> *Your Route. Your People. Your Ride.*
> The original specification is preserved in [`RideAlong_Master_Prompt.md`](RideAlong_Master_Prompt.md).

## Environment (verified)
| Tool | Version |
|---|---|
| JDK (Gradle runtime) | Temurin 21.0.12 (JDK 17 also installed) |
| Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | 8.7.2 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 24 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` |

## Architecture
MVVM + Repository, single `MainActivity` with Navigation Component fragments, plus separate
`SplashActivity`, `OnboardingActivity` and `AuthActivity`.

```
com.example.ridealong
  data/       local (Room), remote (Firestore paths/mappers), repository, model
  domain/     algorithm (GeoHash, Haversine, Dijkstra, RouteMatcher, PolylineCodec), calculator (Fare),
              config, model (pure-Java value types), validator, RideStateMachine
  map/        MapProvider / RoutingProvider / GeocodingProvider + OSM, OSRM, Nominatim (+ optional Google / ORS)
  ui/         auth, onboarding, home, rides, requests, chat, tracking, profile, settings, emergency, notifications, common
  service/    LocationShareService, GeofenceBroadcastReceiver, notification listener
  utils/      permissions, image compression, date/time, constants, network monitor
```

Key decisions:
* **Spark plan only.** No Cloud Functions and no Storage. All multi-document logic runs in client
  **transactions/batches**, and `firestore.rules` validates them with `get()`/`getAfter()`
  (seat changes, membership, rating counters, completed-ride counters).
* **Profile picture** is center-cropped to 256x256 JPEG (<100 KB), stored as Base64 in `users/{uid}`.
* **Private data split:** phone and emergency contacts live in `users/{uid}/private/info` (owner only).
  The phone number a co-rider needs for calling is published per ride in `rides/{rideId}/contacts/{uid}`,
  which only ride members can read.
* **Maps are provider-agnostic.** OSM (osmdroid) + OSRM + Nominatim by default with no key. Google Maps
  SDK, Google Geocoding and OpenRouteService are used only when a key is present. Routing falls back to
  Haversine straight-line distance when offline.
* **Notifications** come from Firestore listeners plus local notifications (no FCM server push on Spark
  without Functions; see the README limitations).

## Roadmap
| # | Phase | Status |
|---|---|---|
| 1 | Project init, Gradle catalog + wrapper, folders, `.gitignore`, status file | DONE |
| 2 | Firebase setup files, Firestore rules / indexes, emulator config | TODO |
| 3 | Theme, design system, reusable components, navigation | TODO |
| 4 | Auth (email, Google, reset) + profile with picture and emergency contacts | TODO |
| 5 | Map abstraction (OSM/OSRM/Nominatim) + location picker | TODO |
| 6 | Post ride + geohash nearby discovery with filters | TODO |
| 7 | Algorithms + unit tests | TODO |
| 8 | Join requests + participant management (transactions) | TODO |
| 9 | Real-time chat, call logging, in-app notifications | TODO |
| 10 | Live location service, tracking map, geofencing | TODO |
| 11 | Ratings, history, block/report | TODO |
| 12 | SOS / emergency | TODO |
| 13 | Room offline support + sync | TODO |
| 14 | Testing, optimization, docs | TODO |

## Required external configuration (cannot be done from code)
1. Create a Firebase project (Spark plan).
2. Enable Authentication: Email/Password and Google. Add the debug **SHA-1** (`./gradlew signingReport`).
3. Create a Firestore database, then deploy `firestore.rules` and `firestore.indexes.json`.
4. Download `google-services.json` into `app/`.
5. *(Optional)* `MAPS_API_KEY` / `ORS_API_KEY` in `local.properties`.
6. Run on an emulator or device.
