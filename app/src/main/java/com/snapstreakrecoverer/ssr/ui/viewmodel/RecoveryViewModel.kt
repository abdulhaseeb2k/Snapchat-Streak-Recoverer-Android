package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Loads the data required to drive a recovery run for a single profile.
 *
 * The previous implementation reused a fresh [FriendViewModel] whose selected
 * profile was never set, so the friend list was always empty and recovery
 * could never start. This loads the profile and its *selected* friends directly
 * from the DAO, keyed off the profile id passed via [load].
 */
class RecoveryViewModel(private val dao: RecoveryDao) : ViewModel() {

    private val _profileId = MutableStateFlow<Int?>(null)

    fun load(profileId: Int) {
        _profileId.value = profileId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: StateFlow<Profile?> = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else dao.getAllProfiles().map { profiles -> profiles.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedFriends: StateFlow<List<Friend>> = _profileId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else dao.getFriendsForProfile(id).map { list -> list.filter { it.isSelected } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
