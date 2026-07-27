package rtlide.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import rtlide.terminal.ansi.AnsiParser
import rtlide.terminal.ansi.TermSpan
import rtlide.terminal.pty.TerminalBackend

@Composable
fun TerminalTabItem(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .background(if (active) IdeColors.TabActiveBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = if (active) IdeColors.TextDefault else IdeColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.Close,
            contentDescription = "Close",
            tint = IdeColors.TextMuted,
            modifier = Modifier.size(12.dp).clickable { onClose() }
        )
    }
}

@Composable
fun TerminalView(backend: TerminalBackend, modifier: Modifier = Modifier) {
    val parser = remember(backend) { AnsiParser() }
    val lines = remember(backend) { mutableStateListOf<AnnotatedString>() }
    var pendingLine by remember(backend) { mutableStateOf(AnnotatedString("")) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(backend) {
        lines.clear()
        pendingLine = AnnotatedString("")
        backend.output.collect { chunk ->
            val result = parser.process(chunk)
            if (result.cleared) {
                lines.clear()
            }
            result.completedLines.forEach { lineSpans ->
                lines.add(toAnnotated(lineSpans))
            }
            pendingLine = toAnnotated(parser.getLineSpans())

            while (lines.size > 4000) lines.removeAt(0)
        }
    }

    LaunchedEffect(lines.size, pendingLine) {
        if (lines.isNotEmpty() || pendingLine.isNotEmpty()) {
            listState.animateScrollToItem(
                if (pendingLine.isNotEmpty()) lines.size else (lines.size - 1).coerceAtLeast(
                    0
                )
            )
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val text = when (event.key) {
                        Key.Enter -> "\n"
                        Key.Backspace -> "\u007F"
                        Key.Tab -> "\t"
                        Key.Escape -> "\u001B"
                        else -> {
                            val c = event.utf16CodePoint.toChar()
                            if (c.code != 0) c.toString() else null
                        }
                    }
                    if (text != null) {
                        backend.write(text)
                        true
                    } else false
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            state = listState,
        ) {
            items(lines) { line ->
                TerminalLine(line)
            }
            item {
                TerminalLine(pendingLine)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun TerminalLine(line: AnnotatedString) {
    if (line.isEmpty()) return
    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        style = LocalTextStyle.current.merge(
            TextStyle(
                textDirection = TextDirection.Content,
                textAlign = TextAlign.Start
            )
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun toAnnotated(spans: List<TermSpan>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        withStyle(
            SpanStyle(
                color = s.fg,
                background = s.bg,
                fontWeight = if (s.bold) FontWeight.Bold else null
            )
        ) {
            append(s.text)
        }
    }
}
