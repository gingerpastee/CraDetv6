package com.example.cradetv6.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(user: UserProfile)

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getUserProfileList(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts")
    suspend fun getAllContactsList(): List<EmergencyContactEntity>

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)
    
    @Query("DELETE FROM user_profile")
    suspend fun deleteUserProfile()
    
    @Query("DELETE FROM emergency_contacts")
    suspend fun deleteAllContacts()
}
