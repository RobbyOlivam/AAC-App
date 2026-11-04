package com.robbyolivam.aac.shared

import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.DefaultVocabulary
import com.robbyolivam.aac.core.Settings
import com.robbyolivam.aac.core.VoiceAge
import com.robbyolivam.aac.core.VocabularyJson
import com.robbyolivam.aac.core.Zip
import com.robbyolivam.aac.core.platform.AudioOutput
import com.robbyolivam.aac.core.platform.SpeechOutput
import com.robbyolivam.aac.core.platform.VoiceInfo
import com.robbyolivam.aac.core.updateBoard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private class MemoryStorage : AppStorage {
    val files = LinkedHashMap<String, ByteArray>()
    override suspend fun read(path: String) = files[path]
    override suspend fun write(path: String, bytes: ByteArray) { files[path] = bytes }
    override suspend fun delete(path: String) { files.remove(path) }
    override suspend fun list(prefix: String) = files.keys.filter { it.startsWith(prefix) }
}

private class FakeSpeech : SpeechOutput {
    val said = mutableListOf<String>()
    val played = mutableListOf<String>()
    override val voices: StateFlow<List<VoiceInfo>> = MutableStateFlow(emptyList())
    override val available: StateFlow<Boolean> = MutableStateFlow(true)
    override var volume = 1f
    override fun speak(text: String, rate: Float, pitch: Float, voiceId: String?, age: VoiceAge, output: AudioOutput, level: Float?) { said += text }
    override fun playRecording(path: String, output: AudioOutput) { played += path }
    override fun earbudConnected() = false
    override fun stop() {}
    override fun shutdown() {}
}

private class FakeFiles(var next: Pair<String, ByteArray>? = null) : FileExchange {
    var saved: ByteArray? = null
    override suspend fun open(accept: String) = next
    override suspend fun save(name: String, bytes: ByteArray, mimeType: String): Boolean { saved = bytes; return true }
}

class TalkerTest {
    private val storage = MemoryStorage()
    private val speech = FakeSpeech()
    private val files = FakeFiles()
    private fun TestScope.talker() = Talker(Platform(storage, object : AppAssets { override suspend fun read(path: String): ByteArray? = null }, files, speech), this)

    @Test
    fun buildsAndSpeaksAMessage() = runTest {
        val t = talker().also { it.load() }
        val home = t.vocabulary.home
        val i = home.cells.first { it?.label == "I" }!!
        val want = home.cells.first { it?.label == "want" }!!
        t.select(i)
        t.select(want)
        assertEquals(listOf("I", "want"), t.message.map { it.text })
        assertEquals(listOf("I", "want"), speech.said, "speak each word is on by default")
        t.speakMessage()
        assertEquals("I want", speech.said.last())
        t.deleteWord()
        assertEquals(listOf("I"), t.message.map { it.text })
        t.clear()
        assertTrue(t.message.isEmpty())
    }

    @Test
    fun foldersAndReturningHome() = runTest {
        val t = talker().also { it.load() }
        val food = t.vocabulary.home.cells.first { it?.action == CellAction.FOLDER && it.label == "Food" }!!
        t.select(food)
        assertEquals(food.linkTo, t.boardId)
        assertTrue(t.message.isEmpty(), "folders don't add words")
        val apple = t.board.cells.first { it?.label == "apple" }!!
        t.select(apple)
        assertEquals(t.vocabulary.homeBoardId, t.boardId, "back home after a word from a folder")
        t.select(food)
        t.back()
        assertEquals(t.vocabulary.homeBoardId, t.boardId)
    }

    @Test
    fun wordThatOpensAPage() = runTest {
        val t = talker().also { it.load() }
        val foodId = t.vocabulary.boards.first { it.name == "Food" }.id
        storage.files[Talker.VOCAB] = VocabularyJson.encode(t.vocabulary.updateBoard(DefaultVocabulary.HOME) { b ->
            b.copy(cells = b.cells.map { c -> if (c?.label == "want") c.copy(linkTo = foodId) else c })
        }).encodeToByteArray()
        val t2 = talker().also { it.load() }
        t2.select(t2.vocabulary.home.cells.first { it?.label == "want" }!!)
        assertEquals(foodId, t2.boardId)
        assertEquals(listOf("want"), t2.message.map { it.text })
    }

    @Test
    fun settingsAndBoardsPersist() = runTest {
        val t = talker().also { it.load() }
        t.updateSettings { it.copy(speechRate = 0.7f, speakOnSelect = false) }
        advanceUntilIdle()
        val again = talker().also { it.load() }
        assertEquals(0.7f, again.settings.speechRate)
        again.select(again.vocabulary.home.cells.first { it?.label == "I" }!!)
        assertTrue(speech.said.isEmpty(), "speak each word turned off")
    }

    @Test
    fun backupRoundTripAndUndo() = runTest {
        val t = talker().also { it.load() }
        storage.files["media/photo.png"] = byteArrayOf(1, 2, 3)
        t.saveBackup()
        advanceUntilIdle()
        val zip = Zip.read(files.saved!!)
        assertTrue("vocabulary.json" in zip && "settings.json" in zip && "media/photo.png" in zip)

        // A backup whose boards differ (one renamed page), restored and then undone.
        val changed = t.vocabulary.copy(boards = t.vocabulary.boards.map { if (it.id == t.vocabulary.homeBoardId) it.copy(name = "Griffin's core") else it })
        val backup = Zip.write(mapOf(
            "vocabulary.json" to VocabularyJson.encode(changed).encodeToByteArray(),
            "settings.json" to "{\"pitch\": 1.3}".encodeToByteArray(),
            "media/new.png" to byteArrayOf(9),
        ))
        files.next = "backup.zip" to backup
        t.importFile()
        advanceUntilIdle()
        assertEquals("Griffin's core", t.vocabulary.home.name, t.notice)
        assertEquals(1.3f, t.settings.pitch)
        assertTrue("media/new.png" in storage.files)
        t.restorePrevious()
        advanceUntilIdle()
        assertNotEquals("Griffin's core", t.vocabulary.home.name)
    }

    @Test
    fun badFileSaysSo() = runTest {
        val t = talker().also { it.load() }
        files.next = "notes.txt" to "hello".encodeToByteArray()
        t.importFile()
        advanceUntilIdle()
        assertTrue(t.notice!!.startsWith("Couldn't read notes.txt"), t.notice)
        assertEquals(Settings(), t.settings)
    }
}
