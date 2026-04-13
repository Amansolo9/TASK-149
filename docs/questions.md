# FieldTrip Ops Governance & Claims - Clarification Questions



## 1. Password Hashing Algorithm and Iteration Count

**Question:** The prompt requires "salted password hashing" but does not specify which algorithm, iteration count, or key length to use. A weak algorithm (MD5, SHA-1) or low iteration count would make stored credentials vulnerable to brute-force attacks even though the app is offline. What hashing parameters should be used?

**My Understanding:** Industry-standard PBKDF2 with HMAC-SHA256 is the appropriate choice for an Android-only app that cannot rely on server-side bcrypt/scrypt. The iteration count should meet or exceed NIST SP 800-132 recommendations (at minimum 100,000 iterations), and a 256-bit key length provides sufficient entropy for offline credential storage.

**Solution:** The implementation in `security/PasswordHasher.kt` uses `PBKDF2WithHmacSHA256` with 120,000 iterations, a 256-bit key length, and a 32-byte random salt generated via `SecureRandom` per user. Password verification uses constant-time XOR comparison to prevent timing-based side-channel attacks. The salt is Base64-encoded and stored alongside the hash in the `credentials` table via `CredentialEntity`.

---

## 2. Booking State Machine: Terminal States and Auto-Close Semantics

**Question:** The prompt lists the booking lifecycle as "Draft → Pending Confirmation → Booked → In Service → Completed" with "Cancelled and Closed as terminal states," and separately says "any Pending Confirmation order auto-closes after 30 minutes." Should auto-close produce a distinct `AutoClosed` state, or map to one of the two declared terminal states? A separate `AutoClosed` state would violate the prompt's explicit enumeration, but mapping it to `Cancelled` implies user intent that did not exist.

**My Understanding:** Since the prompt explicitly names only `Cancelled` and `Closed` as terminal states, auto-close should transition to `Closed` rather than introducing a new state. The distinction between intentional cancellation and timeout expiry can be captured via an audit reason rather than a separate state enum value.

**Solution:** `domain/model/BookingState.kt` defines exactly 8 states: Draft, PendingConfirmation, Booked, ReschedulePending, InService, Completed, Cancelled, and Closed. The `ALLOWED_TRANSITIONS` map routes `PendingConfirmation → Closed` for auto-close and `PendingConfirmation → Cancelled` for explicit user cancellation. `AutoCloseStalePendingUseCase` transitions expired orders to `Closed` with the audit reason "auto-closed: timeout" and releases the reserved quota in a single Room transaction. There is no `AutoClosed` booking state.

---

## 3. Pending Confirmation Auto-Close: Worker Cadence vs. Threshold Precision

**Question:** The prompt says pending bookings auto-close after "30 minutes of inactivity," but Android's WorkManager enforces a minimum periodic interval of 15 minutes. A 15-minute check cadence means a booking could remain open for up to 45 minutes (30 + 15) in the worst case. Should the worker run more frequently, or is approximate enforcement acceptable?

**My Understanding:** WorkManager's 15-minute minimum is an Android platform constraint that cannot be circumvented without foreground services (which would be disproportionate for this use case). The 30-minute threshold should be enforced in the use case logic with millisecond precision against `lastActivityAt`, while the worker simply provides the periodic trigger. Worst-case latency of ~45 minutes is acceptable for an offline booking system.

**Solution:** `work/PendingConfirmationTimeoutWorker.kt` runs on a 15-minute periodic schedule. The actual 30-minute inactivity threshold is evaluated inside `AutoCloseStalePendingUseCase` by comparing `booking.lastActivityAt` against `Instant.now()` with millisecond precision. The worker queries all `PendingConfirmation` bookings and only transitions those that have genuinely exceeded 30 minutes, so the timing granularity of the check does not weaken the business rule — it only introduces bounded latency in detection.

---

## 4. Quota Release on Cancellation: Reserved vs. Confirmed Inventory

**Question:** The prompt requires that auto-close "releases quota," but a booking that was submitted (quota reserved) and then auto-closed has different inventory implications than one that was confirmed (quota confirmed) and later cancelled. Should quota release always decrement the same counter, or should it be context-aware?

**My Understanding:** The release operation must be context-aware. A reservation that was never confirmed should decrement `reservedCount`, while a confirmed booking should decrement `bookedCount`. Using a single counter would either under-release (leaving phantom reservations) or over-release (creating negative counts).

**Solution:** `data/repository/InventoryRepositoryImpl.applyQuotaOperation()` handles `RELEASE` by checking the `quota_ledger` for a prior `CONFIRM` entry for the same booking. If a CONFIRM exists, `bookedCount` is decremented; otherwise `reservedCount` is decremented. This decision is made transactionally inside the same `withTransaction` block as the booking state change, so a crash between the two cannot leave inconsistent inventory.

---

## 5. Refund Boundary Precision: "Exactly 24 Hours" and "Exactly 48 Hours"

**Question:** The prompt specifies three refund bands — ">48 hours: 100%, 24–48 hours: 50%, <24 hours: nonrefundable" — but does not clarify where "exactly 24 hours" and "exactly 48 hours" fall. Is "24–48 hours" inclusive on both ends? If so, "exactly 24h" would get 50%, but the prompt also says "<24 hours" is nonrefundable, and 24 is not less than 24. This boundary ambiguity matters for deterministic refund calculations.

**My Understanding:** The bands should be interpreted with exclusive lower bounds and inclusive upper bounds: >48h maps to 100%, 24h < t <= 48h maps to 50%, and <= 24h maps to 0%. This means "exactly 48h" falls into the 50% band and "exactly 24h" falls into the nonrefundable band. This is the strictest interpretation and avoids ambiguity at the boundaries.

**Solution:** `domain/booking/RefundEngine.kt` uses millisecond-precise duration computation (`Duration.between(now, tripStart).toMillis()`) converted to a `Double` hours value. The `RefundRule` model defines `minHoursBeforeStartExclusive` and `maxHoursBeforeStartInclusive` (nullable for open-ended upper bounds). The seeded default rules encode: FULL (min=48, max=null → >48h), PARTIAL (min=24, max=48 → 24h < t ≤ 48h), NONE (min=-1, max=24 → ≤24h). This places "48h exact" in the partial band and "24h exact" in the nonrefundable band, matching the strictest reading of the prompt.

---

## 6. Refund Integrity: Trusting Caller-Supplied Financial Data

**Question:** The refund computation requires the "paid total" and "trip start time" to calculate the correct band and amount. If callers supply these values as parameters, a malicious or buggy caller could inflate the paid total or shift the trip start to manipulate the refund. Should the use case trust caller input or resolve these values from persisted records?

**My Understanding:** Financial and timing facts for refund calculation must be read from the authoritative persisted records, not from caller parameters. The paid total should come from the confirmed fee items stored on the booking, and the trip start should come from the booking's persisted trip window (which was copied from the inventory slot at submission time). This eliminates the trust-the-caller vector entirely.

**Solution:** `domain/usecase/ApproveRefundUseCase.kt` accepts only `bookingOrderId` — there are no `paidTotal` or `tripStart` parameters. Inside the use case, `booking.paidTotal` and `booking.tripStartAt` are read from the persisted `BookingOrder` row. The `paidTotal` is written atomically by `ConfirmBookingUseCase` when fee items are replaced and confirmed, and `tripStartAt` is copied from the inventory slot at submission time by `SubmitBookingUseCase`. The approver identity is bound to `sessionManager.requireSession()`, not caller input.

---

## 7. Claim Filing: Atomicity of Ticket, Status History, and Evidence

**Question:** The prompt requires "at least one proof attachment" for after-sales tickets. Filing a claim involves inserting the claim record, its initial status history entry, and at least one attachment reference with its file payload. If the app crashes between writing the claim row and writing the attachment payload, the system could end up with a claim that has no evidence (violating the invariant) or an orphaned file on disk with no database reference. How should atomicity be guaranteed across database writes and file system writes?

**My Understanding:** Database writes (claim + status history + attachment references) should be grouped in a single Room transaction. File system writes (the actual evidence bytes) should be deferred until after the transaction commits, since file writes cannot participate in SQLite transactions. If the post-commit file write fails, the staged reference rows should be deleted as a compensating action to maintain the invariant "reference exists implies bytes exist."

**Solution:** `domain/usecase/FileClaimUseCase.kt` validates all evidence (count >= 1, each <= 10MB, allowed MIME types) before opening any transaction. Inside a single `database.withTransaction` block, it inserts the claim row via `claimRepository.save()`, which also writes the initial status history, and stages attachment reference rows via `attachmentRepository.stageInTransaction()`. After the transaction commits, `attachmentRepository.commitPayloads()` writes the actual bytes to disk. If this post-commit flush throws, a `catch` block iterates over the staged refs and calls `attachmentRepository.delete()` for each, then re-throws. This guarantees no orphaned refs without backing files and no orphaned files without refs.

---

## 8. Claim Eligibility Window: Which Timestamp to Use

**Question:** The prompt says claims must be "filed within 7 days of trip end," but what constitutes "trip end"? The itinerary has dates, the booking has state timestamps, and the inventory slot has a date range. If the booking's `updatedAt` is used, unrelated updates (like a fee correction) could shift the eligibility window. Which timestamp is authoritative?

**My Understanding:** The trip end should be derived from the inventory slot's date range, copied into the booking at submission time, and treated as immutable from that point forward. This ensures that neither itinerary edits nor administrative booking updates can shift the claim eligibility window.

**Solution:** `domain/usecase/SubmitBookingUseCase.kt` copies `slot.endDate` (converted to an `Instant` at end-of-day in the device timezone) into `BookingOrder.tripEndAt` at submission time. This field is never modified after initial write. `FileClaimUseCase.execute()` reads `booking.tripEndAt` and passes it to `ClaimValidator.validate()`, which checks that `now` is within 7 days of `tripEndAt`. The claim eligibility window is therefore anchored to the slot's original date, not to any mutable booking timestamp.

---

## 9. Sensitive Field Encryption: Key Management and Legacy Data

**Question:** The prompt requires "encrypted storage for sensitive fields" (traveler contact notes, claim descriptions). How should the encryption key be managed in an offline-only app without a server-side key management service? Additionally, if encryption is added after data already exists in plaintext, how should legacy rows be handled during decryption?

**My Understanding:** Android's KeyStore provides hardware-backed (or TEE-backed) key storage that never exports the key material. The encryption codec should use AES-256-GCM with IV prepended to ciphertext, and the encoded output should carry a version prefix so the decryption path can distinguish encrypted rows from legacy plaintext.

**Solution:** `security/SensitiveFieldCodec.kt` defines the interface with `encrypt(plaintext?)` and `decrypt(stored?)` methods. The production implementation, `AesSensitiveFieldCodec`, wraps the `FieldEncryptor` class that uses AndroidKeyStore-backed AES-256-GCM. Encrypted values are stored with an `enc:v1:` prefix. During decryption, if a stored value lacks this prefix, it is returned as-is — this handles legacy plaintext rows transparently without requiring a data migration. `ItineraryRepositoryImpl` encrypts `notes`, `ClaimRepositoryImpl` encrypts `description`, and `InvestigationNote.note` is encrypted on write and decrypted on read through the same codec.

---

## 10. Session Identity: Preventing Caller-Supplied Actor Spoofing

**Question:** The prompt defines four roles with different privileges, but does not specify how the acting user's identity should be threaded through use cases. If use cases accept an `actor` or `userId` parameter, a caller could spoof identity to perform actions as a different user. How should identity resolution work?

**My Understanding:** A single, process-wide session holder should be the exclusive source of acting identity. All use cases should resolve the actor from this holder rather than accepting identity parameters. The session should be set on successful login and cleared on logout or timeout.

**Solution:** `security/auth/SessionManager.kt` is a singleton that holds a `MutableStateFlow<SessionContext?>`. `SessionContext` carries `userId`, `displayName`, `roles: Set<Role>`, and `sessionId`. `LoginUseCase` calls `sessionManager.set()` on success; `LogoutUseCase` and `SessionTimeoutWorker` call `sessionManager.clear()`. Every privileged use case calls `sessionManager.requireSession()` as its first action and passes the result to `AccessControl` for role/ownership checks. No use case accepts actor, role, or userId as a parameter.

---

## 11. Content Similarity: Algorithm Selection Without External ML

**Question:** The prompt requires detection of ">80% hash similarity" for content deduplication but does not specify the similarity algorithm. Hash equality alone cannot detect near-duplicates (it's all-or-nothing). Should an external ML model or NLP library be used, and how should "similarity" be defined for deterministic, reproducible results on-device?

**My Understanding:** An external ML dependency would be disproportionate for an offline Android app. A character n-gram Jaccard coefficient over normalized text provides a deterministic, reproducible similarity score that captures near-duplicates without requiring external models. Hash equality should be used as a fast path for exact duplicates.

**Solution:** `domain/governance/SimilarityCalculator.kt` implements a two-stage approach. First, SHA-256 hashes of normalized text (lowercased, punctuation stripped, whitespace collapsed) are compared for exact-match fast path. For near-duplicates, character 3-gram sets are computed for both texts, and Jaccard similarity (intersection/union) produces a score in [0.0, 1.0]. The `> 0.80` threshold is strict greater-than — exactly 0.80 does not trigger quarantine. When two items match, the later-indexed item is quarantined and the earlier acts as the primary. `DuplicateCluster` records both IDs and the similarity score.

---

## 12. Demotion Threshold Boundary: Inclusive or Exclusive

**Question:** The prompt says content is automatically demoted when "average rating < 2.5 after 10 reviews." Should a rating of exactly 2.5 trigger demotion? The use of "<" suggests no, but a strict reading could go either way.

**My Understanding:** The `<` operator should be interpreted literally: an average of exactly 2.5 does not trigger demotion. This is the conservative choice that avoids penalizing borderline content and aligns with the prompt's explicit use of the less-than symbol.

**Solution:** `domain/governance/GovernanceEvaluator.kt` checks `averageRating < 2.5` as a strict less-than comparison. An item with an average of exactly 2.5 and 10+ reviews remains in the Active state. The threshold constant is defined in `GovernanceThresholds`.

---

## 13. Rollback Semantics: Delete vs. Mark

**Question:** The prompt mentions "one-tap rollback to the last valid transaction" for quarantined or anomalous entries. Should rollback delete the checkpoint record after restoring the previous state, or mark it as used? Deleting checkpoints would lose the audit trail of what was rolled back and when.

**My Understanding:** Checkpoints should be marked as rolled back rather than deleted. This preserves the full audit trail — an administrator can see that a rollback occurred, what the original snapshot contained, and when the restoration happened.

**Solution:** `domain/usecase/RollbackUseCase.kt` accepts a caller-supplied `restorer` lambda to remain generic across entity types. When a rollback is executed, the `TransactionCheckpoint` row has its `rolledBack` flag set to `true` rather than being deleted. The checkpoint's `snapshotJson` field retains the original entity state as JSON. A `ROLLBACK_EXECUTED` audit event is logged with the checkpoint ID, entity type, and actor.

---

## 14. Refund Rules: Hardcoded Constants vs. Persisted Configuration

**Question:** The prompt specifies refund bands (>48h: 100%, 24–48h: 50%, <24h: 0%) as examples, but does not indicate whether these should be hardcoded or configurable. If hardcoded, changing refund policy requires a code release. If configurable, where should the rules be stored and who should be authorized to change them?

**My Understanding:** Refund rules should be persisted in the database and editable by Administrators, with the prompt's examples used as seeded defaults. This allows policy changes without app updates while maintaining the specified defaults on fresh installs.

**Solution:** `RefundRule` entities are stored in the `refund_rules` table with fields for `code`, `minHoursBeforeStartExclusive`, `maxHoursBeforeStartInclusive` (nullable), `refundPercent`, `active` flag, and audit fields. `RefundEngine.compute()` accepts a `List<RefundRule>` parameter instead of using hardcoded constants. `ApproveRefundUseCase` reads `refundRuleRepository.listActive()` and falls back to `RefundEngine.defaultRules()` if no persisted rules exist. `UpdateRefundRuleUseCase` is Administrator-only and appends to `refund_rule_history` on every change.

---

## 15. SLA Timing Model: Wall-Clock Hours vs. Business Hours

**Question:** The prompt specifies "first response in 4 business hours" and "resolution in 3 days," but does not define what constitutes a "business hour." Does it mean 24/7 wall-clock time, or a configured working-hours window? If the latter, are weekends excluded? Are holidays considered?

**My Understanding:** "Business hours" should default to a standard 9 AM–5 PM window with weekends excluded, since the prompt explicitly uses the term "business hours" rather than "hours." Holiday exclusions are not practical for an offline-only app with no external calendar source. The working-hours window should be configurable by Administrators.

**Solution:** `SlaConfig` stores `workDayStartHour` (default 9), `workDayEndHour` (default 17), and `excludeWeekends` (default true). `BusinessHourCalculator.elapsedBusinessMinutes()` counts minute-by-minute within the configured work window, skipping weekends when excluded. `breachInstant()` computes the exact deadline `Instant` for a given number of business minutes. When `excludeWeekends=false` and the work window is 0–24, computation degrades to simple elapsed wall-clock time for backward compatibility. Administrators configure these values via `SlaConfigFragment` and `UpdateSlaConfigUseCase`.

---

## 16. Auto-Close Waiting Tickets: Configurable vs. Fixed Threshold

**Question:** The prompt says tickets auto-close "after 72 hours without customer response." Should this threshold be fixed at 72 hours, or should it be configurable alongside other SLA parameters? If configurable, changes should take effect immediately without requiring a worker restart.

**My Understanding:** The 72-hour no-response threshold should be configurable via the same SLA configuration that controls first-response and resolution windows. Since this is an operational parameter, Administrators should be able to adjust it, and changes should apply to the next worker run without restarting the app.

**Solution:** `SlaConfig` includes a `travelerNoResponseHours` field (default 72). `AutoCloseWaitingTicketsUseCase` reads the live `SlaConfig` from `SlaConfigRepository` on every execution rather than using a hardcoded constant. Since `WaitingTicketTimeoutWorker` invokes the use case on each run (every 1 hour), an Administrator's change to the no-response threshold via `UpdateSlaConfigUseCase` takes effect within the next worker cycle.

---

## 17. Claim Compensation Calculation: Workflow and Authorization

**Question:** The prompt mentions "compensation calculation" for claims but does not detail who performs it, what fields are required, or how the calculated amount is persisted. Is this a separate step from refund approval, and which roles are authorized?

**My Understanding:** Compensation calculation is distinct from refund approval — it applies to claims (service failures, provider no-shows) rather than booking cancellations. It should require Reviewer or Administrator authorization, be attached to the claim ticket, and record the approver's identity and calculation basis for audit purposes.

**Solution:** `ClaimTicket` carries a nullable `CompensationCalculation` with `amount`, `currency`, `basis` (rule or source code), `approverId`, `approverName`, `decidedAt`, and `note`. `SetClaimCompensationUseCase` validates authorization (Reviewer/Administrator only via `AccessControl`), ensures the amount is non-negative, binds the approver identity from the authenticated session, and persists the calculation transactionally alongside an investigation note. A `CLAIM_COMPENSATION_SET` audit event is logged.

---

## 18. Per-User Deletion: Anonymize vs. Hard Delete

**Question:** The prompt specifies "deletions available per user" but does not clarify whether deletion means hard-deleting all user data or anonymizing PII while preserving structural integrity. Hard deletion could break foreign key relationships (e.g., audit logs referencing a deleted user), while anonymization preserves referential integrity but leaves skeleton records.

**My Understanding:** Both modes should be supported. Anonymization should be the default since it preserves FK integrity and audit continuity, while hard deletion should be available for cases where complete removal is required. In both cases, audit logs should never be deleted — they are the compliance record.

**Solution:** `ExecuteUserDeletionUseCase` supports two scopes: `ANONYMIZE` (default) replaces PII with anonymized placeholders in `claim_tickets`, `booking_orders`, `itinerary_drafts`, and the `users` row while deleting `consent_records`, `export_packages`, `role_assignments`, and `credentials`. `HARD_DELETE` removes all user-owned non-audit rows including the `users` row. In both modes, `audit_logs` are retained. The deletion lifecycle follows: `DELETION_REQUESTED → DELETION_APPROVED → DELETION_EXECUTED` (or `DELETION_FAILED`) with corresponding audit events.

---

## 19. USD Amount Storage: Floating Point vs. Integer Cents

**Question:** The prompt requires "USD amounts with two decimals" for fee itemization, but does not specify the storage format. Floating-point representation (e.g., `Double`) introduces rounding errors that accumulate across fee items and refund calculations. What storage format should be used?

**My Understanding:** All monetary values should be stored as integer cents (`Long`) to eliminate floating-point precision issues. Domain models should convert to `BigDecimal` with `HALF_EVEN` rounding for display and calculation, ensuring deterministic arithmetic throughout the fee and refund pipelines.

**Solution:** `FeeItemEntity.amountCents` is stored as `Long` in the database. The domain model `FeeItem` exposes the amount as `BigDecimal` with scale-2 and `RoundingMode.HALF_EVEN`. `BookingOrderEntity.paidTotalCents` follows the same pattern. `RefundEngine.compute()` operates on `BigDecimal` values with explicit scale and rounding mode, and enforces `refundAmount <= paidTotal` via a `coerceAtMost` guard.

---

## 20. SLA Reminders: Deduplication Across Worker Runs

**Question:** The prompt specifies "on-device reminders" for SLA breaches, but if the reminder worker runs every 30 minutes, the same ticket could generate duplicate reminders on consecutive runs. How should reminder deduplication be handled?

**My Understanding:** The `sla_reminders` table should enforce a UNIQUE constraint on `(ticketId, kind)` so that the same ticket cannot have multiple reminders of the same type. The worker should use an upsert-or-ignore pattern, and stale pre-breach reminders should be cleaned up when SLA configuration changes.

**Solution:** `SlaReminderEntity` has a UNIQUE index on `(ticketId, kind)`. `GenerateSlaRemindersUseCase` evaluates open tickets against the live `SlaConfig`, and the DAO uses `OnConflictStrategy.IGNORE` to suppress duplicate inserts. Reminders fire at 25% of the SLA window remaining (minimum 15 minutes lead). When an Administrator updates the SLA configuration via `UpdateSlaConfigUseCase`, stale pre-breach reminder rows are purged so they can be regenerated with the new thresholds.

---

## 21. Audit Log Integrity: Tamper Detection

**Question:** The prompt requires "audit logs" but does not specify whether they should be tamper-evident. In a purely local system, a user with root access could modify the SQLite database directly. Should the audit trail include any integrity verification mechanism?

**My Understanding:** While no local-only mechanism can fully prevent tampering by a root user, chained HMAC checksums make unauthorized modifications detectable. Each audit entry should include a checksum computed over the entry's content and the previous entry's checksum, creating a hash chain. Any gap or modification breaks the chain.

**Solution:** `audit/AuditLogger.kt` defines the logging interface; `RoomAuditLogger` implements it by computing an HMAC-SHA256 checksum for each entry via `AuditChecksum`. The `AuditLogDao` exposes only `@Insert` and `@Query(SELECT)` methods — no update or delete operations are defined, enforcing append-only semantics at the DAO level. The checksum key is currently a static HMAC key embedded in the app; production deployments should source it from AndroidKeyStore.

---

## 22. Exception Booking: Overbooking Controls

**Question:** The prompt says the app "prevents overbooking" with quota validation, but also mentions "exception reasons" for reschedules. Can an Agent or Administrator override the quota check to book beyond available capacity (e.g., for VIP accommodations or emergency situations)? If so, what controls should apply?

**My Understanding:** Exception booking should be supported but gated behind a per-slot configuration flag and restricted to authorized roles. The quota ledger should record exception operations with a distinct operation type so they are auditable and distinguishable from standard reservations.

**Solution:** `InventorySlotEntity` includes an `allowExceptionBooking` boolean flag, managed by Administrators. The `EXCEPTION` quota operation type is defined in `QuotaOperation` and recorded in the append-only `quota_ledger`. Exception operations bypass the standard `availableCount` check but still require the slot's `allowExceptionBooking` flag to be true. All exception bookings are logged with a `QUOTA_EXCEPTION` audit action.

---

## 23. Reschedule 24-Hour Rule: Role-Based Exception Override

**Question:** The prompt says "any reschedule request must be made at least 24 hours before start time unless an Agent records an exception reason." Does this mean Travelers can never reschedule within 24 hours, even with a reason? Or can any role override the rule with a documented exception?

**My Understanding:** The 24-hour rule is absolute for Travelers — they cannot reschedule within 24 hours under any circumstances. Only Agents and Administrators can override the rule, and they must provide a non-blank exception reason that is recorded for audit purposes.

**Solution:** `RequestRescheduleUseCase` checks if the reschedule is within 24 hours of trip start. If it is, only callers with `Agent` or `Administrator` roles (verified via `AccessControl.requireAgentOrAdmin()`) can proceed, and a non-blank `exceptionReason` is required. Travelers attempting to reschedule within 24 hours receive a validation error. The exception reason is persisted on the `RescheduleRequest` record and a `RESCHEDULE_EXCEPTION` audit event is logged.

---

## 24. Demo Seed Data: Production Safety

**Question:** The prompt does not address how test/demo data should be handled across build types. If demo credentials (predictable usernames and passwords) are seeded in production builds, they become a security vulnerability — anyone who reads the source code or documentation knows valid credentials.

**My Understanding:** Demo seed data should be strictly gated to debug builds. Release/production builds should boot with an empty user table, requiring installation scripts or first-launch provisioning to create real users with strong credentials.

**Solution:** `FieldTripApp.onCreate()` checks `BuildConfig.DEBUG` before invoking `SeedData.populate()`. In debug builds, five test users are created (admin/admin123, agent/agent123, reviewer/reviewer123, traveler/traveler123, inactive/inactive123). In release builds, the seed call is skipped entirely, and the app boots to an empty login screen. ProGuard/R8 minification is enabled in release builds, and the seed data class has no references outside the debug-gated path.

---

## 25. Retention Sweep: Hard Delete vs. Anonymize for 24-Month Policy

**Question:** The prompt says "tickets retained 24 months" but does not specify what happens after 24 months — are records hard-deleted or anonymized? Hard deletion could break foreign key relationships with audit logs and booking orders. Anonymization preserves structure but may not satisfy certain data-erasure interpretations.

**My Understanding:** Anonymization is the safer default for the retention sweep. Replacing PII (descriptions, names) with placeholder values like `"[anonymized]"` preserves FK integrity and allows aggregate reporting to continue functioning. Hard deletion should only be used for explicit per-user deletion requests, not for the automated retention sweep.

**Solution:** `RetentionSweepUseCase` queries closed, auto-closed, and finalized tickets older than 24 months and sets their description to `"[anonymized]"` rather than deleting the rows. This preserves foreign key relationships with `ticket_status_history`, `investigation_notes`, and `audit_logs`. The sweep runs via `RetentionSweepWorker` every 24 hours with idle + charging constraints per the prompt's WorkManager guidance. Each anonymization is logged as a `DATA_ANONYMIZED` audit event, and the sweep itself is logged as `RETENTION_SWEEP_RUN`.

---

## 26. Attachment MIME Type Validation: Accepted Formats

**Question:** The prompt says proof attachments should be "photo/PDF up to 10 MB" but does not enumerate which photo formats are accepted. Should the app accept only JPEG, or also PNG, HEIC, WebP, and other formats that Android devices commonly produce?

**My Understanding:** The app should accept all common photo formats that Android devices produce natively, plus PDF. This includes JPEG, PNG, HEIC (used by some Samsung and Pixel devices), and WebP (Android's preferred compressed format). Exotic formats like TIFF or RAW should be excluded to keep validation simple and avoid large file sizes.

**Solution:** `domain/booking/ClaimValidator.kt` defines the accepted MIME types as: `image/jpeg`, `image/jpg`, `image/png`, `image/heic`, `image/webp`, and `application/pdf`. Each attachment is validated for MIME type membership and `sizeBytes <= 10 * 1024 * 1024` (10 MB). At least one attachment is required per claim. Validation runs before any database transaction is opened, so invalid evidence rejects the entire claim filing without side effects.

---

## 27. Concurrent Session Handling

**Question:** The prompt does not specify what happens if the same user logs in from two devices (or two app instances). Should concurrent sessions be allowed, or should a new login invalidate any existing session?

**My Understanding:** A new login should automatically end any existing active session for the same user. This prevents session confusion and ensures a single authoritative acting context per user. The ended session should be recorded with a reason for audit purposes.

**Solution:** `LoginUseCase` calls `sessionRepository.endExistingSessions(userId, reason = "new_login")` before creating a new session. `SessionManager` is a singleton `MutableStateFlow`, so only one `SessionContext` can be active at a time in a given app instance. The ended session is recorded with the reason "new_login" in the `session_audits` table.

---

## 28. Duplicate Quarantine: Which Item Gets Quarantined

**Question:** When two content items are detected as duplicates (>80% similarity), which one should be quarantined — the original, the copy, or both? The prompt says duplicates are "quarantined" but does not specify the tie-breaking rule.

**My Understanding:** The later-indexed item (by creation time or insertion order) should be quarantined as the presumed duplicate, while the earlier item is treated as the primary/original. Both IDs should be recorded in a cluster record for reviewer reference.

**Solution:** `RunDuplicateScanUseCase` iterates content pairs and, when similarity exceeds 0.80, quarantines the later-indexed item (the one with the higher index in the sorted content list). The earlier item acts as the primary. A `DuplicateCluster` record is created with both `primaryItemId` and `duplicateItemId`, along with the computed similarity score. The quarantined item also receives a `RecommendationSuppression` row and a `GovernanceDecision` record, all within a single transaction per pair.

---

## 29. Export Masking Profiles: Role-Based Data Visibility

**Question:** The prompt mentions "exports and deletions available per user" but does not specify whether exported data should be redacted based on the exporter's role. An Administrator export showing full PII has different risk characteristics than a Traveler export.

**My Understanding:** Export masking should be role-based. Administrators see raw values; all other roles see masked phone numbers, email addresses, and names. The masking profile should be determined by the exporter's highest-privilege role.

**Solution:** `domain/export/ExportRenderer.kt` applies masking profiles mapped from highest-privilege role: Administrator > Reviewer > Agent > Traveler. The Administrator profile shows raw values; all others mask phone/email/name columns by header name. Each export is recorded in `export_packages` with file path, row count, SHA-256 checksum, generator identity, and timestamp. A `EXPORT_CREATED` audit event is logged.

---

## 30. Database Migration Safety: Downgrade Protection

**Question:** The prompt does not address database version management. With 10 schema versions, what happens if a user somehow installs an older version of the app with a lower schema version? Should the database be silently downgraded (risking data loss) or should the app fail safely?

**My Understanding:** Downgrades should be destructive rather than silent — it is better to lose local data and start fresh than to operate on a schema the code does not understand. Upgrades, however, should always preserve data through explicit migrations.

**Solution:** `FieldTripDatabase` is built with `fallbackToDestructiveMigrationOnDowngrade()`, meaning downgrade attempts wipe the database and recreate it. All upgrade paths (v1→v10) use explicit `Migration` objects in `data/db/migration/Migrations.kt` that add columns, create tables, and remap data without loss. The schema version is exported (`exportSchema = true`) for migration test verification via Room's `MigrationTestHelper`.
