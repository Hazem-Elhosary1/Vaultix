package com.vaultix.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaultix.app.ui.navigation.VaultixNavGraph
import androidx.compose.runtime.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.vaultix.app.ui.theme.VaultixTheme
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.AppConfigViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val appConfigViewModel: AppConfigViewModel by viewModels()
    private var privacyOverlay: View? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // App returned to foreground
            authViewModel.checkGracePeriod()
        }

        override fun onStop(owner: LifecycleOwner) {
            // If returning from or transitioning to a system activity (like document scanner), bypass automatic lock
            if (authViewModel.isSystemActivityActive()) {
                return
            }

            // App went to background - lock but allow grace period
            authViewModel.lock(isManual = false)

            // Trigger weak item notification if any weak items were added/updated this session
            val lang = appConfigViewModel.configState.value.language
            com.vaultix.app.security.SecurityNotificationManager.triggerWeakItemNotification(this@MainActivity, lang)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                com.vaultix.app.debug.DebugEventBus.log(
                    category  = com.vaultix.app.debug.DebugCategory.SYSTEM,
                    eventType = "NOTIFICATION_PERMISSION_RESULT",
                    details   = "granted=$isGranted",
                    source    = "MainActivity"
                )
            }

            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Prevent screenshots and screen recording (allowed only in debug builds)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        enableEdgeToEdge()
        window.decorView.setFilterTouchesWhenObscured(true)

        splashScreen.setKeepOnScreenCondition {
            authViewModel.isLoading.value
        }

        // Professional Exit Animation
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = android.view.animation.AlphaAnimation(1f, 0f).apply {
                duration = 500L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            splashScreenView.view.startAnimation(fadeOut)
            
            // Cleanup after animation
            fadeOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    splashScreenView.remove()
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
        }

        // Check for App Shortcut launch
        if (intent?.action == "com.vaultix.app.ACTION_ADD_PASSWORD" || intent?.action == "com.vaultix.app.ACTION_ADD_NOTE") {
            authViewModel.setPendingShortcutAction(intent.action)
        }

        setContent {
            val configState by appConfigViewModel.configState.collectAsStateWithLifecycle()
            
            // Apply language dynamically
            LaunchedEffect(configState.language) {
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(configState.language)
                AppCompatDelegate.setApplicationLocales(appLocale)
                
                // Force configuration update for immediate Compose reaction
                val locale = Locale(configState.language)
                Locale.setDefault(locale)
                val resources = resources
                val configuration = resources.configuration
                configuration.setLocale(locale)
                configuration.setLayoutDirection(locale)
                resources.updateConfiguration(configuration, resources.displayMetrics)
            }

            // Determine layout direction based on language
            val layoutDirection = if (configState.language == "ar") {
                androidx.compose.ui.unit.LayoutDirection.Rtl
            } else {
                androidx.compose.ui.unit.LayoutDirection.Ltr
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
            ) {
                VaultixTheme(
                    themeMode = configState.themeMode,
                    accentColorHex = configState.accentColorHex,
                    fontSizeScale = configState.fontSizeScale
                ) {
                    VaultixNavGraph(authViewModel = authViewModel)
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        authViewModel.updateActivity()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "com.vaultix.app.ACTION_ADD_PASSWORD" || intent.action == "com.vaultix.app.ACTION_ADD_NOTE") {
            authViewModel.setPendingShortcutAction(intent.action)
        }
    }

    override fun onPause() {
        super.onPause()
        // Show privacy overlay to hide content in app switcher
        showPrivacyOverlay()
    }

    override fun onResume() {
        super.onResume()
        // Reset the system activity bypass flag upon returning
        authViewModel.setSystemActivityActive(false)
        // Remove privacy overlay
        hidePrivacyOverlay()
        // Re-apply FLAG_SECURE on resume (allowed only in debug builds)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    /**
     * Shows a branded overlay that covers the app content when switching away.
     */
    private fun showPrivacyOverlay() {
        if (privacyOverlay != null) return
        
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))
            
            val label = TextView(this@MainActivity).apply {
                text = "🔒 VAULTIX"
                setTextColor(android.graphics.Color.parseColor("#F97316"))
                textSize = 24f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
        
        val rootView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        privacyOverlay = overlay
    }

    /**
     * Removes the privacy overlay when returning to the app.
     */
    private fun hidePrivacyOverlay() {
        privacyOverlay?.let {
            val rootView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
            rootView.removeView(it)
        }
        privacyOverlay = null
    }
}
