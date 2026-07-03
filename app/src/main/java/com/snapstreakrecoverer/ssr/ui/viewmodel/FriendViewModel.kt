package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FriendViewModel(private val dao: RecoveryDao) : ViewModel() {

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
            dao.updateFriend(friend.copy(isSelected = !friend.isSelected))
        }
    }

    fun selectAll(selected: Boolean) {
        val profileId = _selectedProfileId.value ?: return
        viewModelScope.launch {
            dao.updateAllFriendsSelection(profileId, selected)
        }
    }

    fun addFriend(friend: Friend) {
        viewModelScope.launch {
            dao.insertFriend(friend)
        }
    }

    fun updateFriend(friend: Friend) {
        viewModelScope.launch {
            dao.updateFriend(friend)
        }
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            dao.deleteFriend(friend)
        }
    }
}
