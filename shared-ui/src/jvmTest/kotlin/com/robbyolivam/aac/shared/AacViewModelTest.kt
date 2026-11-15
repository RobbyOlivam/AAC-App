package com.robbyolivam.aac.shared

import com.robbyolivam.aac.core.CellAction
import com.robbyolivam.aac.core.DefaultVocabulary
import com.robbyolivam.aac.core.VocabularyJson
import com.robbyolivam.aac.core.VoiceAge
import com.robbyolivam.aac.core.Zip
import com.robbyolivam.aac.core.platform.AudioOutput
import com.robbyolivam.aac.core.platform.SpeechOutput
import com.robbyolivam.aac.core.platform.VoiceInfo
import com.robbyolivam.aac.core.updateBoard
import com.robbyolivam.aac.ui.AacViewModel
import com.robbyolivam.aac.ui.EditTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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

private class FakeDevice : DeviceServices {
    var now = 1_800_000_000_000L
    override fun nowMillis() = now
    override fun utcOffsetMillis() = 0L
    override fun keepScreenOn(on: Boolean) {}
    override fun shareText(text: String) = true
}

private object FakeImages : ImageTools {
    override suspend fun size(bytes: ByteArray) = 10 to 10
    override suspend fun jpeg(bytes: ByteArray, maxSize: Int, crop: CropRect?) = byteArrayOf(-1, -40) + bytes.take(4)
}

private object NoAssets : AppAssets {
    override suspend fun read(path: String): ByteArray? = null
}

private object NoFiles : FileExchange {
    override suspend fun open(accept: String, camera: Boolean): Pair<String, ByteArray>? = null
    override suspend fun save(name: String, bytes: ByteArray, mimeType: String) = true
}

class AacViewModelTest {
    private val storage = MemoryStorage()
    private val speech = FakeSpeech()
    private val device = FakeDevice()
    private val platform = Platform(storage, NoAssets, NoFiles, speech, FakeImages, null, device)

    private suspend fun TestScope.vm(): AacViewModel = AacViewModel(platform, backgroundScope).also { it.load() }

    @Test
    fun buildsAndSpeaksAMessage() = runTest {
        val vm = vm()
        val home = vm.currentBoard
        val i = home.cells.indexOfFirst { it?.label == "I" }
        val want = home.cells.indexOfFirst { it?.label == "want" }
        vm.onCellSelected(home.id, i, home.cells[i])
        vm.onCellSelected(home.id, want, home.cells[want])
        assertEquals("I want", vm.messageText())
        assertEquals(listOf("I", "want"), speech.said, "speak each word is on by default")
        vm.speakMessage()
        assertEquals("I want", speech.said.last())
        vm.backspace()
        assertEquals("I", vm.messageText())
        vm.clearMessage()
        assertEquals("", vm.messageText())
    }

    @Test
    fun foldersWordsThatOpenPagesAndReturningHome() = runTest {
        val vm = vm()
        val home = vm.currentBoard
        val foodIndex = home.cells.indexOfFirst { it?.action == CellAction.FOLDER && it.label == "Food" }
        vm.onCellSelected(home.id, foodIndex, home.cells[foodIndex])
        val food = vm.currentBoard
        assertEquals(home.cells[foodIndex]!!.linkTo, food.id)
        val apple = food.cells.indexOfFirst { it?.label == "apple" }
        vm.onCellSelected(food.id, apple, food.cells[apple])
        assertEquals(home.id, vm.currentBoardId, "back home after a word from a folder")

        // A word that also opens a page (imported WordPower boards).
        vm.store.edit { v -> v.updateBoard(home.id) { b -> b.copy(cells = b.cells.map { c -> if (c?.label == "want") c.copy(linkTo = food.id) else c }) } }
        val want = vm.currentBoard.cells.indexOfFirst { it?.label == "want" }
        vm.onCellSelected(home.id, want, vm.currentBoard.cells[want])
        assertEquals(food.id, vm.currentBoardId)
        assertTrue(vm.messageText().endsWith("want"))
    }

    @Test
    fun editingWithUndoAndRedo() = runTest {
        val vm = vm()
        val home = vm.currentBoard
        val index = home.cells.indexOfFirst { it?.label == "want" }
        vm.enterEditMode()
        val target = EditTarget.Grid(home.id, index)
        vm.saveCell(target, home.cells[index]!!.copy(label = "wish"))
        assertEquals("wish", vm.currentBoard.cells[index]!!.label)
        vm.undo()
        assertEquals("want", vm.currentBoard.cells[index]!!.label)
        vm.redo()
        assertEquals("wish", vm.currentBoard.cells[index]!!.label)
        vm.saveCell(target, null)
        assertNull(vm.currentBoard.cells[index])
    }

    @Test
    fun boardsSettingsAndPredictionsPersist() = runTest {
        val vm = vm()
        val home = vm.currentBoard
        val index = home.cells.indexOfFirst { it?.label == "want" }
        vm.saveCell(EditTarget.Grid(home.id, index), home.cells[index]!!.copy(label = "wish"))
        vm.updateSettings { it.copy(speechRate = 0.7f, speakOnSelect = false) }
        runCurrent()
        val again = vm()
        assertEquals("wish", again.currentBoard.cells[index]!!.label)
        assertEquals(0.7f, again.settings.value.speechRate)
        assertTrue(storage.list("snapshots/").isNotEmpty(), "a snapshot is kept")
    }

    @Test
    fun backupRoundTrip() = runTest {
        val vm = vm()
        storage.files["media/photo.jpg"] = byteArrayOf(1, 2, 3)
        val zip = Zip.read(vm.backup.export())
        assertTrue("vocabulary.json" in zip && "settings.json" in zip && "predictor.json" in zip && "media/photo.jpg" in zip)

        val changed = vm.vocabulary.value.copy(boards = vm.vocabulary.value.boards.map { if (it.id == DefaultVocabulary.HOME) it.copy(name = "My core") else it })
        vm.backup.import(mapOf(
            "vocabulary.json" to VocabularyJson.encode(changed).encodeToByteArray(),
            "settings.json" to "{\"pitch\": 1.3}".encodeToByteArray(),
            "media/new.jpg" to byteArrayOf(9),
        ))
        assertEquals("My core", vm.currentBoard.name)
        assertEquals(1.3f, vm.settings.value.pitch)
        assertTrue("media/new.jpg" in storage.files)
        vm.undo()
        assertNotEquals("My core", vm.vocabulary.value.home.name, "a restore can be undone")
    }

    @Test
    fun photosAreSavedSmallAndUsageIsLogged() = runTest {
        val vm = vm()
        val path = vm.media.saveImage(byteArrayOf(1, 2, 3, 4, 5), CropRect(0f, 0f, 1f, 1f))!!
        assertTrue(path.startsWith("media/") && path.endsWith(".jpg") && path in storage.files)

        vm.updateSettings { it.copy(usageLogging = true) }
        val home = vm.currentBoard
        val i = home.cells.indexOfFirst { it?.label == "I" }
        vm.onCellSelected(home.id, i, home.cells[i])
        vm.speakMessage()
        runCurrent()
        val events = vm.usage.readAll()
        assertEquals(listOf("I", "I"), events.map { it.text })
        assertTrue(vm.usage.csv().decodeToString().startsWith("time_utc,"))
        vm.usage.clear()
        assertTrue(vm.usage.readAll().isEmpty())
        assertFalse(storage.files.keys.any { it.startsWith("usage/") })
    }
}
