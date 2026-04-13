# Follow-up Report Against Latest Audit Issues

Date: 2026-04-13  
Mode: Static-only (no run/tests/docker; no code changes)

## Overall Follow-up Verdict
- **Fixed:** 9
- **Partially Fixed:** 0
- **Not Fixed:** 0

## Issue-by-Issue Status

### 1) Stale tests incompatible with current signatures (High)
- **Previous status:** Fail
- **Current status:** **Fixed (static evidence)**
- **Evidence:**
  - `ApproveRefundUseCaseTest` now uses current constructor and booking repository: `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:44`
  - `ApproveRefundUseCaseTest` now calls `execute("b1")` with current signature: `app/src/test/java/com/fieldtripops/domain/usecase/ApproveRefundUseCaseTest.kt:67`
  - `FileClaimAtomicityTest` now calls current `FileClaimUseCase.execute(...)` shape: `app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:113`
  - `FileClaimAtomicityTest` now seeds `BookingOrderEntity` with `tripStartAt/tripEndAt/paidTotalCents`: `app/src/androidTest/java/com/fieldtripops/domain/usecase/FileClaimAtomicityTest.kt:96`
  - `CancelBookingOwnershipTest` now builds `BookingOrder` with new fields: `app/src/test/java/com/fieldtripops/domain/usecase/CancelBookingOwnershipTest.kt:58`
- **Boundary:** Build/test execution not performed; compile/runtime still manual verification.

### 2) Agent booking workflow not reachable (High)
- **Previous status:** Fail
- **Current status:** **Fixed**
- **Evidence:**
  - Agent queue UI added: `app/src/main/res/layout/fragment_shell.xml:96`
  - Agent/Admin visibility and queue adapter wiring: `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:90`, `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:94`
  - Pending-confirmation queue load across bookings: `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:193`
  - Queue click opens booking confirmation flow: `app/src/main/java/com/fieldtripops/ui/shell/ShellFragment.kt:95`

### 3) Booking confirmation read-path authorization gap (High)
- **Previous status:** Fail
- **Current status:** **Fixed**
- **Evidence:**
  - Session required before load: `app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:39`
  - Object-level auth enforced on load (owner/Agent/Admin): `app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:46`
  - Unauthorized load path handled: `app/src/main/java/com/fieldtripops/ui/booking/BookingConfirmViewModel.kt:51`

### 4) One-tap rollback not implemented as checkpoint rollback (High)
- **Previous status:** Fail
- **Current status:** **Fixed**
- **Evidence:**
  - Duplicate quarantine path creates checkpoint before state change: `app/src/main/java/com/fieldtripops/domain/usecase/RunDuplicateScanUseCase.kt:59`
  - Manual governance override creates checkpoint before state change: `app/src/main/java/com/fieldtripops/domain/usecase/GovernanceOverrideUseCase.kt:52`
  - Auto-demotion path creates checkpoint before state change: `app/src/main/java/com/fieldtripops/domain/usecase/RateContentUseCase.kt:52`
  - Quarantine restore executes rollback flow from checkpoint: `app/src/main/java/com/fieldtripops/ui/review/QuarantineViewModel.kt:56`

### 5) Booking terminal semantics mismatch (Medium)
- **Previous status:** Partial Fail
- **Current status:** **Fixed**
- **Evidence:**
  - `AutoClosed` removed from booking enum; only `Cancelled` and `Closed` terminal: `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:10`, `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:11`, `app/src/main/java/com/fieldtripops/domain/model/BookingState.kt:31`
  - Migration remaps legacy `AutoClosed -> Closed`: `app/src/main/java/com/fieldtripops/data/db/migration/Migrations.kt:368`, `app/src/main/java/com/fieldtripops/data/db/migration/Migrations.kt:370`

### 6) Image downsampling/LRU not integrated (Medium)
- **Previous status:** Partial Fail
- **Current status:** **Fixed**
- **Evidence:**
  - `AttachmentImageCache` injected in claim UI: `app/src/main/java/com/fieldtripops/ui/claims/FileClaimFragment.kt:40`
  - Downsampled decode used for previews: `app/src/main/java/com/fieldtripops/ui/claims/FileClaimFragment.kt:163`
  - Decoded bitmap cached in LRU: `app/src/main/java/com/fieldtripops/ui/claims/FileClaimFragment.kt:165`

### 7) SLA business-hour semantics not modeled (Medium)
- **Previous status:** Partial Fail
- **Current status:** **Fixed**
- **Evidence:**
  - Business-hour calculator implemented: `app/src/main/java/com/fieldtripops/domain/sla/BusinessHourCalculator.kt:21`
  - Reminder due/breach calculations now use business-hour deadlines: `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:55`, `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:106`
  - SLA config persists business-hour controls: `app/src/main/java/com/fieldtripops/domain/sla/SlaConfig.kt:31`, `app/src/main/java/com/fieldtripops/domain/sla/SlaConfig.kt:33`, `app/src/main/java/com/fieldtripops/domain/sla/SlaConfig.kt:35`
  - Schema migration adds persisted business-hour fields: `app/src/main/java/com/fieldtripops/data/db/migration/Migrations.kt:375`, `app/src/main/java/com/fieldtripops/data/db/migration/Migrations.kt:380`

### 8) README schema/project metadata outdated (Medium)
- **Previous status:** Partial Fail
- **Current status:** **Fixed**
- **Evidence:**
  - Previously stale `schema v7` headers now indicate historical start plus current schema: `README.md:37`, `README.md:59`, `README.md:71`, `README.md:81`
  - Database version sections now reflect current v10 context: `README.md:205`, `README.md:292`

### 9) Legacy `UserIdHolder` remaining (Low)
- **Previous status:** Partial Fail
- **Current status:** **Fixed**
- **Evidence:**
  - `LoginFragment` no longer imports/sets holder: `app/src/main/java/com/fieldtripops/ui/login/LoginFragment.kt:15`, `app/src/main/java/com/fieldtripops/ui/login/LoginFragment.kt:59`
  - No `UserIdHolder` object usage found in `app/src/main` (only historical comment mention in `SessionManager` docs): `app/src/main/java/com/fieldtripops/security/auth/SessionManager.kt:10`

## Follow-up Conclusion
All previously remaining items from the prior follow-up are now addressed by static evidence.  
Runtime behavior, migration execution, and test pass/fail remain **Manual Verification Required** because this audit intentionally did not execute the app or tests.
