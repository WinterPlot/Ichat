package com.userlabs.ichat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.userlabs.ichat.Look

/** Extra brand tokens that Material's ColorScheme has no slot for. */
@Immutable
class IChatColors(
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val inBubble: Color,
    val onInBubble: Color,
    val divider: Color,
    val dark: Boolean,
)

private val LocalIChatColors = staticCompositionLocalOf<IChatColors> {
    error("IChatTheme not provided")
}

object IChat {
    val colors: IChatColors
        @Composable @ReadOnlyComposable get() = LocalIChatColors.current
}

private fun Int.c() = Color(this)

private fun schemeFrom(k: Look): ColorScheme {
    val accent = k.accent.c()
    val onAccent = k.onAccent.c()
    val soft = Look.blend(k.surface, k.accent, if (k.dark) 0.22f else 0.12f).c()
    val base = if (k.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = soft,
        onPrimaryContainer = k.text.c(),
        secondary = k.accent2.c(),
        onSecondary = onAccent,
        background = k.bg.c(),
        onBackground = k.text.c(),
        surface = k.surface.c(),
        onSurface = k.text.c(),
        surfaceVariant = k.surface2.c(),
        onSurfaceVariant = k.text2.c(),
        surfaceTint = accent,
        surfaceContainerLowest = k.bg.c(),
        surfaceContainerLow = k.surface.c(),
        surfaceContainer = k.surface.c(),
        surfaceContainerHigh = k.surface2.c(),
        surfaceContainerHighest = k.surface2.c(),
        outline = k.text2.c(),
        outlineVariant = Look.alpha(k.text, 0x24).c(),
        error = k.danger.c(),
        onError = Color.White,
    )
}

private val IChatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val IChatTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
)

/**
 * The single theme for every Compose screen. It is derived from [Look], the same palette the
 * remaining View-based screens read, so both worlds always show identical colours.
 */
@Composable
fun IChatTheme(look: Look, content: @Composable () -> Unit) {
    val extra = IChatColors(
        accent = look.accent.c(),
        accent2 = look.accent2.c(),
        onAccent = look.onAccent.c(),
        accentSoft = Look.blend(look.surface, look.accent, if (look.dark) 0.22f else 0.12f).c(),
        inBubble = look.inBubble.c(),
        onInBubble = look.inText.c(),
        divider = look.divider.c(),
        dark = look.dark,
    )
    CompositionLocalProvider(LocalIChatColors provides extra) {
        MaterialTheme(
            colorScheme = schemeFrom(look),
            shapes = IChatShapes,
            typography = IChatTypography,
            content = content,
        )
    }
}
