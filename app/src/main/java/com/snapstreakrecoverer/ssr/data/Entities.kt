package com.snapstreakrecoverer.ssr.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

import androidx.room.Index
import java.util.UUID

@Entity(
    indices = [
        Index(value = ["syncId"], unique = true)
    ]
)
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val profileName: String,
    val snapchatUsername: String,
    val email: String,
    val mobileNumber: String,
    val device: String,
    val refreshDelay: Double = 1.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["profileId"]),
        Index(value = ["profileSyncId"])
    ]
)
data class Friend(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val profileId: Int,
    val profileSyncId: String = "",
    val username: String,
    val displayName: String,
    val isSelected: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

data class ExportedProfile(
    val settings: ProfileSettings,
    val friends: List<ExportedFriend>
)

data class ProfileSettings(
    val username: String = "",
    val email: String = "",
    val mobile_number: String = "",
    val device: String = "",
    val refresh_delay: Double = 1.0
)

data class ExportedFriend(
    val username: String,
    val name: String = "",
    val selected: Boolean = true
)
