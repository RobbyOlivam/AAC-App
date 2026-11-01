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
    /** The core board has a 7th column of folders (vocabulary version 3). */
    const val CORE_COLS = 7

    const val HOME = "core"
    const val PHRASES = "phrases"

    private class Entry(val label: String, val emoji: String, val type: WordType, val spoken: String? = null)

    private fun e(label: String, emoji: String, type: WordType, spoken: String? = null) = Entry(label, emoji, type, spoken)

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun Entry.toCell(boardId: String, action: CellAction = CellAction.WORD, linkTo: String? = null) = Cell(
        id = "$boardId.${slug(label)}",
        label = label,
        spoken = spoken,
        symbol = Symbol(emoji = emoji, libraryId = defaultLibrarySymbols["$boardId.${slug(label)}"]),
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

    /** Folders in the core board's 7th column (added in vocabulary version 3). */
    private val moreFolders = listOf(
        Folder(
            "body", "Body", "🧍", listOf(
                e("head", "🗣️", NOUN), e("hair", "💇", NOUN), e("eyes", "👀", NOUN), e("ears", "👂", NOUN),
                e("nose", "👃", NOUN), e("mouth", "👄", NOUN), e("teeth", "🦷", NOUN), e("tongue", "👅", NOUN),
                e("face", "🙂", NOUN), e("neck", "🦒", NOUN), e("shoulder", "🤷", NOUN), e("arm", "💪", NOUN),
                e("elbow", "💪", NOUN), e("hand", "✋", NOUN), e("fingers", "🖐️", NOUN), e("tummy", "🍽️", NOUN),
                e("back", "🔙", NOUN), e("bottom", "🍑", NOUN), e("leg", "🦵", NOUN), e("knee", "🦵", NOUN),
                e("foot", "🦶", NOUN), e("toes", "🦶", NOUN), e("skin", "✋", NOUN), e("heart", "❤️", NOUN),
                e("chest", "👕", NOUN), e("throat", "🗣️", NOUN), e("chin", "🙂", NOUN), e("cheek", "😊", NOUN),
                e("lips", "👄", NOUN), e("nails", "💅", NOUN),
            )
        ),
        Folder(
            "school", "School", "🏫", listOf(
                e("classroom", "🏫", PLACE), e("desk", "🪑", NOUN), e("pencil", "✏️", NOUN), e("pen", "🖊️", NOUN),
                e("scissors", "✂️", NOUN), e("glue", "🧴", NOUN), e("math", "➗", NOUN), e("reading", "📖", NOUN),
                e("writing", "📝", NOUN), e("art", "🎨", NOUN), e("gym", "🤸", NOUN), e("recess", "🎠", NOUN),
                e("homework", "📝", NOUN), e("test", "💯", NOUN), e("circle time", "⭕", NOUN), e("story", "📚", NOUN),
                e("letters", "🔤", NOUN), e("numbers", "🔢", NOUN), e("colors", "🌈", NOUN), e("science", "🔬", NOUN),
                e("principal", "🧑‍💼", PERSON), e("classmate", "🧒", PERSON), e("line up", "🚶", VERB), e("sit down", "🪑", VERB),
                e("raise hand", "🙋", VERB), e("finish work", "🏁", VERB), e("library", "📚", PLACE), e("cafeteria", "🍱", PLACE),
                e("bus", "🚌", PLACE), e("backpack", "🎒", NOUN), e("lunchbox", "🍱", NOUN), e("calendar", "📅", NOUN),
                e("ruler", "📏", NOUN), e("eraser", "🧽", NOUN), e("whiteboard", "⬜", NOUN), e("quiz", "❓", NOUN),
            )
        ),
        Folder(
            "animals", "Animals", "🐾", listOf(
                e("dog", "🐕", NOUN), e("cat", "🐈", NOUN), e("bird", "🐦", NOUN), e("fish", "🐟", NOUN),
                e("horse", "🐴", NOUN), e("cow", "🐄", NOUN), e("pig", "🐖", NOUN), e("sheep", "🐑", NOUN),
                e("chicken", "🐔", NOUN), e("duck", "🦆", NOUN), e("rabbit", "🐇", NOUN), e("mouse", "🐁", NOUN),
                e("frog", "🐸", NOUN), e("turtle", "🐢", NOUN), e("snake", "🐍", NOUN), e("lion", "🦁", NOUN),
                e("tiger", "🐯", NOUN), e("elephant", "🐘", NOUN), e("monkey", "🐒", NOUN), e("bear", "🐻", NOUN),
                e("giraffe", "🦒", NOUN), e("zebra", "🦓", NOUN), e("dinosaur", "🦖", NOUN), e("shark", "🦈", NOUN),
                e("whale", "🐳", NOUN), e("dolphin", "🐬", NOUN), e("butterfly", "🦋", NOUN), e("bee", "🐝", NOUN),
                e("spider", "🕷️", NOUN), e("bug", "🐛", NOUN), e("owl", "🦉", NOUN), e("penguin", "🐧", NOUN),
            )
        ),
        Folder(
            "activities", "Activities", "⚽", listOf(
                e("coloring", "🖍️", NOUN), e("painting", "🎨", NOUN), e("singing", "🎤", NOUN), e("dancing", "💃", NOUN),
                e("swimming", "🏊", NOUN), e("biking", "🚲", NOUN), e("soccer", "⚽", NOUN), e("basketball", "🏀", NOUN),
                e("baseball", "⚾", NOUN), e("football", "🏈", NOUN), e("video games", "🎮", NOUN), e("movie", "🎬", NOUN),
                e("cooking", "🧑‍🍳", NOUN), e("baking", "🧁", NOUN), e("lego", "🧱", NOUN), e("cards", "🃏", NOUN),
                e("board game", "🎲", NOUN), e("hide and seek", "🙈", NOUN), e("tag", "🏃", NOUN), e("trampoline", "🤸", NOUN),
                e("slide", "🎢", NOUN), e("sandbox", "🏖️", NOUN), e("walk", "🚶", NOUN), e("dolls", "🪆", NOUN),
                e("trains", "🚂", NOUN), e("drawing", "✏️", NOUN), e("camping", "⛺", NOUN), e("fishing", "🎣", NOUN),
                e("shopping", "🛒", NOUN), e("party", "🎉", NOUN), e("bath time", "🛁", NOUN), e("bedtime story", "📖", NOUN),
                e("piano", "🎹", NOUN), e("guitar", "🎸", NOUN), e("drums", "🥁", NOUN), e("craft", "✂️", NOUN),
            )
        ),
        Folder(
            "little-words", "Little words", "🔤", listOf(
                e("a", "🅰️", DETERMINER), e("the", "👉", DETERMINER), e("this", "👇", DETERMINER), e("these", "👇", DETERMINER),
                e("those", "👉", DETERMINER), e("some", "🔢", DETERMINER), e("all", "💯", DETERMINER), e("any", "❔", DETERMINER),
                e("every", "🔁", DETERMINER), e("other", "🔀", DETERMINER), e("him", "👦", PRONOUN), e("her", "👧", PRONOUN),
                e("us", "👫", PRONOUN), e("them", "👥", PRONOUN), e("his", "👦", PRONOUN), e("our", "👫", PRONOUN),
                e("their", "👥", PRONOUN), e("your", "👉", PRONOUN), e("mine", "🙋", PRONOUN), e("when", "⏰", QUESTION),
                e("why", "🤔", QUESTION), e("how", "❓", QUESTION), e("which", "👆", QUESTION), e("with", "🤝", PREPOSITION),
                e("for", "🎁", PREPOSITION), e("of", "🔗", PREPOSITION), e("at", "📍", PREPOSITION), e("from", "⬅️", PREPOSITION),
                e("out", "📤", PREPOSITION), e("off", "📴", PREPOSITION), e("under", "⬇️", PREPOSITION), e("over", "⬆️", PREPOSITION),
                e("by", "📍", PREPOSITION), e("about", "💬", PREPOSITION), e("but", "↔️", CONJUNCTION), e("or", "🔀", CONJUNCTION),
                e("because", "💡", CONJUNCTION), e("if", "❔", CONJUNCTION), e("so", "➡️", CONJUNCTION), e("then", "⏭️", ADVERB),
                e("also", "➕", ADVERB), e("just", "☝️", ADVERB), e("only", "1️⃣", ADVERB), e("maybe", "🤷", ADVERB),
                e("really", "‼️", ADVERB), e("can't", "🚫", NEGATION), e("don't", "🙅", NEGATION), e("won't", "⛔", NEGATION),
            )
        ),
        Folder(
            "more-actions", "More actions", "🤸", listOf(
                e("ask", "🙋", VERB), e("answer", "💬", VERB), e("bring", "🎁", VERB), e("carry", "🧳", VERB),
                e("catch", "🧤", VERB), e("change", "🔄", VERB), e("choose", "👆", VERB), e("climb", "🧗", VERB),
                e("color", "🖍️", VERB), e("count", "🔢", VERB), e("cry", "😢", VERB), e("drive", "🚗", VERB),
                e("fall", "🍂", VERB), e("fly", "✈️", VERB), e("follow", "👣", VERB), e("forget", "🤔", VERB),
                e("grow", "🌱", VERB), e("hear", "👂", VERB), e("hide", "🙈", VERB), e("hit", "👊", VERB),
                e("hold", "🤲", VERB), e("keep", "📦", VERB), e("kick", "🦶", VERB), e("laugh", "😂", VERB),
                e("learn", "🧠", VERB), e("lie down", "🛌", VERB), e("lose", "😞", VERB), e("meet", "🤝", VERB),
                e("move", "↔️", VERB), e("pay", "💵", VERB), e("pick", "👆", VERB), e("pour", "🥛", VERB),
                e("pull", "🧲", VERB), e("remember", "💭", VERB), e("rest", "😌", VERB), e("show", "👀", VERB),
                e("shout", "📢", VERB), e("smell", "👃", VERB), e("smile", "😊", VERB), e("start", "▶️", VERB),
                e("stay", "🏠", VERB), e("taste", "👅", VERB), e("teach", "🧑‍🏫", VERB), e("touch", "👆", VERB),
                e("use", "🛠️", VERB), e("wake up", "⏰", VERB), e("wear", "👕", VERB), e("win", "🏆", VERB),
            )
        ),
        Folder(
            "health", "Health", "🩺", listOf(
                e("sick", "🤒", ADJECTIVE), e("hurt", "🤕", ADJECTIVE), e("pain", "😣", NOUN), e("headache", "🤕", NOUN),
                e("tummy ache", "🤢", NOUN), e("itchy", "🐜", ADJECTIVE), e("cough", "😷", VERB), e("sneeze", "🤧", VERB),
                e("fever", "🤒", NOUN), e("dizzy", "😵", ADJECTIVE), e("throw up", "🤮", VERB), e("bleeding", "🩸", ADJECTIVE),
                e("medicine", "💊", NOUN), e("doctor", "🧑‍⚕️", PERSON), e("nurse", "🧑‍⚕️", PERSON), e("hospital", "🏥", PLACE),
                e("band-aid", "🩹", NOUN), e("ice pack", "🧊", NOUN), e("shot", "💉", NOUN), e("wheelchair", "🦽", NOUN),
                e("toilet", "🚽", NOUN), e("wash hands", "🧼", VERB), e("brush teeth", "🪥", VERB), e("allergy", "🤧", NOUN),
                e("too hot", "🥵", ADJECTIVE), e("too cold", "🥶", ADJECTIVE), e("sleepy", "😴", ADJECTIVE), e("better", "🙂", ADJECTIVE),
                e("worse", "😖", ADJECTIVE), e("glasses", "👓", NOUN), e("hearing aid", "🦻", NOUN), e("thermometer", "🌡️", NOUN),
            )
        ),
        Folder(
            "weather", "Weather", "🌦️", listOf(
                e("sunny", "☀️", ADJECTIVE), e("cloudy", "☁️", ADJECTIVE), e("rain", "🌧️", NOUN), e("snow", "❄️", NOUN),
                e("windy", "💨", ADJECTIVE), e("storm", "⛈️", NOUN), e("thunder", "🌩️", NOUN), e("rainbow", "🌈", NOUN),
                e("warm", "🌤️", ADJECTIVE), e("cold", "🥶", ADJECTIVE), e("sun", "☀️", NOUN), e("moon", "🌙", NOUN),
                e("star", "⭐", NOUN), e("sky", "🌌", NOUN), e("tree", "🌳", NOUN), e("flower", "🌸", NOUN),
                e("grass", "🌱", NOUN), e("leaf", "🍃", NOUN), e("rock", "🪨", NOUN), e("water", "💧", NOUN),
                e("mud", "🟫", NOUN), e("puddle", "💦", NOUN), e("mountain", "⛰️", NOUN), e("river", "🏞️", NOUN),
                e("ocean", "🌊", NOUN), e("fire", "🔥", NOUN), e("spring", "🌷", NOUN), e("summer", "🏖️", NOUN),
                e("fall", "🍂", NOUN), e("winter", "⛄", NOUN), e("umbrella", "☂️", NOUN), e("jacket", "🧥", NOUN),
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
        listOf("Body", "Animals").forEach { put(it, 3) }
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
        if (v.version < 3) {
            // Version 3 also bundled a symbol library: starter buttons whose picture the person
            // never changed get the matching library symbol.
            val defaults = create().boards.flatMap { b -> b.indexedCells().map { it.second } }.associateBy { it.id }
            v = v.copy(boards = v.boards.map { b ->
                b.copy(cells = b.cells.map { c ->
                    val d = c?.let { defaults[it.id] }
                    if (c != null && d != null && c.symbol.imagePath == null && c.symbol.libraryId == null && c.symbol.emoji == d.symbol.emoji) {
                        c.copy(symbol = c.symbol.copy(libraryId = d.symbol.libraryId))
                    } else {
                        c
                    }
                })
            })
            // Version 3 added a 7th core column with eight new folders. Existing buttons keep
            // their row and column; the new column is only filled where it's empty.
            val existing = v.boards.map { it.id }.toSet()
            val newBoards = moreFolderBoards().filter { it.id !in existing }
            v = v.copy(boards = v.boards + newBoards)
            val home = v.home
            if (home.cols == COLS && home.rows == ROWS) {
                var grown = home.resized(ROWS, CORE_COLS)
                moreFolderCells().forEachIndexed { row, cell ->
                    val index = row * CORE_COLS + CORE_COLS - 1
                    if (grown.cells[index] == null && v.board(cell.linkTo!!) != null) grown = grown.withCell(index, cell)
                }
                v = v.updateBoard(home.id) { grown }
            }
        }
        if (v.version < 4) {
            // Version 4 fixed starter pictures that didn't fit their folder ("tablet" showed
            // pills). Only buttons still showing the old picture change.
            v = v.copy(boards = v.boards.map { b ->
                b.copy(cells = b.cells.map { c -> c?.let(::withFixedSymbol) }, scene = b.scene?.let { s ->
                    s.copy(hotspots = s.hotspots.map { h -> h.copy(cell = withFixedSymbol(h.cell)) })
                })
            })
        }
        return v.copy(version = Vocabulary.CURRENT_VERSION)
    }

    private fun withFixedSymbol(cell: Cell): Cell {
        val old = replacedDefaultSymbols[cell.id] ?: return cell
        val new = defaultLibrarySymbols[cell.id] ?: return cell
        return if (cell.symbol.imagePath == null && cell.symbol.libraryId in old) cell.copy(symbol = cell.symbol.copy(libraryId = new)) else cell
    }

    private fun Folder.toFolderCell() = Cell(
        id = "$HOME.folder-$id",
        label = name,
        symbol = Symbol(emoji = emoji, libraryId = defaultLibrarySymbols["$HOME.folder-$id"]),
        type = FOLDER,
        action = CellAction.FOLDER,
        linkTo = id,
    )

    /** Starter cells for the core board's 7th column, one per row. */
    private fun moreFolderCells(): List<Cell> = moreFolders.map { f ->
        f.toFolderCell().copy(level = coreLevels[f.name] ?: Cell.MAX_LEVEL)
    }

    private fun moreFolderBoards(): List<Board> = moreFolders.map { f -> fill(f.id, f.name, f.words.map { it.toCell(f.id) }) }

    fun create(): Vocabulary {
        val folderCells = folders.map { it.toFolderCell() }
        val coreCells = MutableList<Cell?>(ROWS * CORE_COLS) { null }
        for (row in 0 until ROWS) {
            coreColumns.forEachIndexed { col, column -> coreCells[row * CORE_COLS + col] = column[row].toCell(HOME) }
            coreCells[row * CORE_COLS + COLS - 1] = folderCells[row]
        }
        for (i in coreCells.indices) {
            val cell = coreCells[i] ?: continue
            coreCells[i] = cell.copy(level = coreLevels[cell.label] ?: Cell.MAX_LEVEL)
        }
        moreFolderCells().forEachIndexed { row, cell -> coreCells[row * CORE_COLS + CORE_COLS - 1] = cell }
        val boards = buildList {
            add(Board(HOME, "Core", ROWS, CORE_COLS, coreCells))
            folders.forEach { f -> add(fill(f.id, f.name, f.words.map { it.toCell(f.id) })) }
            addAll(moreFolderBoards())
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
