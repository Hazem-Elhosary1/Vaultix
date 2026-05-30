package com.vaultix.app.ui.navigation

import PasswordGeneratorScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.*
import com.vaultix.app.data.model.AuthState
import com.vaultix.app.ui.screens.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.CardViewModel
import com.vaultix.app.ui.viewmodel.FileViewModel
import com.vaultix.app.ui.viewmodel.IdentityViewModel
import com.vaultix.app.ui.viewmodel.SecurityAuditViewModel
import com.vaultix.app.debug.DebugEventBus
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.LiveDebuggerDialog

@Composable
fun VaultixNavGraph(
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val isSetupComplete by authViewModel.isSetupComplete.collectAsStateWithLifecycle()
    val isOnboardingComplete by authViewModel.isOnboardingComplete.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val bypassScreens = listOf(
                Screen.Splash.route,
                Screen.Onboarding.route,
                Screen.Setup.route,
                Screen.Lock.route
            )
            if (currentRoute !in bypassScreens && currentRoute != null) {
                navController.navigate(Screen.Lock.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300)
            )
        }
    ) {
        // Splash
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigate = {
                    val destination = when {
                        !isOnboardingComplete -> Screen.Onboarding.route
                        !isSetupComplete -> Screen.Setup.route
                        else -> Screen.Lock.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Setup.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                authViewModel = authViewModel
            )
        }

        // Setup Flow
        composable(Screen.Setup.route) {
            SetupScreen(
                authViewModel = authViewModel,
                onSetupComplete = {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        // Lock Screen
        composable(Screen.Lock.route) {
            LockScreen(
                authViewModel = authViewModel,
                onAuthenticated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                }
            )
        }

        // Home / Dashboard
        composable(Screen.Home.route) {
            val pendingShortcutAction by authViewModel.pendingShortcutAction.collectAsStateWithLifecycle()

            LaunchedEffect(pendingShortcutAction) {
                pendingShortcutAction?.let { action ->
                    authViewModel.setPendingShortcutAction(null)
                    when (action) {
                        "com.vaultix.app.ACTION_ADD_PASSWORD" -> {
                            navController.navigate(Screen.AddEdit.createRoute("passwords"))
                        }
                        "com.vaultix.app.ACTION_ADD_NOTE" -> {
                            navController.navigate(Screen.AddEdit.createRoute("notes"))
                        }
                    }
                }
            }

            DashboardScreen(
                authViewModel = authViewModel,
                onNavigateToCategory = { type ->
                    if (type == "files") {
                        navController.navigate(Screen.FileVault.route)
                    } else {
                        navController.navigate(Screen.Category.createRoute(type))
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onLocked = {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToScan = {
                    navController.navigate(Screen.CardScan.route)
                },
                onNavigateToFileVault = {
                    navController.navigate(Screen.FileVault.route)
                },
                onNavigateToGenerator = {
                    navController.navigate(Screen.PasswordGenerator.route)
                },
                onNavigateToGlobalSearch = {
                    navController.navigate(Screen.GlobalSearch.route)
                },
                onNavigateToSecurityAudit = {
                    navController.navigate(Screen.SecurityAudit.route)
                },
                onNavigateToDetail = { id, type ->
                    navController.navigate(Screen.Detail.createRoute(id, type))
                },
                onViewPdf = { id, name ->
                    navController.navigate(Screen.PdfViewer.createRoute(id, name))
                },
                onViewImage = { path ->
                    navController.navigate(Screen.ImageViewer.createRoute(path))
                },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium.route)
                }
            )
        }

        // Category Screen
        composable(
            route = Screen.Category.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryType = backStackEntry.arguments?.getString("type") ?: "passwords"
            CategoryScreen(
                categoryType = categoryType,
                authViewModel = authViewModel,
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.Detail.createRoute(id, categoryType))
                },
                onNavigateToAdd = {
                    navController.navigate(Screen.AddEdit.createRoute(categoryType))
                },
                onBack = { navController.popBackStack() },
                onNavigateToScan = {
                    navController.navigate(Screen.CardScan.route)
                },
                onNavigateToFileVault = {
                    navController.navigate(Screen.FileVault.route)
                },
                onNavigateToIdentityEdit = { id ->
                    navController.navigate(Screen.IdentityEdit.createRoute(id))
                }
            )
        }

        // Detail Screen
        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val type = backStackEntry.arguments?.getString("type") ?: "passwords"
            ItemDetailScreen(
                itemId = id,
                categoryType = type,
                authViewModel = authViewModel,
                onEdit = {
                    navController.navigate(Screen.AddEdit.createRoute(type, id))
                },
                onBack = { navController.popBackStack() },
                onViewImage = { path ->
                    navController.navigate(Screen.ImageViewer.createRoute(path))
                }
            )
        }

        // Add/Edit Screen
        composable(
            route = Screen.AddEdit.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("mode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "passwords"
            val id = backStackEntry.arguments?.getString("id")
            val mode = backStackEntry.arguments?.getString("mode")
            AddEditItemScreen(
                categoryType = type,
                itemId = id,
                authViewModel = authViewModel,
                startNfcScanning = type == "cards" && mode == "nfc",
                onNavigateToScan = if (type == "cards") { { navController.navigate(Screen.CardScan.route) } } else { {} },
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Lock.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium.route)
                },
                onNavigateToQRCodeBackup = { masterPassword ->
                    navController.navigate(com.vaultix.app.ui.navigation.Screen.QRCodeBackup.createRoute(masterPassword))
                },
                onNavigateToQRCodeRestore = {
                    navController.navigate(Screen.QRCodeRestore.route)
                },
                onNavigateToDevelopment = {
                    navController.navigate(Screen.Development.route)
                }
            )
        }

        // ── NEW: Card OCR Scanner ──
        composable(Screen.CardScan.route) {
            val cardViewModel: CardViewModel = hiltViewModel()
            CardScanScreen(
                cardViewModel = cardViewModel,
                onCardSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium.route)
                }
            )
        }

        // ── NEW: File Vault ──
        composable(Screen.FileVault.route) {
            val fileViewModel: FileViewModel = hiltViewModel()
            FileVaultScreen(
                fileViewModel = fileViewModel,
                onBack = { navController.popBackStack() },
                onViewPdf = { id, name ->
                    navController.navigate(Screen.PdfViewer.createRoute(id, name))
                },
                onViewImage = { path ->
                    navController.navigate(Screen.ImageViewer.createRoute(path))
                },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium.route)
                }
            )
        }

        // ── NEW: Identity Edit ──
        composable(
            route = Screen.IdentityEdit.route,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val identityViewModel: IdentityViewModel = hiltViewModel()
            IdentityEditScreen(
                identityViewModel = identityViewModel,
                onClose = { navController.popBackStack() },
                existingId = id
            )
        }

        composable(Screen.GlobalSearch.route) {
            GlobalSearchScreen(
                onNavigateToDetail = { id, type ->
                    navController.navigate(Screen.Detail.createRoute(id, type))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PasswordGenerator.route) {
            val generatorViewModel: com.vaultix.app.ui.viewmodel.PasswordGeneratorViewModel = hiltViewModel()
            PasswordGeneratorScreen(
                viewModel = generatorViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val name = backStackEntry.arguments?.getString("name") ?: ""
            PdfViewerScreen(
                fileId = id,
                fileName = name,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            ImageViewerScreen(
                imagePath = java.net.URLDecoder.decode(filePath, "UTF-8"),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SecurityAudit.route) {
            SecurityAuditScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPremium = {
                    navController.navigate(Screen.Premium.route)
                }
            )
        }

        // QR Code Backup (Export)
        composable(
            route = Screen.QRCodeBackup.route,
            arguments = listOf(
                navArgument("masterPassword") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val masterPassword = backStackEntry.arguments?.getString("masterPassword") ?: ""
            QRCodeBackupScreen(
                masterPassword = java.net.URLDecoder.decode(masterPassword, "UTF-8"),
                onBackupComplete = { navController.popBackStack() }
            )
        }

        // QR Code Restore (Import)
        composable(Screen.QRCodeRestore.route) {
            QRCodeRestoreScreen(
                onRestoreComplete = { navController.popBackStack() }
            )
        }

        composable(Screen.Premium.route) {
            val configViewModel: com.vaultix.app.ui.viewmodel.AppConfigViewModel = hiltViewModel()
            PremiumScreen(
                viewModel = configViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Dev Tools (develop branch only) ──
        composable(Screen.Development.route) {
            DevelopmentScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }

    // ── LIVE DEBUGGER FLOATING ACTION BUTTON & TERMINAL OVERLAY ──
    if (authState is AuthState.Authenticated) {
        var showDebugConsole by remember { mutableStateOf(false) }
        val debugEvents by DebugEventBus.events.collectAsStateWithLifecycle()
        var lastSeenEventId by remember { mutableStateOf(0L) }

        LaunchedEffect(showDebugConsole, debugEvents) {
            if (showDebugConsole && debugEvents.isNotEmpty()) {
                lastSeenEventId = debugEvents.last().id
            }
        }

        val unreadCount = remember(debugEvents, lastSeenEventId, showDebugConsole) {
            if (showDebugConsole) 0
            else debugEvents.count { it.id > lastSeenEventId }
        }

        // Log navigation events
        DisposableEffect(navController) {
            val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
                val route = destination.route ?: "unknown"
                val argsStr = arguments?.let { bundle ->
                    bundle.keySet().joinToString(", ") { key ->
                        "$key=${bundle.get(key)}"
                    }
                } ?: ""
                DebugEventBus.log(
                    category = DebugCategory.NAVIGATION,
                    eventType = "NAVIGATE",
                    details = "Route: $route" + (if (argsStr.isNotEmpty()) " with $argsStr" else ""),
                    source = "VaultixNavGraph"
                )
            }
            navController.addOnDestinationChangedListener(listener)
            onDispose {
                navController.removeOnDestinationChangedListener(listener)
            }
        }

        // Floating Debug Console Button (Bottom Start corner to not overlap other FABs)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 76.dp, start = 16.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        ) {
                            Text(text = if (unreadCount > 99) "99+" else unreadCount.toString())
                        }
                    }
                }
            ) {
                FloatingActionButton(
                    onClick = { showDebugConsole = true },
                    containerColor = Color(0xFF1E1E1E), // hacker dark color
                    contentColor = Color(0xFF4AF626), // hacker green
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Debugger console",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (showDebugConsole) {
            LiveDebuggerDialog(
                events = debugEvents,
                onClear = { DebugEventBus.clear() },
                onDismiss = { showDebugConsole = false }
            )
        }
    }
}
}
