package com.robbyolivam.aac.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Open Board Format (https://www.openboardformat.org) import/export, so vocabularies can move
 * to and from other AAC apps (CoughDrop, Cboard, and others). Works on the files inside an .obz
 * zip (path → bytes); the app does the zipping.
 */
object OpenBoardFormat {
    private const val FORMAT = "open-board-0.1"
    private const val STRIP_ID = "aac-quick-strip"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    class Imported(val vocabulary: Vocabulary, val media: Map<String, ByteArray>, val skippedImages: Int)

    /** Exports to .obz entries. [readMedia] loads a stored picture by its relative path. */
    fun export(vocabulary: Vocabulary, readMedia: (String) -> ByteArray?): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        val boardPaths = LinkedHashMap<String, String>()
        val imagePaths = LinkedHashMap<String, String>()
        fun boardPath(id: String) = "boards/${safe(id)}.obf"

        val exportBoards = vocabulary.boards.map { it to it.exportCells() } +
            (Board(STRIP_ID, "Quick strip", 1, vocabulary.quickStrip.size, vocabulary.quickStrip) to vocabulary.quickStrip.map { it as Cell? })
        for ((board, cells) in exportBoards) {
            val images = mutableListOf<JsonObject>()
            val buttons = cells.filterNotNull().map { cell ->
                buildJsonObject {
                    put("id", cell.id)
                    put("label", cell.label)
                    cell.spoken?.let { put("vocalization", it) }
                    put("background_color", cssColor(fitzgeraldRgb(cell.type)))
                    put("ext_aac_type", cell.type.name)
                    if (cell.action == CellAction.PHRASE) put("ext_aac_action", "phrase")
                    if (cell.level != 1) put("ext_aac_level", cell.level)
                    cell.symbol.emoji?.let { put("ext_aac_emoji", it) }
                    if (cell.action == CellAction.FOLDER && cell.linkTo != null) {
                        put("load_board", buildJsonObject {
                            put("id", cell.linkTo)
                            put("path", boardPath(cell.linkTo))
                        })
                    }
                    val image = cell.symbol.imagePath
                    val bytes = image?.let(readMedia)
                    if (image != null && bytes != null) {
                        val imageId = "img-${safe(cell.id)}"
                        val path = "images/${image.substringAfterLast('/')}"
                        files[path] = bytes
                        imagePaths[imageId] = path
                        images += buildJsonObject {
                            put("id", imageId)
                            put("path", path)
                            put("content_type", if (path.endsWith(".png")) "image/png" else "image/jpeg")
                        }
                        put("image_id", imageId)
                    }
                }
            }
            val rows = if (board.isScene) 1 else board.rows
            val cols = if (board.isScene) cells.size.coerceAtLeast(1) else board.cols
            val obf = buildJsonObject {
                put("format", FORMAT)
                put("id", board.id)
                put("name", board.name)
                put("locale", "en")
                put("buttons", JsonArray(buttons))
                put("images", JsonArray(images))
                put("grid", buildJsonObject {
                    put("rows", rows)
                    put("columns", cols)
                    put("order", buildJsonArray {
                        for (r in 0 until rows) add(buildJsonArray {
                            for (c in 0 until cols) {
                                val cell = cells.getOrNull(r * cols + c)
                                add(if (cell == null) JsonNull else JsonPrimitive(cell.id))
                            }
                        })
                    })
                })
            }
            val path = boardPath(board.id)
            boardPaths[board.id] = path
            files[path] = json.encodeToString(JsonObject.serializer(), obf).toByteArray()
        }
        val manifest = buildJsonObject {
            put("format", FORMAT)
            put("root", boardPath(vocabulary.homeBoardId))
            put("paths", buildJsonObject {
                put("boards", JsonObject(boardPaths.mapValues { JsonPrimitive(it.value) }))
                put("images", JsonObject(imagePaths.mapValues { JsonPrimitive(it.value) }))
            })
            put("ext_aac_phrases", boardPath(vocabulary.phrasesBoardId))
            put("ext_aac_quick_strip", boardPath(STRIP_ID))
        }
        files["manifest.json"] = json.encodeToString(JsonObject.serializer(), manifest).toByteArray()
        return files
    }

    private fun Board.exportCells(): List<Cell?> = scene?.hotspots?.map { it.cell } ?: cells

    /**
     * Imports .obz entries (or a single .obf file under any name). Boards, positions, folder links,
     * labels, colors and embedded pictures carry over. Pictures that are only web links are skipped
     * (the app works offline), as are SVGs.
     */
    fun import(files: Map<String, ByteArray>, fallbackStrip: List<Cell>): Imported {
        val manifest = files["manifest.json"]?.let { parse(it) }
        val obfPaths = files.keys.filter { it.endsWith(".obf") }
        require(obfPaths.isNotEmpty()) { "No boards (.obf files) found" }
        val root = manifest?.str("root")?.takeIf { it in files } ?: obfPaths.first()
        val phrasesPath = manifest?.str("ext_aac_phrases")?.takeIf { it in files }
        val stripPath = manifest?.str("ext_aac_quick_strip")?.takeIf { it in files }

        val parsed = obfPaths.associateWith { parse(files.getValue(it)) }
        // Stable, unique ids for our boards; folder links may use either the OBF id or the path.
        val idByPath = LinkedHashMap<String, String>()
        val idByObfId = HashMap<String, String>()
        parsed.forEach { (path, obf) ->
            val base = safe(obf.str("id") ?: path.substringAfterLast('/').removeSuffix(".obf"))
            var id = base
            var n = 2
            while (id in idByPath.values) id = "$base-${n++}"
            idByPath[path] = id
            obf.str("id")?.let { idByObfId[it] = id }
        }

        val media = LinkedHashMap<String, ByteArray>()
        var skippedImages = 0
        val boards = mutableListOf<Board>()
        var strip: List<Cell>? = null

        for ((path, obf) in parsed) {
            val boardId = idByPath.getValue(path)
            val images = obf.arr("images").associateBy { it.jsonObject.str("id") ?: "" }
            val cellsById = LinkedHashMap<String, Cell>()
            obf.arr("buttons").forEachIndexed { i, el ->
                val b = el.jsonObject
                val buttonId = b.str("id") ?: "b$i"
                val load = b["load_board"] as? JsonObject
                val linkTo = load?.let { l ->
                    l.str("id")?.let(idByObfId::get) ?: l.str("path")?.let { p -> idByPath[p] ?: idByPath[p.removePrefix("/")] }
                }
                val type = b.str("ext_aac_type")?.let { runCatching { WordType.valueOf(it) }.getOrNull() }
                    ?: if (linkTo != null) WordType.FOLDER
                    else parseCssColor(b.str("background_color"))?.let(::wordTypeForColor) ?: WordType.OTHER
                var imagePath: String? = null
                b.str("image_id")?.let { images[it]?.jsonObject }?.let { image ->
                    val bytes = imageBytes(image, files)
                    if (bytes == null) {
                        skippedImages++
                    } else {
                        val ext = if (image.str("content_type")?.contains("png") == true) "png" else "jpg"
                        imagePath = "media/obf-${safe(boardId)}-${safe(buttonId)}.$ext"
                        media[imagePath!!] = bytes
                    }
                }
                val label = b.str("label") ?: b.str("vocalization") ?: ""
                cellsById[buttonId] = Cell(
                    id = "$boardId.${safe(buttonId)}",
                    label = label,
                    spoken = b.str("vocalization")?.takeIf { it != label },
                    symbol = Symbol(emoji = b.str("ext_aac_emoji"), imagePath = imagePath),
                    type = type,
                    action = when {
                        linkTo != null -> CellAction.FOLDER
                        b.str("ext_aac_action") == "phrase" -> CellAction.PHRASE
                        else -> CellAction.WORD
                    },
                    linkTo = linkTo,
                    level = (b["ext_aac_level"] as? JsonPrimitive)?.intOrNull ?: 1,
                )
            }
            val grid = obf["grid"] as? JsonObject
            val rows = (grid?.get("rows") as? JsonPrimitive)?.intOrNull ?: 1
            val cols = (grid?.get("columns") as? JsonPrimitive)?.intOrNull ?: cellsById.size.coerceAtLeast(1)
            val order = (grid?.get("order") as? JsonArray)?.map { row -> (row as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull }.orEmpty() }
            val cells = MutableList<Cell?>(rows * cols) { null }
            if (order != null) {
                order.forEachIndexed { r, row ->
                    row.forEachIndexed { c, id -> if (r < rows && c < cols && id != null) cells[r * cols + c] = cellsById[id] }
                }
            } else {
                cellsById.values.take(cells.size).forEachIndexed { i, cell -> cells[i] = cell }
            }
            if (path == stripPath) {
                strip = cells.filterNotNull()
            } else {
                boards += Board(boardId, obf.str("name") ?: boardId, rows, cols, cells)
            }
        }

        val homeId = idByPath.getValue(root)
        val phrasesId = phrasesPath?.let(idByPath::get) ?: run {
            val id = "phrases".let { if (boards.any { b -> b.id == it }) "imported-phrases" else it }
            boards += newBoard(id, "Quick phrases", DefaultVocabulary.ROWS, DefaultVocabulary.COLS)
            id
        }
        val vocabulary = Vocabulary(
            homeBoardId = homeId,
            phrasesBoardId = phrasesId,
            boards = boards,
            quickStrip = strip?.takeIf { it.isNotEmpty() } ?: fallbackStrip,
        )
        return Imported(vocabulary, media, skippedImages)
    }

    private fun imageBytes(image: JsonObject, files: Map<String, ByteArray>): ByteArray? {
        val contentType = image.str("content_type").orEmpty()
        val path = image.str("path")
        if (contentType.contains("svg") || path?.endsWith(".svg") == true) return null
        path?.let { p -> (files[p] ?: files[p.removePrefix("/")])?.let { return it } }
        val data = image.str("data") ?: return null
        if (data.startsWith("data:image/svg")) return null
        val base64 = data.substringAfter("base64,", missingDelimiterValue = "").ifEmpty { return null }
        return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
    }

    private fun parse(bytes: ByteArray): JsonObject = json.parseToJsonElement(bytes.decodeToString()).jsonObject

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "x" }
}
