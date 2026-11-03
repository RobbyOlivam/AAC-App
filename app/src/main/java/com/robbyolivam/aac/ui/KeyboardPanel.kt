package com.robbyolivam.aac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robbyolivam.aac.core.KeyboardLayout

private val qwerty = listOf("qwertyuiop", "asdfghjkl'", "zxcvbnm,.?")
private val abc = listOf("abcdefghi", "jklmnopqr", "stuvwxyz'", "?!.,")

fun keyboardRows(layout: KeyboardLayout): List<String> = if (layout == KeyboardLayout.QWERTY) qwerty else abc

/** Big-key keyboard for spelling; word completions appear in the prediction row above. */
@Composable
fun KeyboardPanel(
    layout: KeyboardLayout,
    typing: String,
    onKey: (String) -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    isScanned: (String, String) -> Boolean = { _, _ -> false },
) {
    val rows = keyboardRows(layout)
    Column(modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.8f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = typing.ifEmpty { "Type a word…" },
                fontSize = 26.sp,
                color = if (typing.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { ch ->
                    Key(ch.toString(), scanned = isScanned("keys-$r", "key-$ch"), onClick = { onKey(ch.toString()) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Key("space", weight = 3f, scanned = isScanned("keys-actions", "key-space"), onClick = onSpace)
            Key("⌫", weight = 1.2f, scanned = isScanned("keys-actions", "key-delete"), onClick = onBackspace, description = "Delete letter") {
                Icon(Icons.Default.Backspace, contentDescription = "Delete letter")
            }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float = 1f,
    description: String = label,
    scanned: Boolean = false,
    onClick: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (scanned) Modifier.border(5.dp, ScanHighlight, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}
