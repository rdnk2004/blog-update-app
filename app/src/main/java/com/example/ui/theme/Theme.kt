package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val WarmEditorialLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracottaPrimary,
    primaryContainer = SoftApricotContainer,
    onPrimaryContainer = DeepTerracottaOnContainer,
    secondary = SagePublishedText,
    onSecondary = OnTerracottaPrimary,
    secondaryContainer = SagePublishedBg,
    onSecondaryContainer = SagePublishedText,
    tertiary = OchreDraftText,
    onTertiary = OnTerracottaPrimary,
    tertiaryContainer = OchreDraftBg,
    onTertiaryContainer = OchreDraftText,
    background = WarmCreamBackground,
    onBackground = EspressoInk,
    surface = WarmPaperSurface,
    onSurface = EspressoInk,
    surfaceVariant = SoftLinenSurfaceVariant,
    onSurfaceVariant = WarmUmberMuted,
    outline = WarmBorderOutline,
    outlineVariant = SubtleDivider,
    error = CrimsonError,
    onError = OnCrimsonError,
    errorContainer = SoftRoseErrorBg,
    onErrorContainer = DeepCrimsonOnContainer
)

private val EditorialShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Calm, warm, high-contrast light editorial theme for My Blog Desk.
 * Light theme only by design to ensure maximum paper-like readability.
 */
@Composable
fun MyBlogDeskTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WarmEditorialLightColorScheme,
        typography = Typography,
        shapes = EditorialShapes,
        content = content
    )
}
