package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.service.ZoyaVoiceService
import com.example.ui.ZoyaHomeScreen
import com.example.ui.ZoyaViewModel
import com.example.ui.theme.ZoyaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ZoyaViewModel by viewModels()

    private var zoyaService: ZoyaVoiceService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ZoyaVoiceService.LocalBinder
            zoyaService = binder.getService()
            zoyaService?.let { viewModel.bindService(it) }
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            zoyaService = null
            viewModel.unbindService()
            isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.updatePermissions(this)
        startAndBindZoyaService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.updatePermissions(this)
        requestAllPermissions()

        setContent {
            ZoyaTheme {
                ZoyaHomeScreen(
                    viewModel = viewModel,
                    onRequestPermissions = {
                        requestAllPermissions()
                    }
                )
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startAndBindZoyaService() {
        val serviceIntent = Intent(this, ZoyaVoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        viewModel.updatePermissions(this)
        if (viewModel.hasMicPermission.value) {
            startAndBindZoyaService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
