package com.robbyolivam.aac.core

import com.robbyolivam.aac.core.WordType.*

/**
 * Emoji are limited to Unicode 13 or older so they render on Android 11 tablets and Fire OS,
 * not only on current Pixels.
 *
 * Starter vocabulary: a 6×8 core board laid out by part of speech (people → verbs →
 * describing words → little words → folders, left to right), plus fringe folders,
 * quick phrases and an always-available strip.
 */
object DefaultVocabulary {
    const val ROWS = 8
    const val COLS = 6

    const val HOME = "core"
    const val PHRASES = "phrases"

    private class Entry(val label: String, val emoji: String, val type: WordType, val spoken: String? = null)

    private fun e(label: String, emoji: String, type: WordType, spoken: String? = null) = Entry(label, emoji, type, spoken)

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun Entry.toCell(boardId: String, action: CellAction = CellAction.WORD, linkTo: String? = null) = Cell(
        id = "$boardId.${slug(label)}",
        label = label,
        spoken = spoken,
        symbol = Symbol(emoji = emoji),
        type = type,
        action = action,
        linkTo = linkTo,
    )

    private class Folder(val id: String, val name: String, val emoji: String, val words: List<Entry>)

    private val folders = listOf(
        Folder(
            "feelings", "Feelings", "😊", listOf(
                e("happy", "😊", ADJECTIVE), e("sad", "😢", ADJECTIVE), e("mad", "😠", ADJECTIVE),
                e("scared", "😨", ADJECTIVE), e("tired", "😴", ADJECTIVE), e("sick", "🤒", ADJECTIVE),
                e("hurt", "🤕", ADJECTIVE), e("excited", "🤩", ADJECTIVE), e("calm", "😌", ADJECTIVE),
                e("silly", "🤪", ADJECTIVE), e("bored", "🥱", ADJECTIVE), e("frustrated", "😤", ADJECTIVE),
                e("worried", "😟", ADJECTIVE), e("proud", "🏅", ADJECTIVE), e("okay", "🙂", ADJECTIVE),
                e("hungry", "😋", ADJECTIVE), e("thirsty", "💧", ADJECTIVE), e("embarrassed", "😳", ADJECTIVE),
                e("lonely", "🥺", ADJECTIVE), e("surprised", "😮", ADJECTIVE), e("love", "❤️", VERB),
                e("hate", "💢", VERB), e("annoyed", "😒", ADJECTIVE), e("nervous", "😬", ADJECTIVE),
            )
        ),
        Folder(
            "food", "Food", "🍎", listOf(
                e("apple", "🍎", NOUN), e("banana", "🍌", NOUN), e("grapes", "🍇", NOUN),
                e("strawberry", "🍓", NOUN), e("orange", "🍊", NOUN), e("watermelon", "🍉", NOUN),
                e("bread", "🍞", NOUN), e("cheese", "🧀", NOUN), e("pizza", "🍕", NOUN),
                e("sandwich", "🥪", NOUN), e("chicken", "🍗", NOUN), e("pasta", "🍝", NOUN),
                e("rice", "🍚", NOUN), e("egg", "🥚", NOUN), e("cereal", "🥣", NOUN),
                e("fries", "🍟", NOUN), e("hamburger", "🍔", NOUN), e("hot dog", "🌭", NOUN),
                e("carrot", "🥕", NOUN), e("corn", "🌽", NOUN), e("cookie", "🍪", NOUN),
                e("cake", "🍰", NOUN), e("ice cream", "🍦", NOUN), e("candy", "🍬", NOUN),
                e("popcorn", "🍿", NOUN), e("pretzel", "🥨", NOUN), e("water", "🚰", NOUN),
                e("milk", "🥛", NOUN), e("juice", "🧃", NOUN), e("snack", "🍿", NOUN),
                e("breakfast", "🍳", NOUN), e("lunch", "🍱", NOUN), e("dinner", "🍽️", NOUN),
                e("taco", "🌮", NOUN), e("noodles", "🍜", NOUN), e("soup", "🥣", NOUN),
            )
        ),
        Folder(
            "people", "People", "👨‍👩‍👧", listOf(
                e("mom", "👩", PERSON), e("dad", "👨", PERSON), e("brother", "👦", PERSON),
                e("sister", "👧", PERSON), e("baby", "👶", PERSON), e("grandma", "👵", PERSON),
                e("grandpa", "👴", PERSON), e("friend", "🧑‍🤝‍🧑", PERSON), e("teacher", "🧑‍🏫", PERSON),
                e("doctor", "🧑‍⚕️", PERSON), e("family", "👨‍👩‍👧", PERSON), e("me", "🙋", PRONOUN),
                e("everyone", "👥", PRONOUN), e("somebody", "👤", PRONOUN), e("dog", "🐕", NOUN),
                e("cat", "🐈", NOUN), e("aunt", "👩‍🦰", PERSON), e("uncle", "👨‍🦱", PERSON),
                e("cousin", "🧒", PERSON), e("nurse", "🧑‍⚕️", PERSON), e("therapist", "🧑‍💼", PERSON),
                e("boy", "👦", PERSON), e("girl", "👧", PERSON), e("people", "👫", PERSON),
            )
        ),
        Folder(
            "places", "Places", "🏠", listOf(
                e("home", "🏠", PLACE), e("school", "🏫", PLACE), e("park", "🏞️", PLACE),
                e("store", "🏪", PLACE), e("outside", "🌳", PLACE), e("inside", "🚪", PLACE),
                e("bathroom", "🚽", PLACE), e("bedroom", "🛏️", PLACE), e("kitchen", "🧑‍🍳", PLACE),
                e("car", "🚗", PLACE), e("playground", "🎠", PLACE), e("pool", "🏊", PLACE),
                e("restaurant", "🍴", PLACE), e("library", "📚", PLACE), e("hospital", "🏥", PLACE),
                e("beach", "🏖️", PLACE), e("bus", "🚌", PLACE), e("work", "🏢", PLACE),
                e("zoo", "🦁", PLACE), e("movies", "🎬", PLACE), e("church", "⛪", PLACE),
                e("farm", "🚜", PLACE), e("doctor's office", "🩺", PLACE), e("grandma's house", "🏡", PLACE),
            )
        ),
        Folder(
            "actions", "Actions", "🏃", listOf(
                e("run", "🏃", VERB), e("walk", "🚶", VERB), e("jump", "🤸", VERB),
                e("sit", "🪑", VERB), e("sleep", "😴", VERB), e("read", "📖", VERB),
                e("watch", "📺", VERB), e("listen", "👂", VERB), e("open", "📂", VERB),
                e("close", "🔒", VERB), e("come", "👋", VERB), e("give", "🎁", VERB),
                e("take", "✊", VERB), e("wash", "🧼", VERB), e("brush", "🪥", VERB),
                e("swim", "🏊", VERB), e("draw", "✏️", VERB), e("sing", "🎤", VERB),
                e("dance", "💃", VERB), e("hug", "🤗", VERB), e("wait", "⏳", VERB),
                e("look", "👀", VERB), e("turn", "🔄", VERB), e("throw", "⚾", VERB),
                e("push", "👐", VERB), e("cut", "✂️", VERB), e("build", "🧱", VERB),
                e("fix", "🔧", VERB), e("find", "🔍", VERB), e("try", "💪", VERB),
                e("think", "🤔", VERB), e("know", "💡", VERB), e("say", "💬", VERB),
                e("tell", "🗣️", VERB), e("buy", "🛒", VERB), e("ride", "🚲", VERB),
                e("cook", "🧑‍🍳", VERB), e("clean", "🧹", VERB), e("call", "📞", VERB),
                e("write", "📝", VERB), e("leave", "🚪", VERB), e("finish", "🏁", VERB),
                e("need", "❗", VERB), e("can", "👌", VERB), e("will", "🔜", VERB),
                e("let", "🙆", VERB), e("tickle", "🪶", VERB), e("kiss", "😘", VERB),
            )
        ),
        Folder(
            "describe", "Describe", "🎨", listOf(
                e("fast", "🐇", ADJECTIVE), e("slow", "🐢", ADJECTIVE), e("loud", "📢", ADJECTIVE),
                e("quiet", "🤫", ADJECTIVE), e("wet", "💦", ADJECTIVE), e("dry", "🏜️", ADJECTIVE),
                e("dirty", "🟫", ADJECTIVE), e("clean", "✨", ADJECTIVE), e("new", "🆕", ADJECTIVE),
                e("old", "🏚️", ADJECTIVE), e("soft", "☁️", ADJECTIVE), e("hard", "🪨", ADJECTIVE),
                e("broken", "💔", ADJECTIVE), e("fun", "🎉", ADJECTIVE), e("funny", "😂", ADJECTIVE),
                e("yucky", "🤢", ADJECTIVE), e("yummy", "😋", ADJECTIVE), e("pretty", "🌸", ADJECTIVE),
                e("scary", "👻", ADJECTIVE), e("easy", "👍", ADJECTIVE), e("difficult", "😓", ADJECTIVE),
                e("same", "👯", ADJECTIVE), e("different", "🔀", ADJECTIVE), e("cold", "🥶", ADJECTIVE),
                e("red", "🔴", ADJECTIVE), e("blue", "🔵", ADJECTIVE), e("green", "🟢", ADJECTIVE),
                e("yellow", "🟡", ADJECTIVE), e("orange", "🟠", ADJECTIVE), e("purple", "🟣", ADJECTIVE),
                e("black", "⚫", ADJECTIVE), e("white", "⚪", ADJECTIVE), e("pink", "🎀", ADJECTIVE),
                e("brown", "🟤", ADJECTIVE), e("up", "⬆️", PREPOSITION), e("down", "⬇️", PREPOSITION),
                e("here", "📍", ADVERB), e("there", "👉", ADVERB), e("very", "‼️", ADVERB),
                e("too", "➕", ADVERB), e("nice", "😇", ADJECTIVE), e("mean", "😈", ADJECTIVE),
            )
        ),
        Folder(
            "things", "Things", "🧸", listOf(
                e("toy", "🧸", NOUN), e("ball", "⚽", NOUN), e("book", "📕", NOUN),
                e("tablet", "📲", NOUN), e("TV", "📺", NOUN), e("music", "🎵", NOUN),
                e("blocks", "🧱", NOUN), e("puzzle", "🧩", NOUN), e("toy car", "🚙", NOUN),
                e("bubbles", "💭", NOUN), e("crayons", "🖍️", NOUN), e("paper", "📄", NOUN),
                e("shoes", "👟", NOUN), e("coat", "🧥", NOUN), e("hat", "🧢", NOUN),
                e("shirt", "👕", NOUN), e("pants", "👖", NOUN), e("socks", "🧦", NOUN),
                e("bed", "🛏️", NOUN), e("chair", "🪑", NOUN), e("door", "🚪", NOUN),
                e("cup", "🥤", NOUN), e("plate", "🍽️", NOUN), e("spoon", "🥄", NOUN),
                e("backpack", "🎒", NOUN), e("computer", "💻", NOUN), e("game", "🎮", NOUN),
                e("video", "🎬", NOUN), e("bath", "🛁", NOUN), e("glasses", "👓", NOUN),
                e("medicine", "💊", NOUN), e("phone", "📱", NOUN), e("headphones", "🎧", NOUN),
                e("blanket", "🛌", NOUN), e("bike", "🚲", NOUN), e("swing", "🎡", NOUN),
            )
        ),
        Folder(
            "time", "Time", "⏰", listOf(
                e("now", "⏰", ADVERB), e("later", "⏳", ADVERB), e("today", "📅", NOUN),
                e("tomorrow", "🌅", NOUN), e("yesterday", "🔙", NOUN), e("morning", "🌄", NOUN),
                e("afternoon", "🌞", NOUN), e("night", "🌙", NOUN), e("before", "⏮️", PREPOSITION),
                e("after", "⏭️", PREPOSITION), e("soon", "🔜", ADVERB), e("always", "♾️", ADVERB),
                e("never", "🙅", ADVERB), e("again", "🔁", ADVERB), e("first", "1️⃣", ADJECTIVE),
                e("next", "➡️", ADJECTIVE), e("last", "🔚", ADJECTIVE), e("weekend", "🎉", NOUN),
                e("birthday", "🎂", NOUN), e("time", "🕐", NOUN), e("day", "☀️", NOUN),
                e("week", "🗓️", NOUN), e("minute", "⏱️", NOUN), e("bedtime", "🛌", NOUN),
            )
        ),
    )

    /** Core words in columns; column 5 holds the folders. Position = motor plan. */
    private val coreColumns: List<List<Entry>> = listOf(
        listOf(
            e("I", "🙋", PRONOUN), e("you", "👉", PRONOUN), e("he", "👦", PRONOUN), e("she", "👧", PRONOUN),
            e("we", "👫", PRONOUN), e("it", "📦", PRONOUN), e("they", "👥", PRONOUN), e("my", "💁", PRONOUN),
        ),
        listOf(
            e("want", "🤲", VERB), e("like", "👍", VERB), e("go", "🚶", VERB), e("stop", "✋", VERB),
            e("help", "🆘", VERB), e("get", "🤏", VERB), e("eat", "🍽️", VERB), e("drink", "🥤", VERB),
        ),
        listOf(
            e("play", "🧸", VERB), e("see", "👀", VERB), e("do", "🛠️", VERB), e("make", "🎨", VERB),
            e("have", "👐", VERB), e("feel", "💗", VERB), e("put", "📥", VERB), e("is", "⚖️", VERB),
        ),
        listOf(
            e("more", "➕", ADJECTIVE), e("big", "🐘", ADJECTIVE), e("little", "🐭", ADJECTIVE), e("good", "👌", ADJECTIVE),
            e("bad", "👎", ADJECTIVE), e("hot", "🔥", ADJECTIVE), e("all done", "🏁", ADJECTIVE), e("that", "👈", DETERMINER),
        ),
        listOf(
            e("not", "🚫", NEGATION), e("what", "❓", QUESTION), e("where", "🗺️", QUESTION), e("who", "🕵️", QUESTION),
            e("in", "📦", PREPOSITION), e("on", "🔛", PREPOSITION), e("to", "➡️", PREPOSITION), e("and", "🔗", CONJUNCTION),
        ),
    )

    /**
     * Vocabulary levels for the core board (1 = first 12 words, 2 = 24, 3 = 36, 4 = all).
     * Words keep their position at every level; lower levels only hide.
     */
    private val coreLevels: Map<String, Int> = buildMap {
        listOf("I", "you", "want", "like", "go", "stop", "help", "eat", "play", "more", "all done", "not")
            .forEach { put(it, 1) }
        listOf("it", "my", "get", "drink", "see", "do", "make", "good", "bad", "what", "Food", "People")
            .forEach { put(it, 2) }
        listOf("he", "she", "we", "they", "have", "feel", "is", "big", "little", "where", "in", "Feelings")
            .forEach { put(it, 3) }
    }

    private val phrases = listOf(
        e("Hi!", "👋", SOCIAL), e("Bye!", "🙋", SOCIAL), e("How are you?", "🙂", SOCIAL),
        e("I'm good", "😊", SOCIAL), e("Wait, I'm still talking", "✋", SOCIAL), e("I need a break", "🛑", SOCIAL),
        e("I don't know", "🤷", SOCIAL), e("I don't like that", "🙅", SOCIAL), e("Please leave me alone", "🚶", SOCIAL),
        e("Let's go!", "🚀", SOCIAL), e("Can I have a turn?", "🔄", SOCIAL), e("Your turn", "👉", SOCIAL),
        e("That's funny!", "😂", SOCIAL), e("I love you", "❤️", SOCIAL), e("I need the bathroom", "🚽", SOCIAL),
        e("That's not what I meant", "↩️", SOCIAL), e("Let me try again", "🔁", SOCIAL),
        e("Please be patient, I talk with this device", "📱", SOCIAL), e("Thank you", "🙏", SOCIAL),
        e("Please", "🥺", SOCIAL), e("Excuse me", "🙋", SOCIAL), e("Sorry", "😔", SOCIAL),
        e("Good morning", "🌅", SOCIAL), e("Good night", "🌙", SOCIAL), e("What's that?", "❓", SOCIAL),
        e("I want to go home", "🏠", SOCIAL), e("Help me, please", "🆘", SOCIAL), e("All done", "✅", SOCIAL),
        e("More, please", "➕", SOCIAL), e("Give me a minute", "⏳", SOCIAL), e("I feel sick", "🤒", SOCIAL),
        e("Stop that", "✋", SOCIAL), e("Yes, please", "👍", SOCIAL), e("No, thank you", "👎", SOCIAL),
        e("I have a question", "🙋", SOCIAL), e("Can you help me?", "🤝", SOCIAL),
    )

    private val strip = listOf(
        e("Yes", "✅", SOCIAL, "Yes"), e("No", "❌", SOCIAL, "No"), e("Stop", "✋", SOCIAL, "Stop!"),
        e("Help", "🆘", SOCIAL, "I need help"), e("Break", "🛑", SOCIAL, "I need a break"),
        e("Hurts", "🤕", SOCIAL, "Something hurts"),
    )

    private fun fill(id: String, name: String, cells: List<Cell>): Board {
        require(cells.size <= ROWS * COLS) { "Too many cells for $id" }
        return Board(id, name, ROWS, COLS, cells + List(ROWS * COLS - cells.size) { null })
    }

    /** Upgrades a saved vocabulary to the current format. */
    fun migrate(vocabulary: Vocabulary): Vocabulary {
        var v = vocabulary
        if (v.version < 2) {
            // Version 2 added vocabulary levels; give the starter core words their levels.
            val levels = create().home.cells.filterNotNull().associate { it.id to it.level }
            v = v.copy(boards = v.boards.map { b ->
                if (b.id != HOME) b else b.copy(cells = b.cells.map { c -> c?.let { it.copy(level = levels[it.id] ?: it.level) } })
            })
        }
        return v.copy(version = Vocabulary.CURRENT_VERSION)
    }

    fun create(): Vocabulary {
        val folderCells = folders.map { f ->
            Cell(
                id = "$HOME.folder-${f.id}",
                label = f.name,
                symbol = Symbol(emoji = f.emoji),
                type = FOLDER,
                action = CellAction.FOLDER,
                linkTo = f.id,
            )
        }
        val coreCells = MutableList<Cell?>(ROWS * COLS) { null }
        for (row in 0 until ROWS) {
            coreColumns.forEachIndexed { col, column -> coreCells[row * COLS + col] = column[row].toCell(HOME) }
            coreCells[row * COLS + COLS - 1] = folderCells[row]
        }
        for (i in coreCells.indices) {
            val cell = coreCells[i] ?: continue
            coreCells[i] = cell.copy(level = coreLevels[cell.label] ?: Cell.MAX_LEVEL)
        }
        val boards = buildList {
            add(Board(HOME, "Core", ROWS, COLS, coreCells))
            folders.forEach { f -> add(fill(f.id, f.name, f.words.map { it.toCell(f.id) })) }
            add(fill(PHRASES, "Quick phrases", phrases.map { it.toCell(PHRASES, CellAction.PHRASE) }))
        }
        return Vocabulary(
            homeBoardId = HOME,
            phrasesBoardId = PHRASES,
            boards = boards,
            quickStrip = strip.map { it.toCell("strip", CellAction.PHRASE) },
        )
    }
}
