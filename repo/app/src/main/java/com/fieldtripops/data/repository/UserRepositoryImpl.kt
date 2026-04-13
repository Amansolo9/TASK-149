package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.RoleAssignmentDao
import com.fieldtripops.data.dao.UserDao
import com.fieldtripops.data.entity.RoleAssignmentEntity
import com.fieldtripops.data.entity.UserEntity
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.RoleAssignment
import com.fieldtripops.domain.model.User
import com.fieldtripops.domain.repository.UserRepository
import java.time.Instant

class UserRepositoryImpl(
    private val userDao: UserDao,
    private val roleAssignmentDao: RoleAssignmentDao
) : UserRepository {

    override suspend fun findById(id: String): User? {
        val entity = userDao.findById(id) ?: return null
        return entity.toDomain(roleAssignmentDao.getByUserId(id))
    }

    override suspend fun findByUsername(username: String): User? {
        val entity = userDao.findByUsername(username) ?: return null
        return entity.toDomain(roleAssignmentDao.getByUserId(entity.id))
    }

    override suspend fun save(user: User) {
        userDao.insert(user.toEntity())
    }

    override suspend fun getAll(): List<User> {
        return userDao.getAll().map { entity ->
            entity.toDomain(roleAssignmentDao.getByUserId(entity.id))
        }
    }

    override suspend fun assignRole(assignment: RoleAssignment) {
        roleAssignmentDao.insert(assignment.toEntity())
    }

    override suspend fun getRoles(userId: String): List<Role> {
        return roleAssignmentDao.getByUserId(userId).map { Role.valueOf(it.role) }
    }

    private fun UserEntity.toDomain(roles: List<RoleAssignmentEntity>): User = User(
        id = id,
        username = username,
        displayName = displayName,
        roles = roles.map { Role.valueOf(it.role) },
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun User.toEntity(): UserEntity = UserEntity(
        id = id,
        username = username,
        displayName = displayName,
        isActive = isActive,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    private fun RoleAssignment.toEntity(): RoleAssignmentEntity = RoleAssignmentEntity(
        id = id,
        userId = userId,
        role = role.name,
        assignedAt = assignedAt.toEpochMilli(),
        assignedBy = assignedBy
    )
}
