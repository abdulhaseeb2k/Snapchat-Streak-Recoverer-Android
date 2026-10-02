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

fun normalizeSnapUsername(username: String): String =
    username.trim().removePrefix("@").lowercase()

fun normalizeProfileName(name: String): String =
    name.trim().lowercase()

fun isSameProfile(
    syncId1: String,
    snapchatUsername1: String,
    profileName1: String,
    syncId2: String,
    snapchatUsername2: String,
    profileName2: String
): Boolean {
    if (syncId1.isNotBlank() && syncId2.isNotBlank() && syncId1 == syncId2) {
        return true
    }
    val u1 = normalizeSnapUsername(snapchatUsername1)
    val u2 = normalizeSnapUsername(snapchatUsername2)
    if (u1.isNotBlank() && u2.isNotBlank() && u1 == u2) {
        return true
    }
    val n1 = normalizeProfileName(profileName1)
    val n2 = normalizeProfileName(profileName2)
    if (n1.isNotBlank() && n2.isNotBlank() && n1 == n2) {
        return true
    }
    return false
}

fun isSameFriend(
    syncId1: String,
    username1: String,
    syncId2: String,
    username2: String
): Boolean {
    if (syncId1.isNotBlank() && syncId2.isNotBlank() && syncId1 == syncId2) {
        return true
    }
    val u1 = normalizeSnapUsername(username1)
    val u2 = normalizeSnapUsername(username2)
    return u1.isNotBlank() && u2.isNotBlank() && u1 == u2
}

