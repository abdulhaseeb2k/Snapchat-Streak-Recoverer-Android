package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.snapstreakrecoverer.ssr.sync.SyncManager

class FriendViewModel(
    private val dao: RecoveryDao,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    private val _selectedProfileId = MutableStateFlow<Int?>(null)
    val selectedProfileId = _selectedProfileId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val friends: StateFlow<List<Friend>> = combine(
        _selectedProfileId,
        _searchQuery
    ) { profileId, query ->
        profileId to query
    }.flatMapLatest { (profileId, query) ->
        if (profileId == null) flowOf(emptyList())
        else dao.getFriendsForProfile(profileId).map { list ->
            list.filter {
                it.username.contains(query, ignoreCase = true) ||
                        it.displayName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedProfile(id: Int?) {
        _selectedProfileId.value = id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFriendSelection(friend: Friend) {
        viewModelScope.launch {
            val updated = friend.copy(isSelected = !friend.isSelected, updatedAt = System.currentTimeMillis())
            dao.updateFriend(updated)
            syncManager?.pushFriend(updated)
        }
    }

    fun selectAll(selected: Boolean) {
        val profileId = _selectedProfileId.value ?: return
        viewModelScope.launch {
            dao.updateAllFriendsSelection(profileId, selected)
            // Push updated friends
            val currentFriends = dao.getFriendsForProfileOnce(profileId)
            currentFriends.forEach { syncManager?.pushFriend(it) }
        }
    }

    fun addFriend(friend: Friend) {
        viewModelScope.launch {
            val friendToSave = if (friend.profileSyncId.isEmpty()) {
                val profile = dao.getAllProfilesOnce().find { it.id == friend.profileId }
                friend.copy(profileSyncId = profile?.syncId ?: "")
            } else friend
            dao.insertFriend(friendToSave)
            syncManager?.pushFriend(friendToSave)
        }
    }

    fun updateFriend(friend: Friend) {
        viewModelScope.launch {
            val updated = friend.copy(updatedAt = System.currentTimeMillis())
            dao.updateFriend(updated)
            syncManager?.pushFriend(updated)
        }
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            syncManager?.deleteFriend(friend)
            dao.deleteFriend(friend)
        }
    }
}
