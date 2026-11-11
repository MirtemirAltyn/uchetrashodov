package com.moifinansy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.moifinansy.core.ui.theme.MoiFinansyTheme
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.settings.ThemeMode
import com.moifinansy.ui.AppState
import com.moifinansy.ui.MainViewModel
import com.moifinansy.ui.lock.LockManager
import com.moifinansy.ui.lock.LockScreen
import com.moifinansy.ui.navigation.MoiFinansyNavHost
import com.moifinansy.ui.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FragmentActivity — для системного окна биометрии. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var settings: SettingsRepository

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            lifecycleScope.launch { lockManager.onForeground() }
        }

        override fun onStop(owner: LifecycleOwner) = lockManager.onBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        // Запрет скриншотов и показа содержимого в «недавних» — отдельная настройка (по умолчанию выключена).
        lifecycleScope.launch {
            settings.blockScreenshots.collect { block ->
                if (block) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent { AppRoot() }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }
}

/** Android 13+: разрешение на уведомления (сроки долгов, бюджеты, регулярные платежи). */
@Composable
private fun NotificationPermissionRequest() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MoiFinansyTheme(darkTheme = dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (state) {
                AppState.LOADING -> Unit
                AppState.ONBOARDING -> OnboardingScreen()
                AppState.READY -> {
                    NotificationPermissionRequest()
                    MoiFinansyNavHost()
                    if (locked) LockScreen()
                }
            }
        }
    }
}
