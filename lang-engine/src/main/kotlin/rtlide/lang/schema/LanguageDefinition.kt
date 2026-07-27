package rtlide.lang.schema

import kotlinx.serialization.Serializable

/**
 * JSON/YAML-serializable language definition. An extension author ships one of
 * these to teach the IDE a new (Arabic) language: keywords, comments, strings,
 * brackets, indentation, and optional TextMate-style scope patterns.
 */
@Serializable
data class LanguageDefinition(
    val id: String,
    val displayName: String,
    val fileExtensions: List<String> = emptyList(),
    val textDirection: TextDir = TextDir.RTL,
    val grammar: Grammar,
    val indent: IndentRules = IndentRules(),
    val brackets: List<BracketPair> = emptyList(),
    val completion: CompletionConfig = CompletionConfig(),
)

@Serializable
enum class TextDir { RTL, LTR, AUTO }

@Serializable
data class Grammar(
    val keywords: List<String> = emptyList(),        // declaration/storage words: دالة، متغير…
    val controlKeywords: List<String> = emptyList(), // flow control: إذا، طالما، أرجع…
    val builtins: List<String> = emptyList(),        // built-in functions: اطبع، اقرأ…
    val constants: List<String> = emptyList(),       // language constants: صحيح، خطأ، عدم
    val operators: List<String> = listOf("=", "+", "-", "*", "/", "==", "!=", "<", ">", "<=", ">="),
    val lineComment: String = "//",
    val blockComment: BlockComment? = null,
    val strings: List<StringRule> = emptyList(),
    // Matches both ASCII and Arabic-Indic (U+0660..U+0669) digits.
    val numberPattern: String = "[0-9\\u0660-\\u0669]+(\\.[0-9\\u0660-\\u0669]+)?",
    // \p{L} matches Arabic letters, so Arabic identifiers work out of the box.
    val identifierPattern: String = "[\\p{L}_][\\p{L}\\p{N}_]*",
    /** Optional advanced scope rules (first anchored match wins). Compiled with
     *  the Unicode flag so \b behaves correctly around Arabic runs. */
    val patterns: List<PatternRule> = emptyList(),
)

@Serializable
data class BlockComment(val start: String, val end: String)

@Serializable
data class StringRule(val begin: String, val end: String, val escape: String = "\\")

@Serializable
data class PatternRule(
    val name: String,   // scope, e.g. "keyword.control.arabic"
    val match: String,  // regex
)

@Serializable
data class IndentRules(
    val indentTriggers: List<String> = listOf("{"),
    val dedentTriggers: List<String> = listOf("}"),
    val indentSize: Int = 4,
    val useSpaces: Boolean = true,
)

/** mirrorInRtl is informational: Skia mirrors bracket GLYPHS automatically in an
 *  RTL run. Logical matching (see BracketMatcher) is direction-agnostic. */
@Serializable
data class BracketPair(val open: String, val close: String, val mirrorInRtl: Boolean = true)

@Serializable
data class CompletionConfig(
    val triggerCharacters: List<String> = listOf("."),
    val keywordCompletion: Boolean = true,
)

// ── Theming is kept separate from grammar so a grammar is theme-agnostic ──
@Serializable
data class Theme(
    val name: String,
    /** scope name -> style */
    val tokenColors: Map<String, TokenStyle>,
)

@Serializable
data class TokenStyle(
    val color: String,          // "#RRGGBB" or "#AARRGGBB"
    val bold: Boolean = false,
    val italic: Boolean = false,
)
