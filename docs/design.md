# FieldTrip Ops Governance & Claims - Design Document

## 1. Overview

FieldTrip Ops is a fully offline Android application for managing travel itinerary bookings, inventory quota enforcement, fee itemization, after-sales claims handling, content quality governance, and data retention. It supports four roles (Traveler, Agent, Reviewer, Administrator) and operates entirely on-device with no internet permissions or remote APIs.

---

## 2. Architecture

### 2.1 Architectural Pattern

The application follows a **Clean Architecture** approach organized in three layers:

- **UI Layer** — Android Views with ViewBinding, Fragments, and ViewModels. All user interaction flows through ViewModel methods that delegate to use cases.
- **Domain Layer** — Pure Kotlin use cases, domain models, validators, and repository interfaces. Contains all business logic with zero Android framework dependencies (except Room's `withTransaction` for atomicity).
- **Data Layer** — Room DAOs, entity classes, repository implementations, and database migrations. Handles persistence, encryption, and entity-to-domain mapping.

### 2.2 Dependency Injection

Koin is the DI framework, organized into five modules:

| Module | Scope | Contents |
|--------|-------|----------|
| `databaseModule` | Singleton | `FieldTripDatabase` instance + 27 DAO singletons |
| `repositoryModule` | Singleton | 22 repository implementations, `PasswordHasher`, `FieldEncryptor`, `SessionManager`, `SensitiveFieldCodec` |
| `auditModule` | Singleton | `RoomAuditLogger` |
| `useCaseModule` | Factory | 26+ use case factories (new instance per call) |
| `viewModelModule` | ViewModel | 11 ViewModels scoped to Fragment/Activity lifecycle |

### 2.3 Threading Model

All IO and computation runs off the main thread via Kotlin coroutines. ViewModels launch coroutines in `viewModelScope`; use cases are `suspend` functions. Room DAOs use `@Query` suspend functions and `withTransaction` for multi-write atomicity. WorkManager workers extend `CoroutineWorker`.

---

## 3. Database Design

### 3.1 Schema Overview

Room over SQLite with 32 tables at schema version 10. The database file is `fieldtripops.db`. Schema export is enabled for migration verification.

### 3.2 Entity Groups

**Authentication & Audit (Phase 1)**
- `users` — User accounts
- `role_assignments` — RBAC role bindings (Traveler, Agent, Reviewer, Administrator)
- `credentials` — Password hashes (PBKDF2), salt, failed attempt counters, lockout timestamps
- `consent_records` — Per-user consent audit trail
- `session_audits` — Session lifecycle events
- `audit_logs` — Append-only, insert-only audit trail with chained HMAC checksums
- `attachment_refs` — File attachment metadata linked to owner entities

**Booking & Inventory (Phase 2)**
- `itinerary_drafts` — Traveler-authored trip drafts (name initials, party size, dates, notes)
- `booking_orders` — Booking lifecycle records with authoritative `tripStartAt`, `tripEndAt`, `paidTotalCents`
- `inventory_slots` — Available trip slots with total/reserved/booked counts
- `quota_ledger` — Append-only ledger of RESERVE/CONFIRM/RELEASE/EXCEPTION operations
- `fee_items` — Line-item fees (BASE_FARE, TAX_FEE, ADJUSTMENT) stored in cents

**Claims & Refunds (Phase 3)**
- `claim_tickets` — Support tickets with claim style, classification, responsibility, compensation fields
- `ticket_status_history` — Append-only state transition records
- `investigation_notes` — Internal notes (encrypted at rest)
- `appeal_records` — Appeal filings with resolution tracking
- `reschedule_requests` — Reschedule requests with exception reason support
- `refund_decisions` — Refund approval records with approver identity, rule used, override reason

**Content Governance (Phase 4)**
- `content_items` — User-generated content with state (Active/Demoted/Quarantined/Excluded), rating aggregates, content hash
- `content_metric_daily` — Daily aggregated metrics
- `content_ratings` — Per-user ratings with unique constraint
- `governance_decisions` — Content state change records with reason
- `recommendation_suppressions` — Active suppression flags
- `duplicate_clusters` — Similarity pairs with score and resolution status
- `transaction_checkpoints` — JSON snapshots for rollback

**Export & Retention (Phase 5)**
- `offline_queue_items` — Queued operations with retry logic
- `export_packages` — Generated exports with row count and SHA-256 checksum

**SLA Configuration (Phase 6)**
- `sla_config` — Singleton row (key="current") with first-response, resolution, and no-response thresholds plus business-hour settings
- `sla_config_history` — Append-only version history

**Deletion & Rules (Phase 7)**
- `deletion_requests` — Per-user deletion request lifecycle (Pending/Approved/Executed/Failed)
- `sla_reminders` — Pre-breach and breach notifications with UNIQUE on (ticketId, kind)
- `refund_rules` — Persisted refund time bands (admin-editable)
- `refund_rule_history` — Rule audit trail

### 3.3 Indexing Strategy

Composite indexes target sub-50ms queries on 100,000 records:
- `booking_orders`: indices on [state, updatedAt], [travelerId, createdAt], [state, lastActivityAt], [itineraryId], [inventorySlotId]
- `claim_tickets`: indices on [state, updatedAt], [travelerId], [bookingOrderId]
- `content_items`: indices on [state, contentHash]

### 3.4 Migration Strategy

Versioned migrations from v1 through v10 (one per implementation phase, plus targeted schema fixes). `fallbackToDestructiveMigrationOnDowngrade()` ensures downgrades never silently corrupt data. Upgrades are always non-destructive.

---

## 4. Security Architecture

### 4.1 Authentication

- **Password hashing:** PBKDF2WithHmacSHA256, 120,000 iterations, 256-bit key, 32-byte random salt per user
- **Constant-time comparison:** Prevents timing-based side-channel attacks during password verification
- **Brute-force protection:** Account locked for 15 minutes after 5 consecutive failed attempts
- **Session management:** `SessionManager` holds a `MutableStateFlow<SessionContext?>`. Sessions expire after 30 minutes of inactivity. `SessionTimeoutWorker` runs every 15 minutes as a background safety net. A new login automatically terminates any existing session for the same user.

### 4.2 Authorization

- **Centralized identity:** All use cases resolve the acting identity from `SessionManager.requireSession()`. No use case accepts caller-supplied actor parameters.
- **Role-based access control:** `AccessControl` provides typed enforcement methods (`requireAdmin`, `requireAgentOrAdmin`, `requireRefundApprover`, `requireReviewerOrAdmin`, `requireOwnerOrRole`).
- **Object-level ownership:** Travelers can only mutate their own bookings, itineraries, and claims. Agent and Administrator roles have explicit override privileges.
- **Defense-in-depth:** UI navigation guards in `ShellFragment` are UX-only; every privileged use case independently re-checks authorization and throws `UnauthorizedException` or `OwnershipViolationException` on failure.

### 4.3 Encryption

- **Sensitive field encryption:** AES-256-GCM via AndroidKeyStore for itinerary notes, claim descriptions, and investigation notes. `SensitiveFieldCodec` wraps ciphertext with an `enc:v1:` prefix for version detection. Legacy plaintext rows are decoded transparently.
- **Export masking:** Role-based masking profiles (Administrator sees raw values; all others mask phone/email/name columns).
- **UI masking:** Sensitive fields (phone, email, names) display only last 4 digits or redacted placeholders.

### 4.4 Audit Trail

- **Append-only enforcement:** `AuditLogDao` exposes only `@Insert` and `@Query(SELECT)` methods — no update or delete.
- **Tamper evidence:** Chained HMAC-SHA256 checksums across log entries.
- **Coverage:** 45+ audit actions spanning authentication, booking lifecycle, claims, refunds, governance, exports, retention, and SLA configuration changes.

---

## 5. Core Domain Logic

### 5.1 Booking State Machine

```
Draft -> PendingConfirmation -> Booked -> InService -> Completed -> Closed
  \          \                   \
   -> Cancelled  -> Closed        -> ReschedulePending -> Booked
                   (auto-close)                          \-> Cancelled
```

- **Terminal states:** `Cancelled` and `Closed` only — no outgoing transitions permitted.
- **Auto-close:** `PendingConfirmation` orders auto-close to `Closed` (not a separate `AutoClosed` state) after 30 minutes of inactivity via `PendingConfirmationTimeoutWorker`.
- **Enforcement:** `BookingState.canTransition()` is the single source of truth, used by both DAOs and use cases.

### 5.2 Claim Ticket Lifecycle

```
Draft -> Submitted -> InReview -> WaitingForTraveler -> AutoClosed -> Closed
                        \-> Resolved -> Appealed -> InReview
                        \-> Rejected -> Appealed -> Finalized
                        \-> Escalated -> Resolved/Rejected
```

- **Terminal states:** `Closed`, `Cancelled`, `Finalized`.
- **Auto-close:** `WaitingForTraveler` tickets auto-close after 72 hours (configurable via SLA config) via `WaitingTicketTimeoutWorker`.
- **Filing window:** Claims must be filed within 7 days of trip end (uses persisted `tripEndAt` from the booking, not caller input).

### 5.3 Quota Management

Inventory uses a `total - reserved - booked` model with four quota operations:
- `RESERVE` — Increments `reservedCount` when a booking is submitted
- `CONFIRM` — Moves units from reserved to booked when an agent confirms
- `RELEASE` — Decrements `bookedCount` (if confirmed) or `reservedCount` (if only reserved) on cancellation
- `EXCEPTION` — Requires per-slot `allowExceptionBooking` flag; Administrator-managed

All quota changes are recorded in the append-only `quota_ledger`. Operations are transactional with booking state changes to prevent partial state on crashes.

### 5.4 Fee Itemization & Refund Engine

- **Fee types:** BASE_FARE, TAX_FEE, ADJUSTMENT — stored as cents (`Long`) to avoid floating-point errors.
- **Refund computation:** `RefundEngine` matches persisted `RefundRule` rows sorted by `minHoursBeforeStartExclusive` DESC. Default seeded bands: >48h = 100%, 24-48h = 50%, <=24h = 0%.
- **Boundary precision:** Uses millisecond-level comparison to avoid truncation artifacts. "48h exact" falls into the 24-48h partial band; "24h exact" into nonrefundable.
- **Manual override:** Allowed at any amount <= paid total, but requires a non-blank override reason.
- **Integrity:** `ApproveRefundUseCase` reads `paidTotal` and `tripStartAt` from the persisted booking — callers pass only the booking ID.

### 5.5 Content Governance

- **Auto-demotion:** Average rating < 2.5 (strict less-than) with >= 10 ratings triggers demotion.
- **Duplicate detection:** SHA-256 hash equality as fast path, then character 3-gram Jaccard similarity over normalized text (lowercased, punctuation stripped, whitespace collapsed).
- **Auto-quarantine:** Similarity > 0.80 (strict greater-than) quarantines the later-indexed item.
- **Recommendation suppression:** `ContentRepository.findRecommendable()` filters out items with active `RecommendationSuppression` rows and items not in `Active` or `Demoted` state.
- **Rollback:** `RollbackUseCase` accepts a caller-supplied restorer lambda. Checkpoints are marked `rolledBack=true` rather than deleted, preserving audit history.

---

## 6. Transactional Integrity

All multi-write workflows use Room's `withTransaction` to guarantee atomicity:

| Workflow | Boundary |
|----------|----------|
| Submit booking | Quota RESERVE + ledger entry + booking insert + itinerary mark-submitted |
| Confirm booking | Fee item replacement + quota CONFIRM + ledger entry + state transition + paidTotalCents write |
| Cancel booking | Quota RELEASE (when applicable) + state update |
| File claim | Claim insert + status history + attachment ref staging; disk bytes flushed post-commit with compensating delete on failure |
| Update SLA | sla_config upsert + history insert |
| Governance override | Content state update + governance decision + suppression clear |
| Duplicate scan | Per-pair: cluster insert + content state + governance decision + suppression |

---

## 7. Background Workers

| Worker | Cadence | Constraints | Purpose |
|--------|---------|-------------|---------|
| `SessionTimeoutWorker` | 15 min | None | Expire idle sessions |
| `PendingConfirmationTimeoutWorker` | 15 min | None | Auto-close stale PendingConfirmation bookings (30-min threshold) |
| `WaitingTicketTimeoutWorker` | 1 hour | None | Auto-close WaitingForTraveler tickets (72h default) |
| `SlaReminderWorker` | 30 min | None | Generate pre-breach/breach reminders, post local notifications |
| `DuplicateScanWorker` | 6 hours | Device idle | Content similarity scanning |
| `RetentionSweepWorker` | 24 hours | Idle + charging | Anonymize closed tickets older than 24 months |

All workers are enqueued as `PeriodicWorkRequests` from `FieldTripApp.enqueueAllWorkers()` during `onCreate`.

---

## 8. UI Architecture

### 8.1 Screen Map

| Screen | Fragment | ViewModel | Role Access |
|--------|----------|-----------|-------------|
| Login | `LoginFragment` | `LoginViewModel` | All |
| Dashboard | `ShellFragment` | `ShellViewModel` | All (role-filtered cards) |
| Itinerary Wizard | `ItineraryWizardFragment` | `ItineraryWizardViewModel` | Traveler |
| Booking Confirm | `BookingConfirmFragment` | `BookingConfirmViewModel` | Agent, Administrator |
| File Claim | `FileClaimFragment` | `FileClaimViewModel` | Traveler |
| My Claims | `MyClaimsFragment` | `MyClaimsViewModel` | Traveler |
| Review Queue | `ReviewQueueFragment` | `ReviewQueueViewModel` | Reviewer, Administrator |
| Quarantine | `QuarantineFragment` | `QuarantineViewModel` | Reviewer, Administrator |
| SLA Config | `SlaConfigFragment` | `SlaConfigViewModel` | Administrator |
| Deletion Requests | `DeletionRequestsFragment` | `DeletionRequestsViewModel` | Administrator |
| Reports | `ReportsFragment` | `ReportsViewModel` | Reviewer, Administrator |
| Consent | `ConsentFragment` | `ConsentViewModel` | All |

### 8.2 Performance Targets

- **RecyclerView:** DiffUtil-based list updates for 60fps scrolling with 1,000+ records.
- **Image memory:** LRU cache with downsampling keeps peak image memory under 20 MB (`AttachmentImageCache`).
- **Query performance:** Composite indexes target sub-50ms on 100,000 local records.

---

## 9. Data Retention & Privacy

- **Retention period:** Closed/finalized tickets anonymized after 24 months. Description replaced with `"[anonymized]"` to preserve FK integrity.
- **Per-user deletion:** Two-phase workflow — request (Traveler or Admin) followed by execution (Admin-only). Supports `ANONYMIZE` (PII replacement, preserves structure) and `HARD_DELETE` (removes all non-audit rows).
- **Data minimization:** On-device consent toggles, no internet permissions, no cloud sync.
- **Export controls:** CSV exports with role-based masking profiles and SHA-256 checksums.
- **Audit preservation:** Audit logs are never deleted, even during hard-delete operations.

---

## 10. Technology Stack

| Component | Technology |
|-----------|------------|
| Platform | Android 10+ (API 29), target SDK 34 |
| Language | Kotlin |
| UI | Android Views + ViewBinding |
| Database | Room over SQLite (32 tables, schema v10) |
| DI | Koin |
| Architecture | ViewModel + UseCase + Repository (Clean Architecture) |
| Background | WorkManager (periodic, constraint-aware) |
| Security | PBKDF2 password hashing, AES-256-GCM via AndroidKeyStore, HMAC-SHA256 audit checksums |
| Build | Gradle with KSP, JDK 17, R8/ProGuard minification in release |
| Testing | JUnit, MockK, Turbine, Truth, Room Testing, Koin Test |
