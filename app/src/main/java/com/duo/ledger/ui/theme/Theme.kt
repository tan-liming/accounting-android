package com.duo.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 白天：暖白底 + 深橙主色，primary 压深是为了让白色文字达到可读对比度。 */
private val LightOrange = lightColorScheme(
    primary = Color(0xFFC14A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3C1200),
    secondary = Color(0xFF93512F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF371400),
    tertiary = Color(0xFF6C5D14),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E15C),
    onTertiaryContainer = Color(0xFF211B00),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF5DED5),
    onSurfaceVariant = Color(0xFF53433D),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF392F2B),
    inverseOnSurface = Color(0xFFFFEDE6),
    inversePrimary = Color(0xFFFFB59A)
)

/** 深色：暖深棕底 + 浅橙主色，不用纯黑，避免夜里刺眼。 */
private val DarkOrange = darkColorScheme(
    primary = Color(0xFFFFB59A),
    onPrimary = Color(0xFF5F1B00),
    primaryContainer = Color(0xFF8F3400),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE7BBA0),
    onSecondary = Color(0xFF4A2100),
    secondaryContainer = Color(0xFF6A3315),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFDBC66A),
    onTertiary = Color(0xFF3A3000),
    tertiaryContainer = Color(0xFF524600),
    onTertiaryContainer = Color(0xFFF8E15C),
    background = Color(0xFF201A17),
    onBackground = Color(0xFFEDE0DA),
    surface = Color(0xFF201A17),
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA08C85),
    outlineVariant = Color(0xFF53433D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFEDE0DA),
    inverseOnSurface = Color(0xFF392F2B),
    inversePrimary = Color(0xFFC14A00)
)

/** 圆角整体放大一档，观感更轻快。 */
private val YoungShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DuoLedgerTheme(content: @Composable () -> Unit) {
    // 固定橙色品牌色，因此不启用 Material You 动态取色；白天/深色仍跟随系统。
    val colors = if (isSystemInDarkTheme()) DarkOrange else LightOrange
    MaterialTheme(
        colorScheme = colors,
        shapes = YoungShapes,
        content = content
    )
}
