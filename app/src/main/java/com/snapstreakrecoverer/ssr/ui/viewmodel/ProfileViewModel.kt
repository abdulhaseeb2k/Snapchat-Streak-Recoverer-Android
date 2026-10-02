package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

import com.snapstreakrecoverer.ssr.sync.SyncManager

class ProfileViewModel(
    private val dao: RecoveryDao,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    val allProfiles: StateFlow<List<Profile>> = dao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            syncManager?.deduplicateLocalProfiles()
        }
    }

    fun insertProfile(profile: Profile) {
        viewModelScope.launch {
            val existing = (if (profile.snapchatUsername.isNotBlank()) {
                dao.getProfileByUsername(profile.snapchatUsername)
            } else null) ?: (if (profile.profileName.isNotBlank()) {
                dao.getProfileByName(profile.profileName)
            } else null)

            if (existing != null) {
                val updated = existing.copy(
                    profileName = profile.profileName,
                    snapchatUsername = profile.snapchatUsername,
                    email = profile.email,
                    mobileNumber = profile.mobileNumber,
                    device = profile.device,
                    refreshDelay = profile.refreshDelay,
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateProfile(updated)
                syncManager?.pushProfile(updated)
            } else {
                val id = dao.insertProfile(profile)
                val inserted = if (profile.id == 0) profile.copy(id = id.toInt()) else profile
                syncManager?.pushProfile(inserted)
            }
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            val updated = profile.copy(updatedAt = System.currentTimeMillis())
            dao.updateProfile(updated)
            syncManager?.pushProfile(updated)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            syncManager?.deleteProfile(profile)
            dao.deleteProfile(profile)
        }
    }

    /**
     * Builds a JSON export of every profile and its friends, in the same shape
     * that [importProfilesFromJson] consumes (keyed by profile name).
     */
    suspend fun buildExportJson(): String {
        val root = JSONObject()
        for (profile in dao.getAllProfilesOnce()) {
            val settings = JSONObject()
                .put("username", profile.snapchatUsername)
                .put("email", profile.email)
                .put("mobile_number", profile.mobileNumber)
                .put("device", profile.device)
                .put("refresh_delay", profile.refreshDelay)

            val friendsArray = JSONArray()
            for (friend in dao.getFriendsForProfileOnce(profile.id)) {
                friendsArray.put(
                    JSONObject()
                        .put("username", friend.username)
                        .put("name", friend.displayName)
                        .put("selected", friend.isSelected)
                )
            }

            root.put(
                profile.profileName,
                JSONObject().put("settings", settings).put("friends", friendsArray)
            )
        }
        return root.toString(2)
    }

    fun importProfilesFromJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val profileName = keys.next()
                    val profileData = json.getJSONObject(profileName)
                    val settings = profileData.getJSONObject("settings")
                    
                    val username = settings.optString("username", "")

                    val existing = (if (username.isNotBlank()) {
                        dao.getProfileByUsername(username)
                    } else null) ?: (if (profileName.isNotBlank()) {
                        dao.getProfileByName(profileName)
                    } else null)

                    val savedProfile = if (existing != null) {
                        val updated = existing.copy(
                            profileName = profileName,
                            snapchatUsername = username,
                            email = settings.optString("email", existing.email),
                            mobileNumber = settings.optString("mobile_number", existing.mobileNumber),
                            device = settings.optString("device", existing.device),
                            refreshDelay = settings.optDouble("refresh_delay", existing.refreshDelay),
                            updatedAt = System.currentTimeMillis()
                        )
                        dao.updateProfile(updated)
                        syncManager?.pushProfile(updated)
                        updated
                    } else {
                        val profile = Profile(
                            profileName = profileName,
                            snapchatUsername = username,
                            email = settings.optString("email", ""),
                            mobileNumber = settings.optString("mobile_number", ""),
                            device = settings.optString("device", ""),
                            refreshDelay = settings.optDouble("refresh_delay", 1.0)
                        )
                        val profileId = dao.insertProfile(profile).toInt()
                        val saved = profile.copy(id = profileId)
                        syncManager?.pushProfile(saved)
                        saved
                    }
                    val profileId = savedProfile.id
                    
                    val friendsArray = profileData.getJSONArray("friends")
                    for (i in 0 until friendsArray.length()) {
                        val friendObj = friendsArray.getJSONObject(i)
                        val fUsername = friendObj.getString("username")
                        val existingFriend = dao.getFriendByUsername(profileId, fUsername)
                        if (existingFriend == null) {
                            val friend = Friend(
                                profileId = profileId,
                                profileSyncId = savedProfile.syncId,
                                username = fUsername,
                                displayName = friendObj.optString("name", ""),
                                isSelected = friendObj.optBoolean("selected", true)
                            )
                            dao.insertFriend(friend)
                            syncManager?.pushFriend(friend)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
