package com.robbyolivam.aac.core

/**
 * A small read-only reader for SQLite database files, in plain Kotlin so it runs on every
 * platform (Android, iOS, web). It reads whole tables; that is all importers need.
 *
 * Supports the standard file format: table b-trees, overflow pages, UTF-8 text and
 * `INTEGER PRIMARY KEY` row ids. Indexes, WAL files and writing are not supported.
 */
class SqliteReader(private val data: ByteArray) {
    private val pageSize: Int
    private val usable: Int
    private val tables: Map<String, Pair<Int, List<String>>>
    private val rowIdColumns = HashMap<String, String>()

    init {
        require(data.size >= 100 && data.decodeToString(0, 15) == "SQLite format 3") { "Not an SQLite database" }
        val raw = u16(16)
        pageSize = if (raw == 1) 65536 else raw
        usable = pageSize - (data[20].toInt() and 0xFF)
        require(u32(56).let { it == 0 || it == 1 }) { "Only UTF-8 databases are supported" }
        val master = HashMap<String, Pair<Int, List<String>>>()
        scan(1) { _, values ->
            if (values.getOrNull(0) == "table") {
                val name = values[1] as String
                val root = (values[3] as Long).toInt()
                master[name.lowercase()] = root to columnsOf(values[4] as? String ?: "")
            }
        }
        tables = master
    }

    fun hasTable(name: String): Boolean = name.lowercase() in tables

    /** Every row of [table] as column name → value (Long, Double, String, ByteArray or null). */
    fun rows(table: String): List<Map<String, Any?>> {
        val (root, columns) = tables[table.lowercase()] ?: return emptyList()
        if (root <= 0) return emptyList() // virtual tables have no b-tree
        val rowIdColumn = rowIdColumns[table.lowercase()]
        val out = ArrayList<Map<String, Any?>>()
        scan(root) { rowId, values ->
            val row = HashMap<String, Any?>(columns.size * 2)
            columns.forEachIndexed { i, c -> row[c] = values.getOrNull(i) }
            if (rowIdColumn != null) row[rowIdColumn] = rowId
            out += row
        }
        return out
    }

    /** Column names from a CREATE TABLE statement; remembers the INTEGER PRIMARY KEY column. */
    private fun columnsOf(sql: String): List<String> {
        val open = sql.indexOf('(')
        val close = sql.lastIndexOf(')')
        if (open < 0 || close <= open) return emptyList()
        val tableName = unquote(sql.substring(0, open).trim().substringAfterLast(' ')).lowercase()
        val parts = ArrayList<String>()
        var depth = 0
        var start = open + 1
        for (i in open + 1 until close) {
            when (sql[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) { parts += sql.substring(start, i); start = i + 1 }
            }
        }
        parts += sql.substring(start, close)
        val columns = ArrayList<String>()
        for (part in parts) {
            val def = part.trim()
            val first = def.substringBefore(' ').substringBefore('\n').uppercase()
            if (first in setOf("UNIQUE", "PRIMARY", "CHECK", "FOREIGN", "CONSTRAINT") || def.isEmpty()) continue
            val name = unquote(def.split(Regex("\\s+"), limit = 2)[0])
            columns += name
            val rest = def.uppercase()
            if (rest.contains(" INTEGER") && rest.contains("PRIMARY KEY")) rowIdColumns[tableName] = name
        }
        return columns
    }

    private fun unquote(s: String) = s.trim().removeSurrounding("\"").removeSurrounding("[", "]").removeSurrounding("`").removeSurrounding("'")

    private fun scan(page: Int, onRow: (rowId: Long, values: List<Any?>) -> Unit) {
        val pending = ArrayDeque<Int>()
        pending.addLast(page)
        var visited = 0
        while (pending.isNotEmpty()) {
            val p = pending.removeFirst()
            require(++visited < data.size / pageSize + 10) { "Damaged database (page loop)" }
            val base = (p - 1).toLong() * pageSize
            require(base + pageSize <= data.size) { "Damaged database (page $p out of range)" }
            val header = base.toInt() + if (p == 1) 100 else 0
            val type = data[header].toInt() and 0xFF
            val cellCount = u16(header + 3)
            when (type) {
                0x05 -> { // interior table page
                    for (i in 0 until cellCount) pending.addLast(u32(base.toInt() + u16(header + 12 + 2 * i)))
                    pending.addLast(u32(header + 8))
                }
                0x0D -> for (i in 0 until cellCount) readLeafCell(base.toInt() + u16(header + 8 + 2 * i), onRow)
                else -> error("Damaged database (page type $type)")
            }
        }
    }

    private fun readLeafCell(at: Int, onRow: (Long, List<Any?>) -> Unit) {
        var pos = at
        val (payloadSize, n1) = varint(pos); pos += n1
        val (rowId, n2) = varint(pos); pos += n2
        val payload = payload(pos, payloadSize.toInt())
        onRow(rowId, record(payload))
    }

    private fun payload(at: Int, size: Int): ByteArray {
        val maxLocal = usable - 35
        if (size <= maxLocal) return data.copyOfRange(at, at + size)
        val minLocal = (usable - 12) * 32 / 255 - 23
        val k = minLocal + (size - minLocal) % (usable - 4)
        val local = if (k <= maxLocal) k else minLocal
        val out = ByteArray(size)
        data.copyInto(out, 0, at, at + local)
        var written = local
        var next = u32(at + local)
        while (written < size && next != 0) {
            val base = (next - 1) * pageSize
            val chunk = minOf(usable - 4, size - written)
            data.copyInto(out, written, base + 4, base + 4 + chunk)
            written += chunk
            next = u32(base)
        }
        require(written == size) { "Damaged database (short overflow chain)" }
        return out
    }

    private fun record(p: ByteArray): List<Any?> {
        val (headerSize, n) = varint(p, 0)
        var h = n
        val types = ArrayList<Long>()
        while (h < headerSize) {
            val (t, len) = varint(p, h); h += len
            types += t
        }
        var pos = headerSize.toInt()
        return types.map { t ->
            when {
                t == 0L -> null
                t in 1L..6L -> {
                    val len = intArrayOf(0, 1, 2, 3, 4, 6, 8)[t.toInt()]
                    var v = p[pos].toLong() // sign-extended first byte
                    for (i in 1 until len) v = (v shl 8) or (p[pos + i].toLong() and 0xFF)
                    pos += len
                    v
                }
                t == 7L -> {
                    var bits = 0L
                    for (i in 0 until 8) bits = (bits shl 8) or (p[pos + i].toLong() and 0xFF)
                    pos += 8
                    Double.fromBits(bits)
                }
                t == 8L -> 0L
                t == 9L -> 1L
                t >= 12 && t % 2 == 0L -> {
                    val len = ((t - 12) / 2).toInt()
                    p.copyOfRange(pos, pos + len).also { pos += len }
                }
                t >= 13 -> {
                    val len = ((t - 13) / 2).toInt()
                    p.decodeToString(pos, pos + len).also { pos += len }
                }
                else -> null
            }
        }
    }

    private fun varint(at: Int): Pair<Long, Int> = varint(data, at)

    private fun varint(b: ByteArray, at: Int): Pair<Long, Int> {
        var v = 0L
        for (i in 0 until 8) {
            val x = b[at + i].toInt() and 0xFF
            v = (v shl 7) or (x and 0x7F).toLong()
            if (x and 0x80 == 0) return v to i + 1
        }
        return ((v shl 8) or (b[at + 8].toLong() and 0xFF)) to 9
    }

    private fun u16(at: Int) = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
    private fun u32(at: Int) = ((data[at].toInt() and 0xFF) shl 24) or ((data[at + 1].toInt() and 0xFF) shl 16) or
        ((data[at + 2].toInt() and 0xFF) shl 8) or (data[at + 3].toInt() and 0xFF)
}
