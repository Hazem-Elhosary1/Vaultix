package com.vaultix.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accentColor: androidx.compose.ui.graphics.Color
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    authViewModel: AuthViewModel
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Default.Shield,
            title = "Zero-Knowledge Security",
            description = "All your data is encrypted with AES-256. Only you hold the keys. We never see your data — ever.",
            accentColor = VaultOrange
        ),
        OnboardingPage(
            icon = Icons.Default.Lock,
            title = "Truly Offline",
            description = "No internet. No cloud. No servers. Your vault lives entirely on your device, protected by military-grade encryption.",
            accentColor = VaultInfo
        ),
        OnboardingPage(
            icon = Icons.Default.Search,
            title = "Everything in One Place",
            description = "Store passwords, credit cards, secure notes, files, and IDs. Search instantly across all your encrypted data.",
            accentColor = VaultSuccess
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    scope.launch {
                        authViewModel.completeOnboarding()
                        onComplete()
                    }
                }) {
                    Text("Skip", color = VaultTextSecondary, fontSize = 14.sp)
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            // Indicators
            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val color by animateColorAsState(
                        targetValue = if (isSelected) VaultOrange else VaultTextDisabled,
                        animationSpec = tween(300),
                        label = "indicator"
                    )
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (isSelected) 32.dp else 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Action button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage < pages.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                authViewModel.completeOnboarding()
                                onComplete()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) {
                    Text(
                        text = if (pagerState.currentPage < pages.size - 1) "Next" else "Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultBlack
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon container with animations
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            page.accentColor.copy(alpha = 0.2f),
                            page.accentColor.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            when (page.title) {
                "Zero-Knowledge Security" -> {
                    // Shield with glow animation
                    ShieldWithGlow(color = page.accentColor)
                }
                "Truly Offline" -> {
                    // Lock with locking animation
                    LockWithAnimation(color = page.accentColor)
                }
                "Everything in One Place" -> {
                    // Search with rotation animation
                    SearchWithRotation(color = page.accentColor)
                }
                else -> {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = page.accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = VaultTextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            fontSize = 16.sp,
            color = VaultTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun ShieldWithGlow(color: Color) {
    val wave1Radius = remember { Animatable(0f) }
    val wave2Radius = remember { Animatable(0f) }
    val wave3Radius = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Wave 1
        launch {
            while (true) {
                wave1Radius.animateTo(
                    targetValue = 80f,
                    animationSpec = tween(2000, easing = androidx.compose.animation.core.LinearEasing)
                )
                wave1Radius.snapTo(0f)
            }
        }
        
        // Wave 2 - delayed
        launch {
            kotlinx.coroutines.delay(500)
            while (true) {
                wave2Radius.animateTo(
                    targetValue = 80f,
                    animationSpec = tween(2000, easing = androidx.compose.animation.core.LinearEasing)
                )
                wave2Radius.snapTo(0f)
            }
        }
        
        // Wave 3 - more delayed
        launch {
            kotlinx.coroutines.delay(1000)
            while (true) {
                wave3Radius.animateTo(
                    targetValue = 80f,
                    animationSpec = tween(2000, easing = androidx.compose.animation.core.LinearEasing)
                )
                wave3Radius.snapTo(0f)
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Wave rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            // Wave 1
            drawCircle(
                color = color.copy(alpha = (1 - wave1Radius.value / 80f) * 0.6f),
                radius = wave1Radius.value.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Wave 2
            drawCircle(
                color = color.copy(alpha = (1 - wave2Radius.value / 80f) * 0.6f),
                radius = wave2Radius.value.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Wave 3
            drawCircle(
                color = color.copy(alpha = (1 - wave3Radius.value / 80f) * 0.6f),
                radius = wave3Radius.value.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        
        // Icon
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(64.dp)
        )
    }
}

@Composable
private fun LockWithAnimation(color: Color) {
    val shackleProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            // Open - from 0 to 1
            shackleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = androidx.compose.animation.core.EaseInOut)
            )
            kotlinx.coroutines.delay(500)
            // Close - from 1 to 0
            shackleProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(800, easing = androidx.compose.animation.core.EaseInOut)
            )
            kotlinx.coroutines.delay(500)
        }
    }
    
    Canvas(modifier = Modifier.size(64.dp)) {
        val w = size.width
        val h = size.height
        val progress = shackleProgress.value
        
        // Lock body
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f)
        )
        
        // Shackle (القوس العلوي) - يفتح وينغلق
        val paint = androidx.compose.ui.graphics.Paint().apply {
            this.color = color
            style = androidx.compose.ui.graphics.PaintingStyle.Stroke
            strokeWidth = w * 0.06f
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        }
        
        // Arc animation - when progress = 0 (closed), sweepAngle = 180f, when progress = 1 (open), sweepAngle = 270f
        val startAngle = 180f
        val sweepAngle = 180f + (90f * progress) // From 180 to 270 degrees
        
        drawContext.canvas.drawArc(
            rect = androidx.compose.ui.geometry.Rect(
                left = w * 0.35f,
                top = h * 0.28f - (w * 0.15f * progress), // Move up when opening
                right = w * 0.65f,
                bottom = h * 0.55f
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            paint = paint
        )
        
        // Keyhole circle
        drawCircle(
            color = androidx.compose.ui.graphics.Color.Black,
            radius = w * 0.04f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.62f)
        )
        
        // Keyhole stem
        drawLine(
            color = androidx.compose.ui.graphics.Color.Black,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.66f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.73f),
            strokeWidth = w * 0.04f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun SearchWithRotation(color: Color) {
    val angle = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        angle.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val angleRad = Math.toRadians(angle.value.toDouble()).toFloat()
        val orbitRadius = 20f // نصف قطر المدار
        val offsetX = (orbitRadius * kotlin.math.cos(angleRad.toDouble())).toFloat()
        val offsetY = (orbitRadius * kotlin.math.sin(angleRad.toDouble())).toFloat()
        
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(64.dp)
                .offset(offsetX.dp, offsetY.dp)
        )
    }
}
