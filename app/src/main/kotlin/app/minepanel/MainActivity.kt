package app.minepanel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.ServiceLocator
import app.minepanel.data.prefs.SettingsSnapshot
import app.minepanel.service.RecordingService
import app.minepanel.ui.common.StorageInvalidScreen
import app.minepanel.ui.onboarding.OnboardingScreen
import app.minepanel.ui.panel.PanelScreen
import app.minepanel.ui.settings.SettingsViewModel
import app.minepanel.ui.theme.MinePanelTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果不阻断流程 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            MinePanelTheme {
                val settings by ServiceLocator.settings()
                    .settings
                    .collectAsStateWithLifecycle(initialValue = SettingsSnapshot())
                val settingsViewModel: SettingsViewModel = viewModel()
                val storageInvalid = ServiceLocator.isStorageInvalid()

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        storageInvalid -> StorageInvalidScreen(
                            onWipe = { settingsViewModel.wipeAll(onDone = ::restartToOnboarding) },
                            lastBackupEpochMs = settings.lastExportEpochMs
                        )
                        settings.onboardingCompleted -> {
                            LaunchedEffect(Unit) { startRecordingService() }
                            PanelScreen(onWiped = ::restartToOnboarding)
                        }
                        else -> OnboardingScreen(onFinished = { /* 设置流自动刷新并切换主页 */ })
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun restartToOnboarding() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
