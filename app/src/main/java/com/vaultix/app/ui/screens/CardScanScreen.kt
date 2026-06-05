package com.vaultix.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.camera.core.CameraControl
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vaultix.app.data.model.Card
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.CardViewModel

/**
 * OCR-based credit card scanner using CameraX + ML Kit Text Recognition.
 * Runs fully on-device (offline). No internet required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScanScreen(
    cardViewModel: CardViewModel,
    onCardSaved: () -> Unit,
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    appConfigViewModel: com.vaultix.app.ui.viewmodel.AppConfigViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Scanned data
    var scannedCardNumber by remember { mutableStateOf("") }
    var scannedExpiryMonth by remember { mutableStateOf("") }
    var scannedExpiryYear by remember { mutableStateOf("") }
    var scannedHolderName by remember { mutableStateOf("") }
    var scanComplete by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var lastDetectionSignature by remember { mutableStateOf("") }
    var stableDetectionCount by remember { mutableStateOf(0) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // Editable fields after scan
    var cardName by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expiryMonth by remember { mutableStateOf("") }
    var expiryYear by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    
    val configState by appConfigViewModel.configState.collectAsState()
    var showProRequiredDialog by remember(configState.isPremium) { mutableStateOf(!configState.isPremium) }

    // Update editable fields when scan completes
    LaunchedEffect(scanComplete) {
        if (scanComplete) {
            cardNumber = scannedCardNumber
            expiryMonth = scannedExpiryMonth
            expiryYear = scannedExpiryYear
            holderName = scannedHolderName
        }
    }

    if (showProRequiredDialog) {
        AlertDialog(
            onDismissRequest = { 
                showProRequiredDialog = false
                onBack() 
            },
            containerColor = VaultSurface,
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = VaultOrange, modifier = Modifier.size(48.dp)) },
            title = { Text(stringResource(R.string.pro_required_title), color = VaultTextPrimary) },
            text = { Text(stringResource(R.string.pro_required_card_scan_text), color = VaultTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { 
                        showProRequiredDialog = false
                        onNavigateToPremium()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) { Text(stringResource(R.string.upgrade_now), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showProRequiredDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.maybe_later), color = VaultTextSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (scanComplete) stringResource(R.string.verify_card_details) else stringResource(R.string.scan_credit_card),
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VaultTextPrimary)
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
        ) {
            if (!scanComplete) {
                // ── Camera Preview Phase ──
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                                    @androidx.annotation.OptIn(ExperimentalGetImage::class)
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(1920, 1080))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                                if (isProcessing || scanComplete) {
                                                    imageProxy.close()
                                                    return@setAnalyzer
                                                }
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null) {
                                                    isProcessing = true
                                                    val inputImage = InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees
                                                    )
                                                    recognizer.process(inputImage)
                                                        .addOnSuccessListener { visionText ->
                                                            detectCardInfo(visionText, inputImage.width, inputImage.height)?.let { info ->
                                                                // Stabilize: Only update if not blank (don't flicker back to empty)
                                                                if (info.number.isNotBlank()) scannedCardNumber = info.number
                                                                if (info.expiryMonth.isNotBlank()) {
                                                                    scannedExpiryMonth = info.expiryMonth
                                                                    scannedExpiryYear = info.expiryYear
                                                                }
                                                                if (info.holderName.isNotBlank()) scannedHolderName = info.holderName

                                                                val signature = listOf(
                                                                    info.number,
                                                                    info.expiryMonth,
                                                                    info.expiryYear,
                                                                    info.holderName
                                                                ).joinToString("|")

                                                                if (signature == lastDetectionSignature && signature.isNotBlank()) {
                                                                    stableDetectionCount += 1
                                                                } else {
                                                                    lastDetectionSignature = signature
                                                                    stableDetectionCount = 1
                                                                }

                                                                // Require 5 stable frames before auto-completing
                                                                if (scannedCardNumber.isNotBlank() && stableDetectionCount >= 5) {
                                                                    scanComplete = true
                                                                }
                                                            }
                                                            isProcessing = false
                                                        }
                                                        .addOnFailureListener {
                                                            isProcessing = false
                                                        }
                                                        .addOnCompleteListener {
                                                            imageProxy.close()
                                                        }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }
                                        }

                                    try {
                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                        cameraControl = camera.cameraControl
                                        
                                    } catch (e: Exception) {
                                        Log.e("CardScan", "Camera bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dimmed background with true cutout hole
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val cutoutWidth = 320.dp.toPx()
                            val cutoutHeight = 200.dp.toPx()
                            
                            val left = (canvasWidth - cutoutWidth) / 2
                            val top = (canvasHeight - cutoutHeight) / 2
                            
                            // Draw dimmed background
                            drawRect(
                                color = Color.Black.copy(alpha = 0.7f),
                                size = size
                            )
                            
                            // Clear the cutout area
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(cutoutWidth, cutoutHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                            )
                        }

                        // Border for the cutout (on top of canvas)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(320.dp)
                                    .height(200.dp)
                                    .border(3.dp, VaultOrange, RoundedCornerShape(16.dp))
                            )
                        }

                        // Flash Toggle Button
                        IconButton(
                            onClick = {
                                isFlashOn = !isFlashOn
                                cameraControl?.enableTorch(isFlashOn)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(VaultBlack.copy(0.5f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Flash",
                                tint = if (isFlashOn) VaultOrange else Color.White
                            )
                        }

                        // Bottom instruction
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, VaultBlack.copy(alpha = 0.8f))
                                    )
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = VaultOrange,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.align_card_hint),
                                    color = VaultTextPrimary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    stringResource(R.string.scan_area_only),
                                    color = VaultTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.embossed_card_tip),
                                    color = VaultOrange.copy(alpha = 0.8f),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        if (scannedCardNumber.isNotBlank() || scannedHolderName.isNotBlank() || scannedExpiryMonth.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = VaultSurface.copy(alpha = 0.92f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 108.dp)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(stringResource(R.string.detecting_info), color = VaultOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (scannedCardNumber.isNotBlank()) stringResource(R.string.ocr_number_format, scannedCardNumber) else stringResource(R.string.ocr_detecting_number),
                                        color = VaultTextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        if (scannedHolderName.isNotBlank()) stringResource(R.string.ocr_name_format, scannedHolderName) else stringResource(R.string.ocr_detecting_name),
                                        color = VaultTextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        if (scannedExpiryMonth.isNotBlank()) {
                                            stringResource(R.string.ocr_expiry_format, scannedExpiryMonth, scannedExpiryYear)
                                        } else {
                                            stringResource(R.string.ocr_detecting_expiry)
                                        },
                                        color = VaultTextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        if (stableDetectionCount >= 2) {
                                            stringResource(R.string.ocr_detected_reliable)
                                        } else {
                                            stringResource(R.string.ocr_hold_steady)
                                        },
                                        color = VaultTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Processing indicator
                        if (isProcessing) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = VaultSurface.copy(alpha = 0.9f),
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = VaultOrange,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.scanning_dots), color = VaultTextPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    // No camera permission
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VaultBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = VaultTextSecondary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                             Text(stringResource(R.string.camera_permission_required), color = VaultTextPrimary, fontSize = 16.sp)
                             Spacer(Modifier.height(8.dp))
                             Button(
                                 onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                 colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                             ) { Text(stringResource(R.string.grant_permission), color = VaultBlack) }
                        }
                    }
                }
            } else {
                // ── Edit / Confirm Phase ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .background(VaultBlack),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Success banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VaultSuccess.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = VaultSuccess, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.card_scanned_success), color = VaultSuccess, fontSize = 14.sp)
                        }
                    }

                    OutlinedTextField(
                        value = cardName,
                        onValueChange = { cardName = it },
                        label = { Text(stringResource(R.string.card_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ocrFieldColors()
                    )

                    OutlinedTextField(
                        value = holderName,
                        onValueChange = { holderName = it },
                        label = { Text(stringResource(R.string.cardholder_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ocrFieldColors()
                    )

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = it },
                        label = { Text(stringResource(R.string.card_number)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ocrFieldColors()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expiryMonth,
                            onValueChange = { expiryMonth = it },
                            label = { Text("MM") },
                            modifier = Modifier.weight(1f),
                            colors = ocrFieldColors()
                        )
                        OutlinedTextField(
                            value = expiryYear,
                            onValueChange = { expiryYear = it },
                            label = { Text("YY") },
                            modifier = Modifier.weight(1f),
                            colors = ocrFieldColors()
                        )
                        OutlinedTextField(
                            value = cvv,
                            onValueChange = { cvv = it },
                            label = { Text(stringResource(R.string.cvv)) },
                            modifier = Modifier.weight(1f),
                            colors = ocrFieldColors()
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Re-scan
                    OutlinedButton(
                        onClick = {
                            scanComplete = false
                            scannedCardNumber = ""
                            scannedExpiryMonth = ""
                            scannedExpiryYear = ""
                            scannedHolderName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultOrange),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.scan_again))
                    }

                    // Save
                    Button(
                        onClick = {
                            val detectedType = detectCardType(cardNumber)
                            val now = System.currentTimeMillis()
                            val card = Card(
                                id = "",
                                cardName = cardName.ifEmpty { detectedType },
                                holderName = holderName,
                                cardNumber = cardNumber.replace(" ", ""),
                                expiryMonth = expiryMonth,
                                expiryYear = expiryYear,
                                cvv = cvv,
                                cardType = detectedType,
                                notes = context.getString(R.string.scanned_via_ocr),
                                isFavorite = false,
                                createdAt = now,
                                updatedAt = now
                            )
                            cardViewModel.insertCard(card)
                            Toast.makeText(context, context.getString(R.string.card_saved_success), Toast.LENGTH_SHORT).show()
                            onCardSaved()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                    ) {
                        Text(stringResource(R.string.save_card), color = VaultBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ── Helper: OCR text field colors ──
@Composable
private fun ocrFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = VaultTextPrimary,
    unfocusedTextColor = VaultTextPrimary,
    focusedBorderColor = VaultOrange,
    unfocusedBorderColor = VaultBorder,
    focusedLabelColor = VaultOrange,
    unfocusedLabelColor = VaultTextSecondary,
    cursorColor = VaultOrange
)

// ── Helper: Card detection ──
data class CardDetectionResult(
    val number: String = "",
    val expiryMonth: String = "",
    val expiryYear: String = "",
    val holderName: String = "",
    val cardType: String = ""
)

private data class RankedLine(
    val text: String,
    val top: Int
)

private data class NumberCandidate(
    val raw: String,
    val formatted: String,
    val top: Int,
    val score: Int
)

private data class ExpiryCandidate(
    val month: String,
    val year: String,
    val score: Int
)

private data class NameCandidate(
    val name: String,
    val score: Int
)

private fun detectCardInfo(visionText: Text, imageWidth: Int, imageHeight: Int): CardDetectionResult? {
    // Determine the "safe area" (the yellow card part) in relative coordinates (0.0 to 1.0)
    // Overlay is 320x200dp. Most screens are ~360-400dp wide.
    // We'll target the center 80% width and center 50% height.
    val horizontalMargin = 0.1f
    val verticalMargin = 0.25f
    
    val lines = visionText.textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            
            // Filter: Must be within the horizontal center
            val centerX = box.centerX().toFloat() / imageWidth
            val centerY = box.centerY().toFloat() / imageHeight
            
            if (centerX < horizontalMargin || centerX > (1f - horizontalMargin)) return@mapNotNull null
            if (centerY < verticalMargin || centerY > (1f - verticalMargin)) return@mapNotNull null

            val text = line.text.trim()
            if (text.isBlank()) null else RankedLine(text, box.top)
        }
    }.sortedBy { it.top }

    if (lines.isEmpty()) return null

    val numberCandidate = findBestCardNumber(lines)
    
    // Global search for number if line-by-line fails
    val allText = lines.joinToString(" ") { it.text }
    val globalNumber = extractCardNumberCandidates(allText).find { isValidCardNumber(it) }
    
    val finalNumber = if (globalNumber != null && (numberCandidate == null || !isValidCardNumber(numberCandidate.raw))) {
        NumberCandidate(
            raw = globalNumber,
            formatted = globalNumber.chunked(4).joinToString(" "),
            top = 0,
            score = 100
        )
    } else {
        numberCandidate
    }

    val expiryCandidate = findBestExpiry(lines)
    val nameCandidate = findBestHolderName(lines, finalNumber?.top)

    if (finalNumber == null && expiryCandidate == null && nameCandidate == null) {
        return null
    }

    return CardDetectionResult(
        number = finalNumber?.formatted.orEmpty(),
        expiryMonth = expiryCandidate?.month.orEmpty(),
        expiryYear = expiryCandidate?.year.orEmpty(),
        holderName = nameCandidate?.name.orEmpty(),
        cardType = finalNumber?.let { detectCardType(it.raw) }.orEmpty()
    )
}

private fun findBestCardNumber(lines: List<RankedLine>): NumberCandidate? {
    val candidates = buildList {
        lines.forEach { line ->
            extractCardNumberCandidates(line.text).forEach { raw ->
                if (isValidCardNumber(raw)) {
                    add(
                        NumberCandidate(
                            raw = raw,
                            formatted = raw.chunked(4).joinToString(" "),
                            top = line.top,
                            score = scoreCardNumberCandidate(line.text, raw)
                        )
                    )
                }
            }
        }
    }
    return candidates.maxByOrNull { it.score }
}

private fun extractCardNumberCandidates(text: String): List<String> {
    // Clean text: common OCR mistakes for embossed numbers
    val cleaned = text.uppercase()
        .replace('O', '0')
        .replace('Q', '0')
        .replace('D', '0')
        .replace('U', '0')
        .replace('I', '1')
        .replace('L', '1')
        .replace('T', '7')
        .replace('Z', '2')
        .replace('S', '5')
        .replace('B', '8')
        .replace('G', '6')
        .replace('E', '3')
    
    // Look for patterns like 1234 5678 1234 5678 or 1234567812345678
    val matches = Regex("""(?:\d[\d\s-]{12,22}\d)""").findAll(cleaned)
    return matches.mapNotNull { match ->
        val digits = match.value.replace(Regex("[^0-9]"), "")
        if (digits.length in 13..19) digits else null
    }.distinct().toList()
}

private fun scoreCardNumberCandidate(text: String, rawNumber: String): Int {
    val normalized = text.uppercase()
    var score = rawNumber.length * 10
    if (normalized.contains("CARD")) score += 25
    if (normalized.contains("NUMBER")) score += 15
    if (normalized.contains("CREDIT")) score += 10
    if (normalized.contains("DEBIT")) score += 10
    if (normalized.contains("VISA") || normalized.contains("MASTERCARD") || normalized.contains("AMEX")) score += 15
    if (text.count { it.isDigit() } == rawNumber.length) score += 10
    if (text.count { it == ' ' } >= 3) score += 5
    return score
}

private fun findBestExpiry(lines: List<RankedLine>): ExpiryCandidate? {
    val regex = Regex("""(0[1-9]|1[0-2])\s?[/\-]\s?(\d{2}|\d{4})""")
    val candidates = buildList {
        lines.forEach { line ->
            regex.find(line.text)?.let { match ->
                var year = match.groupValues[2]
                if (year.length == 4) year = year.takeLast(2)
                val score = scoreExpiryCandidate(line.text)
                add(ExpiryCandidate(match.groupValues[1], year, score))
            }
        }
    }
    return candidates.maxByOrNull { it.score }
}

private fun scoreExpiryCandidate(text: String): Int {
    val normalized = text.uppercase()
    var score = 10
    if (normalized.contains("EXP")) score += 20
    if (normalized.contains("VALID")) score += 15
    if (normalized.contains("THRU") || normalized.contains("THROUGH")) score += 15
    if (normalized.contains("GOOD")) score += 10
    return score
}

private fun findBestHolderName(lines: List<RankedLine>, numberTop: Int?): NameCandidate? {
    val blacklist = setOf(
        "VISA", "MASTERCARD", "AMEX", "DISCOVER", "CREDIT", "DEBIT", "CARD", "NUMBER",
        "VALID", "THRU", "THROUGH", "GOOD", "EXP", "EXPIRES", "DATE", "BANK", "NATIONAL",
        "WORLD", "PLATINUM", "GOLD", "SILVER", "PREMIER", "ELECTRON", "CLASSIC", "PLUS",
        "REWARDS", "BUSINESS", "CORPORATE", "CASHBACK", "TRAVEL", "MEMBER", "SINCE",
        "CENTRAL", "INVESTMENT", "COMMERCIAL", "ISLAMIC", "EGYPT", "ARAB", "CAIRO",
        "ALEXANDRIA", "QNB", "HSBC", "CIB", "NBE", "BM", "BANQUE", "MISR", "OFFER", "LIMITED"
    )

    val candidates = buildList {
        lines.forEach { line ->
            val normalized = line.text.trim().replace(Regex("\\s+"), " ")
            if (normalized.length !in 6..28) return@forEach
            if (normalized.any { it.isDigit() }) return@forEach

            val upper = normalized.uppercase()
            if (blacklist.any { upper.contains(it) }) return@forEach

            val wordCount = normalized.split(" ").size
            if (wordCount !in 2..4) return@forEach

            // Check if it looks like a person's name (mostly letters, no weird symbols)
            if (!Regex("""^[A-Z][A-Z'\-]+(?:\s+[A-Z][A-Z'\-]+){1,3}$""").matches(upper)) return@forEach

            var score = 10
            // Vertical position: Holder name is almost always BELOW the card number
            if (numberTop != null) {
                if (line.top > numberTop) score += 20 // Corrected: favor names below number
                else score -= 15 // Penalize names above number (usually bank names)
            }
            
            if (wordCount == 2) score += 10
            if (wordCount == 3) score += 7
            if (normalized.length in 12..22) score += 5
            
            add(NameCandidate(normalized.uppercase(), score))
        }
    }

    return candidates.maxByOrNull { it.score }
}

private fun isValidCardNumber(number: String): Boolean {
    if (number.length !in 13..19) return false
    return luhnCheck(number)
}

/** Luhn algorithm to validate credit card numbers. */
private fun luhnCheck(number: String): Boolean {
    val digits = number.map { it.digitToInt() }
    var sum = 0
    var alternate = false
    for (i in digits.size - 1 downTo 0) {
        var d = digits[i]
        if (alternate) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
        alternate = !alternate
    }
    return sum % 10 == 0
}

/** Detect card brand from number prefix. */
private fun detectCardType(number: String): String {
    val clean = number.replace(" ", "")
    return when {
        clean.startsWith("4") -> "Visa"
        clean.startsWith("5") || clean.startsWith("2") -> "Mastercard"
        clean.startsWith("34") || clean.startsWith("37") -> "Amex"
        clean.startsWith("6") -> "Discover"
        else -> "Card"
    }
}
