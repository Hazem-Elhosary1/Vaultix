package com.vaultix.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultix.app.R
import com.vaultix.app.data.model.AuthState
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    authViewModel: AuthViewModel,
    onAuthenticated: () -> Unit
) {
    val isBiometricEnabled by authViewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val failedAttempts by authViewModel.failedAttempts.collectAsStateWithLifecycle()

    val isLockedOut by authViewModel.isLockedOut.collectAsStateWithLifecycle()
    val lockoutRemaining by authViewModel.lockoutRemainingSeconds.collectAsStateWithLifecycle()

    var pin by remember { mutableStateOf("") }
    var showPasswordMode by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isShaking by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val shakeOffset by animateFloatAsState(
        targetValue = if (isShaking) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "shake"
    )

    // Auto-launch biometric on open (only if not locked out)
    LaunchedEffect(isBiometricEnabled, isLockedOut) {
        if (isBiometricEnabled && !isLockedOut) {
            launchBiometric(
                context = context,
                onSuccess = {
                    authViewModel.onBiometricSuccess()
                    onAuthenticated()
                },
                onError = {}
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(VaultNavy, VaultBlack))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo - App Icon
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Vaultix App Icon",
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("VAULTIX", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary, letterSpacing = 6.sp)
            Text("Enter your credentials to unlock", fontSize = 14.sp, color = VaultTextSecondary)

            Spacer(Modifier.height(48.dp))

            // Lockout timer banner
            if (isLockedOut) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VaultError.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Lock, null, tint = VaultError, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Too many failed attempts", color = VaultError, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Try again in ${lockoutRemaining}s",
                            color = VaultError.copy(alpha = 0.8f),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (failedAttempts > 0 && !isLockedOut) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VaultError.copy(alpha = 0.15f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = VaultError, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$failedAttempts failed attempt${if (failedAttempts > 1) "s" else ""}",
                            color = VaultError,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            val isAuthLoading = authState is AuthState.Loading

            AnimatedContent(
                targetState = showPasswordMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "auth_mode"
            ) { passwordMode ->
                if (passwordMode) {
                    // Password mode
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = { Text("Master Password") },
                            enabled = !isLockedOut && !isAuthLoading,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }, enabled = !isAuthLoading) {
                                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = VaultTextSecondary)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultOrange,
                                unfocusedBorderColor = VaultBorder,
                                focusedLabelColor = VaultOrange,
                                cursorColor = VaultOrange,
                                focusedTextColor = VaultTextPrimary,
                                unfocusedTextColor = VaultTextPrimary
                            ),
                            isError = errorMessage != null
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                authViewModel.verifyPassword(
                                    password.toCharArray(),
                                    onSuccess = onAuthenticated,
                                    onFailure = {
                                        errorMessage = "Incorrect password"
                                        isShaking = true
                                        password = ""
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLockedOut && !isAuthLoading && password.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLockedOut) VaultSurface else VaultOrange)
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VaultBlack, strokeWidth = 2.dp)
                            } else {
                                Text("Unlock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { showPasswordMode = false }, enabled = !isAuthLoading) {
                            Text("Use PIN instead", color = VaultOrange)
                        }
                    }
                } else {
                    // PIN mode
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PinDots(pin = pin, maxLength = 6)
                        Spacer(Modifier.height(24.dp))

                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    pin = it
                                    errorMessage = null
                                }
                            },
                            label = { Text("PIN") },
                            enabled = !isLockedOut && !isAuthLoading,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultOrange,
                                unfocusedBorderColor = VaultBorder,
                                focusedLabelColor = VaultOrange,
                                cursorColor = VaultOrange,
                                focusedTextColor = VaultTextPrimary,
                                unfocusedTextColor = VaultTextPrimary
                            ),
                            isError = errorMessage != null
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                authViewModel.verifyPin(
                                    pin.toCharArray(),
                                    onSuccess = onAuthenticated,
                                    onFailure = {
                                        errorMessage = "Incorrect PIN"
                                        isShaking = true
                                        pin = ""
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isLockedOut && !isAuthLoading && pin.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VaultBlack, strokeWidth = 2.dp)
                            } else {
                                Text("Unlock", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isBiometricEnabled) {
                                OutlinedButton(
                                    onClick = {
                                        launchBiometric(
                                            context = context,
                                            onSuccess = {
                                                authViewModel.onBiometricSuccess()
                                                onAuthenticated()
                                            },
                                            onError = {}
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultOrange),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange)
                                ) {
                                    Icon(Icons.Default.Fingerprint, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Biometric")
                                }
                            }

                            TextButton(onClick = { showPasswordMode = true }) {
                                Text("Use password", color = VaultTextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { showRecoveryDialog = true }) {
                Text("Forgot Password or PIN?", color = VaultOrange.copy(alpha = 0.7f), fontSize = 13.sp)
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Text(msg, color = VaultError, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        
        if (showRecoveryDialog) {
            RecoveryDialog(
                authViewModel = authViewModel,
                onDismiss = { showRecoveryDialog = false },
                onSuccess = { 
                    showRecoveryDialog = false
                    onAuthenticated() 
                }
            )
        }
    }
}

@Composable
fun RecoveryDialog(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var recoveryKey by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1: Enter Key, 2: New Password
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text(if (step == 1) "Vault Recovery" else "Reset Master Password", color = VaultOrange, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 1) {
                    Text("Enter your 24-character recovery key to regain access.", fontSize = 13.sp, color = VaultTextSecondary)
                    OutlinedTextField(
                        value = recoveryKey,
                        onValueChange = { recoveryKey = it.uppercase(); error = null },
                        label = { Text("Recovery Key") },
                        placeholder = { Text("XXXX-XXXX-XXXX...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VaultOrange, unfocusedBorderColor = VaultBorder)
                    )
                } else {
                    Text("Create a new master password for your vault.", fontSize = 13.sp, color = VaultTextSecondary)
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VaultOrange, unfocusedBorderColor = VaultBorder)
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VaultOrange, unfocusedBorderColor = VaultBorder)
                    )
                }
                
                error?.let { Text(it, color = VaultError, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step == 1) {
                        scope.launch {
                            if (authViewModel.verifyRecoveryKey(recoveryKey)) {
                                step = 2
                            } else {
                                error = "Invalid recovery key"
                            }
                        }
                    } else {
                        if (newPassword != confirmPassword) {
                            error = "Passwords do not match"
                            return@Button
                        }
                        if (newPassword.length < 8) {
                            error = "Password must be at least 8 characters"
                            return@Button
                        }
                        authViewModel.resetPasswordWithRecoveryKey(
                            recoveryKey = recoveryKey,
                            newPassword = newPassword.toCharArray(),
                            onSuccess = onSuccess,
                            onFailure = { error = it }
                        )
                    }
                },
                enabled = if (step == 1) recoveryKey.isNotEmpty() else (newPassword.isNotEmpty() && confirmPassword.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
            ) {
                Text(if (step == 1) "Verify Key" else "Reset & Unlock", color = VaultBlack)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VaultTextSecondary) }
        }
    )
}

private fun launchBiometric(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context as? FragmentActivity ?: return
    val executor = ContextCompat.getMainExecutor(context)

    val biometricManager = BiometricManager.from(context)
    when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {}
        else -> {
            onError("Biometric not available")
            return
        }
    }

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            onError(errString.toString())
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Vaultix")
        .setSubtitle("Use your biometric to access your vault")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}

