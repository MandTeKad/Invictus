# Invictus — Instructions for Claude (GitHub handoff)

The user has a phone only right now, but owns a computer. They want a native Android APK built by GitHub Actions, downloadable on their phone. They will hand you this ZIP and ask you to push it to their GitHub repository.

## Required work

1. Extract the ZIP and commit the **contents of the `InvictusAndroid/` directory to the repository root** (i.e. `.github/workflows/build-android.yml` must end up in the repo root). Prefer a private repo. Never request or store GitHub credentials in source files.
2. Use the included workflow `.github/workflows/build-android.yml` to build `:app:assembleDebug` on GitHub Actions. The project does not ship with a Gradle wrapper binary, so the workflow installs Gradle 8.11.1 using `gradle/actions/setup-gradle@v4`.
3. Push to `main`. The build runs automatically; alternatively choose Actions > Build Invictus APK > Run workflow.
4. **Review the logs and fix any actual build failures**, then rerun. This source has not been compiled in the originating environment; do not claim the APK is ready before a green build.
5. Once green, instruct the user to open the workflow run > Artifacts > `Invictus-debug-APK`, download and unzip that artifact on their Android phone, then open `app-debug.apk` to install. Android may ask them to allow installing unknown apps for their browser/file manager.
6. Commit all build fixes. Do not publish a release APK or expose private training data. The debug APK is for personal sideloading, not Play Store distribution.

## What the app currently provides
- Kotlin + Jetpack Compose interface; dark red and gold identity, golden laurel splash fade.
- Room-based offline workout sets and body-weight logging, Monday–Thursday templates, recovery check-in.
- Android sensor-based step collection **only while the app is open**; estimated walking calories above resting energy. A complete background/day step total and Health Connect are **not** built yet.
- A local-only proof of concept, not a medically validated coach or complete v1.0 app.

## Common checks
- JDK 17 / Android Gradle Plugin 8.9.2 / Kotlin 2.1.20 / compileSdk 35 / Gradle 8.11.1.
- If Compose compilation fails, fix imports and Compose API mismatches, preserving intended UX.
- Ensure the Android 10+ `ACTIVITY_RECOGNITION` runtime permission is requested (currently implemented).
- Avoid adding cloud APIs, telemetry, billing, or secrets.
- Verify `.github` hidden directory is present in repository.

## User's Android installation steps
1. In GitHub on mobile, open the repo's **Actions** tab.
2. Open latest successful **Build Invictus APK** run.
3. Under **Artifacts**, tap **Invictus-debug-APK**.
4. Unzip downloaded archive and open `app-debug.apk` from the Files app.
5. Allow installation from that source if prompted, then open Invictus.

If a run fails, inspect logs and send the user an accurate error explanation, never a fabricated APK link.
