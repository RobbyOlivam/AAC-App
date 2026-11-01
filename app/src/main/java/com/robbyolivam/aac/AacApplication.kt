package com.robbyolivam.aac

import android.app.Application
import com.robbyolivam.aac.data.BackupManager
import com.robbyolivam.aac.data.MediaFiles
import com.robbyolivam.aac.data.OpenBoardFiles
import com.robbyolivam.aac.data.PdfExporter
import com.robbyolivam.aac.data.SettingsStore
import com.robbyolivam.aac.data.SymbolLibrary
import com.robbyolivam.aac.data.VocabularyStore
import com.robbyolivam.aac.speech.AudioRecorder
import com.robbyolivam.aac.speech.Speaker
import java.io.File

/** Simple manual dependency container; there are few enough pieces that DI isn't needed yet. */
class AacApplication : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var vocabulary: VocabularyStore
        private set
    lateinit var media: MediaFiles
        private set
    lateinit var speaker: Speaker
        private set
    lateinit var recorder: AudioRecorder
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var openBoard: OpenBoardFiles
        private set
    lateinit var pdf: PdfExporter
        private set
    lateinit var symbols: SymbolLibrary
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        removeShelvedVoicePacks()
        vocabulary = VocabularyStore(this)
        media = MediaFiles(this, vocabulary)
        speaker = Speaker(this)
        recorder = AudioRecorder(this)
        backup = BackupManager(vocabulary, settings, media)
        openBoard = OpenBoardFiles(vocabulary, media)
        symbols = SymbolLibrary(this)
        pdf = PdfExporter(vocabulary, symbols)
    }

    /**
     * Downloadable voice packs were tried in 0.3.0 and shelved (code kept on the
     * shelved/neural-voice-packs branch). Frees their downloads (~63 MB each) and moves anyone
     * who picked one back to the default voice.
     */
    private fun removeShelvedVoicePacks() {
        File(filesDir, "voices").deleteRecursively()
        File(filesDir, "voice-data-v1").deleteRecursively()
        if (settings.settings.value.voiceName?.startsWith("pack:") == true) settings.update { it.copy(voiceName = null) }
    }
}
