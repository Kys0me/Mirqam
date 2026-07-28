package rtlide.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import rtlide.terminal.pb.TerminalBackend

/**
 * A dead-simple Run console. No terminal emulation, just a line-based output
 * log and a direct input field at the bottom.
 */
@Composable
fun IntegratedTerminalView(
    backend: TerminalBackend,
    modifier: Modifier = Modifier
) {
    val outputLines = remember { mutableStateListOf<AnnotatedString>() }
    var pendingText by remember { mutableStateOf("") }
    var inputFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val isAlive by backend.isAlive.collectAsState()

    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = IdeColors.TextDefault
    )

    // Collect output from the process
    LaunchedEffect(backend) {
        backend.output.collect { chunk ->
            if (chunk == "\u000C") {
                outputLines.clear()
                pendingText = ""
                return@collect
            }
            val text = chunk.replace("\r", "")
            var start = 0
            var index = text.indexOf('\n')
            while (index != -1) {
                val line = pendingText + text.substring(start, index)
                outputLines.add(parseSimpleAnsi(line))
                pendingText = ""
                start = index + 1
                index = text.indexOf('\n', start)
            }
            pendingText += text.substring(start)

            // Auto-scroll to bottom
            if (outputLines.isNotEmpty() || pendingText.isNotEmpty() || inputFieldValue.text.isNotEmpty()) {
                lazyListState.animateScrollToItem((outputLines.size + 1).coerceAtLeast(0))
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier
                .fillMaxSize()
                .background(IdeColors.TabActiveBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isAlive) runCatching { focusRequester.requestFocus() }
                }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                state = lazyListState,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(outputLines) { line ->
                    Text(line, style = textStyle)
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pendingText.isNotEmpty()) {
                            Text(parseSimpleAnsi(pendingText), style = textStyle)
                        }

                        if (isAlive) {
                            TerminalInputField(
                                value = inputFieldValue,
                                onValueChange = { inputFieldValue = it },
                                textStyle = textStyle,
                                focusRequester = focusRequester,
                                onSend = {
                                    val text = inputFieldValue.text
                                    if (text.isNotEmpty()) {
                                        backend.write(text + "\n")
                                        outputLines.add(buildAnnotatedString {
                                            append(pendingText)
                                            withStyle(SpanStyle(color = IdeColors.TextMuted)) {
                                                append(text)
                                            }
                                        })
                                        pendingText = ""
                                        inputFieldValue = TextFieldValue("")
                                    } else {
                                        backend.write("\n")
                                        outputLines.add(parseSimpleAnsi(pendingText))
                                        pendingText = ""
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isAlive) {
        if (isAlive) {
            runCatching { focusRequester.requestFocus() }
        }
    }
}

@Composable
fun TerminalInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textStyle: TextStyle,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onSend()
                    true
                } else false
            },
        textStyle = textStyle,
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { innerTextField ->
            Box(
                Modifier.drawWithContent {
                    drawContent()
                    // Draw block cursor
                    textLayoutResult?.let { layout ->
                        val cursorIndex = value.selection.start
                        val cursorRect = if (cursorIndex < value.text.length) {
                            layout.getBoundingBox(cursorIndex)
                        } else {
                            val cursorRect = layout.getCursorRect(cursorIndex)
                            val isRtl = layout.getParagraphDirection(cursorIndex) == ResolvedTextDirection.Rtl
                            val width = 8.dp.toPx()
                            Rect(
                                left = if (isRtl) cursorRect.left - width else cursorRect.left,
                                top = cursorRect.top,
                                right = if (isRtl) cursorRect.left else cursorRect.left + width,
                                bottom = cursorRect.bottom
                            )
                        }

                        if (isFocused) {
                            drawRect(
                                color = IdeColors.CaretColor,
                                topLeft = Offset(cursorRect.left, cursorRect.top),
                                size = Size(cursorRect.width, cursorRect.height),
                                alpha = 0.7f
                            )
                        } else {
                            // Hollow box when not focused
                            drawRect(
                                color = IdeColors.CaretColor,
                                topLeft = Offset(cursorRect.left, cursorRect.top),
                                size = Size(cursorRect.width, cursorRect.height),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                                alpha = 0.5f
                            )
                        }
                    }
                }
            ) {
                innerTextField()
            }
        },
        onTextLayout = { textLayoutResult = it },
        singleLine = true
    )
}

/**
 * Very basic ANSI parser for compiler colors.
 */
private fun parseSimpleAnsi(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        if (text[i] == '\u001B' && i + 1 < text.length && text[i + 1] == '[') {
            val end = text.indexOf('m', i + 2)
            if (end != -1) {
                val params = text.substring(i + 2, end).split(';').mapNotNull { it.toIntOrNull() }
                // Handle basic 30-37 (foreground) colors
                if (params.contains(31)) pushStyle(SpanStyle(color = Color(0xFFCD3131)))
                else if (params.contains(32)) pushStyle(SpanStyle(color = Color(0xFF0DBC79)))
                else if (params.contains(33)) pushStyle(SpanStyle(color = Color(0xFFE5E510)))
                else if (params.contains(0)) pop()
                
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}
