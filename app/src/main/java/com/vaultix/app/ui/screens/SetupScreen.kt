package com.vaultix.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

enum class SetupStep {
    CREATE_PASSWORD,
    CONFIRM_PASSWORD,
    CREATE_PIN,
    CONFIRM_PIN,
    BIOMETRIC,
    RECOVERY_KEY,
    WARNING
}

@Composable
fun SetupScreen(
    authViewModel: AuthViewModel,
    onSetupComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(SetupStep.CREATE_PASSWORD) }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
            .statusBarsPadding()
    ) {
        // Step progress indicator at top
        Column {
            LinearProgressIndicator(
                progress = {
                    when (currentStep) {
                        SetupStep.CREATE_PASSWORD -> 0.1f
                        SetupStep.CONFIRM_PASSWORD -> 0.25f
                        SetupStep.CREATE_PIN -> 0.45f
                        SetupStep.CONFIRM_PIN -> 0.65f
                        SetupStep.BIOMETRIC -> 0.75f
                        SetupStep.RECOVERY_KEY -> 0.9f
                        SetupStep.WARNING -> 1f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                color = VaultOrange,
                trackColor = VaultSurface
            )
            
            if (currentStep != SetupStep.CREATE_PASSWORD) {
                IconButton(
                    onClick = {
                        currentStep = when (currentStep) {
                            SetupStep.CONFIRM_PASSWORD -> SetupStep.CREATE_PASSWORD
                            SetupStep.CREATE_PIN -> SetupStep.CONFIRM_PASSWORD
                            SetupStep.CONFIRM_PIN -> SetupStep.CREATE_PIN
                            SetupStep.BIOMETRIC -> SetupStep.CONFIRM_PIN
                            SetupStep.RECOVERY_KEY -> SetupStep.BIOMETRIC
                            SetupStep.WARNING -> SetupStep.RECOVERY_KEY
                            else -> SetupStep.CREATE_PASSWORD
                        }
                        errorMessage = null
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VaultOrange)
                }
            }
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "setup_step"
        ) { step ->
            when (step) {
                SetupStep.CREATE_PASSWORD -> CreatePasswordStep(
                    password = masterPassword,
                    onPasswordChange = {
                        masterPassword = it
                        errorMessage = null
                    },
                    errorMessage = errorMessage,
                    isProcessing = isProcessing,
                    onNext = {
                        if (masterPassword.length < 8) {
                            errorMessage = "Password must be at least 8 characters"
                        } else {
                            currentStep = SetupStep.CONFIRM_PASSWORD
                        }
                    }
                )

                SetupStep.CONFIRM_PASSWORD -> ConfirmPasswordStep(
                    password = confirmPassword,
                    onPasswordChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    errorMessage = errorMessage,
                    isProcessing = isProcessing,
                    onNext = {
                        if (masterPassword != confirmPassword) {
                            errorMessage = "Passwords do not match"
                        } else {
                            scope.launch {
                                isProcessing = true
                                authViewModel.setupMasterPassword(masterPassword.toCharArray())
                                currentStep = SetupStep.CREATE_PIN
                                isProcessing = false
                            }
                        }
                    }
                )

                SetupStep.CREATE_PIN -> CreatePinStep(
                    pin = pin,
                    onPinChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            pin = it
                            errorMessage = null
                        }
                    },
                    errorMessage = errorMessage,
                    isProcessing = isProcessing,
                    onNext = {
                        if (pin.length < 4) {
                            errorMessage = "PIN must be at least 4 digits"
                        } else {
                            currentStep = SetupStep.CONFIRM_PIN
                        }
                    }
                )

                SetupStep.CONFIRM_PIN -> ConfirmPinStep(
                    pin = confirmPin,
                    onPinChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            confirmPin = it
                            errorMessage = null
                        }
                    },
                    errorMessage = errorMessage,
                    isProcessing = isProcessing,
                    onNext = {
                        if (pin != confirmPin) {
                            errorMessage = "PINs do not match"
                        } else {
                            scope.launch {
                                isProcessing = true
                                authViewModel.setupPin(pin.toCharArray())
                                currentStep = SetupStep.BIOMETRIC
                                isProcessing = false
                            }
                        }
                    }
                )

                SetupStep.BIOMETRIC -> BiometricSetupStep(
                    isProcessing = isProcessing,
                    onEnable = {
                        scope.launch {
                            isProcessing = true
                            authViewModel.setBiometricEnabled(true)
                            currentStep = SetupStep.RECOVERY_KEY
                            isProcessing = false
                        }
                    },
                    onSkip = { currentStep = SetupStep.RECOVERY_KEY }
                )

                SetupStep.RECOVERY_KEY -> RecoveryKeyStep(
                    authViewModel = authViewModel,
                    isProcessing = isProcessing,
                    onNext = { 
                        scope.launch {
                            isProcessing = true
                            currentStep = SetupStep.WARNING 
                            isProcessing = false
                        }
                    }
                )

                SetupStep.WARNING -> WarningStep(
                    isProcessing = isProcessing,
                    onComplete = {
                        scope.launch {
                            isProcessing = true
                            authViewModel.completeSetup()
                            onSetupComplete()
                            isProcessing = false
                        }
                    }
                )
            }
        }

        // Error snackbar
        errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = VaultError,
                contentColor = VaultTextPrimary
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun CreatePasswordStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    isProcessing: Boolean,
    onNext: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = VaultOrange,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Create Master Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This is the key to your vault. Choose something strong and memorable — there is NO recovery option.",
            fontSize = 14.sp,
            color = VaultTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Master Password") },
            placeholder = { Text("Enter master password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = VaultTextSecondary
                    )
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

        // Password strength indicator
        if (password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            PasswordStrengthBar(password = password.toCharArray())
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VaultBlack, strokeWidth = 2.dp)
            } else {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
            }
        }
    }
}

@Composable
private fun ConfirmPasswordStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    isProcessing: Boolean,
    onNext: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = VaultOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Confirm Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Re-enter your master password to confirm.", fontSize = 14.sp, color = VaultTextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Confirm Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
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

        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
        }
    }
}

@Composable
private fun CreatePinStep(
    pin: String,
    onPinChange: (String) -> Unit,
    errorMessage: String?,
    isProcessing: Boolean,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Pin, null, tint = VaultOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Create PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("4-6 digit PIN for quick unlock. Different from your panic PIN.", fontSize = 14.sp, color = VaultTextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        PinDots(pin = pin, maxLength = 6)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("PIN (4-6 digits)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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

        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
        }
    }
}

@Composable
private fun ConfirmPinStep(
    pin: String,
    onPinChange: (String) -> Unit,
    errorMessage: String?,
    isProcessing: Boolean,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Pin, null, tint = VaultOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Confirm PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Re-enter your PIN to confirm.", fontSize = 14.sp, color = VaultTextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        PinDots(pin = pin, maxLength = 6)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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

        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)) {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
        }
    }
}

@Composable
private fun BiometricSetupStep(
    isProcessing: Boolean,
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Fingerprint, null, tint = VaultOrange, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("Enable Biometric", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use fingerprint or face recognition for quick, secure access to your vault.",
            fontSize = 14.sp,
            color = VaultTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VaultBlack, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Fingerprint, null, tint = VaultBlack)
                Spacer(Modifier.width(8.dp))
                Text("Enable Biometric", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultTextSecondary),
            border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
        ) {
            Text("Skip for now", fontSize = 16.sp)
        }
    }
}

@Composable
private fun WarningStep(
    isProcessing: Boolean,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(VaultError.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, null, tint = VaultError, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("⚠️ Important Warning", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultError)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VaultSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WarningItem("There is NO password recovery")
                WarningItem("There is NO cloud backup")
                WarningItem("If you forget your master password, use your RECOVERY KEY")
                WarningItem("If you lose BOTH your password and recovery key, ALL data is permanently lost")
                WarningItem("This is by design — zero-knowledge means only YOU have access")
                WarningItem("Write down your master password AND recovery key and store them separately")
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VaultBlack, strokeWidth = 2.dp)
            } else {
                Text("I Understand, Let's Go", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
            }
        }
    }
}

@Composable
private fun WarningItem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = VaultError, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = VaultTextSecondary)
    }
}

@Composable
fun PinDots(pin: String, maxLength: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxLength) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        if (index < pin.length) VaultOrange else VaultBorder,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
fun PasswordStrengthBar(password: CharArray) {
    val strength = remember(password) {
        var score = 0
        if (password.size >= 12) score++
        if (password.size >= 20) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }

    val (color, label) = when {
        strength <= 1 -> Pair(StrengthVeryWeak, "Very Weak")
        strength == 2 -> Pair(StrengthWeak, "Weak")
        strength == 3 -> Pair(StrengthFair, "Fair")
        strength in 4..5 -> Pair(StrengthStrong, "Strong")
        else -> Pair(StrengthVeryStrong, "Very Strong")
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Password strength", fontSize = 12.sp, color = VaultTextSecondary)
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { strength / 6f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = VaultSurface
        )
    }
}

@Composable
private fun RecoveryKeyStep(
    authViewModel: AuthViewModel,
    isProcessing: Boolean,
    onNext: () -> Unit
) {
    val recoveryKey = remember { authViewModel.generateRecoveryKey() }
    val scope = rememberCoroutineScope()
    var isSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Key, null, tint = VaultOrange, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text("Your Recovery Key", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(
            "This 24-character key is the ONLY way to recover your vault if you forget your master password.",
            fontSize = 14.sp,
            color = VaultTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VaultSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange.copy(0.3f))
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    recoveryKey,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultOrange,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(16.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                
                TextButton(onClick = { 
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(recoveryKey))
                    android.widget.Toast.makeText(context, "Recovery key copied!", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy to clipboard", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isSaved,
                onCheckedChange = { isSaved = it },
                colors = CheckboxDefaults.colors(checkedColor = VaultOrange)
            )
            Text(
                "I have written down this recovery key and stored it in a safe place.",
                fontSize = 12.sp,
                color = VaultTextPrimary
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                scope.launch {
                    authViewModel.setupRecoveryKey(recoveryKey)
                    onNext()
                }
            },
            enabled = isSaved && !isProcessing,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VaultBlack, strokeWidth = 2.dp)
            } else {
                Text("Confirm & Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VaultBlack)
            }
        }
    }
}
