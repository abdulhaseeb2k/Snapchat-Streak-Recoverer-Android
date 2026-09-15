package com.snapstreakrecoverer.ssr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryDao {
    // Profile operations
    @Query("SELECT * FROM Profile WHERE isDeleted = 0")
    fun getAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM Profile WHERE isDeleted = 0")
    suspend fun getAllProfilesOnce(): List<Profile>

    @Query("SELECT * FROM Profile WHERE syncId = :syncId LIMIT 1")
    suspend fun getProfileBySyncId(syncId: String): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Query("UPDATE Profile SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteProfile(id: Int, timestamp: Long = System.currentTimeMillis())

    // Friend operations
    @Query("SELECT * FROM Friend WHERE profileId = :profileId AND isDeleted = 0")
    fun getFriendsForProfile(profileId: Int): Flow<List<Friend>>

    @Query("SELECT * FROM Friend WHERE profileId = :profileId AND isDeleted = 0")
    suspend fun getFriendsForProfileOnce(profileId: Int): List<Friend>

    @Query("SELECT * FROM Friend WHERE syncId = :syncId LIMIT 1")
    suspend fun getFriendBySyncId(syncId: String): Friend?

    @Query("SELECT * FROM Friend WHERE profileSyncId = :profileSyncId AND isDeleted = 0")
    fun getFriendsForProfileSync(profileSyncId: String): Flow<List<Friend>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Query("UPDATE Friend SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteFriend(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE Friend SET isSelected = :selected WHERE profileId = :profileId AND isDeleted = 0")
    suspend fun updateAllFriendsSelection(profileId: Int, selected: Boolean)
}
