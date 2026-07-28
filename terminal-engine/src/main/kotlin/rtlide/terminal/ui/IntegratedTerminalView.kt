package rtlide.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = IdeColors.TextDefault
    )

    // Collect output from the process
    LaunchedEffect(backend) {
        val pending = StringBuilder()
        backend.output.collect { chunk ->
            val text = chunk.replace("\r", "")
            var start = 0
            var index = text.indexOf('\n')
            while (index != -1) {
                pending.append(text.substring(start, index))
                outputLines.add(parseSimpleAnsi(pending.toString()))
                pending.setLength(0)
                start = index + 1
                index = text.indexOf('\n', start)
            }
            pending.append(text.substring(start))
            pendingText = pending.toString()
            
            // Auto-scroll to bottom
            if (outputLines.isNotEmpty() || pendingText.isNotEmpty()) {
                lazyListState.animateScrollToItem((outputLines.size + (if (pendingText.isNotEmpty()) 1 else 0)).coerceAtLeast(1))
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier
                .fillMaxSize()
                .background(IdeColors.TabActiveBackground)
                .focusRequester(focusRequester)
                .focusable()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = lazyListState,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(outputLines) { line ->
                    Text(line, style = textStyle)
                }
                if (pendingText.isNotEmpty()) {
                    item {
                        Text(parseSimpleAnsi(pendingText), style = textStyle)
                    }
                }
            }

            // Input area
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .background(IdeColors.TabInactiveBackground)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("> ", style = textStyle, color = IdeColors.TextMuted)
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                if (inputText.isNotEmpty()) {
                                    backend.write(inputText + "\n")
                                    // Echo locally
                                    outputLines.add(buildAnnotatedString {
                                        withStyle(SpanStyle(color = IdeColors.TextMuted)) {
                                            append("> ")
                                            append(inputText)
                                        }
                                    })
                                    inputText = ""
                                }
                                true
                            } else false
                        },
                    textStyle = textStyle,
                    cursorBrush = SolidColor(IdeColors.CaretColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    singleLine = true
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
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
                else if (params.contains(0)) pop() // Simplified reset
                
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}
