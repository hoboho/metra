# METRA — Work & Earnings Tracker

An offline-first Android app for project-based workers who are paid per meter
laid above a daily threshold. Persian (Farsi) UI, Jalali calendar, Toman
currency, A4 Persian PDF reports.

> مترا — ثبت کارکرد روزانه، هزینه‌ها و درآمد، با گزارش PDF فارسی

---

## What it does

A worker records, for each workday: the project, work area, employer,
supervisor, worker count, working hours, **daily meters**, expenses, and any
income the company reported (fixed salary, mission allowance, overtime, other).

METRA calculates exactly one thing:

```
additionalMeters  = max(dailyMeters - thresholdMeters, 0)
additionalPayment = additionalMeters × ratePerMeter
```

Defaults: threshold **400 m**, rate **15 000 Toman/m**. Both are configurable
and **versioned by effective date** — a rate change applies forward only, so a
record from last year keeps last year's rate forever.

Everything else the app shows as income was typed in by the user and is labelled
receivables. METRA has no payroll, tax, insurance or deduction logic,
and it never silently nets expenses against salary.

| Daily meters | Additional meters | Payment (15 000 T/m) |
|---|---|---|
| 0 | 0 | 0 |
| 1 | 0 | 0 |
| 399 | 0 | 0 |
| 400 | 0 | 0 |
| 401 | 1 | 15 000 |
| 520 | 120 | **1 800 000** |

---

## Build

### Requirements

| | |
|---|---|
| JDK | 21 |
| Android SDK | platform 37, build-tools 37.0.0 |
| Gradle | 9.6.0 (via the wrapper) |
| Android Studio | Narwhal 3 Feature Drop or newer (AGP 9.4.0) |

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/Android/sdk
```

### Android Studio

1. `File → Open…` → select this directory.
2. Let Gradle sync (AGP 9.4.0 requires Gradle ≥ 9.6.0; the wrapper handles it).
3. `Build → Build Bundle(s)/APK(s) → Build APK(s)`.

### Command line

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # release APK (unsigned)
./gradlew :app:testDebugUnitTest # JVM unit tests
./gradlew :app:lintDebug         # static analysis
```

Output lands in `app/build/outputs/apk/{debug,release}/`.

### Signing a release

`app/build.gradle.kts` reads an **optional** `keystore.properties` at the
project root. No key is committed to this repository, and the release build
produces an unsigned APK when the file is absent.

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

---

## Project layout

```
app/src/main/java/ir/metra/app/
├── core/        Android-facing utilities: date, format, pdf, backup,
│                share, notification, security, devtools
├── data/        Room entities, DAOs, repository implementations, DataStore
├── domain/      Models, calculators, repositories (interfaces), use cases
├── di/          Hilt modules and the Room migration list
├── feature/     Compose screens + ViewModels, one package per feature
└── ui/          Theme, navigation graph, shared Compose components
```

Full details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Tests

| Suite | Location | Runs here? |
|---|---|---|
| JVM unit tests | `app/src/test/` | **Yes** — `./gradlew :app:testDebugUnitTest` |
| Instrumented (Room, repositories) | `app/src/androidTest/` | Requires a device or emulator |

The JVM suite carries the financial proof: every threshold boundary (0, 1,
399, 400, 401, 520 m), overflow safety, rule-versioning stability, expense
aggregation, Jalali conversion round-trips, Persian digit handling, CSV
escaping and backup encryption.

The instrumented suite proves the parts only a real SQLite database can show —
soft deletes, cascade behaviour, denormalised expense totals, and the
recomputation-on-save path. It compiles in this environment but has not been
executed (no device available); see [Known limitations](#known-limitations).

---

## Data model

Eight normalized Room tables at schema version 1, with the schema exported to
`app/schemas/`. Full reference: [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md).

`user_profile` · `projects` · `work_records` · `expenses` · `payment_rules` ·
`app_settings` · `report_configurations` · `backup_metadata`

**Money is always an integer number of Toman** (`Long`). **Dates are epoch
days** (or epoch millis for timestamps). Jalali values are presentation only —
no Persian date string is ever the stored representation.

Migrations are additive; `fallbackToDestructiveMigration()` is not used
anywhere.

---

## Privacy

- **No `INTERNET` permission.** Verified on the built APK — the app cannot
  reach the network at all.
- No analytics, no ads, no account, no telemetry.
- Local database is the only source of truth.
- Optional app lock: PIN (PBKDF2-HMAC-SHA256, 150 000 iterations) with biometric
  as a convenience unlock.
- Optional AES-256-GCM encrypted backups (PBKDF2, 120 000 iterations).
- A restore never overwrites silently: it shows the incoming vs. existing
  counts, requires an explicit strategy, and writes a safety snapshot first.

---

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — layers, rules, PDF pipeline, backup format
- [Data model](docs/DATA_MODEL.md) — entities, columns, constraints, migration policy
- [User guide](docs/USER_GUIDE.md) — راهنمای کاربری (Persian)

---

## Known limitations

- **Instrumented tests have not been executed.** They compile cleanly
  (`:app:compileDebugAndroidTestKotlin` passes) but this environment has no
  device, emulator or KVM. The JVM suite is the executed proof.
- **PDF output has not been visually inspected** for the same reason. The
  rendering pipeline (RTL `StaticLayout`, A4 pagination, repeated table
  headers) compiles and is exercised by construction, but glyph shaping in
  Vazirmatn has not been eyeballed on a device.
- **Receipt photos** are stored as content URIs, not copied into app storage,
  so they can disappear if the user deletes the source file.
- **No cloud sync** — by design.

---

## Licences

- [Vazirmatn](https://github.com/rastikerdar/vazirmatn) font, SIL Open Font
  License 1.1. Copyright Saber Rastikerdar.
- [PersianDate](https://github.com/samanzamani/PersianDate) by Saman Zamani.
- AndroidX, Jetpack Compose, Kotlin — Apache License 2.0.
