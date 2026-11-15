package com.robbyolivam.aac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.robbyolivam.aac.core.Board
import com.robbyolivam.aac.core.UsageEvent
import com.robbyolivam.aac.core.UsageLog
import com.robbyolivam.aac.core.UsageReport
import kotlinx.coroutines.launch
import kotlin.math.max

private enum class Period(val label: String, val days: Int?) { WEEK("7 days", 7), MONTH("30 days", 30), ALL("Everything", null) }

/** Usage report from the opt-in log: for parents, teachers and speech therapists. */
@Composable
fun ReportScreen(vm: AacViewModel) {
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val vocabulary by vm.vocabulary.collectAsState()
    var period by remember { mutableStateOf(Period.WEEK) }
    var reloads by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    val events by produceState<List<UsageEvent>?>(null, reloads) {
        value = vm.usage.readAll()
    }
    val device = vm.platform.device
    val report = remember(events, period) { events?.let { buildReport(it, period, device.nowMillis(), device.utcOffsetMillis()) } }
    val exportCsv: (String) -> Unit = { name ->
        scope.launch {
            val ok = runCatching { vm.platform.files.save(name, vm.usage.csv(), "text/csv") }.getOrDefault(false)
            vm.notice = if (ok) "Usage log saved" else "Couldn't save the log"
        }
    }

    BackHandler { vm.screen = Screen.SETTINGS }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.screen = Screen.SETTINGS }) {
                Icon(Icons.Default.ArrowBack, null)
                Text("  Back to settings")
            }
            Text("  Usage report", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val status = when {
                !settings.usageLogging -> "Logging is off. Turn it on in Settings → Usage log to start collecting."
                settings.usagePaused -> "Logging is paused (the person paused it from the main screen)."
                else -> "Logging is on. Everything stays on this device."
            }
            Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p -> FilterChip(period == p, { period = p }, { Text(p.label) }) }
            }
            when {
                report == null -> Text("Loading…")
                report.totalWords == 0 && report.messages == 0 && report.phrasesSpoken == 0 ->
                    Text("Nothing logged in this period yet.", Modifier.padding(vertical = 24.dp))
                else -> ReportBody(report, period, vocabulary.home)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {
                    exportCsv("aac-usage-${UsageLog.isoUtc(device.nowMillis() + device.utcOffsetMillis()).take(10)}.csv")
                }) { Text("Export CSV") }
                if (!confirmClear) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear log…", color = MaterialTheme.colorScheme.error) }
                } else {
                    Button(onClick = {
                        scope.launch {
                            vm.usage.clear()
                            confirmClear = false
                            reloads++
                        }
                    }) { Text("Tap to delete everything") }
                }
            }
            Text(
                "Numbers count words added to the message (taps, typed words, predictions). " +
                    "Average message length counts words in messages spoken with the speak button. " +
                    "“Core” means words from the Core page.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
            )
            Box(Modifier.height(24.dp))
        }
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

private fun buildReport(events: List<UsageEvent>, period: Period, now: Long, offset: Long): UsageReport {
    val from = when (val days = period.days) {
        null -> events.minOfOrNull { it.time } ?: now
        else -> {
            // Local midnight today, then back to the first day of the period.
            val midnight = (now + offset).floorDiv(DAY_MS) * DAY_MS - offset
            midnight - (days - 1) * DAY_MS
        }
    }
    return UsageLog.report(events, from, now + 1, offset)
}

private val WEEKDAYS = listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed") // 1970-01-01 was a Thursday
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Mon 3 Nov" for a day number (days since 1970). */
private fun dayLabel(day: Long): String {
    val iso = UsageLog.isoUtc(day * DAY_MS) // yyyy-mm-dd…
    val month = iso.substring(5, 7).toInt()
    val date = iso.substring(8, 10).toInt()
    return "${WEEKDAYS[day.mod(7)]} $date ${MONTHS[month - 1]}"
}

@Composable
private fun ReportBody(report: UsageReport, period: Period, home: Board) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Words used", "${report.totalWords}", Modifier.weight(1f))
        StatTile("Different words", "${report.uniqueWords}", Modifier.weight(1f))
        StatTile("Active days", "${report.activeDays}", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Messages spoken", "${report.messages}", Modifier.weight(1f))
        StatTile("Avg words / message", if (report.messages == 0) "–" else fmt(report.meanLengthOfUtterance.toFloat(), 1), Modifier.weight(1f))
        StatTile("Core words", "${report.corePercent}%", Modifier.weight(1f))
    }
    if (report.phrasesSpoken > 0) {
        Text("Ready-made phrases and quick-strip buttons spoken: ${report.phrasesSpoken}", fontSize = 14.sp)
    }

    // Long periods show the last 60 days so bars stay readable.
    val days = report.wordsPerDay.takeLast(60)
    if (period != Period.WEEK || days.any { it.second > 0 }) {
        SectionLabel("Words per day")
        DailyBars(days)
    }

    if (report.newWords.isNotEmpty()) {
        SectionLabel("New words (${report.newWords.size})")
        Text(report.newWords.joinToString(", ") { display(it) }, fontSize = 15.sp)
    }

    if (report.topWords.isNotEmpty()) {
        SectionLabel("Most used words")
        TopWords(report.topWords)
    }

    if (home.cells.isNotEmpty()) {
        SectionLabel("Core page: taps per button")
        HeatMap(home, report.buttonTaps)
    }
}

/** The log keeps words lower-case; show "I" (and "I'm"…) the way people write them. */
private fun display(word: String) = if (word == "i" || word.startsWith("i'")) "I" + word.drop(1) else word

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** One series, one hue: bars rise from a shared baseline; the busiest day is labelled. */
@Composable
private fun DailyBars(days: List<Pair<Long, Int>>) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val peak = days.maxOfOrNull { it.second } ?: 0
    fun label(day: Long) = dayLabel(day)
    val busiest = days.maxByOrNull { it.second }
    val summary = days.joinToString("; ") { "${label(it.first)}: ${it.second}" }
    Column(Modifier.semantics { contentDescription = "Words per day. $summary" }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = days.size.coerceAtLeast(1)
            val slot = size.width / n
            val gap = if (slot > 6.dp.toPx()) 2.dp.toPx() else 0f
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            days.forEachIndexed { i, (_, count) ->
                if (count == 0 || peak == 0) return@forEachIndexed
                val h = size.height * count / peak
                drawRoundRect(
                    color,
                    topLeft = Offset(i * slot + gap / 2, size.height - h),
                    size = Size(max(1f, slot - gap), h),
                    cornerRadius = CornerRadius(minOf(4.dp.toPx(), (slot - gap) / 2)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(days.firstOrNull()?.let { label(it.first) }.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            Box(Modifier.weight(1f))
            Text(days.lastOrNull()?.let { label(it.first) }.orEmpty(), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
        if (busiest != null && busiest.second > 0) {
            Text("Busiest day: ${label(busiest.first)} (${busiest.second} words)", fontSize = 13.sp)
        }
    }
}

@Composable
private fun TopWords(words: List<Pair<String, Int>>) {
    val top = words.first().second.coerceAtLeast(1)
    val color = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        words.forEach { (word, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(display(word), Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / top)
                            .background(color, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    )
                }
                Text("$count", Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 13.sp)
            }
        }
    }
}

/** The Core page as a grid shaded light → dark by taps (one hue), with the count written in. */
@Composable
private fun HeatMap(board: Board, taps: Map<Pair<String, Int>, Int>) {
    val peak = (board.cells.indices.maxOfOrNull { taps[board.id to it] ?: 0 } ?: 0).coerceAtLeast(1)
    val hue = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().aspectRatio(board.cols / board.rows.toFloat() * 1.3f)) {
        for (row in 0 until board.rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until board.cols) {
                    val index = row * board.cols + col
                    val cell = board.cells[index]
                    val count = taps[board.id to index] ?: 0
                    val share = count.toFloat() / peak
                    val fill = hue.copy(alpha = 0.08f + 0.8f * share).compositeOver(surface)
                    val textColor = if (share > 0.55f) Color.White else ink
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(1.dp)
                            .background(fill, RoundedCornerShape(4.dp))
                            .semantics { contentDescription = "${cell?.label ?: "empty"}: $count" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cell.label, fontSize = 9.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
