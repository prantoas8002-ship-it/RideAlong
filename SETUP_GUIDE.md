# RideAlong: Setup Guide

Requirements: **Android Studio** (Ladybug or newer), **JDK 17+**, Android SDK platform 35. Gradle 8.9
and AGP 8.7.2 are pinned through the wrapper and `gradle/libs.versions.toml`.

Without any configuration the project **builds**, but the app shows a "Firebase isn't configured"
screen at launch. You have two options:

* **A. Real Firebase project (Spark plan, free).** Use this for real devices and real users.
* **B. Firebase Emulator Suite only.** Use this for local development; no Google account is needed.

---

## A. Firebase project (Spark plan)

### 1. Create the project
1. Open <https://console.firebase.google.com> → **Add project** (Analytics not needed).
2. Stay on the **Spark (free)** plan. RideAlong doesn't use Cloud Functions or Storage.

### 2. Register the Android app
1. **Project settings → Your apps → Add app → Android**.
2. Package name: `com.example.ridealong`.
3. Add the **SHA-1** of your debug key (required for Google Sign-In):
   ```bash
   ./gradlew signingReport        # copy SHA1 of variant "debug"
   ```
   (Add the release SHA-1 too when you sign a release build.)
4. Download **`google-services.json`** and put it in **`app/`** (it is gitignored, never commit it).
   `app/google-services.json.example` shows the expected shape.

### 3. Authentication
**Build → Authentication → Sign-in method**, then enable:
* **Email/Password**
* **Google**: set a support email. Re-download `google-services.json` after enabling Google so it
  contains the web OAuth client (`default_web_client_id`).

Password reset uses Firebase's built-in **reset-link email**. You can customise the template under
*Authentication → Templates*.

### 4. Firestore
1. **Build → Firestore Database → Create database** (production mode, pick a nearby region, e.g. `asia-south1`).
2. Deploy the rules and indexes with the Firebase CLI:
   ```bash
   npm install -g firebase-tools        # or use firestore-tests/node_modules/.bin/firebase
   firebase login
   cp .firebaserc.example .firebaserc   # put your project id inside
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   Index creation takes a few minutes. Until it finishes, queries show a "missing index" error.

### 5. (Optional) Remote configuration
Create the document **`config/app`** to tune values without a new release, for example:
```json
{
  "matching": { "weightPickup": 0.25, "weightTime": 0.25, "weightRoute": 0.35, "weightDestination": 0.15,
                "maxPickupDistanceKm": 3, "maxTimeDifferenceMinutes": 90, "defaultSearchRadiusKm": 5 },
  "fareRates": { "CNG": { "baseFare": 40, "perKm": 18, "minimumFare": 60 },
                 "CAR": { "baseFare": 60, "perKm": 30, "minimumFare": 150 } },
  "locationUpdateIntervalMs": 10000
}
```
Clients can read `config/*` but never write it.

### 6. (Optional) Map keys
Copy `local.properties.example` values into `local.properties`:
* `MAPS_API_KEY`: enables **Google Maps** as a choice in *Settings → Map provider* (plus Google
  Geocoding / Routes when that provider is selected). Needs a Google Cloud project with billing.
* `ORS_API_KEY`: free [OpenRouteService](https://openrouteservice.org/dev/#/signup) key, used for routing
  before the public OSRM server.

Leave both empty to use the free OpenStreetMap + OSRM + Nominatim stack.

### 7. Run
Open the folder in Android Studio, let Gradle sync, and run the `app` configuration on an emulator
(API 24+, **with Google Play services** for Google Sign-In, location and geofencing) or a real device.

---

## B. Emulator-only development mode

1. Install the tools: `cd firestore-tests && npm install`.
2. Start the emulators from the project root:
   ```bash
   firestore-tests/node_modules/.bin/firebase emulators:start --only auth,firestore --project demo-ridealong
   ```
3. In `local.properties` set:
   ```properties
   USE_FIREBASE_EMULATOR=true
   FIREBASE_EMULATOR_HOST=10.0.2.2       # your PC's LAN IP when using a real device
   FIREBASE_EMULATOR_PROJECT_ID=demo-ridealong
   ```
   Without `google-services.json` the app initialises Firebase with this `demo-*` project id, which by
   Firebase convention only talks to the emulators.
4. (Optional) Seed test users and a ride **into the emulator only**:
   ```bash
   cd firestore-tests
   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 npm run seed
   ```
   This creates `tester1@ridealong.test` / `tester2@ridealong.test` (password `test1234`). The script
   refuses to run unless both emulator variables are set. The app itself never contains sample data.
5. Google Sign-In does not work against the Auth emulator. Use email/password.

---

## Tests

```bash
./gradlew testDebugUnitTest                 # algorithms, validators, state machine (JVM)
cd firestore-tests && npm test              # security rules (starts the Firestore emulator; needs Java 11+)
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Firebase isn't configured" screen | Add `app/google-services.json` (or use emulator mode) and rebuild |
| Google Sign-In "isn't configured" | Enable Google provider, add SHA-1, re-download `google-services.json` |
| Google Sign-In error 10 | SHA-1 of the build you're running isn't registered |
| "missing Firestore index" | Run `firebase deploy --only firestore:indexes` and wait for the build |
| PERMISSION_DENIED | Rules not deployed, or the action isn't allowed (e.g. joining your own ride) |
| Map tiles blank | Device offline or tile server blocked. Routes fall back to straight lines |
| No notifications in background | Expected without Blaze/FCM. Enable *Settings → Background sync* |
