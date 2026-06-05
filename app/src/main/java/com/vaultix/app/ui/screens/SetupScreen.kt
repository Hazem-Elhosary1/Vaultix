package com.vaultix.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.vaultix.app.R
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
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "setup_step",
            modifier = Modifier.fillMaxSize()
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
                            errorMessage = context.getString(R.string.password_min_length)
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
                            errorMessage = context.getString(R.string.passwords_not_match)
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
                            errorMessage = context.getString(R.string.setup_pin_error)
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
                            errorMessage = context.getString(R.string.setup_pin_match_error)
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
                    onNext = { key ->
                        scope.launch {
                            isProcessing = true
                            authViewModel.setupRecoveryKey(key)
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
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.create_master_password),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_password_desc),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.master_password)) },
            placeholder = { Text(stringResource(R.string.setup_enter_password)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
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
            enabled = !isProcessing && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.continue_text), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
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
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.confirm_password), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_confirm_password_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.confirm_password)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            isError = errorMessage != null
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing && password.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.continue_text), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
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
        Icon(Icons.Default.Pin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.create_pin), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_pin_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        PinDots(pin = pin, maxLength = 6)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text(stringResource(R.string.setup_pin_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            isError = errorMessage != null
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing && pin.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.continue_text), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
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
        Icon(Icons.Default.Pin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.confirm_pin), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_confirm_pin_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        PinDots(pin = pin, maxLength = 6)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text(stringResource(R.string.confirm_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            isError = errorMessage != null
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing && pin.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.continue_text), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
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
        Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.enable_biometric), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.setup_biometric_desc),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onEnable,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.enable_biometric), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(stringResource(R.string.setup_biometric_skip), fontSize = 16.sp)
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
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_warning_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WarningItem(stringResource(R.string.setup_warning_1))
                WarningItem(stringResource(R.string.setup_warning_2))
                WarningItem(stringResource(R.string.setup_warning_3))
                WarningItem(stringResource(R.string.setup_warning_4))
                WarningItem(stringResource(R.string.setup_warning_5))
                WarningItem(stringResource(R.string.setup_warning_6))
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.setup_warning_button), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun WarningItem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
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
        strength <= 1 -> Pair(StrengthVeryWeak, stringResource(R.string.very_weak))
        strength == 2 -> Pair(StrengthWeak, stringResource(R.string.weak))
        strength == 3 -> Pair(StrengthFair, stringResource(R.string.fair))
        strength in 4..5 -> Pair(StrengthStrong, stringResource(R.string.strong))
        else -> Pair(StrengthVeryStrong, stringResource(R.string.very_strong))
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.password_strength), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { strength / 6f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun RecoveryKeyStep(
    authViewModel: AuthViewModel,
    isProcessing: Boolean,
    onNext: (String) -> Unit
) {
    val recoveryKey = remember { authViewModel.generateRecoveryKey() }
    val scope = rememberCoroutineScope()
    var isSaved by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.recovery_key), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.setup_recovery_key_desc),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    recoveryKey,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )
                Spacer(Modifier.height(16.dp))
                val clipboardManager = LocalClipboardManager.current
                
                TextButton(onClick = { 
                    clipboardManager.setText(AnnotatedString(recoveryKey))
                    Toast.makeText(context, context.getString(R.string.setup_recovery_key_copied), Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.setup_recovery_key_copy), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isSaved,
                onCheckedChange = { isSaved = it },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Text(
                stringResource(R.string.setup_recovery_key_checkbox),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onNext(recoveryKey) },
            enabled = isSaved && !isProcessing,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.continue_text), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
