package com.example.wristtype.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlin.math.*

@Composable
fun ArcKeyboard(
    modifier: Modifier = Modifier,
    hoverAngleRad: Float,            // 0 = North, +clockwise
    highlightedIndex: Int,           // 0..5
    groups: List<String>,
    centerPreview: String? = null,
    ringThickness: Dp = 12.dp,
    labelBox: Dp = 44.dp,             // box that centers each label
    labelNudgeX: Dp = (-3).dp,   // move left (negative), right (positive)
    labelNudgeY: Dp = (-2).dp    // move up (negative), down (positive)

) {
    // theme colors (outside draw scope)
    val bg: Color = MaterialTheme.colors.background
    val onBg = MaterialTheme.colors.onBackground
    val arcActive = onBg.copy(alpha = 0.85f)
    val arcIdle   = onBg.copy(alpha = 0.25f)
    val pointer   = onBg.copy(alpha = 0.90f)
    val labelHi   = onBg.copy(alpha = 1f)
    val labelLo   = onBg.copy(alpha = 0.6f)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // make a perfect square from the smaller dimension
        val sidePx = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val sideDp = with(LocalDensity.current) { sidePx.toDp() }

        // geometry (pulled inward so nothing clips on a round face)
        val density = LocalDensity.current
        val strokePx = with(density) { ringThickness.toPx() }
        val labelBoxPx = with(density) { labelBox.toPx() }

        val radiusPx      = sidePx * 0.33f   // ring radius
        val labelRadiusPx = sidePx * 0.44f   // where label boxes are centered (outside ring)

        Box(modifier = Modifier.size(sideDp), contentAlignment = Alignment.Center) {
            // ---- ring + pointer ----
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .background(bg)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rect = Rect(cx - radiusPx, cy - radiusPx, cx + radiusPx, cy + radiusPx)

                // draw 6 arcs of 60°; convert north=top to drawArc's 0°=3 o'clock
                repeat(6) { i ->
                    val northCenter = i * 60f
                    val northStart = northCenter - 30f
                    val drawStart = 90f - northStart
                    val isHi = (i == highlightedIndex)

                    drawArc(
                        color = if (isHi) arcActive else arcIdle,
                        startAngle = drawStart,
                        sweepAngle = 60f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                }

                // pointer
                val northDeg = Math.toDegrees(hoverAngleRad.toDouble()).toFloat()
                val drawDeg = 90f - northDeg
                val pointerLen = radiusPx + strokePx * 0.25f
                val px = cx + pointerLen * cos(Math.toRadians(drawDeg.toDouble())).toFloat()
                val py = cy + pointerLen * sin(Math.toRadians(drawDeg.toDouble())).toFloat()
                drawLine(color = pointer, start = Offset(cx, cy), end = Offset(px, py), strokeWidth = 2f)
            }

            // ---- labels: centered boxes placed on the ring ----
            repeat(6) { i ->
                val northCenterDeg = i * 60f
                val drawDeg = 90f - northCenterDeg
                val cx = sidePx / 2f
                val cy = sidePx / 2f
                val centerX = cx + labelRadiusPx * cos(Math.toRadians(drawDeg.toDouble())).toFloat()
                val centerY = cy + labelRadiusPx * sin(Math.toRadians(drawDeg.toDouble())).toFloat()

                val txt = groups.getOrNull(i) ?: ""
                val color = if (i == highlightedIndex) labelHi else labelLo

                Box(
                    modifier = Modifier
                        .absoluteOffset(
                            x = with(density) { (centerX - labelBoxPx / 2 ).toDp() },
                            y = with(density) { (centerY - labelBoxPx / 2 ).toDp() }
                        )
                        .size(labelBox)
                        .offset(x = labelNudgeX, y = labelNudgeY),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = txt, color = color, style = TextStyle(fontSize = 11.sp))
                }
            }

            // ---- center preview ----
            if (!centerPreview.isNullOrEmpty()) {
                Text(
                    text = centerPreview,
                    color = labelHi,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
