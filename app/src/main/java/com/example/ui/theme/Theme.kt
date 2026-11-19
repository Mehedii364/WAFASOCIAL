package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

private val Context.wafaThemeDataStore: DataStore<Preferences> by preferencesDataStore(name = "wafa_preferences")

class ThemePreferenceRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.applicationContext.wafaThemeDataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: ThemeMode.DARK.name
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.applicationContext.wafaThemeDataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
}

private val WafaDarkColorScheme = darkColorScheme(
    primary = ElectricTeal,
    onPrimary = ElectricTealDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = WarmAmber,
    onSecondary = WarmAmberDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = SkyVerified,
    onTertiary = Color(0xFF062538),
    tertiaryContainer = SkyContainerDark,
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonSurfaceHigh,
    surfaceContainerHigh = CarbonSurfaceHighest,
    outline = HairlineDark,
    outlineVariant = Color(0xFF1E293B),
    error = CoralError,
    onError = Color(0xFF450A0A),
    errorContainer = CoralErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val WafaLightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = PureWhiteSurface,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = DeepAmber,
    onSecondary = PureWhiteSurface,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = SkyAccentLight,
    onTertiary = PureWhiteSurface,
    tertiaryContainer = SkyContainerLight,
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = AlabasterCanvas,
    onBackground = TextPrimaryLight,
    surface = PureWhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = MistSurface,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = PureWhiteSurface,
    surfaceContainerLow = AlabasterCanvas,
    surfaceContainer = MistSurface,
    surfaceContainerHigh = MistSurfaceHigh,
    outline = HairlineLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = CrimsonErrorLight,
    onError = PureWhiteSurface,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

val WafaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WafaSocialTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) WafaDarkColorScheme else WafaLightColorScheme,
        typography = WafaTypography,
        shapes = WafaShapes,
        content = content
    )
}
