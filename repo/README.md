# FieldTrip Ops — Governance & Claims

Offline Android application for managing travel itinerary bookings, quota validation, fee itemization, content quality governance, and post-trip claims handling.

## Role Surfaces (post-audit fix)

| Role | Reachable Screens |
|------|-------------------|
| Traveler | Login → Shell → New Itinerary (wizard), My Claims (list + File Claim) |
| Agent | Login → Shell → confirm any PendingConfirmation booking, cancel any booking; (sees same dashboard, no admin/reviewer cards) |
| Reviewer | Login → Shell → Review Queue (transition tickets), Reports |
| Administrator | Login → Shell → SLA Configuration, Reports, plus all of the above |

Defense-in-depth: navigation guards in `ShellFragment` are UX only; every privileged
use case re-checks the session via `AccessControl` and rejects unauthorized callers
with `UnauthorizedException` or `OwnershipViolationException`.

## Authorization model

- All privileged use cases call `sessionManager.requireSession()` and pass the
  result through `AccessControl` for role and (where applicable) ownership checks.
- The acting user's identity (e.g., refund approver, claim filer, audit actor) is
  always read from the authenticated session — callers cannot pass an `actor`
  string. See `SessionManager`, `SessionContext`, `AccessControl`.
- Object-level rules: travelers may only mutate or file claims against their OWN
  bookings; Agent and Administrator have explicit override roles.

## Claim filing & evidence atomicity

`FileClaimUseCase` validates evidence (≥1 attachment, ≤10 MB, photo or PDF) BEFORE
opening a Room transaction. Inside the transaction it inserts the claim row,
status history, and attachment ref rows. Disk byte writes are deferred until
AFTER the transaction commits — if the transaction rolls back, no on-disk
artifacts ever existed. If the post-commit byte flush fails, ref rows are deleted
in a compensating action so "ref exists ⟹ bytes exist" always holds.

## Per-user deletion workflow (since schema v7; current v10)

Every user deletion is an explicit tracked request in `deletion_requests`:

1. `RequestUserDeletionUseCase` — a Traveler may request deletion of their OWN
   data; an Administrator may request deletion of any user. A duplicate open
   request is rejected idempotently (`AlreadyPending`).
2. `ExecuteUserDeletionUseCase` — Administrator-only. Transitions the record
   through `Approved → Executed` and transactionally applies the scope:
   - `ANONYMIZE` (default) — replaces PII with anonymized placeholders in
     `claim_tickets`, `booking_orders`, `itinerary_drafts`, and the `users`
     row; deletes `consent_records`, `export_packages`, `role_assignments`,
     and `credentials`; preserves audit logs.
   - `HARD_DELETE` — deletes all user-owned non-audit rows including the
     `users` row. Audit logs are retained.
3. Audit chain: `DELETION_REQUESTED` → `DELETION_APPROVED` →
   `DELETION_EXECUTED` (or `DELETION_FAILED`) → `DATA_ANONYMIZED` /
   `DATA_DELETED` marker.

UI: travelers request self-deletion from the Shell dashboard; administrators
review and execute via the Deletion Requests screen.

## On-device SLA reminders (since schema v7; current v10)

`GenerateSlaRemindersUseCase` evaluates open tickets against the persisted
`sla_config` and writes `sla_reminders` rows. Reminders fire at 25% of the
SLA window remaining (min 15 minutes lead). The `sla_reminders` table has a
UNIQUE index on `(ticketId, kind)` to guarantee dedup across re-evaluations,
and stale pre-breach rows are purged whenever the admin updates SLA config.

`SlaReminderWorker` runs every 30 minutes on-device (no network) and posts a
local Android notification summarizing the number of new reminders. Breach
rows are retained for audit; pre-breach rows are acknowledgeable.

## Claim compensation calculation (since schema v7; current v10)

`ClaimTicket` carries a nullable `CompensationCalculation` with amount,
currency, basis (rule or source), approver identity, timestamp, and note.
`SetClaimCompensationUseCase` lets Reviewer / Administrator record the
calculation transactionally alongside an investigation note and writes a
`CLAIM_COMPENSATION_SET` audit event. Amount is validated (non-negative; must
not exceed any other business constraint) and the approver identity is bound
to the authenticated session.

## Stored refund rules (since schema v7; current v10)

Refund policy is persisted in `refund_rules`. `RefundEngine.compute` takes a
rule list rather than hardcoding time-band constants; `ApproveRefundUseCase`
reads `RefundRuleRepository.listActive()` and passes the rows to the engine.
Seeded defaults preserve the pre-existing bands:

| Rule code                  | Window            | Percent |
|----------------------------|-------------------|---------|
| `FULL_REFUND_OVER_48H`     | `> 48h`           | 100%    |
| `PARTIAL_REFUND_24_TO_48H` | `24h < t ≤ 48h`   | 50%     |
| `NO_REFUND_UNDER_24H`      | `≤ 24h`           | 0%      |

Administrators edit rules via `UpdateRefundRuleUseCase`; each change appends
to `refund_rule_history` and writes a `REFUND_RULE_CREATED` /
`REFUND_RULE_UPDATED` audit event. Non-admin callers are rejected with
`UnauthorizedException`.

## SLA configuration

SLA values (first response, resolution, no-response auto-close) are persisted in
`sla_config` and editable via `UpdateSlaConfigUseCase` (Administrator only). The
SLA admin screen is at `SlaConfigFragment`. `AutoCloseWaitingTicketsUseCase` and
`GenerateReportUseCase` consume the live values, so changes take effect
immediately. Each change emits a `SLA_CHANGED` audit log row and a
`sla_config_history` row.

**Timing model: configurable business hours.** SLA windows are computed via
`BusinessHourCalculator` using a configurable working-hours window
(`workDayStartHour`–`workDayEndHour`, default 09:00–17:00) and optional
weekend exclusion (`excludeWeekends`, default true). Administrators can
adjust these via the SLA Configuration screen. When weekends are not
excluded and the work window is 0–24, computation degrades to simple
elapsed wall-clock time for backward compatibility. Holiday exclusions
are not modeled (offline-only app; no external calendar source).

## Sensitive field encryption

The free-text `notes`, claim `description`, and investigation `note` columns are
encrypted at rest via `SensitiveFieldCodec` (production: `AesSensitiveFieldCodec`
backed by AndroidKeyStore). The codec's ciphertext envelope is `enc:v1:<base64>`,
which lets the decode path detect legacy plaintext rows and pass them through.

## Demo seeding behavior

| Build type | Seeded credentials? |
|------------|---------------------|
| Debug      | Yes — admin/admin123, agent/agent123, reviewer/reviewer123, traveler/traveler123, inactive/inactive123 |
| Release    | No — first-launch is empty; install scripts must provision real users |

This is gated by `BuildConfig.DEBUG` in `FieldTripApp.onCreate`.

## Booking lifecycle terminal semantics

`Cancelled` and `Closed` are the only TERMINAL booking states — no outgoing
transitions are permitted. The pending-confirmation auto-close path goes
`PendingConfirmation → Closed` with an audit reason of "auto-closed: timeout".
There is no separate `AutoClosed` booking state; this strictly aligns with the
prompt requirement that only `Cancelled` and `Closed` are terminal.

## Transactional guarantees

| Workflow | Transaction boundary |
|----------|----------------------|
| Submit booking | inventory RESERVE + ledger entry + booking insert + itinerary mark-submitted, all in one `withTransaction` |
| Confirm booking | replace fee items + inventory CONFIRM + ledger entry + state update, all in one `withTransaction` |
| Cancel booking | inventory RELEASE (when applicable) + state update, in one `withTransaction` |
| File claim | claim insert + status history + attachment ref staging, all in one `withTransaction`; disk bytes flushed post-commit with compensating delete on failure |
| Update SLA | sla_config upsert + history insert in one `withTransaction` |
| Approve refund | single insert (record-only) — not a multi-write |
| Governance override | content state update + governance decision insert + suppression clear (when applicable), all in one `withTransaction` |
| Run duplicate scan | per-pair: cluster insert + content state update + governance decision insert + suppression establish, all in one `withTransaction` |


## Tech Stack

- **Platform:** Native Android 10+ (API 29)
- **Language:** Kotlin
- **UI:** Android Views with ViewBinding
- **Database:** Room over SQLite (local only, no server)
- **DI:** Koin
- **Architecture:** ViewModel + UseCase + Repository
- **Background:** WorkManager (periodic, idle-constrained)
- **Security:** PBKDF2 password hashing, AES-256-GCM field encryption via AndroidKeyStore, append-only audit logs with chained HMAC checksums

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- An Android device or emulator running Android 10+ (API 29+)

## Building

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Running

1. Open the project in Android Studio
2. Select a device/emulator running Android 10+
3. Run the `app` configuration
4. The app boots to a login screen; first launch seeds demo users

## Demo Credentials

| Username   | Password      | Role          |
|------------|---------------|---------------|
| admin      | admin123      | Administrator |
| agent      | agent123      | Agent         |
| reviewer   | reviewer123   | Reviewer      |
| traveler   | traveler123   | Traveler      |
| inactive   | inactive123   | Traveler (inactive) |

## Project Structure

```
app/src/main/java/com/fieldtripops/
├── FieldTripApp.kt              # Application class, Koin init, worker registration
├── di/                          # Koin DI modules
├── data/
│   ├── db/                      # Room database, converters, migrations (v1→v10)
│   ├── entity/                  # Room entities (32 tables)
│   ├── dao/                     # Room DAOs (append-only where required)
│   ├── repository/              # Repository implementations with transactions
│   └── seed/                    # Demo seed data
├── domain/
│   ├── model/                   # Domain models (pure Kotlin) + state machines
│   ├── booking/                 # FeeCalculator, ItineraryValidator, RefundEngine, ClaimValidator, RescheduleValidator
│   ├── governance/              # GovernanceEvaluator, SimilarityCalculator, thresholds
│   ├── reports/                 # Report definitions and result types
│   ├── export/                  # ExportRenderer with role-based masking
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Business logic use cases (31+)
├── security/                    # PasswordHasher, FieldEncryptor, AuditChecksum
├── audit/                       # Append-only AuditLogger
├── attachment/                  # File storage abstraction
├── ui/
│   ├── login/                   # Login screen
│   ├── shell/                   # Post-auth dashboard with booking list
│   ├── itinerary/               # Multi-step itinerary wizard
│   ├── booking/                 # Booking confirmation + fee itemization
│   └── util/                    # Masked field rendering, view extensions
└── work/                        # WorkManager background jobs (6)
```

## Implemented Phases

### Phase 1 — Foundation, Auth, Core Data
- Local login/logout with salted PBKDF2 password hashing
- Failed-login lockout (5 attempts, 15-minute lockout)
- Session timeout (30 min) with WorkManager safety net
- Append-only audit logs with chained HMAC checksums
- AES-256-GCM field encryption via AndroidKeyStore
- Field masking helpers (phone/email/name)
- RBAC roles (Traveler, Agent, Reviewer, Administrator)
- Consent records and session audits

### Phase 2 — Itinerary, Inventory, Booking
- Multi-step traveler itinerary wizard (Basics → Dates → Review)
- Validation: party size 1–12, dates MM/DD/YYYY, range ≤ 365 days
- Inventory slots with `total - reserved - booked` quota model
- `BookingOrder` lifecycle: Draft → PendingConfirmation → Booked → … → Closed
- Transactional quota operations (RESERVE/CONFIRM/RELEASE/EXCEPTION)
- Append-only `QuotaLedger`
- Fee itemization (BASE_FARE, TAX_FEE, ADJUSTMENT) with deterministic totals
- Pending-confirmation 30-minute auto-close worker

### Phase 3 — Reschedule, Refund, Claims
- Reschedule requests with 24h lead-time rule + agent exception override
- Refund engine with deterministic time-band rules (>48h, 24-48h, <24h)
- Refund decisions with approver identity, rule used, manual override audit
- Claim filing with required proof (≤10 MB, photo/PDF), 7-day window
- Claim ticket lifecycle: Draft → Submitted → InReview → Resolved/Rejected → Appealed → …
- 72-hour `WaitingForTraveler` auto-close worker
- Append-only `TicketStatusHistory`, `InvestigationNote`, `AppealRecord`

### Phase 4 — Content Governance, Dedup, Rollback
- Content metrics (ratings, comments, favorites, downloads)
- Auto-demotion: avg rating < 2.5 with ≥ 10 ratings
- Duplicate detection: SHA-256 hash equality + 3-gram Jaccard similarity
- Auto-quarantine when similarity > 80%
- Recommendation suppression layer
- Reviewer/Admin override with mandatory reason
- Transaction checkpoints and rollback (compensating writes only — never deletes audit)

### Phase 5 — Reports, Exports, Retention
- Reports: bookings by state, claims by type/responsibility/SLA, refund totals, governance actions
- CSV exports with role-based masking and SHA-256 checksum
- Retention sweep: 24-month ticket anonymization (preserves FK integrity)
- Idle-constrained maintenance workers (duplicate scan, retention sweep)

## Background Workers

| Worker                            | Cadence  | Constraints     |
|-----------------------------------|----------|-----------------|
| SessionTimeoutWorker              | 15 min   | None            |
| PendingConfirmationTimeoutWorker  | 15 min   | None            |
| WaitingTicketTimeoutWorker        | 1 hour   | None            |
| SlaReminderWorker                 | 30 min   | None            |
| DuplicateScanWorker               | 6 hours  | Device idle     |
| RetentionSweepWorker              | 24 hours | Idle + charging |

## Offline Design

This application operates entirely offline:
- No internet permissions
- No remote APIs or cloud sync
- All data stored locally in Room/SQLite (32 tables at schema v10, indexed for sub-50ms queries on 100K records)
- All business logic computed on-device
- Sensitive data encrypted at rest via AndroidKeyStore

## Key Security Features

- Salted PBKDF2 password hashing (120K iterations, 256-bit)
- AES-256-GCM field encryption via AndroidKeyStore
- Append-only audit logs with chained HMAC-SHA256 checksums for tamper evidence
- Failed login lockout (5 attempts, 15-minute lockout)
- Session timeout (30 minutes inactivity)
- UI field masking for sensitive data (phone, email, names)
- Export masking by role profile
- Role-based access control (RBAC) at use-case level
- Mandatory reason capture on all override actions
