package com.robbyolivam.aac.core

/**
 * Zip files in plain Kotlin, for platforms without a zip library (web, iOS): reading (stored
 * and deflated entries) and writing (stored entries, which is all backups need).
 * Backups, Open Board (.obz) and TouchChat (.ce) files are all zips.
 */
object Zip {
    /** Every file in [zip] (directories skipped), name → bytes. */
    fun read(zip: ByteArray, maxTotalBytes: Long = 400L * 1024 * 1024): Map<String, ByteArray> {
        val end = findEndOfCentralDirectory(zip)
        val count = u16(zip, end + 10)
        var pos = u32(zip, end + 16).toInt()
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        repeat(count) {
            require(u32(zip, pos) == 0x02014b50L) { "Damaged zip (central directory)" }
            val method = u16(zip, pos + 10)
            val compressedSize = u32(zip, pos + 20).toInt()
            val size = u32(zip, pos + 24).toInt()
            val nameLength = u16(zip, pos + 28)
            val extraLength = u16(zip, pos + 30)
            val commentLength = u16(zip, pos + 32)
            val localHeader = u32(zip, pos + 42).toInt()
            val name = zip.decodeToString(pos + 46, pos + 46 + nameLength).removePrefix("/")
            pos += 46 + nameLength + extraLength + commentLength
            if (name.endsWith("/") || name.contains("..")) return@repeat
            require(u32(zip, localHeader) == 0x04034b50L) { "Damaged zip (entry $name)" }
            val dataStart = localHeader + 30 + u16(zip, localHeader + 26) + u16(zip, localHeader + 28)
            val raw = zip.copyOfRange(dataStart, dataStart + compressedSize)
            total += size
            require(total <= maxTotalBytes) { "The file is too large" }
            out[name] = when (method) {
                0 -> raw
                8 -> Inflate.inflate(raw, size)
                else -> throw IllegalArgumentException("Unsupported zip compression ($method) for $name")
            }
        }
        return out
    }

    fun isZip(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** A zip of [files] (stored, not compressed). */
    fun write(files: Map<String, ByteArray>): ByteArray {
        val out = ByteBuilder()
        val central = ByteBuilder()
        for ((name, data) in files) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            out.u32(0x04034b50); out.u16(20); out.u16(0x0800); out.u16(0); out.u16(0); out.u16(0x21)
            out.u32(crc); out.u32(data.size.toLong()); out.u32(data.size.toLong())
            out.u16(nameBytes.size); out.u16(0); out.bytes(nameBytes); out.bytes(data)
            central.u32(0x02014b50); central.u16(20); central.u16(20); central.u16(0x0800); central.u16(0)
            central.u16(0); central.u16(0x21); central.u32(crc); central.u32(data.size.toLong()); central.u32(data.size.toLong())
            central.u16(nameBytes.size); central.u16(0); central.u16(0); central.u16(0); central.u16(0)
            central.u32(0); central.u32(offset.toLong()); central.bytes(nameBytes)
        }
        val centralStart = out.size
        out.bytes(central.toByteArray())
        out.u32(0x06054b50); out.u16(0); out.u16(0); out.u16(files.size); out.u16(files.size)
        out.u32(central.size.toLong()); out.u32(centralStart.toLong()); out.u16(0)
        return out.toByteArray()
    }

    private fun findEndOfCentralDirectory(zip: ByteArray): Int {
        var i = zip.size - 22
        val stop = maxOf(0, zip.size - 22 - 65535)
        while (i >= stop) {
            if (u32(zip, i) == 0x06054b50L) return i
            i--
        }
        throw IllegalArgumentException("Not a zip file")
    }

    private val crcTable = LongArray(256) { n ->
        var c = n.toLong()
        repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
        c
    }

    fun crc32(data: ByteArray): Long {
        var c = 0xFFFFFFFFL
        for (b in data) c = crcTable[((c xor b.toLong()) and 0xFF).toInt()] xor (c ushr 8)
        return c xor 0xFFFFFFFFL
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, at: Int): Long = (u16(b, at).toLong()) or (u16(b, at + 2).toLong() shl 16)

    private class ByteBuilder {
        private var buf = ByteArray(1024)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }

        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun u16(v: Int) { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte() }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        fun toByteArray() = buf.copyOf(size)
    }
}

/** A DEFLATE (RFC 1951) decoder. */
internal object Inflate {
    private val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    /** Canonical Huffman decoding table: counts per length and symbols in code order. */
    private class Huffman(lengths: IntArray, n: Int) {
        val counts = IntArray(16)
        val symbols = IntArray(n)

        init {
            for (i in 0 until n) counts[lengths[i]]++
            counts[0] = 0
            val offsets = IntArray(16)
            for (len in 1 until 16) offsets[len] = offsets[len - 1] + counts[len - 1]
            for (i in 0 until n) if (lengths[i] != 0) symbols[offsets[lengths[i]]++] = i
        }
    }

    private class Input(val data: ByteArray) {
        var pos = 0
        var bitBuf = 0
        var bitCount = 0

        fun bits(need: Int): Int {
            var v = bitBuf
            while (bitCount < need) {
                require(pos < data.size) { "Damaged compressed data" }
                v = v or ((data[pos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            bitBuf = v ushr need
            bitCount -= need
            return v and ((1 shl need) - 1)
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1 until 16) {
                code = code or bits(1)
                val count = h.counts[len]
                if (code - count < first) return h.symbols[index + (code - first)]
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            throw IllegalArgumentException("Damaged compressed data (bad code)")
        }
    }

    private class Output(expected: Int) {
        var buf = ByteArray(maxOf(expected, 64))
        var size = 0

        fun put(b: Byte) {
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = b
        }
    }

    private val fixedLit: Huffman by lazy {
        val l = IntArray(288) { when { it < 144 -> 8; it < 256 -> 9; it < 280 -> 7; else -> 8 } }
        Huffman(l, 288)
    }
    private val fixedDist: Huffman by lazy { Huffman(IntArray(30) { 5 }, 30) }

    fun inflate(data: ByteArray, expectedSize: Int = data.size * 3): ByteArray {
        val input = Input(data)
        val out = Output(expectedSize)
        do {
            val last = input.bits(1)
            when (input.bits(2)) {
                0 -> stored(input, out)
                1 -> codes(input, out, fixedLit, fixedDist)
                2 -> dynamic(input, out)
                else -> throw IllegalArgumentException("Damaged compressed data (block type)")
            }
        } while (last == 0)
        return out.buf.copyOf(out.size)
    }

    private fun stored(input: Input, out: Output) {
        input.bitBuf = 0
        input.bitCount = 0
        val d = input.data
        require(input.pos + 4 <= d.size) { "Damaged compressed data" }
        val len = (d[input.pos].toInt() and 0xFF) or ((d[input.pos + 1].toInt() and 0xFF) shl 8)
        input.pos += 4
        require(input.pos + len <= d.size) { "Damaged compressed data" }
        for (i in 0 until len) out.put(d[input.pos + i])
        input.pos += len
    }

    private fun dynamic(input: Input, out: Output) {
        val nLen = input.bits(5) + 257
        val nDist = input.bits(5) + 1
        val nCode = input.bits(4) + 4
        val lengths = IntArray(320)
        for (i in 0 until nCode) lengths[CODE_LENGTH_ORDER[i]] = input.bits(3)
        val lenCode = Huffman(lengths.copyOf(19), 19)
        var i = 0
        while (i < nLen + nDist) {
            var symbol = input.decode(lenCode)
            if (symbol < 16) {
                lengths[i++] = symbol
            } else {
                var len = 0
                when (symbol) {
                    16 -> { require(i > 0) { "Damaged compressed data" }; len = lengths[i - 1]; symbol = 3 + input.bits(2) }
                    17 -> symbol = 3 + input.bits(3)
                    else -> symbol = 11 + input.bits(7)
                }
                require(i + symbol <= nLen + nDist) { "Damaged compressed data" }
                repeat(symbol) { lengths[i++] = len }
            }
        }
        val lit = Huffman(lengths.copyOfRange(0, nLen), nLen)
        val dist = Huffman(lengths.copyOfRange(nLen, nLen + nDist), nDist)
        codes(input, out, lit, dist)
    }

    private fun codes(input: Input, out: Output, lit: Huffman, dist: Huffman) {
        while (true) {
            var symbol = input.decode(lit)
            if (symbol < 256) {
                out.put(symbol.toByte())
            } else if (symbol == 256) {
                return
            } else {
                symbol -= 257
                require(symbol < 29) { "Damaged compressed data (length)" }
                val len = LENGTH_BASE[symbol] + input.bits(LENGTH_EXTRA[symbol])
                val ds = input.decode(dist)
                require(ds < 30) { "Damaged compressed data (distance)" }
                val distance = DIST_BASE[ds] + input.bits(DIST_EXTRA[ds])
                require(distance <= out.size) { "Damaged compressed data (distance too far)" }
                var from = out.size - distance
                repeat(len) { out.put(out.buf[from++]) }
            }
        }
    }
}
