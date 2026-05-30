package com.vaultix.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigate: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(500)
        )
        delay(1500)
        onNavigate()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(VaultNavy, VaultBlack),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale.value)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(VaultOrange.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                ShieldLockIcon(
                    modifier = Modifier.size(80.dp),
                    color = VaultOrange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "VAULTIX",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextPrimary,
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Secure Digital Vault",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = VaultOrange,
                letterSpacing = 2.sp
            )
        }

        // Bottom tagline
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Zero Knowledge • Fully Offline • AES-256",
                fontSize = 11.sp,
                color = VaultTextDisabled,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ShieldLockIcon(
    modifier: Modifier = Modifier,
    color: Color = VaultOrange
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Draw shield
        val shieldPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.9f, h * 0.2f)
            lineTo(w * 0.9f, h * 0.55f)
            quadraticTo(w * 0.9f, h * 0.85f, w * 0.5f, h * 0.95f)
            quadraticTo(w * 0.1f, h * 0.85f, w * 0.1f, h * 0.55f)
            lineTo(w * 0.1f, h * 0.2f)
            close()
        }
        drawPath(shieldPath, color = color.copy(alpha = 0.3f))

        // Draw lock body
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f)
        )

        // Draw shackle
        val paint = androidx.compose.ui.graphics.Paint().apply {
            this.color = color
            style = androidx.compose.ui.graphics.PaintingStyle.Stroke
            strokeWidth = w * 0.06f
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        }
        drawContext.canvas.drawArc(
            rect = androidx.compose.ui.geometry.Rect(
                left = w * 0.35f,
                top = h * 0.28f,
                right = w * 0.65f,
                bottom = h * 0.55f
            ),
            startAngle = 180f,
            sweepAngle = 180f,
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
