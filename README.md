# INVICTUS — Native Android personal training app (v0.1 source)

Native Kotlin/Jetpack Compose proof-of-concept with persistent Room storage.

## Present functionality
- Command dashboard with current day, phase, local history summary
- Forge with Monday–Thursday planned exercises, manual weights/reps/sets
- Records with saved workout sets, deletion, and body-weight entries
- Recovery check-ins: hours slept, fatigue, previous-day steps, symptoms
- Offline-only storage, no sign-in, no permissions, no subscriptions

**Not built/verified as an APK in this environment:** no Android SDK or Gradle wrapper binary is installed here. The included source project is intended to be synced and built in Android Studio on your computer. This is not a production-hardened fitness or medical app.

## Simplest way to run
1. Install current Android Studio from https://developer.android.com/studio.
2. Unzip `InvictusAndroid.zip` and choose **Open** in Android Studio; choose its `InvictusAndroid` folder.
3. Allow Android Studio to install any requested Android SDK components and perform Gradle Sync. It uses Android Gradle Plugin 8.9.2 and Kotlin 2.1.20 (baseline versions, deliberately pinned).
4. If Studio does not automatically create/use a Gradle wrapper, create a fresh Android Studio project with an Empty Activity, then copy over `app/src`, `app/build.gradle.kts`, and the root/settings Gradle files, or generate a wrapper with locally installed Gradle (`gradle wrapper --gradle-version 8.11.1`).
5. On Android: enable Developer options by tapping **Build number** seven times in Settings → About phone → Software information, then enable USB debugging. Connect USB, authorize your computer and choose **Run** in Android Studio.
6. Alternatively choose Build → Build APK(s), then transfer the debug APK to the phone and install it after granting the required local installation permission.

Android Studio may suggest updating pinned build dependencies; verify compatibility before upgrading several at once.

## Data and limits
- App keeps Room database privately on the device. Uninstalling the app deletes app data. Export and encrypted backups are planned, **not implemented**.
- Current v0.1 set logging uses 0 lb to indicate bodyweight or time-based drills; the field `reps / min` is a temporary compromise, to be replaced by typed measurement units.
- Session ID is created when the activity is composed; use it as a working logging prototype, not a final historical session management system.
- At present training plans are static templates. Recovery advice is educational, transparent rules only; it isn't a medical diagnosis.
- Monday–Thursday templates correspond to the later TREX phase, **not** the initial four-week at-home ramp-up. Start appropriately conservatively.
- The weight target is an optional ambitious outcome; preserving strength, muscle, and performance takes precedence.

## Next milestones
v0.2: persistent workout-session lifecycle, rep-target intelligence, rest timer, exercise substitution and 4-week starter plan.
v0.3: unit-tested training rules, fatigue and missed-day adjustment logic, deload support.
v0.4+: export/backup, analytics, combat timers, nutrition, optional Health Connect and AI.

## Architecture
- `Database.kt`: Room entities, DAO and local persistence
- `TrainingPlan.kt`: program templates and simple transparent advice
- `MainActivity.kt`: Compose UI

Future versions should split UI, domain and data into modules and add automated tests before scaling features.


## v0.1.1 update
- Added an Invictus splash screen with a golden laurel wreath on a dark red background.
- Splash fades into the main app automatically on startup.
- Updated the app color identity to dark red and gold.

## v0.1.2 — Movement and calorie estimation
- Adds a Movement tab with `Sensor.TYPE_STEP_COUNTER`, runtime activity-recognition permission, and locally persisted foreground step increments.
- **Important limitation:** counts only sensor increments during foreground app use, not full-day totals while closed. A complete daily step counter requires Health Connect aggregation or a carefully designed foreground service, and remains a later milestone.
- Estimates **net calories above rest** for level walking, not total calories burned. Uses estimated stride length (0.415 × height), weight and 0.45 kcal/kg/km, default 0.8 calibration factor, and a rough ±30% illustrative range. Neither factor nor range is scientifically validated for this individual.
- Calibration setting is stored locally; no automatic calorie-expenditure calibration yet.
- Sensor tracking does not require location, GPS or Internet.

## GitHub Actions build (phone-only installation)
This source includes `.github/workflows/build-android.yml`. Push this folder's **contents** to the root of a GitHub repository. Push to `main` or select Actions > Build Invictus APK > Run workflow. After a successful run, download the `Invictus-debug-APK` artifact and install the contained `app-debug.apk` on Android. See `CLAUDE_GITHUB_HANDOFF.md` for complete instructions. The debug build is for personal testing. No APK has been verified yet.
