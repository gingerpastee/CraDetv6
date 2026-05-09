package com.example.cradetv6

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cradetv6.data.AppDatabase
import com.example.cradetv6.data.EmergencyContactEntity
import com.example.cradetv6.data.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val userDao = AppDatabase.getDatabase(application).userDao()

    val userProfile: StateFlow<UserProfile?> = userDao.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contacts: StateFlow<List<EmergencyContactEntity>> = userDao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProfile(email: String, password: String, bloodType: String, abnormalities: String) {
        viewModelScope.launch {
            userDao.insertUserProfile(UserProfile(email, password, bloodType, abnormalities))
        }
    }

    fun addContact(name: String, phone: String) {
        viewModelScope.launch {
            userDao.insertContact(EmergencyContactEntity(name = name, phone = phone))
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            userDao.deleteContact(contact)
        }
    }

    fun logout() {
        viewModelScope.launch {
            userDao.deleteUserProfile()
            userDao.deleteAllContacts()
        }
    }
}
