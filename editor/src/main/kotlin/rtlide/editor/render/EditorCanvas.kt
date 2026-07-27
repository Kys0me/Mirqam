package rtlide.editor.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.yield
import rtlide.core.document.Caret
import rtlide.core.document.Document
import rtlide.core.theme.IdeColors
import rtlide.editor.EditorState
import rtlide.editor.intelligence.CompletionList
import rtlide.editor.intelligence.CompletionState
import rtlide.lang.highlight.Highlighter
import rtlide.lang.indent.Brackets
import rtlide.lang.indent.newlineIndent
import rtlide.lang.schema.BracketPair
import rtlide.lang.schema.IndentRules
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import kotlin.math.roundToInt

/**
 * Custom RTL code editor. Each logical line is laid out once with a TextMeasurer;
 * ALL geometry (caret x, hit-testing, bracket boxes) is read back from the
 * resulting TextLayoutResult, which has already resolved the Bidi runs. Lines are
 * drawn hugging the right edge (origin computed per line) so the editor reads
 * right-to-left while mixed Arabic/Latin content stays internally correct.
 */
@Composable
fun EditorCanvas(
    state: EditorState,
    doc: Document,
    highlighter: Highlighter,
    keywords: List<String>,
    brackets: List<BracketPair>,
    indent: IndentRules,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer: TextMeasurer = rememberTextMeasurer()
    val focus = remember { FocusRequester() }
    val vscroll = rememberScrollState()
    val completion = remember { CompletionState() }

    val fontSize = state.fontSize
    val lineHeight = (fontSize * 1.6f).dp
    val lineHeightPx = with(density) { lineHeight.toPx() }
    val gutterWidth = (fontSize * 4f).dp
    val gutterWidthPx = with(density) { gutterWidth.toPx() }

    val baseStyle = remember(fontSize) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            color = IdeColors.TextDefault,
            // Resolve base direction per line from its first strong character.
            textDirection = TextDirection.Content,
        )
    }

    var menuPos by remember { mutableStateOf(Offset.Zero) }
    var showMenu by remember { mutableStateOf(false) }

    // Insert the currently-selected completion (only the un-typed remainder).
    fun applyCompletion() {
        val item = completion.items.getOrNull(completion.selected) ?: return
        val line = doc.lineText(doc.caret.line)
        val end = doc.caret.col.coerceIn(0, line.length)
        var start = end
        while (start > 0 && (line[start - 1].isLetter() || line[start - 1] == '_')) start--
        val prefix = line.substring(start, end)
        doc.insert(if (item.length >= prefix.length) item.substring(prefix.length) else item)
        completion.hide()
    }

    BoxWithConstraints(modifier.background(Color(0xFF1E1E1E))) {
        val editorWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 1200f
        val canvasWidthPx = (editorWidthPx - gutterWidthPx).coerceAtLeast(100f)

        // Cache one layout per line; recompute only when text / theme / width change.
        val layouts: List<TextLayoutResult> = remember(doc.lines, highlighter.version, canvasWidthPx, fontSize) {
            doc.lines.map { line ->
                measurer.measure(
                    text = highlighter.highlight(line),
                    style = baseStyle,
                    softWrap = false,
                    maxLines = 1,
                    layoutDirection = LayoutDirection.Rtl,
                )
            }
        }

        val contentHeight = lineHeight * doc.lines.size.coerceAtLeast(1)

        fun originX(layout: TextLayoutResult): Float = canvasWidthPx - layout.size.width
        fun textY(lineIndex: Int, layout: TextLayoutResult): Float =
            lineIndex * lineHeightPx + ((lineHeightPx - layout.size.height) / 2f).coerceAtLeast(0f)

        fun hitTest(pos: Offset): Caret {
            val line = (pos.y / lineHeightPx).toInt()
                .coerceIn(0, (doc.lines.size - 1).coerceAtLeast(0))
            val layout = layouts.getOrNull(line)
            val col = if (layout != null) {
                val localX = pos.x - originX(layout)
                layout.getOffsetForPosition(Offset(localX, layout.size.height / 2f))
            } else 0
            return Caret(line, col.coerceIn(0, doc.lineText(line).length))
        }

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    handleKey(event, doc, completion, keywords, indent) { applyCompletion() }
                }
                .verticalScroll(vscroll)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                                val delta = event.changes.first().scrollDelta.y
                                if (delta != 0f) {
                                    state.zoom(delta)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
        ) {
            Row(Modifier.height(contentHeight).fillMaxWidth()) {
                // Gutter first => right side in RTL.
                Gutter(
                    count = doc.lines.size,
                    caretLine = doc.caret.line,
                    lineHeight = lineHeight,
                    width = gutterWidth,
                    fontSize = fontSize
                )

                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerHoverIcon(PointerIcon.Text)
                        .pointerInput(layouts, canvasWidthPx) {
                            detectTapGestures(
                                onTap = { pos ->
                                    doc.caret = hitTest(pos)
                                    doc.selectionAnchor = null
                                    completion.hide()
                                    focus.requestFocus()
                                }
                            )
                        }
                        .pointerInput(layouts, canvasWidthPx) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                        menuPos = event.changes.first().position
                                        showMenu = true
                                    }
                                }
                            }
                        }
                        .pointerInput(layouts, canvasWidthPx) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    doc.selectionAnchor = hitTest(pos)
                                    doc.caret = doc.selectionAnchor!!
                                    focus.requestFocus()
                                },
                                onDrag = { change, _ ->
                                    doc.caret = hitTest(change.position)
                                }
                            )
                        }
                ) {
                    // 0) Current Line Highlight
                    val cl = doc.caret.line
                    val currentLineLayout = layouts.getOrNull(cl)
                    if (currentLineLayout != null) {
                        drawRect(
                            color = IdeColors.LineHighlight,
                            topLeft = Offset(0f, cl * lineHeightPx),
                            size = Size(size.width, lineHeightPx)
                        )
                    }

                    // 1) Selection
                    val range = doc.getSelectionRange()
                    if (range != null) {
                        val (start, end) = range
                        for (i in start.line..end.line) {
                            val layout = layouts.getOrNull(i) ?: continue
                            val ox = originX(layout)
                            val ty = textY(i, layout)
                            val lineLen = doc.lineText(i).length
                            val s = if (i == start.line) start.col else 0
                            val e = if (i == end.line) end.col else lineLen
                            if (s < e) {
                                val path = layout.getPathForRange(s, e)
                                translate(ox, ty) {
                                    drawPath(path, color = IdeColors.SelectionBackground.copy(alpha = 0.4f))
                                }
                            } else if (i != end.line) {
                                // Empty line or end of line selection
                                drawRect(
                                    color = IdeColors.SelectionBackground.copy(alpha = 0.4f),
                                    topLeft = Offset(ox + layout.size.width, ty),
                                    size = Size(10f, lineHeightPx)
                                )
                            }
                        }
                    }

                    // 1) draw each highlighted, Bidi-shaped line, right-aligned.
                    layouts.forEachIndexed { i, layout ->
                        drawText(layout, topLeft = Offset(originX(layout), textY(i, layout)))
                    }

                    // 2) caret + matching-bracket highlight for the caret line.
                    val layout = layouts.getOrNull(cl)
                    if (layout != null) {
                        val lineStr = doc.lineText(cl)
                        val col = doc.caret.col.coerceIn(0, lineStr.length)
                        val ox = originX(layout)
                        val ty = textY(cl, layout)

                        // Matching bracket: logical scan -> Bidi-correct bounding box.
                        val match = Brackets.matchOnLine(lineStr, col, brackets)
                        if (match != null && match in lineStr.indices) {
                            val bb = layout.getBoundingBox(match)
                            drawRect(
                                color = Color(0x5539A169),
                                topLeft = Offset(ox + bb.left, ty + bb.top),
                                size = Size(bb.width.coerceAtLeast(2f), bb.height),
                            )
                        }

                        // Caret: never sum glyph widths — ask the layout.
                        val cr = layout.getCursorRect(col)
                        drawLine(
                            color = Color(0xFFAEAFAD),
                            start = Offset(ox + cr.left, ty),
                            end = Offset(ox + cr.left, ty + layout.size.height),
                            strokeWidth = 2f,
                        )
                    }
                }
            }
        }

        // Completion popup, anchored to the (Bidi-correct) caret position.
        if (completion.visible) {
            val cl = doc.caret.line.coerceIn(0, layouts.lastIndex.coerceAtLeast(0))
            val layout = layouts.getOrNull(cl)
            val col = doc.caret.col.coerceIn(0, doc.lineText(cl).length)
            val caretX = if (layout != null) originX(layout) + layout.getCursorRect(col).left else 0f
            val caretY = cl * lineHeightPx + lineHeightPx - vscroll.value
            Box(
                Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset(caretX.roundToInt(), caretY.roundToInt()) }
            ) {
                CompletionList(completion) { applyCompletion() }
            }
        }

        if (showMenu) {
            Box(
                Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset {
                        IntOffset(
                            menuPos.x.roundToInt(),
                            (menuPos.y - vscroll.value).roundToInt()
                        )
                    }
            ) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(IdeColors.TabInactiveBackground).border(1.dp, IdeColors.BorderColor)
                ) {
                    IdeMenuItem("قص", onClick = { doc.cutSelection(); showMenu = false })
                    IdeMenuItem("نسخ", onClick = { doc.copySelection(); showMenu = false })
                    IdeMenuItem("لصق", onClick = { doc.paste(); showMenu = false })
                    HorizontalDivider(color = IdeColors.BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    IdeMenuItem("تحديد الكل", onClick = { doc.selectAll(); showMenu = false })
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Wait until the FocusRequester is attached to the layout.
        yield()
        runCatching {
            focus.requestFocus()
        }
    }
}

@Composable
private fun IdeMenuItem(text: String, onClick: () -> Unit = {}) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 13.sp, color = IdeColors.TextDefault) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(
            textColor = IdeColors.TextDefault,
        )
    )
}

private fun refreshCompletion(doc: Document, completion: CompletionState, keywords: List<String>) {
    val line = doc.lineText(doc.caret.line)
    val end = doc.caret.col.coerceIn(0, line.length)
    var start = end
    while (start > 0 && (line[start - 1].isLetter() || line[start - 1] == '_')) start--
    val prefix = line.substring(start, end)
    if (prefix.isNotEmpty()) {
        completion.show(keywords.filter { it.startsWith(prefix) && it != prefix }.take(8))
    } else {
        completion.hide()
    }
}

/**
 * Key handling. Logical semantics only: arrows move the logical column/line and
 * the layout decides where that lands visually, so navigation feels natural in
 * mixed Arabic/Latin runs.
 *
 * NOTE: character input reads awt keyChar, which is fine for direct Arabic
 * keyboard layouts. For robust IME / complex-script composition, route input
 * through a platform text-input session (see README).
 */
private fun handleKey(
    e: KeyEvent,
    doc: Document,
    completion: CompletionState,
    keywords: List<String>,
    indent: IndentRules,
    applyCompletion: () -> Unit,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    
    if (e.isCtrlPressed) {
        when (e.key) {
            Key.C -> { doc.copySelection(); return true }
            Key.V -> { doc.paste(); return true }
            Key.X -> { doc.cutSelection(); return true }
            Key.A -> { doc.selectAll(); return true }
        }
    }

    when (e.key) {
        Key.Escape -> {
            if (completion.visible) { completion.hide(); return true }
            return false
        }
        Key.Backspace -> {
            doc.backspace(); refreshCompletion(doc, completion, keywords); return true
        }
        Key.Tab -> {
            if (completion.visible) { applyCompletion(); return true }
            doc.insert(if (indent.useSpaces) " ".repeat(indent.indentSize) else "\t"); return true
        }
        Key.Enter -> {
            if (completion.visible) { applyCompletion(); return true }
            doc.insert(newlineIndent(doc.lineText(doc.caret.line), indent))
            completion.hide(); return true
        }
        Key.DirectionUp -> {
            if (completion.visible) { completion.selected = (completion.selected - 1).coerceAtLeast(0); return true }
            doc.moveCaret(-1, 0, e.isShiftPressed); return true
        }
        Key.DirectionDown -> {
            if (completion.visible) {
                completion.selected = (completion.selected + 1).coerceAtMost(completion.items.lastIndex.coerceAtLeast(0)); return true
            }
            doc.moveCaret(1, 0, e.isShiftPressed); return true
        }
        Key.DirectionLeft -> { completion.hide(); doc.moveCaret(0, -1, e.isShiftPressed); return true }
        Key.DirectionRight -> { completion.hide(); doc.moveCaret(0, 1, e.isShiftPressed); return true }
        else -> {
            val ch = e.awtEventOrNull?.keyChar
            if (ch != null && ch != CHAR_UNDEFINED && !ch.isISOControl()) {
                val autoClose = when (ch) {
                    '(' -> ")"
                    '[' -> "]"
                    '{' -> "}"
                    '"' -> "\""
                    '\'' -> "'"
                    else -> null
                }
                if (autoClose != null) {
                    doc.insert(ch.toString() + autoClose)
                    doc.moveCaret(0, -1, false)
                } else {
                    doc.insert(ch.toString())
                }
                refreshCompletion(doc, completion, keywords)
                return true
            }
            return false
        }
    }
}
