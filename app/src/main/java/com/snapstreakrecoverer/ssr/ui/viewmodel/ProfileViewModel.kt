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
import org.json.JSONObject

class ProfileViewModel(private val dao: RecoveryDao) : ViewModel() {

    val allProfiles: StateFlow<List<Profile>> = dao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertProfile(profile: Profile) {
        viewModelScope.launch {
            dao.insertProfile(profile)
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            dao.updateProfile(profile)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            dao.deleteProfile(profile)
        }
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
                    
                    val profile = Profile(
                        profileName = profileName,
                        snapchatUsername = settings.optString("username", ""),
                        email = settings.optString("email", ""),
                        mobileNumber = settings.optString("mobile_number", ""),
                        device = settings.optString("device", ""),
                        refreshDelay = settings.optDouble("refresh_delay", 1.0)
                    )
                    
                    val profileId = dao.insertProfile(profile).toInt()
                    
                    val friendsArray = profileData.getJSONArray("friends")
                    for (i in 0 until friendsArray.length()) {
                        val friendObj = friendsArray.getJSONObject(i)
                        val friend = Friend(
                            profileId = profileId,
                            username = friendObj.getString("username"),
                            displayName = friendObj.optString("name", ""),
                            isSelected = friendObj.optBoolean("selected", true)
                        )
                        dao.insertFriend(friend)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
