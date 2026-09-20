# METRA — Architecture

Single Gradle module (`app`), three layers, strict inward dependency:

```
feature  (Compose + ViewModel)
   ↓
domain   (models, calculators, repository interfaces, use cases)
   ↑
data     (Room, DataStore, repository implementations)

core     shared utilities, usable by all three
```

`feature` never touches Room. `domain` has no Android imports except
`android.graphics` inside the PDF renderer. Business rules live in `domain` and
nowhere else — no arithmetic on money appears in a Compose file.

---

## Package map

| Package | Responsibility |
|---|---|
| `core/date` | `JalaliCalendar`, `JalaliDate`, `DateRange`. Epoch-day storage, Jalali presentation. |
| `core/format` | `NumberFormatter`, `DateFormatter`, `PersianDigits`. |
| `core/pdf` | `PdfReportEngine` — the only Android renderer. `PdfModel` is pure Kotlin. |
| `core/backup` | `BackupCipher` (AES-256-GCM), `BackupPayload` (serializable DTOs), `AppInfo`. |
| `core/share` | `FileSharer` — ACTION_SEND via FileProvider. |
| `core/notification` | `ReminderNotifier`, `ReminderScheduler`, boot/receiver wiring. |
| `core/security` | `PinManager` (PBKDF2), `LockSession`, `BiometricAuthenticator`. |
| `core/devtools` | `SampleDataGenerator` — **never called from production code.** |
| `domain` | Calculators, aggregates, repository interfaces, use cases. |
| `data/local` | 8 entities, DAOs, `MetraDatabase`. |
| `data/repository` | Repository implementations. |
| `data/preferences` | DataStore for device-only settings. |
| `di` | Hilt modules, `Migrations.ALL`. |
| `feature/*` | One package per screen: Compose screen + ViewModel. |
| `ui` | Theme, `MetraNavHost`, shared components. |

---

## The one calculation

`PaymentCalculator` is the sole home of earning math. Nothing else computes it.

```kotlin
fun additionalMeters(dailyMeters: Int, thresholdMeters: Int): Int
fun additionalPayment(additionalMeters: Int, ratePerMeter: Long): Long
fun additionalPayment(dailyMeters: Int, rule: PaymentRule): Long
```

Negative inputs throw rather than clamping silently. Results are `Int` for
meters and `Long` for Toman — 10 000 000 m at 15 000 T/m is 149 994 000 000
Toman, which overflows `Int`.

### Why snapshots

Each `WorkRecord` stores `thresholdMetersSnapshot` and `ratePerMeterSnapshot`
alongside the computed `additionalMeters` and `additionalMeterPayment`. The rule
is selected by `PaymentRuleSelector.applicableRule(rules, workDateEpochDay)` —
the newest rule whose `effectiveFromEpochDay <= workDate`, falling back to the
earliest rule when the workday predates every rule (paying nothing there would
silently destroy the record).

Adding a new rate therefore cannot re-price history. This is asserted in
`PaymentRuleSelectorTest` (pure) and `WorkRecordRepositoryTest` (against Room).

### What is *not* calculated

`fixedSalary`, `missionAllowance`, `overtimeAmount`, `otherIncome` are
derived from work records. `WorkTotals` keeps
`recordedCompanyIncome` (user-entered only) separate from
`totalRecordedIncome` (which adds METRA's own calculation), so a report can
label each figure honestly.

Expenses are a separate column. `netRecordedAmount` = income − expenses is
exposed but always labelled as a personal net figure, never as a payroll
result. It may legitimately be negative.

---

## Strings and localisation

The default locale **is** Persian: `values/strings.xml` holds the full catalogue
(386 strings) and `values-en` is a thin override, not a translation target.
`MissingTranslation` is therefore disabled in `app/lint.xml` with that reason
recorded next to it — the default locale is complete, so nothing is actually
untranslated.

Compose screens use `stringResource`. ViewModels, repositories and domain
services cannot, so they take an injected `StringProvider`
(`core/i18n/StringProvider.kt`, an interface with an `AndroidStringProvider`
implementation bound in `PlatformModule`). No user-facing Persian literal
remains in the `feature` or `data/repository` layers.

The one exception is deliberate: report and PDF content in `domain/report`
(`PdfReportComposer`, `ReportBuilder`, CSV headers) builds Persian document
text, and those layers are pure Kotlin by design — threading Android resources
through them would break their testability.

A related rule this enforces: **error type is never inferred from message
text.** `WorkEditorViewModel` routes a message to the project field by checking
`MetraError.Validation.Field.PROJECT_NAME`, not by searching the string for
«پروژه».

---

## Repositories

Interfaces in `domain/repository`, implementations in `data/repository`. All
mutations return `MetraResult<T>` (`Result<T>` wrapping a `MetraException`
carrying a sealed `MetraError` with a Persian `userMessage`).

Notable implementation behaviours:

- **`WorkRecordRepositoryImpl.upsert`** recomputes additional meters/payment and
  re-snapshots the applicable rule *from the record's own date*, inside a
  transaction, then refreshes the denormalised `expense_total`.
- **`duplicateAsNewDay`** copies context only (project, work area, employer,
  supervisor, worker count, working hours). Meters, money, expenses and notes
  are zeroed/cleared.
- **`PaymentRuleRepositoryImpl`** seeds the 400 m / 15 000 T default rule on
  first access, and re-seeds it if the user deletes the last rule.
- Room's `@Upsert` returns `-1` on UPDATE; the repositories map that back to the
  incoming row id so callers always get a usable id.

---

## Date handling

Storage is **epoch days** (`Long`) for calendar dates and **epoch millis** for
timestamps. Jalali is derived on demand.

`JalaliCalendar.dayOfWeek` is computed from the epoch day, *not* from
PersianDate's `dayOfWeek()`/`dayName()`, which are off by one. Pinned by
`JalaliCalendarTest` against known Gregorian weekdays.

`JalaliDate.isLeapYear` delegates to `PersianDate.isJalaliLeap`. A hand-written
2820-year rule was tried and abandoned: it disagrees with the library's 33-year
cycle at 1403/1404 and 1436/1437, and since every conversion goes through the
library, mixing the two would make month lengths inconsistent with conversions.

`JalaliDate.initJalaliDate(1404, 12, 30)` throws, so `plusJalaliMonths` clamps
through `daysInMonth` first.

---

## PDF pipeline

```
ReportBuilder          → ReportData (paged, 500 rows/page)
PdfReportComposer      → PdfDocumentSpec  (pure Kotlin blocks)
PdfReportEngine        → PdfDocument      (Android: StaticLayout + Canvas)
ReportFileWriter       → File in filesDir/reports
FileSharer             → ACTION_SEND
```

The split matters: `PdfModel` and the composer are pure Kotlin, so report
*content* is testable without a device. Only `PdfReportEngine` touches Android.

RTL is done with `StaticLayout` + `TextDirectionHeuristics.FIRSTSTRONG_RTL`.
`Canvas.drawText` is never used for Persian — it does not shape or reorder.
Page size is A4 (595 × 842 pt), margins 34/40/46. Table columns run
right-to-left and the header repeats on every page a table spans. The font is
copied from `assets/fonts` to a temp file (PdfDocument needs a real path), with
`Typeface.SANS_SERIF` as fallback.

---

## CSV / JSON export

`ReportFileWriter.writeCsv` writes UTF-8 **with a byte-order mark** — without it
Excel misreads Persian text. Sixteen Persian headers; RFC-4180 quoting via
`CsvEscaping`, which also quotes any field starting with `=`, `+`, `-` or `@`
so a spreadsheet cannot execute user-typed text as a formula.

`BackupManager.exportJson` writes the full `BackupPayload` as JSON.

---

## Backup format

```
METRAENC1 | salt length (2 bytes) | salt | IV (12 bytes) | AES-256-GCM ciphertext
```

Key derivation: PBKDF2-HMAC-SHA256, 120 000 iterations. An unencrypted backup is
plain JSON starting with `METRA-BACKUP`. `formatVersion = 1` is stamped
explicitly so an incompatible file is rejected with a Persian message rather
than half-imported.

Inside a payload, expenses reference their work record by **list index** and
projects are matched by **name** — local database ids are meaningless in another
installation. Rule snapshots are imported verbatim.

Restore is never silent:

1. The user picks a file; `preview()` decodes it and shows incoming vs. existing
   counts.
2. The user chooses a strategy (`ADD_ONLY`, `MERGE`, `REPLACE`).
3. `restore()` writes `filesDir/backups/metra-safety-<ts>.metra` **before**
   touching the database, and logs a row in `backup_metadata` with a SHA-256.

`AEADBadTagException` maps to `MetraError.WrongPassphrase`.

---

## Notifications

A daily reminder uses an exact alarm that **re-arms itself from the receiver**
rather than `setRepeating`. If `SecurityException` is thrown (exact alarms
denied), it falls back to `setAndAllowWhileIdle`. The receiver notifies only
when today has no record *and* `lastReminderNotifiedEpochDay != today`, so a
reboot cannot double-notify.

---

## App lock

PIN → PBKDF2-HMAC-SHA256, 150 000 iterations, 16-byte salt, stored as
`salt$hash` hex in DataStore. Comparison uses `MessageDigest.isEqual`.
`LockSession` is a single in-memory boolean — no credential is retained, so a
process death re-locks the app. `MainActivity.onStop()` re-locks unless
`isChangingConfigurations`.

Biometrics (androidx.biometric, which requires `FragmentActivity`) are a
convenience unlock only; the PIN is always the fallback.

---

## Prefill

`BuildPrefill` precedence: **selected project defaults → previous workday →
global defaults.** Working hours come *only* from the previous workday. Meters,
money and notes are never copied. `PrefillSource` drives a visible banner so the
user can see and override where each value came from. The behaviour is
toggleable via `usePreviousWorkdayInfo`.

---

## Work log

`WorkRecordFilter` (date range, project, employer, supervisor, work area,
min/max meters, only-with-additional, only-with-expenses) compiles to a single
DAO query using the `(:param IS NULL OR column = :param)` idiom. Sorting is in
memory via `WorkRecordSort.comparator()`. Search goes through
`observeSearch(query)`. The calendar view is built from
`observeRecordsInRange`, with leading blanks from `JalaliCalendar.dayOfWeek`
(Saturday-first).

---

## Dependency injection

Hilt, KSP. `AppModule` provides the database and DAOs; `PlatformModule` provides
`Clock`, dispatchers, `FileSharer`, and the directory providers.
`Migrations.ALL` is `emptyArray()` at version 1 and is passed to
`addMigrations(*Migrations.ALL)`. There is no
`fallbackToDestructiveMigration()` call anywhere in the project.

`Clock` is an injected `fun interface`, so time is fakeable in tests.

---

## Build configuration notes

Things that are non-obvious and were measured rather than assumed:

- **AGP 9.4.0 requires Gradle ≥ 9.6.0** (9.3.0 refuses it).
- **AGP 9 forbids the `org.jetbrains.kotlin.android` plugin** — `kotlin.compose`
  implies it.
- AGP 9 removed `android.defaults.buildfeatures.buildconfig` and
  `android.enableR8.fullMode`.
- `resourceConfigurations` is now `androidResources { localeFilters += … }`.
- Compose UI 1.12+ and core-ktx 1.19 hard-require AGP ≥ 9.1.0; staying on AGP 8
  is not an option.
- Kotlin 2.3's `-jvm-default` accepts `disable | enable | no-compatibility`;
  `all` is invalid.
- Room rejects a data class whose non-null property is missing from the query
  projection — shared aggregates were split into `TotalsColumns` (`@Embedded`)
  plus wrapper rows.
