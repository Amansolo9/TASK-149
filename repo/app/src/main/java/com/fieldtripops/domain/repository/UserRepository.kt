package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.RoleAssignment
import com.fieldtripops.domain.model.User

interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByUsername(username: String): User?
    suspend fun save(user: User)
    suspend fun getAll(): List<User>
    suspend fun assignRole(assignment: RoleAssignment)
    suspend fun getRoles(userId: String): List<Role>
}
