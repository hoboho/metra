# METRA — Data Model

Room, schema **version 1**, identity hash `2122e8559f54ef0787b5b506e4abad78`.
Exported to `app/schemas/ir.metra.app.data.local.MetraDatabase/1.json`.

Eight tables, two foreign keys, ten indices.

---

## Global conventions

| Concern | Rule |
|---|---|
| **Money** | `INTEGER` (`Long`), always whole **Toman**. Never `REAL`, never Rial. |
| **Calendar dates** | `INTEGER` epoch days (`Long`). Jalali is presentation only. |
| **Timestamps** | `INTEGER` epoch millis (`Long`). |
| **Time of day** | `INTEGER` minute-of-day, nullable. |
| **Booleans** | `INTEGER` 0/1. |
| **Enums** | `TEXT` — the enum **name**, never the ordinal. |
| **Text** | `NOT NULL`, empty string rather than null. |
| **Primary keys** | `INTEGER` autoGenerate, named `id`. |

Storing the enum name is deliberate: adding a new `ExpenseCategory` in the
middle of the enum cannot silently reinterpret existing rows.

---

## `user_profile` — singleton

One row, `id = UserProfileEntity.SINGLETON_ID`.

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `full_name` | TEXT | NOT NULL |
| `company_name` | TEXT | NOT NULL |
| `employee_code` | TEXT | NOT NULL |
| `report_footer_note` | TEXT | NOT NULL |
| `onboarding_completed` | INTEGER | NOT NULL |
| `created_at` / `updated_at` | INTEGER | NOT NULL |

`onboarding_completed` lives here (not DataStore) so a backup carries it.

---

## `projects`

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `name` | TEXT | NOT NULL |
| `employer` | TEXT | NOT NULL |
| `work_area` | TEXT | NOT NULL |
| `default_supervisor` | TEXT | NOT NULL |
| `default_worker_count` | INTEGER | NOT NULL |
| `notes` | TEXT | NOT NULL |
| `is_active` | INTEGER | NOT NULL |
| `created_at` / `updated_at` | INTEGER | NOT NULL |

Indices: `is_active`, `name`.

`is_active` is archive, not delete. Archiving keeps the project selectable for
historical records while hiding it from the default picker.

---

## `work_records` — the core table (24 columns)

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | INTEGER | NOT NULL | |
| `project_id` | INTEGER | **NULL** | FK → `projects.id`, `ON DELETE SET NULL` |
| `project_name_snapshot` | TEXT | NOT NULL | Denormalized |
| `work_area_snapshot` | TEXT | NOT NULL | Denormalized |
| `employer_snapshot` | TEXT | NOT NULL | Denormalized |
| `supervisor_snapshot` | TEXT | NOT NULL | Denormalized |
| `worker_count` | INTEGER | NOT NULL | |
| `work_date_epoch_day` | INTEGER | NOT NULL | Indexed |
| `daily_meters` | INTEGER | NOT NULL | User input |
| `additional_meters` | INTEGER | NOT NULL | **Computed** |
| `threshold_meters_snapshot` | INTEGER | NOT NULL | **Snapshot** |
| `rate_per_meter_snapshot` | INTEGER | NOT NULL | **Snapshot** |
| `additional_meter_payment` | INTEGER | NOT NULL | **Computed** |
| `fixed_salary` | INTEGER | NOT NULL | User-recorded |
| `mission_allowance` | INTEGER | NOT NULL | User-recorded |
| `overtime_amount` | INTEGER | NOT NULL | User-recorded |
| `other_income` | INTEGER | NOT NULL | User-recorded |
| `expense_total` | INTEGER | NOT NULL | Denormalized sum |
| `notes` | TEXT | NOT NULL | |
| `work_start_minute_of_day` | INTEGER | NULL | |
| `work_end_minute_of_day` | INTEGER | NULL | |
| `created_at` / `updated_at` | INTEGER | NOT NULL | |
| `is_deleted` | INTEGER | NOT NULL | Soft delete |

Indices: `work_date_epoch_day`, `project_id`, `is_deleted`,
`(is_deleted, work_date_epoch_day)`.

### Design decisions

**Snapshots.** `threshold_meters_snapshot` and `rate_per_meter_snapshot` record
the rule that was in force on `work_date_epoch_day`. The computed columns are
derived from those, never from the current rule. This is what makes a rate
change non-retroactive.

**Context snapshots.** `project_name_snapshot` etc. mean that renaming or
archiving a project never rewrites history, and a report from two years ago
still reads correctly. `project_id` is kept for filtering and joins; the FK is
`SET NULL` so deleting a project keeps the workdays.

**`expense_total` denormalized.** Kept in sync in the same transaction as any
expense mutation (`replaceExpenses`, `addExpense`, `deleteExpense`). This is a
read-optimisation for the work log list and dashboard, which would otherwise
need a join per row.

**Soft delete.** `is_deleted` rather than a real delete: a mis-tap should be
recoverable, and a deleted workday must not silently drag its expenses and
report history with it. Every read query filters on it. `purgeDeleted()` is the
only path that removes rows, and it is an explicit user action.

---

## `expenses`

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `work_record_id` | INTEGER | NOT NULL |
| `amount` | INTEGER | NOT NULL |
| `category` | TEXT | NOT NULL |
| `description` | TEXT | NOT NULL |
| `receipt_photo_uri` | TEXT | NULL |
| `created_at` | INTEGER | NOT NULL |

FK → `work_records.id`, **`ON DELETE CASCADE`**.
Indices: `work_record_id`, `category`.

Categories: `TRANSPORTATION`, `FOOD`, `ACCOMMODATION`, `MATERIALS`, `TOOLS`,
`OTHER`.

`amount >= 0` is enforced in the domain model's `init` block, not only in the
validator, so no code path can construct a negative expense. The message is
Persian because ViewModels surface it directly.

Many expenses per day are supported; there is no per-day cap.

---

## `payment_rules`

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `threshold_meters` | INTEGER | NOT NULL |
| `rate_per_meter` | INTEGER | NOT NULL |
| `effective_from_epoch_day` | INTEGER | NOT NULL |
| `currency_code` | TEXT | NOT NULL |
| `label` | TEXT | NULL |
| `created_at` | INTEGER | NOT NULL |

**Unique index on `effective_from_epoch_day`** — two rules cannot take effect on
the same day, which keeps `PaymentRuleSelector` deterministic.

Rows are **append-only**. There is no update rule in the UI: changing a rate
means adding a new one with a future effective date. The default rule
(400 m / 15 000 T) is seeded on first access and re-seeded if the user deletes
the last one, so a record can never be left without a rule.

Selection: newest rule with `effective_from_epoch_day <= work_date`, else the
earliest rule.

---

## `app_settings` — singleton

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `default_project_id` | INTEGER | NULL |
| `default_supervisor` | TEXT | NOT NULL |
| `default_worker_count` | INTEGER | NOT NULL |
| `default_work_area` | TEXT | NOT NULL |
| `default_threshold_meters` | INTEGER | NOT NULL |
| `default_rate_per_meter` | INTEGER | NOT NULL |
| `currency_code` | TEXT | NOT NULL |
| `use_previous_workday_info` | INTEGER | NOT NULL |
| `reminder_enabled` | INTEGER | NOT NULL |
| `reminder_minute_of_day` | INTEGER | NOT NULL |
| `updated_at` | INTEGER | NOT NULL |

Work defaults live in Room, **not** DataStore, so a backup carries them.
`default_threshold_meters` / `default_rate_per_meter` are the seed values for a
*new* rule — they are never used to price an existing record.

---

## `report_configurations`

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `name` | TEXT | NOT NULL |
| `report_type` | TEXT | NOT NULL |
| `project_id` | INTEGER | NULL |
| `start_epoch_day` / `end_epoch_day` | INTEGER | NULL |
| `include_daily_table` | INTEGER | NOT NULL |
| `include_notes` | INTEGER | NOT NULL |
| `created_at` / `updated_at` | INTEGER | NOT NULL |

Saved report presets. `report_type` is the `ReportType` enum name.

---

## `backup_metadata` — audit log

| Column | Type | Null |
|---|---|---|
| `id` | INTEGER | NOT NULL |
| `file_name` | TEXT | NOT NULL |
| `created_at` | INTEGER | NOT NULL |
| `operation` | TEXT | NOT NULL |
| `restore_strategy` | TEXT | NULL |
| `encrypted` | INTEGER | NOT NULL |
| `project_count` / `work_record_count` / `expense_count` | INTEGER | NOT NULL |
| `sha256` | TEXT | NULL |
| `safety_snapshot_path` | TEXT | NULL |

Index: `created_at`.

Written on every export and restore so a restore can be traced back to the
safety snapshot that preceded it.

---

## What lives in DataStore instead

`metra_preferences` holds device-only state that must **not** travel with a
backup: `themeMode`, `appLockEnabled`, `pinHash`, `biometricEnabled`, and the
local mirror of reminder scheduling.

---

## Migration policy

- `exportSchema = true`; the JSON is committed at `app/schemas/`.
- `addMigrations(*Migrations.ALL)` where `Migrations.ALL` is `emptyArray()` at
  version 1.
- **No `fallbackToDestructiveMigration()` anywhere in the project.** A future
  schema change without a migration fails loudly rather than wiping user data.
- Future migrations must be additive and must never rewrite
  `threshold_meters_snapshot` or `rate_per_meter_snapshot`.
