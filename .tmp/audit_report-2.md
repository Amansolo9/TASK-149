# Static Audit Report (Regenerated)

## 1. Verdict
- **Overall conclusion:** **Partial Pass**
- Material progress is visible, but there are still High-severity delivery and security gaps, plus stale test artifacts that are statically inconsistent with current production signatures.

## 2. Scope and Static Verification Boundary
- **Reviewed:** Android app source under `app/src/main`, unit/instrumented tests under `app/src/test` and `app/src/androidTest`, Room schema/migrations, DI wiring, workers, navigation, and README.
- **Not reviewed:** Runtime behavior on device/emulator, actual frame timing/memory profiling, SQL latency benchmarking on 100k records, WorkManager execution cadence in real OS conditions.
- **Intentionally not executed:** app startup, Gradle build/tests, Docker, external services.
- **Manual verification required for:**
  - 60fps list scrolling with 1,000+ records
  - sub-50ms query targets on 100,000 rows
  - peak image memory <20 MB under real workloads
  - WorkManager scheduling behavior under Android idle/charging restrictions

## 3. Repository / Requirement Mapping Summary
- **Prompt core goal mapped:** Offline Android system for itinerary booking ops, content governance, claims/appeals, SLA/retention/admin controls across Traveler/Agent/Reviewer/Admin roles.
- **Core flows mapped against code:** itinerary wizard + booking submission, booking confirmation/cancellation/reschedule, refund decisioning, claim filing with attachments, claim transitions/appeals, SLA reminders, governance/dedup, retention and deletion workflows, auth/session/role guards.
- **Primary implementation areas reviewed:** `domain/usecase/*`, `ui/*`, `security/auth/*`, `data/repository/*`, `data/db/*`, `work/*`, `README.md`.

## 4. Section-by-section Review

### 1. Hard Gates
#### 1.1 Documentation and static verifiability
- **Conclusion:** **Partial Pass**
- **Rationale:** README gives setup/build/test commands and architecture overview, but key static inconsistencies remain (schema/version docs and stale tests no longer matching current signatures).
- **Evidence:** `README.md:163`, `README.md:282`, `README.md:195`, `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:95`, `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:39`, `app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:109`

#### 1.2 Material deviation from Prompt
- **Conclusion:** **Partial Pass**
- **Rationale:** System is mostly aligned, but there are deviations in strict booking terminal semantics and rollback workflow behavior.
- **Evidence:** `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:30`, `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:31`, `app/src/main/java/com/fieldtripops/ui/review/QuarantineFragment.kt:41`, `app/src/main/java/com/fieldtripops/domain/usecase/RollbackUseCase.kt:32`

### 2. Delivery Completeness
#### 2.1 Coverage of explicit core requirements
- **Conclusion:** **Partial Pass**
- **Rationale:** Many core requirements are implemented (offline Room, quota checks, claim evidence, reschedule/refund rules, SLA/deletion), but several explicit requirements are still incomplete or only partial.
- **Evidence:**
  - Booking submit/quota + transactional reserve: `app/src/main/java/com/fieldtripops/domain/usecase/SubmitBookingUseCase.kt:82`
  - 30-min pending auto-close: `app/src/main/java/com/fieldtripops/domain/usecase/AutoCloseStalePendingUseCase.kt:26`
  - Claim evidence + 7-day window: `app/src/main/java/com/fieldtripops/domain/booking/ClaimValidator.kt:35`
  - 72h-like config-based waiting auto-close: `app/src/main/java/com/fieldtripops/domain/usecase/AutoCloseWaitingTicketsUseCase.kt:25`
  - Gap: agent operational access flow (UI shows own bookings only): `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:159`

#### 2.2 End-to-end 0?1 deliverable vs partial/demo
- **Conclusion:** **Partial Pass**
- **Rationale:** Repo has full Android project structure and broad feature set; however, stale tests and some incomplete operational flows reduce confidence for acceptance as fully production-shaped.
- **Evidence:** `README.md:188`, `app/src/main/java/com/fieldtripops/FieldTripApp.kt:35`, `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:44`

### 3. Engineering and Architecture Quality
#### 3.1 Structure and module decomposition
- **Conclusion:** **Pass**
- **Rationale:** Clear layering (UI/ViewModel/UseCase/Repository/Room), DI via Koin, worker separation, and dedicated domain modules.
- **Evidence:** `README.md:200`, `app/src/main/java/com/fieldtripops/di/UseCaseModule.kt:36`, `app/src/main/java/com/fieldtripops/di/RepositoryModule.kt:58`

#### 3.2 Maintainability and extensibility
- **Conclusion:** **Partial Pass**
- **Rationale:** Good use-case decomposition and centralized auth policy, but dead/legacy seams and stale tests increase maintenance risk.
- **Evidence:** `app/src/main/java/com/fieldtripops/security/auth/AccessControl.kt:9`, `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:182`, `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:39`

### 4. Engineering Details and Professionalism
#### 4.1 Error handling, logging, validation, API design
- **Conclusion:** **Partial Pass**
- **Rationale:** Validation/error handling is generally present and audit logging is structured; however, there is at least one direct repository read path without authorization enforcement.
- **Evidence:** `app/src/main/java/com/fieldtripops/domain/booking/ItineraryValidator.kt:21`, `app/src/main/java/com/fieldtripops/audit/RoomAuditLogger.kt:14`, `app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:34`

#### 4.2 Product-level organization vs demo
- **Conclusion:** **Partial Pass**
- **Rationale:** App resembles a real product in scope; remaining gaps are around requirement fit and acceptance integrity, not just toy scaffolding.
- **Evidence:** `README.md:220`, `app/src/main/java/com/fieldtripops/work/SlaReminderWorker.kt:24`

### 5. Prompt Understanding and Requirement Fit
#### 5.1 Business-goal and constraints fit
- **Conclusion:** **Partial Pass**
- **Rationale:** Most domain semantics are recognized, but prompt-specific constraints still have notable misses (agent workflow reachability, strict booking terminals, rollback semantics, business-hours SLA interpretation).
- **Evidence:** `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:159`, `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:31`, `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:42`

### 6. Aesthetics (frontend-only / full-stack tasks)
#### 6.1 Visual/interaction quality
- **Conclusion:** **Cannot Confirm Statistically**
- **Rationale:** Static XML/View code exists, but visual rendering quality and interaction polish require runtime/manual UI inspection.
- **Evidence:** `app/src/main/res/layout/fragment_shell.xml:1` (layout exists), `app/src/main/res/layout/fragment_itinerary_wizard.xml:1` (layout exists)
- **Manual verification note:** Validate alignment/spacing/feedback and role-surface clarity on-device.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker / High
1. **Severity:** High  
   **Title:** Stale tests are statically incompatible with current production signatures  
   **Conclusion:** Fail  
   **Evidence:**
   - `app/src/main/java/com/fieldtripops/domain/usecase/ApproveRefundUseCase.kt:44` (current execute signature)
   - `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:44` (old call shape)
   - `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:39` (old constructor shape)
   - `app/src/main/java/com/fieldtripops/domain/usecase/FileClaimUseCase.kt:64` (current execute signature)
   - `app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:109` (extra argument)
   - `app/src/main/java/com/fieldtripops/data/entity/BookingOrderEntity.kt:38` (new required fields)
   - `app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:87` (old constructor usage)
   - `app/src/main/java/com/fieldtripops/domain/model/BookingOrder.kt:21`
   - `app/src/test/java/com/fieldtripops/domain/usecase/CancelBookingOwnershipTest.kt:46`
   **Impact:** Static acceptance confidence is weakened; documented test surface is unreliable and may not compile against current code.
   **Minimum actionable fix:** Update/remove obsolete tests so all test sources match current public signatures/entities and maintain a single authoritative test suite.

2. **Severity:** High  
   **Title:** Agent booking workflow is not materially reachable from current UI flow  
   **Conclusion:** Fail  
   **Evidence:**
   - `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:159` (loads bookings by current session user only)
   - `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:60` (confirm flow only from displayed list)
   - `app/src/main/java/com/fieldtripops/domain/usecase/ConfirmBookingUseCase.kt:43` (agent/admin required)
   **Impact:** Prompt says Agent books/reschedules/itemizes fees; current UI path does not provide agent access to traveler pending bookings for confirmation.
   **Minimum actionable fix:** Add agent queue/search surface for pending bookings across travelers (with proper authz and audit) and wire it to confirmation/reschedule flows.

3. **Severity:** High  
   **Title:** Booking confirmation screen has read-path authorization gap  
   **Conclusion:** Fail  
   **Evidence:**
   - `app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmFragment.kt:19` (no route guard base)
   - `app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:34` (direct `findById` read)
   - `app/src/main/res/navigation/nav_graph.xml:72` (destination is routable)
   **Impact:** Sensitive booking details can be loaded by ID without explicit object-level read authorization in this path.
   **Minimum actionable fix:** Enforce role+ownership checks before data load (route-level + use-case-backed read API with AccessControl).

4. **Severity:** High  
   **Title:** One-tap rollback requirement is not implemented as rollback-to-checkpoint flow  
   **Conclusion:** Fail  
   **Evidence:**
   - `app/src/main/java/com/fieldtripops/ui/review/QuarantineFragment.kt:41` (restore triggers override)
   - `app/src/main/java/com/fieldtripops/ui/review/QuarantineViewModel.kt:51` (calls `GovernanceOverrideUseCase`)
   - `app/src/main/java/com/fieldtripops/domain/usecase/RollbackUseCase.kt:32` (rollback exists but not used by quarantine action)
   **Impact:** Prompt calls for quarantine anomalies with one-tap rollback to last valid transaction; implementation currently does state override, not checkpoint restoration.
   **Minimum actionable fix:** Wire quarantine one-tap action to checkpoint-backed rollback flow and ensure checkpoints are created in relevant mutation paths.

### Medium
5. **Severity:** Medium  
   **Title:** Strict booking terminal-state semantics differ from prompt wording  
   **Conclusion:** Partial Fail  
   **Evidence:** `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:11`, `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:31`  
   **Impact:** Prompt specifies Cancelled/Closed as terminal; model introduces `AutoClosed` terminal separately.
   **Minimum actionable fix:** Either align state machine exactly to prompt or document/justify accepted semantic extension clearly in acceptance notes.

6. **Severity:** Medium  
   **Title:** Image downsampling/LRU components exist but are not integrated into app flows  
   **Conclusion:** Partial Fail  
   **Evidence:**
   - `app/src/main/java/com/fieldtripops/attachment/ImageDecoder.kt:25`
   - `app/src/main/java/com/fieldtripops/attachment/AttachmentImageCache.kt:15`
   - `app/src/main/java/com/fieldtripops/di/RepositoryModule.kt:65` (cache registered)
   - `app/src/main/java/com/fieldtripops/ui/claims/FileClaimFragment.kt:100` (attachment handling path has no decoder/cache use)
   **Impact:** Prompt-level image memory/performance requirement is not demonstrated in active UI code paths.
   **Minimum actionable fix:** Integrate decoder+cache where attachment previews are rendered and add integration tests around decode/cache lifecycle.

7. **Severity:** Medium  
   **Title:** SLA implementation does not model business-hour semantics  
   **Conclusion:** Partial Fail  
   **Evidence:** `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:42`, `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:51`, `app/src/main/java/com/fieldtripops/domain/usecase/UpdateSlaConfigUseCase.kt:27`  
   **Impact:** Prompt examples reference business-hour SLA; current logic uses continuous elapsed minutes/hours only.
   **Minimum actionable fix:** Add configurable business-calendar support (working hours/weekends/holidays) or explicitly scope SLA semantics to elapsed-clock time and update acceptance expectations.

8. **Severity:** Medium  
   **Title:** README schema/project metadata is outdated after v8 migration  
   **Conclusion:** Partial Fail  
   **Evidence:** `README.md:195`, `README.md:282`, `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:95`  
   **Impact:** Reduces static verifiability and reviewer trust.
   **Minimum actionable fix:** Update README schema/version statements and migration references to v8.

### Low
9. **Severity:** Low  
   **Title:** Legacy `UserIdHolder` remains in login flow despite session-manager model  
   **Conclusion:** Partial Fail  
   **Evidence:** `app/src/main/java/com/fieldtripops/ui/login/LoginFragment.kt:15`, `app/src/main/java/com/fieldtripops/ui/login/LoginFragment.kt:60`, `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:182`  
   **Impact:** Increases confusion and long-term misuse risk.
   **Minimum actionable fix:** Remove legacy holder and keep a single source of identity truth (`SessionManager`).

## 6. Security Review Summary
- **Authentication entry points:** **Pass**  
  Evidence: session established on login and bound into `SessionManager` (`app/src/main/java/com/fieldtripops/domain/usecase/LoginUseCase.kt:116`), cleared on logout (`app/src/main/java/com/fieldtripops/domain/usecase/LogoutUseCase.kt:26`).

- **Route-level authorization:** **Partial Pass**  
  Evidence: admin/reviewer screens use `AuthorizedFragment` (`app/src/main/java/com/fieldtripops/ui/reports/ReportsFragment.kt:12`, `app/src/main/java/com/fieldtripops/ui/admin/SlaConfigFragment.kt:13`), but `BookingConfirmFragment` has no equivalent guard (`app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmFragment.kt:19`).

- **Object-level authorization:** **Partial Pass**  
  Evidence: strong owner/role checks in use cases (`app/src/main/java/com/fieldtripops/domain/usecase/FileClaimUseCase.kt:77`, `app/src/main/java/com/fieldtripops/domain/usecase/CancelBookingUseCase.kt:39`); gap on read path in booking confirm loader (`app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:34`).

- **Function-level authorization:** **Pass**  
  Evidence: centralized guards (`app/src/main/java/com/fieldtripops/security/auth/AccessControl.kt:11`) used across privileged use cases (e.g., `UpdateSlaConfigUseCase`, `GenerateReportUseCase`, `ExecuteUserDeletionUseCase`).

- **Tenant/user data isolation:** **Partial Pass**  
  Evidence: use cases generally bind actor via session and enforce owner checks (`app/src/main/java/com/fieldtripops/domain/usecase/SubmitBookingUseCase.kt:56`, `app/src/main/java/com/fieldtripops/domain/usecase/GenerateExportUseCase.kt:49`); direct repository read in booking confirm weakens isolation for that route (`app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:34`).

- **Admin/internal/debug protection:** **Pass**  
  Evidence: admin operations require admin role in both UI and use cases (`app/src/main/java/com/fieldtripops/ui/admin/DeletionRequestsViewModel.kt:40`, `app/src/main/java/com/fieldtripops/domain/usecase/ExecuteUserDeletionUseCase.kt:36`). No debug endpoints found.

## 7. Tests and Logging Review
- **Unit tests:** **Partial Pass**  
  Many relevant tests exist (`app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundAuthoritativeTest.kt:24`, `app/src/test/java/com/fieldtripops/domain/usecase/TransitionClaimAuthzTest.kt:23`), but stale incompatible tests remain (`app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:39`).

- **API/integration tests:** **Partial Pass**  
  Instrumented tests exist for migrations/atomicity/quota (`app/src/androidTest/java/com/fieldtripops/domain/usecase/SubmitBookingQuotaTest.kt:29`, `app/src/androidTest/java/com/fieldtripops/data/db/V6V7MigrationTest.kt:12`), but some are stale against latest signatures (`app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:109`).

- **Logging categories/observability:** **Pass**  
  Audit action taxonomy is comprehensive (`app/src/main/java/com/fieldtripops/audit/AuditAction.kt:3`) and writes are centralized/chained (`app/src/main/java/com/fieldtripops/audit/RoomAuditLogger.kt:23`).

- **Sensitive-data leakage risk in logs/responses:** **Partial Pass**  
  Core sensitive text fields are encrypted at rest (`app/src/main/java/com/fieldtripops/data/repository/ClaimRepositoryImpl.kt:147`, `app/src/main/java/com/fieldtripops/data/repository/ItineraryRepositoryImpl.kt:59`). Some audit details still include user display names/reasons (`app/src/main/java/com/fieldtripops/domain/usecase/ApproveRefundUseCase.kt:92`) which may be acceptable operationally but should be reviewed against minimization policy.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- **Unit tests exist:** Yes (`app/src/test/java/...`), using JUnit/MockK/Truth/Coroutines-test (`app/build.gradle.kts:87`).
- **Instrumented/integration tests exist:** Yes (`app/src/androidTest/java/...`) via AndroidJUnitRunner (`app/build.gradle.kts:18`, `app/build.gradle.kts:95`).
- **Test entry points documented:** Yes (`README.md:167`, `README.md:168`).
- **Static caveat:** Some tests are incompatible with current production signatures (see Issues #1), reducing trust in stated coverage.

### 8.2 Coverage Mapping Table
| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Refund uses authoritative persisted booking totals/times | `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundAuthoritativeTest.kt:57` | asserts captured decision uses persisted paid total and session approver (`...:65`, `...:67`) | sufficient | None major | Keep and extend with edge-time bands |
| Booking quota validation and duplicate submit behavior | `app/src/androidTest/java/com/fieldtripops/domain/usecase/SubmitBookingQuotaTest.kt:84` | sold-out result with remaining/requested (`...:97`), duplicate rejection (`...:109`) | sufficient | No direct concurrent-race test | Add concurrent submit contention test against same slot |
| Claim evidence requirements + ownership | `app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:123` | no evidence rejected (`...:129`), cross-owner blocked (`...:192`) | insufficient | Test file is statically stale vs current signatures | Rewrite this class to current API/entity signatures and re-validate atomicity cases |
| Transition authorization matrix for claims | `app/src/test/java/com/fieldtripops/domain/usecase/TransitionClaimAuthzTest.kt:46` | traveler denied / reviewer-admin allowed (`...:54`, `...:70`) | basically covered | No explicit appealed-owner path assertion | Add tests for owner appeal transitions and denied non-owner traveler |
| Route-level admin/reviewer access | (No dedicated UI auth tests found) | N/A | missing | Fragments rely on runtime route guards without test coverage | Add fragment tests validating `AuthorizedFragment` enforcement behavior |
| Off-main-thread heavy report/export dispatch | `app/src/test/java/com/fieldtripops/ui/reports/ReportsViewModelDispatcherTest.kt:50` | asserts use case executes on injected IO dispatcher (`...:54`) | basically covered | Only report path covered | Add equivalent test for export path and large payload formatting |
| Migration safety for v6?v7 features | `app/src/androidTest/java/com/fieldtripops/data/db/V6V7MigrationTest.kt:21` | checks new tables/columns (`...:28`, `...:33`) | basically covered | No v7?v8 migration test observed | Add migration test covering `booking_orders.tripStartAt/tripEndAt/paidTotalCents` backfill |

### 8.3 Security Coverage Audit
- **Authentication:** Basically covered by unit tests around session/access primitives (`app/src/test/java/com/fieldtripops/security/auth/AccessControlTest.kt:53`).
- **Route authorization:** Missing direct tests for fragment-level route guards; severe route bugs could slip through.
- **Object-level authorization:** Partially covered (`CancelBookingOwnershipTest`, `TransitionClaimAuthzTest`), but read-side object auth gap in booking confirm route is not tested.
- **Tenant/data isolation:** Partially covered in use-case tests; no end-to-end UI/route isolation tests.
- **Admin/internal protection:** Basically covered in access-control and admin use-case tests, but no integration tests for navigation bypass attempts.

### 8.4 Final Coverage Judgment
- **Final Coverage Judgment:** **Partial Pass**
- **Boundary explanation:**
  - Major business logic areas (refund, claim transitions, quota, some migrations) have meaningful static tests.
  - However, stale incompatible tests and missing route-level/security integration coverage mean severe defects could still remain undetected while parts of test suite appear present.

## 9. Final Notes
- The codebase shows substantial remediation progress and stronger use-case-level authorization than before.
- Remaining acceptance blockers are concentrated in: stale test integrity, agent-operability path completeness, booking read-path authorization, and checkpoint rollback requirement fit.
- Performance and UX quality claims remain **Manual Verification Required** due static-only audit boundary.
