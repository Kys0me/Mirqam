package rtlide.core.theme

import androidx.compose.ui.graphics.Color

object IdeColors {
    val ToolbarBackground = Color(0xFF3C3C3C)
    val StatusbarBackground = Color(0xFF007ACC)
    val GutterBackground = Color(0xFF1E1E1E)
    val GutterForeground = Color(0xFF858585)
    val CaretColor = Color(0xFFAEAFAD)
    val SelectionBackground = Color(0xFF264F78) // editor text selection
    val TabActiveBackground = Color(0xFF1E1E1E)
    val TabInactiveBackground = Color(0xFF2B2D30)
    val BorderColor = Color(0xFF393B40)
    val TextDefault = Color(0xFFDFE1E5)
    val TextMuted = Color(0xFF6F737A)
    val LineHighlight = Color(0xFF2E3136)

    val AccentBlue = Color(0xFF3574F0)
    val GutterSeparator = Color(0xFF43454A)

    // --- Menu-specific additions ---
    val MenuSelectionBackground = Color(0xFF4B6EAF) // hovered/highlighted row in dropdown menus
    val MenuSelectionText = Color(0xFFFFFFFF)        // text color on a highlighted row
    val TextSecondary = Color(0xFF8C8C8C)            // dimmed text, e.g. shortcut hints
    val TextDisabled = Color(0xFF6B6B6B)             // disabled menu items
}