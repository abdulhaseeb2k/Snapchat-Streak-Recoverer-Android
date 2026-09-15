package com.snapstreakrecoverer.ssr.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SyncManager(
    private val dao: RecoveryDao,
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeUserId: String? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private var onRemoteThemeChanged: ((String) -> Unit)? = null

    fun setRemoteThemeListener(listener: (String) -> Unit) {
        onRemoteThemeChanged = listener
    }

    fun startSync(userId: String) {
        if (activeUserId == userId) return
        stopSync()
        activeUserId = userId

        val db = firestore ?: return

        scope.launch {
            try {
                // Initial upload / merge of existing local records to Firestore
                uploadLocalProfiles(userId, db)
                // Attach real-time snapshot listeners
                attachProfilesListener(userId, db)
                attachSettingsListener(userId, db)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSync() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
        activeUserId = null
    }

    private suspend fun uploadLocalProfiles(userId: String, db: FirebaseFirestore) {
        val localProfiles = dao.getAllProfilesOnce()
        for (profile in localProfiles) {
            val docRef = db.collection("users").document(userId)
                .collection("profiles").document(profile.syncId)

            val snapshot = runCatching { docRef.get().await() }.getOrNull()
            if (snapshot == null || !snapshot.exists()) {
                docRef.set(profile.toFirestoreMap())
            }

            val friends = dao.getFriendsForProfileOnce(profile.id)
            for (friend in friends) {
                val friendRef = docRef.collection("friends").document(friend.syncId)
                val friendSnapshot = runCatching { friendRef.get().await() }.getOrNull()
                if (friendSnapshot == null || !friendSnapshot.exists()) {
                    val updatedFriend = if (friend.profileSyncId.isEmpty()) {
                        friend.copy(profileSyncId = profile.syncId)
                    } else friend
                    friendRef.set(updatedFriend.toFirestoreMap())
                }
            }
        }
    }

    private fun attachProfilesListener(userId: String, db: FirebaseFirestore) {
        val profilesRef = db.collection("users").document(userId).collection("profiles")
        val registration = profilesRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val remoteProfile = mapToFirestoreProfile(data)
                    val existing = dao.getProfileBySyncId(remoteProfile.syncId)

                    if (remoteProfile.isDeleted) {
                        if (existing != null) {
                            dao.deleteProfile(existing)
                        }
                    } else if (existing == null || remoteProfile.updatedAt > existing.updatedAt) {
                        val profileToSave = Profile(
                            id = existing?.id ?: 0,
                            syncId = remoteProfile.syncId,
                            profileName = remoteProfile.profileName,
                            snapchatUsername = remoteProfile.snapchatUsername,
                            email = remoteProfile.email,
                            mobileNumber = remoteProfile.mobileNumber,
                            device = remoteProfile.device,
                            refreshDelay = remoteProfile.refreshDelay,
                            updatedAt = remoteProfile.updatedAt,
                            isDeleted = false
                        )
                        val insertedId = dao.insertProfile(profileToSave).toInt()
                        val effectiveId = if (existing != null) existing.id else insertedId
                        attachFriendsListener(userId, remoteProfile.syncId, effectiveId, db)
                    } else {
                        attachFriendsListener(userId, remoteProfile.syncId, existing.id, db)
                    }
                }
            }
        }
        listeners.add(registration)
    }

    private fun attachFriendsListener(
        userId: String,
        profileSyncId: String,
        localProfileId: Int,
        db: FirebaseFirestore
    ) {
        val friendsRef = db.collection("users").document(userId)
            .collection("profiles").document(profileSyncId)
            .collection("friends")

        val registration = friendsRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val remoteFriend = mapToFirestoreFriend(data)
                    val existing = dao.getFriendBySyncId(remoteFriend.syncId)

                    if (remoteFriend.isDeleted) {
                        if (existing != null) {
                            dao.deleteFriend(existing)
                        }
                    } else if (existing == null || remoteFriend.updatedAt > existing.updatedAt) {
                        val friendToSave = Friend(
                            id = existing?.id ?: 0,
                            syncId = remoteFriend.syncId,
                            profileId = localProfileId,
                            profileSyncId = profileSyncId,
                            username = remoteFriend.username,
                            displayName = remoteFriend.displayName,
                            isSelected = remoteFriend.isSelected,
                            updatedAt = remoteFriend.updatedAt,
                            isDeleted = false
                        )
                        dao.insertFriend(friendToSave)
                    }
                }
            }
        }
        listeners.add(registration)
    }

    private fun attachSettingsListener(userId: String, db: FirebaseFirestore) {
        val settingsRef = db.collection("users").document(userId)
            .collection("settings").document("preferences")

        val registration = settingsRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val theme = snapshot.getString("theme")
            if (!theme.isNullOrBlank()) {
                onRemoteThemeChanged?.invoke(theme)
            }
        }
        listeners.add(registration)
    }

    fun syncTheme(themeName: String) {
        val userId = activeUserId ?: return
        val db = firestore ?: return
        scope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("settings").document("preferences")
                    .set(
                        mapOf(
                            "theme" to themeName,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pushProfile(profile: Profile) {
        val userId = activeUserId ?: return
        val db = firestore ?: return
        scope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("profiles").document(profile.syncId)
                    .set(profile.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pushFriend(friend: Friend) {
        val userId = activeUserId ?: return
        if (friend.profileSyncId.isEmpty()) return
        val db = firestore ?: return
        scope.launch {
            try {
                db.collection("users").document(userId)
                    .collection("profiles").document(friend.profileSyncId)
                    .collection("friends").document(friend.syncId)
                    .set(friend.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteProfile(profile: Profile) {
        val userId = activeUserId ?: return
        val db = firestore ?: return
        scope.launch {
            try {
                val softDeleted = profile.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
                db.collection("users").document(userId)
                    .collection("profiles").document(profile.syncId)
                    .set(softDeleted.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteFriend(friend: Friend) {
        val userId = activeUserId ?: return
        if (friend.profileSyncId.isEmpty()) return
        val db = firestore ?: return
        scope.launch {
            try {
                val softDeleted = friend.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
                db.collection("users").document(userId)
                    .collection("profiles").document(friend.profileSyncId)
                    .collection("friends").document(friend.syncId)
                    .set(softDeleted.toFirestoreMap())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
