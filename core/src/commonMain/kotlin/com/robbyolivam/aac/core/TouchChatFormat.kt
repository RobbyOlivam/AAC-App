package com.robbyolivam.aac.core

/**
 * Imports TouchChat vocabulary files (.ce, from TouchChat's Menu → Import/Export Vocab →
 * Export and Share). A .ce file is a zip holding SQLite databases:
 *
 * - `<name>.c4v`: pages, buttons, styles and actions;
 * - `Images.c4s`: the person's own pictures (PNG). TouchChat's SymbolStix pictures are licensed
 *   and are not in the file, so those buttons get a picture from this app's library instead.
 *
 * What comes across: every page reachable from the home page, each button in its grid
 * position, its label and spoken text, page links, its colour (as a word type), hidden
 * buttons, the person's own pictures and recorded sounds. Buttons that only control TouchChat
 * itself (clear, delete word, back, home…) are left out, because this app has its own controls.
 * WordPower's grammar states (buttons that change after "I", "he"…) keep their normal word.
 */
object TouchChatFormat {

    class Imported(
        val vocabulary: Vocabulary,
        /** Files to save, path relative to the app's files dir → bytes. */
        val media: Map<String, ByteArray>,
        val pages: Int,
        val buttons: Int,
        /** Buttons that only controlled TouchChat (clear, back…) and were left out. */
        val skippedControls: Int,
        /** Buttons whose TouchChat picture was replaced with one from this app's library. */
        val symbolsMatched: Int,
        /** Buttons whose TouchChat picture had no match here (they show the word only). */
        val symbolsMissing: Int,
    )

    fun isTouchChat(files: Map<String, ByteArray>): Boolean = files.keys.any { it.endsWith(".c4v", ignoreCase = true) }

    /**
     * @param symbolFor picks a library symbol id for a word, for buttons whose picture is a
     *   SymbolStix one that can't be brought over; null leaves the button without a picture.
     */
    fun import(
        files: Map<String, ByteArray>,
        fallbackStrip: List<Cell>,
        symbolFor: (String) -> String? = { null },
        maxPages: Int = 600,
    ): Imported {
        val vocabFile = files.entries.firstOrNull { it.key.endsWith(".c4v", ignoreCase = true) }?.value
            ?: throw IllegalArgumentException("No TouchChat vocabulary (.c4v) in the file")
        val db = SqliteReader(vocabFile)
        require(db.hasTable("pages") && db.hasTable("buttons")) { "This TouchChat file uses a format this app doesn't know" }
        val images = files.entries.firstOrNull { it.key.endsWith(".c4s", ignoreCase = true) }?.value
            ?.let { runCatching { SqliteReader(it) }.getOrNull() }
            ?.rows("symbols")
            ?.mapNotNull { r -> (r["rid"] as? String)?.let { rid -> (r["data"] as? ByteArray)?.let { rid to it } } }
            ?.toMap().orEmpty()

        fun Map<String, Any?>.long(key: String): Long? = this[key] as? Long
        fun Map<String, Any?>.int(key: String): Int? = long(key)?.toInt()
        fun Map<String, Any?>.str(key: String): String? = this[key] as? String

        val resources = db.rows("resources").associateBy { it.long("id") }
        val resourceByRid = resources.values.associateBy { it.str("rid") }
        val pages = db.rows("pages")
        val pageById = pages.associateBy { it.long("id") }
        val pageByResource = pages.associateBy { it.long("resource_id") }
        val boxInstanceByPage = db.rows("button_box_instances").groupBy { it.long("page_id") }
        val boxes = db.rows("button_boxes").associateBy { it.long("id") }
        val cellsByBox = db.rows("button_box_cells").groupBy { it.long("button_box_id") }
        val buttonsById = db.rows("buttons").associateBy { it.long("id") }
        val buttonByResource = buttonsById.values.associateBy { it.long("resource_id") }
        val setIdByResource = db.rows("button_sets").associateBy({ it.long("resource_id") }, { it.long("id") })
        val defaultButtonOfSet = db.rows("button_set_modifiers")
            .filter { it.long("modifier") == 0L }
            .associateBy({ it.long("button_set_id") }, { it.long("button_id") })
        val styles = db.rows("button_styles").associateBy { it.long("id") }
        val symbolLinks = db.rows("symbol_links").associateBy { it.long("id") }
        val sounds = db.rows("sounds").associateBy { it.long("resource_id") }
        val actionData = db.rows("action_data").groupBy { it.long("action_id") }
        val actionsByResource = db.rows("actions").groupBy { it.long("resource_id") }
            .mapValues { (_, list) -> list.sortedBy { it.long("rank") } }
        val homePageId = db.rows("special_pages").firstOrNull { it.str("name") == "HOME" }?.long("page_id")
            ?: pages.minByOrNull { it.long("id") ?: Long.MAX_VALUE }?.long("id")
            ?: throw IllegalArgumentException("The TouchChat file has no pages")

        class Action(val code: Int, val data: Map<Int, String>)

        fun actionsOf(resourceId: Long?): List<Action> = actionsByResource[resourceId].orEmpty().map { a ->
            Action(a.int("code") ?: -1, actionData[a.long("id")].orEmpty().associate { (it.int("key") ?: -1) to (it.str("value") ?: "") })
        }

        /** The button shown in a grid cell: a plain button, or a button set's normal (unmodified) button. */
        fun buttonInCell(resourceId: Long?): Map<String, Any?>? {
            val type = resources[resourceId]?.int("type")
            return when (type) {
                TYPE_BUTTON -> buttonByResource[resourceId]
                TYPE_BUTTON_SET -> setIdByResource[resourceId]?.let(defaultButtonOfSet::get)?.let(buttonsById::get)
                else -> null
            }
        }

        fun pageForRid(rid: String?): Long? =
            resourceByRid[rid]?.long("id")?.let(pageByResource::get)?.long("id")

        fun navTarget(actions: List<Action>): Long? =
            actions.firstOrNull { it.code in NAVIGATE_CODES }?.let { pageForRid(it.data[0]) }

        // Pages reachable from home, home first, in the order a person would find them.
        val order = ArrayList<Long>()
        val seen = HashSet<Long>()
        val queue = ArrayDeque(listOf(homePageId))
        seen += homePageId
        while (queue.isNotEmpty() && order.size < maxPages) {
            val pageId = queue.removeFirst()
            val page = pageById[pageId] ?: continue
            order += pageId
            for (inst in boxInstanceByPage[page.long("id")].orEmpty()) {
                for (cell in cellsByBox[inst.long("button_box_id")].orEmpty()) {
                    val button = buttonInCell(cell.long("resource_id")) ?: continue
                    val target = navTarget(actionsOf(button.long("resource_id"))) ?: continue
                    if (seen.add(target)) queue.addLast(target)
                }
            }
        }

        // Board ids and names.
        val boardIdByPage = LinkedHashMap<Long, String>()
        val nameByPage = HashMap<Long, String>()
        for (pageId in order) {
            val raw = resources[pageById[pageId]?.long("resource_id")]?.str("name").orEmpty()
            val name = if (pageId == homePageId) "Home" else cleanName(raw).ifEmpty { "Page" }
            val base = "tc-" + slug(name)
            var id = base
            var n = 2
            while (id in boardIdByPage.values) id = "$base-${n++}"
            boardIdByPage[pageId] = id
            nameByPage[pageId] = name
        }

        val pageNames = boardIdByPage.entries.associate { (page, id) -> id to nameByPage.getValue(page) }
        val knownTypes = HashMap<String, WordType>()
        val knownSymbols = HashMap<String, String>()
        DefaultVocabulary.create().allWordCells().forEach { c ->
            knownTypes.getOrPut(c.text.lowercase()) { c.type }
            c.symbol.libraryId?.let { knownSymbols.getOrPut(c.text.lowercase()) { it } }
        }

        val media = LinkedHashMap<String, ByteArray>()
        val mediaByRid = HashMap<String, String>()
        var buttonCount = 0
        var skippedControls = 0
        var symbolsMatched = 0
        var symbolsMissing = 0

        val boards = order.mapNotNull { pageId ->
            val page = pageById.getValue(pageId)
            val boardId = boardIdByPage.getValue(pageId)
            val inst = boxInstanceByPage[pageId]?.firstOrNull() ?: return@mapNotNull null
            val box = boxes[inst.long("button_box_id")] ?: return@mapNotNull null
            val cols = (box.int("layout_x") ?: 1).coerceIn(1, 30)
            val rows = (box.int("layout_y") ?: 1).coerceIn(1, 30)
            val pageStyle = styles[page.long("button_style_id")]
            val cells = MutableList<Cell?>(rows * cols) { null }
            for (c in cellsByBox[box.long("id")].orEmpty()) {
                val location = c.int("location") ?: continue
                if (location !in cells.indices) continue
                val button = buttonInCell(c.long("resource_id")) ?: continue
                val actions = actionsOf(button.long("resource_id"))
                val label = button.str("label").orEmpty().replace("//", " ").replace(Regex("\\s+"), " ").trim()
                val message = button.str("message").orEmpty().replace(Regex("\\s+"), " ").trim()
                val target = navTarget(actions)?.let(boardIdByPage::get)
                val soundRid = actions.firstOrNull { it.code == CODE_PLAY_SOUND }?.data?.get(0)
                val soundPath = soundRid?.let { rid ->
                    mediaByRid[rid] ?: resourceByRid[rid]?.long("id")?.let(sounds::get)?.let { s ->
                        (s["data"] as? ByteArray)?.let { bytes ->
                            "media/tc-sound-${media.size}.wav".also { path -> media[path] = bytes; mediaByRid[rid] = path }
                        }
                    }
                }
                // A picture-only page link (like TouchChat's "more" arrow) gets the page's name.
                val shown = label.ifEmpty { message }.ifEmpty {
                    val name = target?.let { t -> pageNames[t] }.orEmpty()
                    // "Food" → "Food2" is TouchChat's next page of the same topic.
                    if (name.trimEnd { it.isDigit() } == nameByPage[pageId] && name != nameByPage[pageId]) "more" else name
                }
                if (shown.isEmpty() && target == null && soundPath == null) continue
                if (message.isEmpty() && target == null && soundPath == null) {
                    skippedControls++ // clear, delete word, back, home, media controls…
                    continue
                }
                // Picture: the person's own (in Images.c4s), else a match from our library.
                val linkRid = symbolLinks[button.long("symbol_link_id")]?.str("rid")
                var imagePath: String? = null
                var libraryId: String? = null
                if (linkRid != null) {
                    val own = images[linkRid]
                    if (own != null) {
                        imagePath = mediaByRid[linkRid] ?: "media/tc-picture-${media.size}.png".also { media[it] = own; mediaByRid[linkRid] = it }
                    } else {
                        // The starter vocabulary's hand-picked picture first, then the library.
                        libraryId = knownSymbols[shown.lowercase()] ?: symbolFor(shown)
                        if (libraryId != null) symbolsMatched++ else symbolsMissing++
                    }
                }
                // Word type: this app's own for words it knows (so forms and grammar help work),
                // else from the button colour; plain white or grey buttons say nothing.
                val color = (styles[button.long("button_style_id")]?.long("body_color") ?: pageStyle?.long("body_color"))?.toInt()
                val wordType = knownTypes[(message.ifEmpty { shown }).lowercase()]
                    ?: color?.let(::bgrToRgb)?.takeUnless(::isNeutral)?.let(::wordTypeForColor)
                    ?: WordType.OTHER
                val speaksNow = actions.any { it.code == CODE_SPEAK_NOW } && actions.none { it.code == CODE_INSERT }
                buttonCount++
                cells[location] = Cell(
                    id = "$boardId.$location",
                    label = shown,
                    spoken = message.takeIf { it.isNotEmpty() && it != shown },
                    symbol = Symbol(imagePath = imagePath, libraryId = libraryId),
                    type = if (message.isEmpty() && target != null) WordType.FOLDER else wordType,
                    action = when {
                        message.isEmpty() && target != null -> CellAction.FOLDER
                        message.isEmpty() -> CellAction.PHRASE // a sound button
                        speaksNow -> CellAction.PHRASE
                        else -> CellAction.WORD
                    },
                    // A word that also opens a page (WordPower: "I" opens the verbs that follow it).
                    linkTo = target,
                    hidden = button.long("visible") == 0L,
                    audioPath = soundPath,
                )
            }
            Board(boardId, nameByPage.getValue(pageId), rows, cols, cells)
        }.toMutableList()
        require(boards.isNotEmpty()) { "No pages could be read from the TouchChat file" }

        val phrasesId = "tc-quick-phrases".let { base -> if (boards.any { it.id == base }) "$base-2" else base }
        boards += newBoard(phrasesId, "Quick phrases", DefaultVocabulary.ROWS, DefaultVocabulary.COLS)
        val vocabulary = Vocabulary(
            homeBoardId = boardIdByPage.getValue(homePageId),
            phrasesBoardId = phrasesId,
            boards = boards,
            quickStrip = fallbackStrip,
        )
        return Imported(vocabulary, media, boards.size - 1, buttonCount, skippedControls, symbolsMatched, symbolsMissing)
    }

    /** White, near-white and grey: a button colour that doesn't mark a kind of word. */
    private fun isNeutral(rgb: Int): Boolean {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) < 24
    }

    /** TouchChat stores colours as 0xBBGGRR. */
    internal fun bgrToRgb(c: Int): Int = ((c and 0xFF) shl 16) or (c and 0xFF00) or ((c shr 16) and 0xFF)

    /** ".Basic60 I you" → "I you"; "._KB-QWERTY" → "KB-QWERTY". */
    internal fun cleanName(raw: String): String = raw.trim().trimStart('.', '_', ' ').replace(Regex("\\s+"), " ")
        .replace(Regex("^Basic\\d+ "), "") // WordPower's "Basic60 " prefix

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "page" }

    private const val TYPE_BUTTON = 4
    private const val TYPE_BUTTON_SET = 5

    /** Action codes, worked out from real files (TouchChat doesn't publish them). */
    private val NAVIGATE_CODES = setOf(8, 9, 73)
    private const val CODE_PLAY_SOUND = 4
    private const val CODE_INSERT = 10
    private const val CODE_SPEAK_NOW = 42
}
