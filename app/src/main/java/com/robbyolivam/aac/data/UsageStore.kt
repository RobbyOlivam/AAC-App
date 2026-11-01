package com.robbyolivam.aac.data

import android.content.Context
import com.robbyolivam.aac.core.UsageEvent
import com.robbyolivam.aac.core.UsageLog
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * The opt-in usage log: one JSON line per event in app storage. It never leaves the device
 * unless someone exports it (it's excluded from Android's cloud backup too).
 */
class UsageStore(context: Context) {
    private val file = File(context.filesDir, "usage/usage.jsonl")
    private val writer = Executors.newSingleThreadExecutor()

    fun append(event: UsageEvent) {
        writer.execute {
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(UsageLog.encode(event) + "\n")
            }
        }
    }

    /** Every event, oldest first. Waits for pending writes so a report includes the last tap. */
    fun readAll(): List<UsageEvent> {
        writer.submit {}.get()
        if (!file.exists()) return emptyList()
        return file.useLines { lines -> lines.mapNotNull(UsageLog::decode).toList() }
    }

    fun exportCsv(output: OutputStream) {
        output.write(UsageLog.toCsv(readAll()).toByteArray())
    }

    fun clear() {
        writer.submit { file.delete() }.get()
    }

    /** Drops events older than [keepDays], so the log can't grow forever. */
    fun prune(keepDays: Int = 400) {
        writer.execute {
            if (!file.exists()) return@execute
            val cutoff = System.currentTimeMillis() - keepDays * 24L * 60 * 60 * 1000
            val lines = file.readLines()
            val kept = lines.filter { line -> UsageLog.decode(line)?.let { it.time >= cutoff } ?: false }
            if (kept.size != lines.size) file.writeText(kept.joinToString("") { "$it\n" })
        }
    }
}
