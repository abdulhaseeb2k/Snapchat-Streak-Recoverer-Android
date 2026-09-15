package com.snapstreakrecoverer.ssr.sync

import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile

data class FirestoreProfile(
    val syncId: String = "",
    val profileName: String = "",
    val snapchatUsername: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val device: String = "",
    val refreshDelay: Double = 1.0,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

data class FirestoreFriend(
    val syncId: String = "",
    val profileSyncId: String = "",
    val username: String = "",
    val displayName: String = "",
    val isSelected: Boolean = true,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)

data class FirestoreSettings(
    val theme: String = "SYSTEM",
    val updatedAt: Long = 0L
)

fun Profile.toFirestoreMap(): Map<String, Any?> = mapOf(
    "syncId" to syncId,
    "profileName" to profileName,
    "snapchatUsername" to snapchatUsername,
    "email" to email,
    "mobileNumber" to mobileNumber,
    "device" to device,
    "refreshDelay" to refreshDelay,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted
)

fun Friend.toFirestoreMap(): Map<String, Any?> = mapOf(
    "syncId" to syncId,
    "profileSyncId" to profileSyncId,
    "username" to username,
    "displayName" to displayName,
    "isSelected" to isSelected,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted
)

fun mapToFirestoreProfile(map: Map<String, Any?>): FirestoreProfile {
    return FirestoreProfile(
        syncId = map["syncId"] as? String ?: "",
        profileName = map["profileName"] as? String ?: "",
        snapchatUsername = map["snapchatUsername"] as? String ?: "",
        email = map["email"] as? String ?: "",
        mobileNumber = map["mobileNumber"] as? String ?: "",
        device = map["device"] as? String ?: "",
        refreshDelay = (map["refreshDelay"] as? Number)?.toDouble() ?: 1.0,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        isDeleted = map["isDeleted"] as? Boolean ?: false
    )
}

fun mapToFirestoreFriend(map: Map<String, Any?>): FirestoreFriend {
    return FirestoreFriend(
        syncId = map["syncId"] as? String ?: "",
        profileSyncId = map["profileSyncId"] as? String ?: "",
        username = map["username"] as? String ?: "",
        displayName = map["displayName"] as? String ?: "",
        isSelected = map["isSelected"] as? Boolean ?: true,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        isDeleted = map["isDeleted"] as? Boolean ?: false
    )
}
