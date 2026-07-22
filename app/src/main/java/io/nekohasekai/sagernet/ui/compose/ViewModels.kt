package io.nekohasekai.sagernet.ui.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConfigurationViewModel(app: Application) : AndroidViewModel(app) {

    private val _profiles = MutableStateFlow<List<ProxyEntity>>(emptyList())
    val profiles: StateFlow<List<ProxyEntity>> = _profiles.asStateFlow()

    private val _selectedProfileId = MutableStateFlow(DataStore.selectedProxy)
    val selectedProfileId: StateFlow<Long> = _selectedProfileId.asStateFlow()

    private val _currentGroup = MutableStateFlow<ProxyGroup?>(null)
    val currentGroup: StateFlow<ProxyGroup?> = _currentGroup.asStateFlow()

    fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val groupId = DataStore.currentGroupId()
            val group = SagerDatabase.groupDao.getById(groupId)
            val list = SagerDatabase.proxyDao.getByGroup(groupId)
            _currentGroup.value = group
            _profiles.value = list
            _selectedProfileId.value = DataStore.selectedProxy
        }
    }

    fun selectProfile(profileId: Long) {
        DataStore.selectedProxy = profileId
        _selectedProfileId.value = profileId
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val groupId = DataStore.currentGroupId()
            ProfileManager.deleteProfile(groupId, profileId)
            loadProfiles()
        }
    }
}

class GroupViewModel(app: Application) : AndroidViewModel(app) {

    private val _groups = MutableStateFlow<List<ProxyGroup>>(emptyList())
    val groups: StateFlow<List<ProxyGroup>> = _groups.asStateFlow()

    fun loadGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            _groups.value = SagerDatabase.groupDao.allGroups()
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            io.nekohasekai.sagernet.database.GroupManager.deleteGroup(groupId)
            loadGroups()
        }
    }
}

class RouteViewModel(app: Application) : AndroidViewModel(app) {

    private val _rules = MutableStateFlow<List<RuleEntity>>(emptyList())
    val rules: StateFlow<List<RuleEntity>> = _rules.asStateFlow()

    fun loadRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _rules.value = SagerDatabase.rulesDao.allRules()
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            SagerDatabase.rulesDao.deleteById(ruleId)
            loadRules()
        }
    }
}
