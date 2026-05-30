package com.vaultix.app.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Color palette ──────────────────────────────────────────────────────────────
private val C_GREEN  = Color(0xFF4AF626)
private val C_RED    = Color(0xFFFF5252)
private val C_YELLOW = Color(0xFFFFB300)
private val C_BLUE   = Color(0xFF2979FF)
private val C_BG     = Color(0xFF080808)
private val C_CARD   = Color(0xFF0F0F0F)
private val C_SURF   = Color(0xFF161616)

private fun categoryColor(cat: DebugCategory): Color = when (cat) {
    DebugCategory.AUTH       -> Color(0xFFAB47BC)
    DebugCategory.NAVIGATION -> Color(0xFF2196F3)
    DebugCategory.CRUD       -> Color(0xFF26C6DA)
    DebugCategory.FILE       -> Color(0xFFFF9800)
    DebugCategory.SECURITY   -> Color(0xFFFF5252)
    DebugCategory.SYSTEM     -> Color(0xFF9E9E9E)
    DebugCategory.CRYPTO     -> Color(0xFFFFEB3B)
}

private fun severityColor(sev: DebugSeverity): Color = when (sev) {
    DebugSeverity.INFO     -> C_GREEN
    DebugSeverity.WARNING  -> C_YELLOW
    DebugSeverity.CRITICAL -> C_RED
}

// ── Main Dialog ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDebuggerDialog(
    events: List<DebugEvent>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context   = LocalContext.current
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchQuery      by remember { mutableStateOf("") }
    var selCategory      by remember { mutableStateOf<DebugCategory?>(null) }
    var selSeverity      by remember { mutableStateOf<DebugSeverity?>(null) }
    var autoScroll       by remember { mutableStateOf(true) }
    var showTimestamp    by remember { mutableStateOf(true) }
    var showSource       by remember { mutableStateOf(true) }
    var expandedEventId  by remember { mutableStateOf<Long?>(null) }

    // ── Derived filtered list ──────────────────────────────────────────────────
    val filtered = remember(events, searchQuery, selCategory, selSeverity) {
        events.filter { e ->
            val cat = selCategory == null || e.category == selCategory
            val sev = selSeverity == null || e.severity == selSeverity
            val txt = searchQuery.isBlank() ||
                    e.eventType.contains(searchQuery, ignoreCase = true) ||
                    e.details.contains(searchQuery, ignoreCase = true) ||
                    e.source.contains(searchQuery, ignoreCase = true) ||
                    e.category.label.contains(searchQuery, ignoreCase = true)
            cat && sev && txt
        }
    }

    // Stats
    val totalInfo  = remember(events) { events.count { it.severity == DebugSeverity.INFO } }
    val totalWarn  = remember(events) { events.count { it.severity == DebugSeverity.WARNING } }
    val totalCrit  = remember(events) { events.count { it.severity == DebugSeverity.CRITICAL } }

    // Auto-scroll on new events
    LaunchedEffect(filtered.size) {
        if (autoScroll && filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.97f)
            .heightIn(max = 680.dp),
        containerColor = C_BG,
        shape = RoundedCornerShape(18.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ── Title row ─────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(C_GREEN.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "● LIVE",
                                color = C_GREEN,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(fontFamily = FontFamily.Monospace)
                            )
                        }
                        Text(
                            text = "DEBUGGER",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFamily = FontFamily.Monospace)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Toggle timestamp
                        IconButton(
                            onClick = { showTimestamp = !showTimestamp },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.AccessTime, null,
                                tint = if (showTimestamp) C_GREEN else Color(0xFF444444),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        // Toggle source
                        IconButton(
                            onClick = { showSource = !showSource },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountTree, null,
                                tint = if (showSource) C_BLUE else Color(0xFF444444),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        // Auto-scroll toggle
                        IconButton(
                            onClick = { autoScroll = !autoScroll },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                if (autoScroll) Icons.Default.PlayArrow else Icons.Default.Pause,
                                null,
                                tint = if (autoScroll) C_GREEN else Color(0xFF444444),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        // Copy all to clipboard
                        IconButton(
                            onClick = {
                                val text = events.joinToString("\n") { e ->
                                    val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(e.timestamp))
                                    "[$t][${e.severity.label}][${e.category.label}][${e.source}] ${e.eventType}: ${e.details}"
                                }
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("vaultix_debug", text))
                                android.widget.Toast.makeText(context, "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF888888), modifier = Modifier.size(15.dp))
                        }
                        // Export / Download logs to file
                        IconButton(
                            onClick = {
                                try {
                                    val text = events.joinToString("\n") { e ->
                                        val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(e.timestamp))
                                        "[$t][${e.severity.label}][${e.category.label}][${e.source}] ${e.eventType}: ${e.details}"
                                    }
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    val fileName = "vaultix_debug_$timestamp.txt"
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                        android.os.Environment.DIRECTORY_DOWNLOADS
                                    )
                                    val file = java.io.File(downloadsDir, fileName)
                                    file.writeText(text)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Logs exported to Downloads/$fileName",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Export failed: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, null, tint = Color(0xFF888888), modifier = Modifier.size(15.dp))
                        }
                        // Close
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── Stats bar ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(C_SURF, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(value = events.size.toString(),   label = "TOTAL", color = Color(0xFF888888))
                    StatDivider()
                    StatItem(value = totalInfo.toString(),     label = "INFO",  color = C_GREEN)
                    StatDivider()
                    StatItem(value = totalWarn.toString(),     label = "WARN",  color = C_YELLOW)
                    StatDivider()
                    StatItem(value = totalCrit.toString(),     label = "CRIT",  color = C_RED)
                    StatDivider()
                    StatItem(value = filtered.size.toString(), label = "SHOWN", color = C_BLUE)
                }

                // ── Category filter (horizontal scroll) ───────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniChip(
                        label = "ALL",
                        color = Color(0xFF888888),
                        selected = selCategory == null
                    ) { selCategory = null }

                    DebugCategory.values().forEach { cat ->
                        val cnt = events.count { it.category == cat }
                        MiniChip(
                            label = "${cat.label} $cnt",
                            color = categoryColor(cat),
                            selected = selCategory == cat
                        ) { selCategory = if (selCategory == cat) null else cat }
                    }
                }

                // ── Severity filter ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniChip("ALL",  Color(0xFF888888), selSeverity == null)        { selSeverity = null }
                    MiniChip("INFO", C_GREEN,           selSeverity == DebugSeverity.INFO)     { selSeverity = if (selSeverity == DebugSeverity.INFO)    null else DebugSeverity.INFO }
                    MiniChip("WARN", C_YELLOW,          selSeverity == DebugSeverity.WARNING)  { selSeverity = if (selSeverity == DebugSeverity.WARNING) null else DebugSeverity.WARNING }
                    MiniChip("CRIT", C_RED,             selSeverity == DebugSeverity.CRITICAL) { selSeverity = if (selSeverity == DebugSeverity.CRITICAL) null else DebugSeverity.CRITICAL }
                }

                // ── Search ────────────────────────────────────────────────────
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search type · details · source...",
                            color = Color(0xFF444444),
                            fontSize = 11.sp,
                            style = TextStyle(fontFamily = FontFamily.Monospace)
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF444444), modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color(0xFF444444), modifier = Modifier.size(14.dp))
                        }}
                    } else null,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = C_SURF,
                        unfocusedContainerColor = C_SURF,
                        focusedBorderColor      = C_GREEN,
                        unfocusedBorderColor    = Color(0xFF2A2A2A)
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        text = {
            // ── Event log ─────────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp),
                color = Color(0xFF040404),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF1E1E1E))
            ) {
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "⬡",
                                color = Color(0xFF222222),
                                fontSize = 32.sp,
                                style = TextStyle(fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                if (events.isEmpty()) "NO EVENTS CAPTURED" else "NO MATCHING EVENTS",
                                color = Color(0xFF333333),
                                fontSize = 11.sp,
                                style = TextStyle(fontFamily = FontFamily.Monospace)
                            )
                            if (searchQuery.isNotEmpty() || selCategory != null || selSeverity != null) {
                                Text(
                                    "← CLEAR FILTERS TO SEE ALL ${events.size} EVENTS",
                                    color = Color(0xFF2A2A2A),
                                    fontSize = 9.sp,
                                    style = TextStyle(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered.size) { index ->
                            val event = filtered[index]
                            val isExpanded = (expandedEventId == event.id)
                            val prevEvent = if (index > 0) filtered[index - 1] else null
                            val elapsedMs = prevEvent?.let { event.timestamp - it.timestamp }
                            EventRow(
                                event = event,
                                showTimestamp = showTimestamp,
                                showSource = showSource,
                                expanded = isExpanded,
                                elapsedMs = elapsedMs,
                                onToggleExpand = {
                                    expandedEventId = if (isExpanded) null else event.id
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                // Clear
                OutlinedButton(
                    onClick = {
                        onClear()
                        searchQuery  = ""
                        selCategory  = null
                        selSeverity  = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = C_RED),
                    border = BorderStroke(1.dp, C_RED.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("CLEAR", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                // Scroll to bottom
                Button(
                    onClick = {
                        scope.launch {
                            if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.size - 1)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = C_SURF, contentColor = C_GREEN),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.VerticalAlignBottom, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("BOTTOM", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                // Close
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = C_SURF, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CLOSE", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    )
}

// ── Event Row ─────────────────────────────────────────────────────────────────

@Composable
private fun EventRow(
    event: DebugEvent,
    showTimestamp: Boolean,
    showSource: Boolean,
    expanded: Boolean,
    elapsedMs: Long?,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val timeStr = remember(event.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(event.timestamp))
    }
    val catColor = categoryColor(event.category)
    val sevColor = severityColor(event.severity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C_CARD, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (expanded) catColor.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onToggleExpand() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Category badge
            Text(
                text = event.category.label,
                color = catColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .background(catColor.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(5.dp))
            // Severity badge
            Text(
                text = event.severity.label,
                color = sevColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .background(sevColor.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(6.dp))
            // Event type
            Text(
                text = event.eventType,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
            
            // Elapsed time indicator
            if (elapsedMs != null && elapsedMs >= 0) {
                Text(
                    text = if (elapsedMs < 1000) "+${elapsedMs}ms" else "+${String.format(Locale.US, "%.1fs", elapsedMs / 1000.0)}",
                    color = Color(0xFF666666),
                    fontSize = 8.sp,
                    style = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Timestamp
            if (showTimestamp) {
                Text(
                    text = timeStr,
                    color = C_GREEN.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
        }

        // ── Details ───────────────────────────────────────────────────────────
        if (event.details.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF060606), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF161616), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = event.details,
                        color = Color(0xFFE0E0E0),
                        fontSize = 10.sp,
                        style = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("event_details", event.details))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = C_GREEN, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Details", color = C_GREEN, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            } else {
                Text(
                    text = event.details,
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
        }

        // ── Source & Meta ─────────────────────────────────────────────────────
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (event.source.isNotEmpty()) {
                    Text(
                        text = "⬡ Source: ${event.source}",
                        color = Color(0xFF666666),
                        fontSize = 8.sp,
                        style = TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
                Text(
                    text = "ID: ${event.id}",
                    color = Color(0xFF444444),
                    fontSize = 8.sp,
                    style = TextStyle(fontFamily = FontFamily.Monospace)
                )
            }
        } else if (showSource && event.source.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "⬡ ${event.source}",
                color = Color(0xFF383838),
                fontSize = 8.sp,
                style = TextStyle(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

// ── Small Composables ─────────────────────────────────────────────────────────

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(fontFamily = FontFamily.Monospace)
        )
        Text(
            label,
            color = Color(0xFF444444),
            fontSize = 8.sp,
            style = TextStyle(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .background(Color(0xFF1E1E1E))
    )
}

@Composable
private fun MiniChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) color.copy(alpha = 0.16f) else Color(0xFF111111),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) color.copy(alpha = 0.45f) else Color(0xFF1E1E1E)
        ),
        modifier = Modifier.height(26.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                label,
                fontSize = 9.sp,
                style = TextStyle(fontFamily = FontFamily.Monospace),
                color = if (selected) color else Color(0xFF555555),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
