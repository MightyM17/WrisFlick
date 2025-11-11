package com.example.wristtype.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

private val DarkColors = Colors(
    primary = Color(0xFF9BE7FF),
    onPrimary = Color.Black,
    secondary = Color(0xFF80CBC4),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White
)

@Composable
fun WristTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColors,
        typography = Typography(),   // default is fine for now
        content = content
    )
}
