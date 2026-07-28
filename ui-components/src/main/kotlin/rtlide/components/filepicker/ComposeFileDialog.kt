package rtlide.components.filepicker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rtlide.core.theme.IdeColors
import java.io.File

/**
 * IntelliJ-style "Select Path" file/folder chooser.
 *
 * Layout mirrors the JetBrains FileChooserDialog:
 *  - a compact title bar
 *  - an editable path field ("Path:") that jumps the tree to a typed location
 *  - a single filesystem tree (root drives + quick-access nodes), each row
 *    22dp tall with a chevron, a folder/file glyph, and the name
 *  - a "Show hidden files" checkbox
 *  - Cancel / OK buttons, right aligned, flat
 *
 * FilePickerState is used for the "current directory" / selection / search
 * concepts; the recursive tree itself walks the filesystem directly so any
 * folder at any depth can be expanded in place, the way IntelliJ's tree does.
 */

private val ROW_HEIGHT = 22.dp
private val INDENT = 14.dp

@Composable
fun ComposeFileDialog(
    state: FilePickerState,
    onDismiss: () -> Unit
) {
    var showHidden by remember { mutableStateOf(false) }
    // Tracks which directories are currently expanded in the tree.
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var pathFieldText by remember(state.currentDirectory) {
        mutableStateOf(state.currentDirectory.absolutePath)
    }
    var pathError by remember { mutableStateOf(false) }

    fun expandChainOf(dir: File) {
        var d: File? = dir
        while (d != null) {
            expanded[d.absolutePath] = true
            d = d.parentFile
        }
    }

    fun commitPath() {
        val raw = pathFieldText.trim()
        val resolved = when {
            raw.isEmpty() -> raw
            raw == "~" -> System.getProperty("user.home")
            raw.startsWith("~/") || raw.startsWith("~\\") ->
                File(System.getProperty("user.home"), raw.substring(2)).path
            else -> raw
        }
        val target = File(resolved)
        when {
            target.isDirectory -> {
                state.navigateTo(target)
                state.selectedFile = null
                pathFieldText = target.absolutePath
                expandChainOf(target)
                pathError = false
            }
            target.isFile -> {
                val parent = target.parentFile
                if (parent != null && parent.isDirectory) {
                    state.navigateTo(parent)
                    state.selectedFile = target
                    expandChainOf(parent)
                    pathError = false
                } else {
                    pathError = true
                }
            }
            else -> pathError = true
        }
    }

    // Make sure the tree opens with the current directory's chain expanded.
    remember(state.currentDirectory) {
        var dir: File? = state.currentDirectory
        while (dir != null) {
            expanded[dir.absolutePath] = true
            dir = dir.parentFile
        }
        true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .height(520.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(IdeColors.GutterBackground)
                .border(1.dp, IdeColors.BorderColor, RoundedCornerShape(3.dp))
                .clickable(enabled = false) {}
        ) {
            TitleBar(onDismiss)
            HorizontalDivider(color = IdeColors.BorderColor)

            PathField(
                value = pathFieldText,
                isError = pathError,
                onValueChange = {
                    pathFieldText = it
                    pathError = false
                },
                onCommit = { commitPath() }
            )

            FilterField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it }
            )

            HorizontalDivider(color = IdeColors.BorderColor)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(IdeColors.GutterBackground)
            ) {
                FileTree(
                    roots = treeRoots(),
                    expanded = expanded,
                    selected = state.selectedFile,
                    filter = state.searchQuery,
                    showHidden = showHidden,
                    onToggleExpand = { file ->
                        val key = file.absolutePath
                        expanded[key] = !(expanded[key] ?: false)
                    },
                    onSelect = { file ->
                        state.selectedFile = file
                        pathFieldText = file.absolutePath
                    },
                    onActivate = { file ->
                        if (file.isDirectory) {
                            val key = file.absolutePath
                            expanded[key] = !(expanded[key] ?: false)
                        } else {
                            state.selectedFile = file
                            state.onFileSelected(file)
                        }
                    }
                )
            }

            HorizontalDivider(color = IdeColors.BorderColor)

            Footer(
                showHidden = showHidden,
                onShowHiddenChange = { showHidden = it },
                selectedName = state.selectedFile?.name,
                onCancel = onDismiss,
                onOk = {
                    val selected = state.selectedFile ?: state.currentDirectory
                    state.onFileSelected(selected)
                }
            )
        }
    }
}

/** Synthetic top-level nodes: user home, desktop, then every filesystem root. */
private fun treeRoots(): List<File> {
    val home = File(System.getProperty("user.home"))
    val desktop = File(home, "Desktop")
    val quick = listOfNotNull(
        home,
        desktop.takeIf { it.isDirectory }
    )
    val drives = File.listRoots()?.toList() ?: emptyList()
    // De-duplicate in case home/desktop happen to sit under a listed root twice.
    return (quick + drives).distinctBy { it.absolutePath }
}

private fun childrenOf(dir: File, showHidden: Boolean): List<File> {
    return try {
        dir.listFiles()
            ?.filter { showHidden || !it.isHidden }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
private fun TitleBar(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "افتح ملف أو مجلد",
            color = IdeColors.TextDefault,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "إغلاق",
            tint = IdeColors.TextMuted,
            modifier = Modifier.size(14.dp).clickable { onDismiss() }
        )
    }
}

@Composable
private fun PathField(
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("المسار:", color = IdeColors.TextMuted, fontSize = 11.sp, modifier = Modifier.width(48.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).height(28.dp),
                singleLine = true,
                isError = isError,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = IdeColors.TextDefault
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = IdeColors.TabInactiveBackground,
                    unfocusedContainerColor = IdeColors.TabInactiveBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorContainerColor = IdeColors.TabInactiveBackground,
                    errorIndicatorColor = Color(0xFFBF616A),
                    focusedTextColor = IdeColors.TextDefault,
                    unfocusedTextColor = IdeColors.TextDefault,
                    errorTextColor = IdeColors.TextDefault
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onCommit() }),
                shape = RoundedCornerShape(2.dp)
            )
        }
        if (isError) {
            Text(
                "المسار غير موجود",
                color = Color(0xFFBF616A),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 48.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("تصفية:", color = IdeColors.TextMuted, fontSize = 11.sp, modifier = Modifier.width(48.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(28.dp),
            singleLine = true,
            placeholder = { Text("اكتب للتصفية…", fontSize = 11.sp, color = IdeColors.TextMuted) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = IdeColors.TextDefault),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = IdeColors.TabInactiveBackground,
                unfocusedContainerColor = IdeColors.TabInactiveBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = IdeColors.TextDefault,
                unfocusedTextColor = IdeColors.TextDefault
            ),
            shape = RoundedCornerShape(2.dp)
        )
    }
}

/** How deep a filter search will recurse into an unexpanded folder looking for a match. */
private const val FILTER_MAX_DEPTH = 12

@Composable
private fun FileTree(
    roots: List<File>,
    expanded: Map<String, Boolean>,
    selected: File?,
    filter: String,
    showHidden: Boolean,
    onToggleExpand: (File) -> Unit,
    onSelect: (File) -> Unit,
    onActivate: (File) -> Unit
) {
    // Flatten the tree into a single list of (file, depth) pairs.
    //  - No filter: respects the user's manual expand/collapse state.
    //  - Filtering: only branches that contain a match survive, and every
    //    directory on the path to a match is auto-expanded so results are
    //    always visible without the user hand-expanding folders.
    val rows = remember(roots, expanded.toMap(), filter, showHidden) {
        buildFilteredRows(roots, expanded, filter, showHidden)
    }

    if (filter.isNotBlank() && rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد نتائج مطابقة", color = IdeColors.TextMuted, fontSize = 12.sp)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.first.absolutePath }) { (file, depth) ->
            TreeRow(
                file = file,
                depth = depth,
                isExpanded = (filter.isNotBlank() && file.isDirectory) || expanded[file.absolutePath] == true,
                isSelected = selected == file,
                filter = filter,
                onToggleExpand = { onToggleExpand(file) },
                onClick = { onSelect(file) },
                onDoubleClick = { onActivate(file) }
            )
        }
    }
}

/** True if [file]'s own name matches [filter], or any descendant within [FILTER_MAX_DEPTH] does. */
private fun matchesSubtree(file: File, filter: String, showHidden: Boolean, depth: Int): Boolean {
    if (file.name.contains(filter, ignoreCase = true)) return true
    if (!file.isDirectory || depth >= FILTER_MAX_DEPTH) return false
    return childrenOf(file, showHidden).any { matchesSubtree(it, filter, showHidden, depth + 1) }
}

private fun buildFilteredRows(
    roots: List<File>,
    expanded: Map<String, Boolean>,
    filter: String,
    showHidden: Boolean
): List<Pair<File, Int>> {
    val result = mutableListOf<Pair<File, Int>>()
    // Quick-access roots (e.g. "Desktop") can also be literal children of
    // another root (e.g. "home"). Track what's already been rendered so a
    // folder never appears twice in the flattened list — LazyColumn requires
    // unique keys, and duplicates would also just be confusing to look at.
    val visited = mutableSetOf<String>()
    fun walk(file: File, depth: Int) {
        val path = file.absolutePath
        if (!visited.add(path)) return
        if (filter.isNotBlank()) {
            if (!matchesSubtree(file, filter, showHidden, depth)) {
                visited.remove(path)
                return
            }
            result.add(file to depth)
            if (file.isDirectory) {
                childrenOf(file, showHidden).forEach { walk(it, depth + 1) }
            }
        } else {
            result.add(file to depth)
            if (file.isDirectory && expanded[path] == true) {
                childrenOf(file, showHidden).forEach { walk(it, depth + 1) }
            }
        }
    }
    roots.forEach { walk(it, 0) }
    return result
}

/** Bolds the first case-insensitive occurrence of [filter] within [name]. */
private fun highlightMatch(name: String, filter: String) = buildAnnotatedString {
    if (filter.isBlank()) {
        append(name)
        return@buildAnnotatedString
    }
    val idx = name.indexOf(filter, ignoreCase = true)
    if (idx < 0) {
        append(name)
    } else {
        append(name.substring(0, idx))
        withStyle(SpanStyle(color = Color(0xFFFFC66D), fontWeight = FontWeight.Bold)) {
            append(name.substring(idx, idx + filter.length))
        }
        append(name.substring(idx + filter.length))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    file: File,
    depth: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    filter: String,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (isSelected) IdeColors.SelectionBackground else Color.Transparent)
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .padding(start = INDENT * depth + 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse chevron — only clickable/visible for directories.
        Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            if (file.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IdeColors.TextMuted,
                    modifier = Modifier.size(14.dp).clickable { onToggleExpand() }
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (file.isDirectory) Color(0xFFEBCB8B) else IdeColors.TextMuted,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        Spacer(Modifier.width(6.dp))

        Text(
            text = highlightMatch(file.name.ifEmpty { file.absolutePath }, filter),
            color = if (isSelected) Color.White else IdeColors.TextDefault,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Footer(
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    selectedName: String?,
    onCancel: () -> Unit,
    onOk: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = showHidden,
            onCheckedChange = onShowHiddenChange,
            colors = CheckboxDefaults.colors(checkedColor = IdeColors.StatusbarBackground),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text("إظهار الملفات المخفية", color = IdeColors.TextMuted, fontSize = 11.sp)

        Spacer(Modifier.weight(1f))

        Text(
            selectedName ?: "",
            color = IdeColors.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp)
        )

        Spacer(Modifier.width(8.dp))

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            shape = RoundedCornerShape(2.dp)
        ) {
            Text("إلغاء", color = IdeColors.TextDefault, fontSize = 12.sp)
        }

        Spacer(Modifier.width(6.dp))

        Button(
            onClick = onOk,
            colors = ButtonDefaults.buttonColors(containerColor = IdeColors.StatusbarBackground),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            shape = RoundedCornerShape(2.dp)
        ) {
            Text("موافق", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}