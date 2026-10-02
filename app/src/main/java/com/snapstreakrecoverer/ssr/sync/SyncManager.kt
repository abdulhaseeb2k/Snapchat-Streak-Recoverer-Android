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

    suspend fun deduplicateLocalProfiles(userId: String? = activeUserId, db: FirebaseFirestore? = firestore) {
        val profiles = dao.getAllProfilesOnce()
        val seen = mutableListOf<Profile>()
        for (p in profiles) {
            val match = seen.find { isSameProfile(it.syncId, it.snapchatUsername, it.profileName, p.syncId, p.snapchatUsername, p.profileName) }
            if (match != null) {
                // p is a duplicate of match! Merge friends into match
                val pFriends = dao.getFriendsForProfileOnce(p.id)
                val matchFriends = dao.getFriendsForProfileOnce(match.id)
                for (f in pFriends) {
                    val existingF = matchFriends.find { isSameFriend(it.syncId, it.username, f.syncId, f.username) }
                    if (existingF == null) {
                        dao.insertFriend(f.copy(id = 0, profileId = match.id, profileSyncId = match.syncId))
                    }
                    dao.deleteFriend(f)
                }
                // Delete duplicate profile locally
                dao.deleteProfile(p)
                // If synced, soft-delete duplicate in Firestore so it won't be downloaded again
                if (userId != null && db != null && p.syncId != match.syncId) {
                    runCatching {
                        val softDeleted = p.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
                        db.collection("users").document(userId)
                            .collection("profiles").document(p.syncId)
                            .set(softDeleted.toFirestoreMap())
                    }
                }
            } else {
                seen.add(p)
            }
        }
    }

    private suspend fun uploadLocalProfiles(userId: String, db: FirebaseFirestore) {
        deduplicateLocalProfiles(userId, db)

        val remoteDocs = runCatching {
            db.collection("users").document(userId).collection("profiles").get().await()
        }.getOrNull()?.documents?.mapNotNull { it.data?.let { d -> mapToFirestoreProfile(d) } } ?: emptyList()

        val activeRemoteProfiles = remoteDocs.filter { !it.isDeleted }
        val localProfiles = dao.getAllProfilesOnce()

        for (profile in localProfiles) {
            val matchingRemote = activeRemoteProfiles.find { rp ->
                isSameProfile(
                    rp.syncId, rp.snapchatUsername, rp.profileName,
                    profile.syncId, profile.snapchatUsername, profile.profileName
                )
            }

            val effectiveSyncId = if (matchingRemote != null) {
                if (matchingRemote.syncId != profile.syncId) {
                    val updatedProfile = profile.copy(syncId = matchingRemote.syncId)
                    dao.updateProfile(updatedProfile)
                    dao.reassignFriendsToProfile(profile.id, profile.id, matchingRemote.syncId)
                    matchingRemote.syncId
                } else {
                    profile.syncId
                }
            } else {
                profile.syncId
            }

            val docRef = db.collection("users").document(userId)
                .collection("profiles").document(effectiveSyncId)

            if (matchingRemote == null || profile.updatedAt > matchingRemote.updatedAt) {
                docRef.set(profile.copy(syncId = effectiveSyncId).toFirestoreMap())
            }

            val friends = dao.getFriendsForProfileOnce(profile.id)
            for (friend in friends) {
                val friendRef = docRef.collection("friends").document(friend.syncId)
                val friendSnapshot = runCatching { friendRef.get().await() }.getOrNull()
                if (friendSnapshot == null || !friendSnapshot.exists()) {
                    val updatedFriend = if (friend.profileSyncId != effectiveSyncId) {
                        friend.copy(profileSyncId = effectiveSyncId)
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

                    val existingBySync = dao.getProfileBySyncId(remoteProfile.syncId)
                    val existing = existingBySync ?: run {
                        val byUser = if (remoteProfile.snapchatUsername.isNotBlank()) {
                            dao.getProfileByUsername(remoteProfile.snapchatUsername)
                        } else null
                        byUser ?: if (remoteProfile.profileName.isNotBlank()) {
                            dao.getProfileByName(remoteProfile.profileName)
                        } else null
                    }

                    if (remoteProfile.isDeleted) {
                        if (existing != null && (existing.syncId == remoteProfile.syncId || existingBySync != null)) {
                            dao.deleteProfile(existing)
                        }
                    } else if (existing == null) {
                        val profileToSave = Profile(
                            id = 0,
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
                        attachFriendsListener(userId, remoteProfile.syncId, insertedId, db)
                    } else {
                        val profileToSave = if (remoteProfile.updatedAt > existing.updatedAt) {
                            existing.copy(
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
                        } else {
                            existing.copy(syncId = remoteProfile.syncId)
                        }
                        dao.updateProfile(profileToSave)
                        dao.reassignFriendsToProfile(existing.id, existing.id, remoteProfile.syncId)
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
                    val existingBySync = dao.getFriendBySyncId(remoteFriend.syncId)
                    val existing = existingBySync ?: dao.getFriendByUsername(localProfileId, remoteFriend.username)

                    if (remoteFriend.isDeleted) {
                        if (existing != null) {
                            dao.deleteFriend(existing)
                        }
                    } else if (existing == null) {
                        val friendToSave = Friend(
                            id = 0,
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
                    } else {
                        val friendToSave = if (remoteFriend.updatedAt > existing.updatedAt) {
                            existing.copy(
                                syncId = remoteFriend.syncId,
                                profileSyncId = profileSyncId,
                                username = remoteFriend.username,
                                displayName = remoteFriend.displayName,
                                isSelected = remoteFriend.isSelected,
                                updatedAt = remoteFriend.updatedAt,
                                isDeleted = false
                            )
                        } else {
                            existing.copy(syncId = remoteFriend.syncId, profileSyncId = profileSyncId)
                        }
                        dao.updateFriend(friendToSave)
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
