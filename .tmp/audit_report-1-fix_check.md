# Follow-up Reinspection Report (Against Latest Audit Findings)

- **Date:** 2026-04-13
- **Audit mode:** Static-only (no app run, no tests executed, no Docker)

## Scope
This report checks whether issues listed in the **latest audit** were fixed in the current codebase.

## Issue Status Summary

| Prior Finding | Previous Severity | Current Status |
|---|---:|---|
| Per-user deletion workflow missing | High | **Fixed** |
| No on-device SLA reminder mechanism | High | **Fixed** |
| Claims compensation calculation not tracked | Medium | **Fixed** |
| Refund policy hardcoded (not persisted rules) | Medium | **Fixed** |
| README migration/version mismatch | Low | **Fixed** |

---

## 1) Per-user deletion workflow missing
- **Status:** **Fixed**
- **What changed (static evidence):**
  - Dedicated deletion request/execute use cases added with role/ownership checks and audit trail.
    - `app/src/main/java/com/fieldtripops/domain/usecase/RequestUserDeletionUseCase.kt:27`
    - `app/src/main/java/com/fieldtripops/domain/usecase/ExecuteUserDeletionUseCase.kt:22`
  - Repository contract includes transactional deletion/anonymization execution.
    - `app/src/main/java/com/fieldtripops/domain/repository/DeletionRequestRepository.kt:6`
    - `app/src/main/java/com/fieldtripops/domain/repository/DeletionRequestRepository.kt:19`
  - Room schema includes `DeletionRequestEntity` at DB v7.
    - `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:91`
    - `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:95`
  - UI paths documented and wired for requester/admin workflows.
    - `README.md:37`
    - `README.md:56`

## 2) No on-device SLA reminder mechanism
- **Status:** **Fixed**
- **What changed (static evidence):**
  - Reminder generation use case added with dedup/stale purge behavior.
    - `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:25`
    - `app/src/main/java/com/fieldtripops/domain/usecase/GenerateSlaRemindersUseCase.kt:40`
  - Worker added to generate reminders and post local notifications.
    - `app/src/main/java/com/fieldtripops/work/SlaReminderWorker.kt:24`
    - `app/src/main/java/com/fieldtripops/work/SlaReminderWorker.kt:53`
  - Worker scheduling added in app bootstrap (30-minute cadence).
    - `app/src/main/java/com/fieldtripops/FieldTripApp.kt:98`
    - `app/src/main/java/com/fieldtripops/FieldTripApp.kt:100`
  - Schema support added (`SlaReminderEntity`, DB v7).
    - `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:91`
    - `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:92`
- **Boundary:** Runtime notification delivery behavior is **Manual Verification Required**.

## 3) Claims compensation calculation not tracked
- **Status:** **Fixed**
- **What changed (static evidence):**
  - Claim model now includes `CompensationCalculation`.
    - `app/src/main/java/com/fieldtripops/domain/model/ClaimTicket.kt:21`
    - `app/src/main/java/com/fieldtripops/domain/model/ClaimTicket.kt:28`
  - Dedicated compensation use case added with reviewer/admin authorization and validation.
    - `app/src/main/java/com/fieldtripops/domain/usecase/SetClaimCompensationUseCase.kt:25`
    - `app/src/main/java/com/fieldtripops/domain/usecase/SetClaimCompensationUseCase.kt:44`
  - Repository interface and implementation persist compensation atomically.
    - `app/src/main/java/com/fieldtripops/domain/repository/ClaimRepository.kt:30`
    - `app/src/main/java/com/fieldtripops/data/repository/ClaimRepositoryImpl.kt:116`
  - Mapping to/from entity includes compensation fields.
    - `app/src/main/java/com/fieldtripops/data/repository/ClaimRepositoryImpl.kt:155`
    - `app/src/main/java/com/fieldtripops/data/repository/ClaimRepositoryImpl.kt:180`

## 4) Refund policy hardcoded instead of persisted rules
- **Status:** **Fixed**
- **What changed (static evidence):**
  - Refund engine now accepts persisted rule list and documents repository-driven behavior.
    - `app/src/main/java/com/fieldtripops/domain/booking/RefundEngine.kt:10`
    - `app/src/main/java/com/fieldtripops/domain/booking/RefundEngine.kt:69`
  - `ApproveRefundUseCase` now reads active rules from `RefundRuleRepository`.
    - `app/src/main/java/com/fieldtripops/domain/usecase/ApproveRefundUseCase.kt:19`
    - `app/src/main/java/com/fieldtripops/domain/usecase/ApproveRefundUseCase.kt:49`
  - Refund rules repository contract added (active/all/find/upsert/seed defaults).
    - `app/src/main/java/com/fieldtripops/domain/repository/RefundRuleRepository.kt:5`
  - Admin update use case for refund rules added.
    - `app/src/main/java/com/fieldtripops/domain/usecase/UpdateRefundRuleUseCase.kt:16`
- **Boundary:** Effective runtime rule application remains **Manual Verification Required** unless executed tests/build are run.

## 5) README migration/version mismatch
- **Status:** **Fixed**
- **What changed (static evidence):**
  - README now states migrations as `v1?v7`.
    - `README.md:195`
  - Database version is now 7 and aligned with phase-7 entities.
    - `app/src/main/java/com/fieldtripops/data/db/FieldTripDatabase.kt:95`

---

## Final Follow-up Conclusion
- **Result:** All issues reported in the latest audit are now **statically fixed**.
- **Static verification boundary:** Runtime behavior (notification delivery timing, actual deletion side-effects on live device data, operational performance) remains **Manual Verification Required**.
