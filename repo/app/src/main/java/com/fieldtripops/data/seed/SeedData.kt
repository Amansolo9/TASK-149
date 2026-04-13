package com.fieldtripops.data.seed

import com.fieldtripops.data.db.FieldTripDatabase
import com.fieldtripops.data.entity.CredentialEntity
import com.fieldtripops.data.entity.InventorySlotEntity
import com.fieldtripops.data.entity.RoleAssignmentEntity
import com.fieldtripops.data.entity.UserEntity
import com.fieldtripops.security.PasswordHasher
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

object SeedData {

    suspend fun populate(database: FieldTripDatabase) {
        val userDao = database.userDao()
        val roleDao = database.roleAssignmentDao()
        val credDao = database.credentialDao()
        val hasher = PasswordHasher()

        // Check if data already exists
        if (userDao.getAll().isNotEmpty()) return

        val now = Instant.now().toEpochMilli()

        // Admin user
        val adminId = "seed-admin-001"
        userDao.insert(UserEntity(adminId, "admin", "System Administrator", true, now, now))
        roleDao.insert(RoleAssignmentEntity(UUID.randomUUID().toString(), adminId, "Administrator", now, "system"))
        val adminSalt = hasher.generateSalt()
        credDao.insert(CredentialEntity(adminId, hasher.hash("admin123", adminSalt), adminSalt, 0, null, null))

        // Agent user
        val agentId = "seed-agent-001"
        userDao.insert(UserEntity(agentId, "agent", "Travel Agent", true, now, now))
        roleDao.insert(RoleAssignmentEntity(UUID.randomUUID().toString(), agentId, "Agent", now, "system"))
        val agentSalt = hasher.generateSalt()
        credDao.insert(CredentialEntity(agentId, hasher.hash("agent123", agentSalt), agentSalt, 0, null, null))

        // Reviewer user
        val reviewerId = "seed-reviewer-001"
        userDao.insert(UserEntity(reviewerId, "reviewer", "Content Reviewer", true, now, now))
        roleDao.insert(RoleAssignmentEntity(UUID.randomUUID().toString(), reviewerId, "Reviewer", now, "system"))
        val reviewerSalt = hasher.generateSalt()
        credDao.insert(CredentialEntity(reviewerId, hasher.hash("reviewer123", reviewerSalt), reviewerSalt, 0, null, null))

        // Traveler user
        val travelerId = "seed-traveler-001"
        userDao.insert(UserEntity(travelerId, "traveler", "Jane Traveler", true, now, now))
        roleDao.insert(RoleAssignmentEntity(UUID.randomUUID().toString(), travelerId, "Traveler", now, "system"))
        val travelerSalt = hasher.generateSalt()
        credDao.insert(CredentialEntity(travelerId, hasher.hash("traveler123", travelerSalt), travelerSalt, 0, null, null))

        // Inactive user for testing
        val inactiveId = "seed-inactive-001"
        userDao.insert(UserEntity(inactiveId, "inactive", "Inactive User", false, now, now))
        roleDao.insert(RoleAssignmentEntity(UUID.randomUUID().toString(), inactiveId, "Traveler", now, "system"))
        val inactiveSalt = hasher.generateSalt()
        credDao.insert(CredentialEntity(inactiveId, hasher.hash("inactive123", inactiveSalt), inactiveSalt, 0, null, null))

        // Seed a few demo inventory slots so the traveler booking flow has
        // something to submit against in debug builds.
        val inventoryDao = database.inventorySlotDao()
        val today = LocalDate.now()
        val slots = listOf(
            InventorySlotEntity(
                id = "slot-demo-standard-7d",
                itineraryType = "standard",
                serviceName = "Demo Standard Tour",
                startDateEpochDay = today.plusDays(14).toEpochDay(),
                endDateEpochDay = today.plusDays(16).toEpochDay(),
                totalQuota = 4, reservedCount = 0, bookedCount = 0,
                allowExceptionBooking = false
            ),
            InventorySlotEntity(
                id = "slot-demo-premium-14d",
                itineraryType = "premium",
                serviceName = "Demo Premium Trek",
                startDateEpochDay = today.plusDays(60).toEpochDay(),
                endDateEpochDay = today.plusDays(65).toEpochDay(),
                totalQuota = 8, reservedCount = 0, bookedCount = 0,
                allowExceptionBooking = true
            ),
            InventorySlotEntity(
                id = "slot-demo-soldout",
                itineraryType = "standard",
                serviceName = "Sold Out Sample",
                startDateEpochDay = today.plusDays(5).toEpochDay(),
                endDateEpochDay = today.plusDays(7).toEpochDay(),
                totalQuota = 2, reservedCount = 0, bookedCount = 2,
                allowExceptionBooking = false
            )
        )
        for (s in slots) inventoryDao.upsert(s)
    }
}
