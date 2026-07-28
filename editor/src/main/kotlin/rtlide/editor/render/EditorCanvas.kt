package rtlide.editor.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import rtlide.core.document.Caret
import rtlide.core.document.Document
import rtlide.core.theme.IdeColors
import rtlide.editor.EditorState
import rtlide.editor.EditorTab
import rtlide.editor.intelligence.CompletionList
import rtlide.editor.intelligence.CompletionState
import rtlide.lang.analysis.Diagnostic
import rtlide.lang.analysis.Severity
import rtlide.lang.indent.Brackets
import rtlide.lang.indent.calculateSmartEnter
import rtlide.lang.schema.BracketPair
import rtlide.lang.schema.IndentRules
import rtlide.lang.schema.TextDir
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EditorCanvas(
    state: EditorState,
    tab: EditorTab,
    modifier: Modifier = Modifier,
) {
    val doc = tab.document
    val highlighter = tab.highlighter
    val diagnostics = tab.diagnostics
    val keywords = remember(tab.lang) {
        (tab.lang.grammar.controlKeywords + tab.lang.grammar.keywords + tab.lang.grammar.builtins + tab.lang.grammar.constants)
            .distinct()
    }
    val brackets = tab.lang.brackets
    val indent = tab.lang.indent

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
    // Under the app-wide RTL layout the Row mirrors: the gutter sits on the right
    // and the text canvas starts at x = 0. In LTR the canvas starts after the gutter.
    val canvasLeftPx = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 0f else gutterWidthPx

    val baseStyle = remember(fontSize) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            color = IdeColors.TextDefault,
            textDirection = TextDirection.Content,
        )
    }

    var menuPos by remember { mutableStateOf(Offset.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    
    var showTooltip by remember { mutableStateOf(false) }
    var isTooltipHovered by remember { mutableStateOf(false) }
    var activeTooltipDiags by remember { mutableStateOf<List<Diagnostic>>(emptyList()) }
    var selectedFixIndex by remember { mutableStateOf(0) }
    val hoveredDiags = tab.hoveredDiagnostics

    var lastClickTime by remember { mutableStateOf(0L) }
    var clickCount by remember { mutableStateOf(0) }

    LaunchedEffect(hoveredDiags, isTooltipHovered, tab.instantTooltip) {
        if (hoveredDiags.isNotEmpty()) {
            activeTooltipDiags = hoveredDiags
            selectedFixIndex = 0
            
            if (tab.instantTooltip) {
                showTooltip = true
                tab.instantTooltip = false
            } else if (!showTooltip) {
                // IntelliJ editor tooltip delay (Editor > General: 500 ms)
                delay(500.milliseconds)
                showTooltip = true
            }
        } else if (isTooltipHovered) {
            showTooltip = true
        } else {
            // Short grace period so the mouse can travel into the popup, as in IntelliJ
            delay(300.milliseconds)
            if (tab.hoveredDiagnostics.isEmpty() && !isTooltipHovered) {
                showTooltip = false
                activeTooltipDiags = emptyList()
                tab.instantTooltip = false
            }
        }
    }

    // IntelliJ hides the inspection popup as soon as the editor scrolls or the
    // document is re-analyzed (typing invalidates the hovered diagnostics).
    LaunchedEffect(vscroll.value, diagnostics) {
        if (showTooltip && !isTooltipHovered) {
            showTooltip = false
            activeTooltipDiags = emptyList()
            tab.hoveredDiagnostics = emptyList()
        }
    }

    val allSuggestions = remember(keywords, tab.symbols) {
        (keywords + tab.symbols).distinct()
    }

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

        fun hitTest(pos: Offset, scrolled: Boolean = false): Caret {
            val y = if (scrolled) pos.y else pos.y + vscroll.value
            val line = (y / lineHeightPx).toInt()
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
                    if (tab.activePendingFix != null) return@onPreviewKeyEvent false
                    if (showTooltip && activeTooltipDiags.isNotEmpty()) {
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            showTooltip = false
                            activeTooltipDiags = emptyList()
                            return@onPreviewKeyEvent true
                        }
                        val firstDiagWithFixes = activeTooltipDiags.find { it.fixes.isNotEmpty() }
                        if (firstDiagWithFixes != null) {
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        selectedFixIndex = (selectedFixIndex + 1) % firstDiagWithFixes.fixes.size
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.DirectionUp -> {
                                        selectedFixIndex = (selectedFixIndex - 1 + firstDiagWithFixes.fixes.size) % firstDiagWithFixes.fixes.size
                                        return@onPreviewKeyEvent true
                                    }
                                    Key.Enter -> {
                                        tab.applyQuickFix(firstDiagWithFixes.fixes[selectedFixIndex], firstDiagWithFixes.location, firstDiagWithFixes.length)
                                        showTooltip = false
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                        }
                    }
                    handleKey(event, doc, completion, allSuggestions, indent, brackets, tab.lang.textDirection) { applyCompletion() }
                }
                .verticalScroll(vscroll)
                // Keyed on layouts/diagnostics: the handler must be restarted after
                // analysis or re-layout, otherwise it hit-tests against stale data
                // and hovering an unused symbol never finds its diagnostic.
                .pointerInput(layouts, diagnostics, canvasLeftPx) {
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
                            if (event.type == PointerEventType.Move) {
                                val pos = event.changes.first().position
                                // Adjust hit test by the canvas origin (gutter is on the
                                // right in RTL) to correctly map mouse to text character.
                                // This node sits inside verticalScroll, so the position
                                // is already in content coordinates (scrolled = true).
                                val adjustedPos = Offset(pos.x - canvasLeftPx, pos.y)
                                val caret = hitTest(adjustedPos, scrolled = true)
                                // Hover only reacts to highlighted problems (squiggles),
                                // as in IntelliJ; Information/Hint stay on Alt+Enter.
                                val diags = diagnostics.filter { d ->
                                    (d.severity == Severity.Error || d.severity == Severity.Warning) &&
                                    d.location.line - 1 == caret.line &&
                                    caret.col in (d.location.column - 1)..<(d.location.column - 1 + d.length)
                                }
                                if (diags != tab.hoveredDiagnostics) tab.hoveredDiagnostics = diags
                            }
                            if (event.type == PointerEventType.Exit) {
                                tab.hoveredDiagnostics = emptyList()
                            }
                        }
                    }
                }
        ) {
            Row(Modifier.height(contentHeight).fillMaxWidth()) {
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
                                    val now = System.currentTimeMillis()
                                    if (now - lastClickTime < 300) {
                                        clickCount++
                                    } else {
                                        clickCount = 1
                                    }
                                    lastClickTime = now

                                    when (clickCount) {
                                        2 -> {
                                            val caret = hitTest(pos, scrolled = true)
                                            doc.selectWordAt(caret)
                                        }
                                        3 -> {
                                            val caret = hitTest(pos, scrolled = true)
                                            doc.selectLineAt(caret.line)
                                            clickCount = 0
                                        }
                                        else -> {
                                            doc.caret = hitTest(pos, scrolled = true)
                                            doc.selectionAnchor = null
                                            completion.hide()
                                            showTooltip = false
                                            focus.requestFocus()
                                        }
                                    }
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
                                    doc.selectionAnchor = hitTest(pos, scrolled = true)
                                    doc.caret = doc.selectionAnchor!!
                                    focus.requestFocus()
                                },
                                onDrag = { change, _ ->
                                    doc.caret = hitTest(change.position, scrolled = true)
                                }
                            )
                        }
                ) {
                    val cl = doc.caret.line
                    val currentLineLayout = layouts.getOrNull(cl)
                    if (currentLineLayout != null) {
                        drawRect(
                            color = IdeColors.LineHighlight,
                            topLeft = Offset(0f, cl * lineHeightPx),
                            size = Size(size.width, lineHeightPx)
                        )
                    }

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
                                drawRect(
                                    color = IdeColors.SelectionBackground.copy(alpha = 0.4f),
                                    topLeft = Offset(ox + layout.size.width, ty),
                                    size = Size(10f, lineHeightPx)
                                )
                            }
                        }
                    }

                    layouts.forEachIndexed { i, layout ->
                        drawText(layout, topLeft = Offset(originX(layout), textY(i, layout)))
                    }

                    for (diag in diagnostics) {
                        if (diag.severity == Severity.Information || diag.severity == Severity.Hint) continue
                        
                        val lineIndex = diag.location.line - 1
                        val layout = layouts.getOrNull(lineIndex) ?: continue
                        val lineStr = doc.lineText(lineIndex)
                        val start = diag.location.column - 1
                        val end = (start + diag.length).coerceAtMost(lineStr.length)
                        if (start !in 0..<end) continue

                        val ox = originX(layout)
                        val ty = textY(lineIndex, layout)
                        val color = when (diag.severity) {
                            Severity.Error -> Color.Red
                            Severity.Warning -> Color(0xFFEBCB8B)
                            else -> Color.Gray
                        }

                        val path = layout.getPathForRange(start, end)
                        translate(ox, ty) {
                            val bounds = path.getBounds()
                            val squiggleY = bounds.bottom
                            val squigglePath = Path().apply {
                                moveTo(bounds.left, squiggleY)
                                var x = bounds.left
                                var up = true
                                while (x < bounds.right) {
                                    x += 2f
                                    val y = if (up) squiggleY - 2f else squiggleY + 2f
                                    lineTo(x, y)
                                    up = !up
                                }
                            }
                            drawPath(squigglePath, color = color, style = Stroke(width = 1.5f))
                        }
                    }

                    val layout = layouts.getOrNull(cl)
                    if (layout != null) {
                        val lineStr = doc.lineText(cl)
                        val col = doc.caret.col.coerceIn(0, lineStr.length)
                        val ox = originX(layout)
                        val ty = textY(cl, layout)

                        val match = Brackets.matchOnLine(lineStr, col, brackets)
                        if (match != null && match in lineStr.indices) {
                            val bb = layout.getBoundingBox(match)
                            drawRect(
                                color = Color(0x5539A169),
                                topLeft = Offset(ox + bb.left, ty + bb.top),
                                size = Size(bb.width.coerceAtLeast(2f), bb.height),
                            )
                        }

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

        if (completion.visible) {
            val cl = doc.caret.line.coerceIn(0, layouts.lastIndex.coerceAtLeast(0))
            val layout = layouts.getOrNull(cl)
            val col = doc.caret.col.coerceIn(0, doc.lineText(cl).length)
            val caretX = if (layout != null) canvasLeftPx + originX(layout) + layout.getCursorRect(col).left else canvasLeftPx
            val caretY = cl * lineHeightPx + lineHeightPx - vscroll.value
            Box(
                Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset(caretX.roundToInt(), caretY.roundToInt()) }
            ) {
                CompletionList(completion) { applyCompletion() }
            }
        }

        if (showTooltip && activeTooltipDiags.isNotEmpty()) {
            val primaryDiag = activeTooltipDiags.first()
            val lineIndex = (primaryDiag.location.line - 1).coerceIn(0, layouts.lastIndex.coerceAtLeast(0))
            val layout = layouts.getOrNull(lineIndex)
            val col = (primaryDiag.location.column - 1).coerceIn(0, doc.lineText(lineIndex).length)
            val x = if (layout != null) canvasLeftPx + originX(layout) + layout.getCursorRect(col).left else canvasLeftPx
            val lineTop = lineIndex * lineHeightPx - vscroll.value
            val lineBottom = lineTop + lineHeightPx
            val editorHeightPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.MAX_VALUE
            var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
            
            Box(
                Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset {
                        // Clamp inside the editor; flip above the line when the
                        // popup would not fit below — same policy as IntelliJ.
                        val tx = x.coerceIn(0f, (editorWidthPx - tooltipSize.width).coerceAtLeast(0f))
                        val ty = if (lineBottom + tooltipSize.height > editorHeightPx && lineTop - tooltipSize.height >= 0f) {
                            lineTop - tooltipSize.height
                        } else {
                            lineBottom
                        }
                        IntOffset(tx.roundToInt(), ty.roundToInt())
                    }
                    .onSizeChanged { tooltipSize = it }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Enter, PointerEventType.Move -> isTooltipHovered = true
                                    PointerEventType.Exit -> isTooltipHovered = false
                                }
                            }
                        }
                    }
            ) {
                ProblemTooltip(activeTooltipDiags, selectedFixIndex) { fix, diag -> 
                    tab.applyQuickFix(fix, diag.location, diag.length)
                    showTooltip = false 
                    activeTooltipDiags = emptyList()
                }
            }
        }

        if (tab.activePendingFix != null) {
            val (fix, loc) = tab.activePendingFix!!
            QuickFixDialog(
                value = tab.quickFixInput ?: "",
                onValueChange = { tab.quickFixInput = it },
                onConfirm = { tab.applyQuickFix(fix, loc, tab.activePendingFixLength) },
                onDismiss = { tab.activePendingFix = null; tab.quickFixInput = null }
            )
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
        yield()
        runCatching {
            focus.requestFocus()
        }
    }
}

@Composable
fun QuickFixDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IdeColors.GutterBackground)
                    .border(1.dp, IdeColors.BorderColor, RoundedCornerShape(8.dp))
                    .clickable(enabled = false) {}
                    .padding(16.dp)
            ) {
                Text(
                    "تعيين قيمة ابتدائية",
                    color = IdeColors.TextDefault,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "أدخل القيمة للمتغير:",
                    color = IdeColors.TextMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = IdeColors.TextDefault,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(IdeColors.TextDefault),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value.isNotEmpty()) onConfirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IdeColors.TabInactiveBackground, RoundedCornerShape(4.dp))
                        .border(1.dp, IdeColors.BorderColor, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) {
                                when (it.key) {
                                    Key.Enter -> { 
                                        onConfirm()
                                        true 
                                    }
                                    Key.Escape -> { onDismiss(); true }
                                    else -> false
                                }
                            } else false
                        }
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("إلغاء", color = IdeColors.TextDefault, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = onConfirm,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = IdeColors.StatusbarBackground),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("تأكيد", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
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

/** IntelliJ (New UI, dark) inspection tooltip: severity icon + message,
 *  a full-width separator, then quick fixes as links with the Alt+Enter hint. */
@Composable
fun ProblemTooltip(diags: List<Diagnostic>, selectedIndex: Int, onQuickFix: (rtlide.lang.analysis.QuickFix, Diagnostic) -> Unit) {
    val background = Color(0xFF2B2D30)
    val borderColor = Color(0xFF43454A)
    val separator = Color(0xFF393B40)
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .shadow(8.dp, shape)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .widthIn(min = 200.dp, max = 500.dp)
    ) {
        diags.forEachIndexed { dIndex, diag ->
            if (dIndex > 0) HorizontalDivider(color = separator)
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                SeverityIcon(diag.severity)
                Spacer(Modifier.width(8.dp))
                Text(diag.message, color = Color(0xFFDFE1E5), fontSize = 13.sp, lineHeight = 18.sp)
            }
            
            if (diag.fixes.isNotEmpty()) {
                HorizontalDivider(color = separator)
                Column(Modifier.padding(vertical = 4.dp)) {
                    diag.fixes.forEachIndexed { fIndex, fix ->
                        val isSelected = dIndex == 0 && fIndex == selectedIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Color(0xFF2E436E) else Color.Transparent)
                                .clickable { onQuickFix(fix, diag) }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fix.label, color = Color(0xFF548AF7), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (fIndex == 0) {
                                Spacer(Modifier.width(24.dp))
                                Text("Alt+Enter", color = Color(0xFF6F737A), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** IntelliJ-style severity glyph: red circle for errors, yellow triangle for warnings. */
@Composable
private fun SeverityIcon(severity: Severity) {
    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        when (severity) {
            Severity.Warning -> {
                Canvas(Modifier.fillMaxSize()) {
                    val triangle = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(triangle, Color(0xFFF2C55C))
                }
                Text("!", color = Color(0xFF323232), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            }
            else -> {
                val bg = if (severity == Severity.Error) Color(0xFFDB5C5C) else Color(0xFF548AF7)
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(percent = 50)).background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun refreshCompletion(doc: Document, completion: CompletionState, suggestions: List<String>) {
    val line = doc.lineText(doc.caret.line)
    val end = doc.caret.col.coerceIn(0, line.length)
    var start = end
    while (start > 0 && (line[start - 1].isLetter() || line[start - 1] == '_')) start--
    val prefix = line.substring(start, end)
    if (prefix.isNotEmpty()) {
        completion.show(suggestions.filter { it.startsWith(prefix) && it != prefix }.take(8))
    } else {
        completion.hide()
    }
}

private fun handleKey(
    e: KeyEvent,
    doc: Document,
    completion: CompletionState,
    suggestions: List<String>,
    indent: IndentRules,
    brackets: List<BracketPair>,
    textDirection: TextDir,
    applyCompletion: () -> Unit,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    
    val isShift = e.isShiftPressed
    val isCtrl = e.isCtrlPressed

    if (isCtrl) {
        when (e.key) {
            Key.C -> { doc.copySelection(); return true }
            Key.V -> { doc.paste(); return true }
            Key.X -> { doc.cutSelection(); return true }
            Key.A -> { doc.selectAll(); return true }
            Key.Z -> { doc.undo(); return true }
            Key.Y -> { doc.redo(); return true }
            Key.DirectionLeft -> {
                val delta = if (textDirection == TextDir.RTL) 1 else -1
                doc.moveCaretByWord(delta, isShift)
                return true
            }
            Key.DirectionRight -> {
                val delta = if (textDirection == TextDir.RTL) -1 else 1
                doc.moveCaretByWord(delta, isShift)
                return true
            }
            Key.Backspace -> {
                doc.deleteByWord(-1)
                return true
            }
            Key.Delete -> {
                doc.deleteByWord(1)
                return true
            }
        }
    }

    when (e.key) {
        Key.MoveHome -> {
            doc.moveCaretToLineStart(isShift)
            return true
        }
        Key.MoveEnd -> {
            doc.moveCaretToLineEnd(isShift)
            return true
        }
        Key.Escape -> {
            if (completion.visible) { completion.hide(); return true }
            return false
        }
        Key.Backspace -> {
            if (!doc.hasSelection) {
                val line = doc.lineText(doc.caret.line)
                val c = doc.caret.col
                if (c > 0 && c < line.length) {
                    val b = line[c - 1]
                    val a = line[c]
                    val isPair = brackets.any { it.open == b.toString() && it.close == a.toString() } ||
                        (b == '(' && a == ')') || (b == '[' && a == ']') || (b == '{' && a == '}') ||
                        (b == '"' && a == '"') || (b == '\'' && a == '\'')
                    if (isPair) {
                        doc.deleteForward()
                    }
                }
            }

            val line = doc.lineText(doc.caret.line)
            val before = line.substring(0, doc.caret.col.coerceAtMost(line.length))
            if (before.isNotEmpty() && before.all { it == ' ' || it == '\t' }) {
                val toDelete = if (indent.useSpaces) indent.indentSize else 1
                repeat(toDelete) { doc.backspace() }
            } else {
                doc.backspace()
            }
            refreshCompletion(doc, completion, suggestions)
            return true
        }
        Key.Delete -> {
            doc.deleteForward()
            return true
        }
        Key.Tab -> {
            if (completion.visible) { applyCompletion(); return true }
            doc.insert(if (indent.useSpaces) " ".repeat(indent.indentSize) else "\t"); return true
        }
        Key.Enter -> {
            if (completion.visible) { applyCompletion(); return true }
            val currentLine = doc.lineText(doc.caret.line)
            val startLine = doc.caret.line
            val result = calculateSmartEnter(currentLine, indent, doc.text(), startLine)
            doc.insert(result.text)
            doc.caret = Caret(startLine + result.caretLineOffset, result.caretColOffset)
            completion.hide(); return true
        }
        Key.DirectionUp -> {
            if (completion.visible) { completion.selected = (completion.selected - 1).coerceAtLeast(0); return true }
            doc.moveCaret(-1, 0, isShift); return true
        }
        Key.DirectionDown -> {
            if (completion.visible) {
                completion.selected = (completion.selected + 1).coerceAtMost(completion.items.lastIndex.coerceAtLeast(0)); return true
            }
            doc.moveCaret(1, 0, isShift); return true
        }
        Key.DirectionLeft -> {
            completion.hide()
            val delta = if (textDirection == TextDir.RTL) 1 else -1
            doc.moveCaret(0, delta, isShift)
            return true
        }
        Key.DirectionRight -> {
            completion.hide()
            val delta = if (textDirection == TextDir.RTL) -1 else 1
            doc.moveCaret(0, delta, isShift)
            return true
        }
        else -> {
            val ch = e.awtEventOrNull?.keyChar
            if (ch != null && ch != CHAR_UNDEFINED && !ch.isISOControl()) {
                val line = doc.lineText(doc.caret.line)
                val c = doc.caret.col
                
                // Type-over: if typing the closing character that is already there, just move caret
                if (c < line.length && line[c] == ch && ")]}\"'".contains(ch)) {
                    doc.moveCaret(0, 1, false)
                    return true
                }

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
                refreshCompletion(doc, completion, suggestions)
                return true
            }
            return false
        }
    }
}
