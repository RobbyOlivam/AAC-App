package com.robbyolivam.aac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One section of the manual: a heading and its lines (paragraphs and "- " bullets). */
data class ManualSection(val title: String, val lines: List<String>)

/**
 * The instruction manual lives in assets/help/manual.md (one file, kept up to date with the
 * features). "## " starts a section; "- " lines are bullets; **text** is bold.
 */
fun parseManual(markdown: String): List<ManualSection> {
    val sections = mutableListOf<ManualSection>()
    var title: String? = null
    val lines = mutableListOf<String>()
    fun flush() {
        title?.let { sections += ManualSection(it, lines.toList()) }
        lines.clear()
    }
    markdown.lines().forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith("## ") -> { flush(); title = line.removePrefix("## ").trim() }
            line.startsWith("# ") || line.isEmpty() -> Unit
            else -> lines += line
        }
    }
    flush()
    return sections
}

/** "**bold** text" → styled text. */
fun boldMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/** Settings → How to use: start the guided tour, or read the manual (tap a section to open it). */
@Composable
fun HelpScreen(vm: AacViewModel) {
    val sections by produceState(emptyList<ManualSection>()) {
        value = vm.platform.assets.read("help/manual.md")?.let { parseManual(it.decodeToString()) }.orEmpty()
    }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    if (sections.isNotEmpty() && open.isEmpty()) open[sections.first().title] = true

    BackHandler { vm.screen = Screen.SETTINGS }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.screen = Screen.SETTINGS }) {
                Icon(Icons.Default.ArrowBack, null)
                Text("  Back to settings")
            }
            Text("  How to use", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Button(onClick = vm::startTutorial, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text("  Take the tour", fontSize = 18.sp)
                }
            }
            items(sections, key = { it.title }) { section ->
                val expanded = open[section.title] == true
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable { open[section.title] = !expanded }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(section.title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Close" else "Open")
                        }
                        if (expanded) {
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                section.lines.forEach { line ->
                                    if (line.startsWith("- ")) {
                                        Row {
                                            Text("•  ", fontSize = 15.sp)
                                            Text(boldMarkdown(line.removePrefix("- ")), fontSize = 15.sp)
                                        }
                                    } else {
                                        Text(boldMarkdown(line), fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Text("", Modifier.padding(bottom = 24.dp)) }
        }
    }
}
