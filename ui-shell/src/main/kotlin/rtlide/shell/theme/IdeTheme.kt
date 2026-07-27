package rtlide.shell.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

import rtlide.core.theme.IdeColors

@Composable
fun IdeTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF007ACC),
            background = Color(0xFF1E1E1E),
            surface = Color(0xFF252526),
            secondary = Color(0xFF37373D),
            onBackground = Color(0xFFD4D4D4),
            onSurface = Color(0xFFD4D4D4),
            surfaceVariant = Color(0xFF2D2D30),
            outline = Color(0xFF3C3C3C)
        )
    } else {
        lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background, contentColor = colors.onBackground) {
            content()
        }
    }
}
