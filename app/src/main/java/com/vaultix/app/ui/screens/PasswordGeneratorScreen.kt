import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultix.app.R
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.GeneratedPasswordEntry
import com.vaultix.app.ui.viewmodel.PasswordGeneratorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    viewModel: PasswordGeneratorViewModel,
    onBack: () -> Unit
) {
    val generatedPassword by viewModel.generatedPassword.collectAsStateWithLifecycle()
    val passwordHistory by viewModel.passwordHistory.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var length by remember { mutableFloatStateOf(16f) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    // Generate initial password if none exists
    LaunchedEffect(Unit) {
        if (generatedPassword.isEmpty()) {
            viewModel.generate(length.toInt(), includeUppercase, includeNumbers, includeSymbols)
        }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.password_generator), fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = VaultTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(20.dp),
                color = VaultSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = generatedPassword,
                        fontSize = if (generatedPassword.length > 20) 18.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultOrange,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(onClick = { 
                            viewModel.generate(length.toInt(), includeUppercase, includeNumbers, includeSymbols)
                        }) {
                            Icon(Icons.Default.Refresh, "Regenerate", tint = VaultTextSecondary)
                        }
                        IconButton(onClick = {
                            if (generatedPassword.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(generatedPassword))
                                Toast.makeText(context, context.getString(R.string.copy_password), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = VaultTextSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Configuration Area
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = VaultSurface)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.length_format, length.toInt()).uppercase(java.util.Locale.getDefault()), color = VaultTextPrimary, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = length,
                        onValueChange = { length = it },
                        valueRange = 8f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = VaultOrange,
                            activeTrackColor = VaultOrange,
                            inactiveTrackColor = VaultOrange.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    GeneratorToggle("Include Uppercase (A-Z)", includeUppercase) { includeUppercase = it }
                    GeneratorToggle("Include Numbers (0-9)", includeNumbers) { includeNumbers = it }
                    GeneratorToggle("Include Symbols (!@#$)", includeSymbols) { includeSymbols = it }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Timeline History Area
            if (passwordHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.History, null, tint = VaultOrange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.password_history),
                            color = VaultTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    passwordHistory.forEachIndexed { index, entry ->
                        TimelineItem(
                            entry = entry,
                            isLast = index == passwordHistory.size - 1,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(entry.password))
                                Toast.makeText(context, context.getString(R.string.copy_password), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.generate(length.toInt(), includeUppercase, includeNumbers, includeSymbols) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
            ) {
                Text(stringResource(R.string.generate_new_password), color = VaultBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimelineItem(
    entry: GeneratedPasswordEntry,
    isLast: Boolean,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline line and dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(VaultOrange, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(VaultOrange.copy(alpha = 0.3f))
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .padding(start = 8.dp, bottom = 24.dp)
                .weight(1f)
        ) {
            Text(
                text = entry.timestamp,
                color = VaultTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy() },
                shape = RoundedCornerShape(12.dp),
                color = VaultSurface.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.password,
                        color = VaultTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = VaultTextDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VaultTextPrimary, fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VaultOrange,
                checkedTrackColor = VaultOrange.copy(alpha = 0.3f)
            )
        )
    }
}
