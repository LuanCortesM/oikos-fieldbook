package com.oikos.fieldbook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OikosOrange = Color(0xFFFF6A00)
private val OikosOrangeHighlight = Color(0xFFFF8A1F)
private val OikosOrangeSoft = Color(0xFFFFB066)

private val OikosDarkColors = darkColorScheme(
    primary = OikosOrange,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF5F2500),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = OikosOrangeHighlight,
    tertiary = OikosOrangeSoft,
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF202027),
    onSurfaceVariant = Color(0xFFB8B8C2),
    outline = Color(0xFF2E2E36),
    error = Color(0xFFFFB4AB)
)

private val OikosLightColors = lightColorScheme(
    primary = OikosOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1CC),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFFE85D00),
    tertiary = OikosOrangeHighlight,
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0F0EF),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFE0E0E0),
    error = Color(0xFFB3261E)
)

private val OikosTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

private val OikosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun OikosTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) OikosDarkColors else OikosLightColors,
        typography = OikosTypography,
        shapes = OikosShapes,
        content = content
    )
}
