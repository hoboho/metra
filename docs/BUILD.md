# Building and signing METRA for release

## What the build produces

| Task | Output | Signed? |
|---|---|---|
| `./gradlew :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | Debug key — installable |
| `./gradlew :app:assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` | Only if `keystore.properties` exists |
| `./gradlew :app:bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` | Only if `keystore.properties` exists |

**Google Play accepts `.aab` only.** APKs are for local testing.

By default there is no `keystore.properties`, so release output is deliberately
unsigned — no private key is committed to this repository.

---

## Creating a keystore

Run this **once**, on your own machine, and keep the file somewhere safe:

```bash
keytool -genkeypair -v \
  -keystore metra-release.jks \
  -alias metra \
  -keyalg RSA -keysize 2048 \
  -validity 10950 \
  -storepass 'YOUR_STORE_PASSWORD' \
  -keypass 'YOUR_KEY_PASSWORD' \
  -dname "CN=Your Name, OU=Mobile, O=Your Org, L=Tehran, C=IR"
```

`-validity 10950` is 30 years. Google Play App Signing removes most of the risk
of key expiry, but a short validity has bitten enough projects to be worth
avoiding.

> **If you lose this file you cannot publish an update to the same app.**
> Back it up in at least two places. If you enrol in Play App Signing (the
> default for new apps) Google holds the key that actually signs distributed
> builds, and your upload key becomes replaceable — which is the main reason to
> enrol.

---

## Configuring the build

Create `keystore.properties` in the **project root** (next to `settings.gradle.kts`):

```properties
storeFile=/absolute/path/to/metra-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=metra
keyPassword=YOUR_KEY_PASSWORD
```

`storeFile` may be absolute or relative to the project root.

Then build:

```bash
./gradlew :app:bundleRelease
```

`app/build.gradle.kts` reads this file if it exists and wires
`signingConfigs.release` automatically; when it is absent, release builds are
left unsigned rather than failing.

**`keystore.properties` must never be committed.** It is in `.gitignore` — verify
before pushing.

---

## Bumping the version

Google Play requires a strictly increasing `versionCode` for every upload:

```kotlin
// app/build.gradle.kts
versionCode = 2
versionName = "1.0.1"
```

---

## Requirements for a new Play listing (checked 2026-09)

- **targetSdk ≥ 36.** Google Play requires new apps to target Android 16 (API 36)
  from 31 August 2026. This project is already at `targetSdk = 36`.
- **64-bit native code.** N/A — METRA has no native libraries.
- **Data safety form.** METRA declares no `INTERNET` permission and collects
  nothing, so every answer is "no data collected or shared".
- **Privacy policy URL.** Required even for an app that collects nothing. Any
  stable public page will do.
- **Content rating questionnaire.** Must be completed in Play Console.
- **Closed testing.** New personal developer accounts must run a closed test
  with at least 12 testers for 14 days before applying for production access.
- **Store assets.** Icon 512×512, feature graphic 1024×500, at least two
  screenshots.

---

## Before you ship

Run the instrumented suite on a real device first. It compiles but has never
been executed in the development environment:

```bash
./gradlew :app:connectedReleaseAndroidTest
```

Also check the AAB rather than the APK, because R8 behaves identically but
resource shrinking can differ between the two paths:

```bash
./gradlew :app:bundleRelease
```
