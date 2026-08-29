package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Sonet AI starburst / spark glyph, inspired by minimal modern AI branding.
 */
@Composable
fun SonetLogo(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = (this.size.minDimension / 2f) * 0.9f
        val strokeWidth = (this.size.minDimension * 0.12f).coerceAtLeast(2f)

        // 8-spoke asterisk with alternating major and minor lengths
        val numSpokes = 8
        for (i in 0 until numSpokes) {
            val angle = Math.toRadians((i * 45.0) - 22.5)
            val radius = if (i % 2 == 0) maxRadius else maxRadius * 0.65f

            val endX = center.x + (radius * cos(angle)).toFloat()
            val endY = center.y + (radius * sin(angle)).toFloat()

            val startX = center.x + (radius * 0.22f * cos(angle)).toFloat()
            val startY = center.y + (radius * 0.22f * sin(angle)).toFloat()

            drawLine(
                color = tint,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
