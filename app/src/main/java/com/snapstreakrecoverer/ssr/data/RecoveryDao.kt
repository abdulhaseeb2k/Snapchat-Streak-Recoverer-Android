package com.snapstreakrecoverer.ssr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryDao {
    // Profile operations
    @Query("SELECT * FROM Profile")
    fun getAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM Profile")
    suspend fun getAllProfilesOnce(): List<Profile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    // Friend operations
    @Query("SELECT * FROM Friend WHERE profileId = :profileId")
    fun getFriendsForProfile(profileId: Int): Flow<List<Friend>>

    @Query("SELECT * FROM Friend WHERE profileId = :profileId")
    suspend fun getFriendsForProfileOnce(profileId: Int): List<Friend>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Query("UPDATE Friend SET isSelected = :selected WHERE profileId = :profileId")
    suspend fun updateAllFriendsSelection(profileId: Int, selected: Boolean)
}
