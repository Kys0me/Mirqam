package rtlide.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import rtlide.terminal.ansi.AnsiParser
import rtlide.terminal.ansi.TermSpan
import rtlide.terminal.pty.ShellProcessBackend
import rtlide.terminal.pty.TerminalBackend

/**
 * Integrated terminal panel. Each logical line is rendered with
 * TextDirection.Content, so a Latin command line stays LTR while an Arabic
 * diagnostic flips RTL — Skia reorders each paragraph from its first strong
 * character, and combined Arabic/Latin lines interleave correctly.
 */
@Composable
fun TerminalPanel(backend: TerminalBackend, modifier: Modifier = Modifier) {
    val parser = remember { AnsiParser() }
    val lines = remember { mutableStateListOf<AnnotatedString>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(backend) {
        lines.clear()
        lines += toAnnotated(parser.feed("\u001B[32m● طرفية RTL\u001B[0m — Arabic/Latin • ANSI colors • Bidi-aware"))
        lines += toAnnotated(parser.feed("اكتب أمرًا واضغط \"تنفيذ\" (مثال: echo مرحبا && ls -la)"))
        
        val sb = StringBuilder()
        backend.output.collect { chunk ->
            for (ch in chunk) {
                when (ch) {
                    '\n' -> { lines += toAnnotated(parser.feed(sb.toString())); sb.clear() }
                    '\r' -> Unit
                    else -> sb.append(ch)
                }
            }
            while (lines.size > 4000) lines.removeAt(0)
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) runCatching { listState.scrollToItem(lines.lastIndex) }
    }

    DisposableEffect(Unit) { onDispose { backend.close() } }

    fun run() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        lines += toAnnotated(parser.feed("\u001B[36m$ \u001B[0m$cmd"))
        backend.write(cmd + "\n")
        input = ""
    }

    Column(modifier.background(IdeColors.GutterBackground).border(1.dp, IdeColors.BorderColor)) {
        Row(
            Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("طرفية", color = IdeColors.TextDefault, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            state = listState,
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    style = LocalTextStyle.current.merge(TextStyle(textDirection = TextDirection.Content)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(IdeColors.TabInactiveBackground).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                singleLine = true,
                placeholder = { Text("أدخل أمرًا…", color = IdeColors.TextMuted) },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Content,
                    color = IdeColors.TextDefault
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { run() }, modifier = Modifier.height(36.dp)) { Text("تنفيذ", fontSize = 12.sp) }
        }
    }
}

private fun toAnnotated(spans: List<TermSpan>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        withStyle(SpanStyle(color = s.fg, background = s.bg, fontWeight = if (s.bold) FontWeight.Bold else null)) {
            append(s.text)
        }
    }
}
