package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.ZoyaState
import com.example.service.ZoyaVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZoyaViewModel : ViewModel() {

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _hasContactsPermission = MutableStateFlow(false)
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    private val _hasPhonePermission = MutableStateFlow(false)
    val hasPhonePermission: StateFlow<Boolean> = _hasPhonePermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _serviceInstance = MutableStateFlow<ZoyaVoiceService?>(null)
    val serviceInstance: StateFlow<ZoyaVoiceService?> = _serviceInstance.asStateFlow()

    fun updatePermissions(context: Context) {
        _hasMicPermission.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        _hasContactsPermission.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        _hasPhonePermission.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            _hasNotificationPermission.value = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            _hasNotificationPermission.value = true
        }
    }

    fun bindService(service: ZoyaVoiceService) {
        _serviceInstance.value = service
    }

    fun unbindService() {
        _serviceInstance.value = null
    }

    fun toggleOrbSession() {
        val service = _serviceInstance.value ?: return
        if (service.sessionState.value == ZoyaState.ERROR || service.sessionState.value == ZoyaState.IDLE) {
            service.restartSession()
        } else {
            service.liveSession.closeSession()
        }
    }
}
