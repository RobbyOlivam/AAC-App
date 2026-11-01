package com.robbyolivam.aac.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.robbyolivam.aac.core.ExpansionCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Whether "Expand my words" can run on this phone, and if not, why not. */
sealed interface ExpanderState {
    data object Checking : ExpanderState
    /** This phone has no on-device Gemini (it needs Android AICore, e.g. Pixel 9 or later). */
    data object Unsupported : ExpanderState
    data object NeedsDownload : ExpanderState
    data class Downloading(val bytes: Long, val total: Long) : ExpanderState
    data object Ready : ExpanderState
    data class Failed(val message: String) : ExpanderState
}

/**
 * "Expand my words": Gemini Nano, running on the phone through Android AICore, turns short
 * messages ("want juice") into full sentences ("I want some juice, please."). Nothing leaves the
 * phone. Every answer is checked by [ExpansionCheck] so it can't add new ideas, and it's only
 * ever offered as a suggestion — never spoken by itself.
 */
class WordExpander {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var model: GenerativeModel? = null

    private val _state = MutableStateFlow<ExpanderState>(ExpanderState.Checking)
    val state: StateFlow<ExpanderState> = _state.asStateFlow()

    private fun client(): GenerativeModel? =
        model ?: runCatching { Generation.getClient() }.onFailure { Log.w(TAG, "No on-device model", it) }.getOrNull()?.also { model = it }

    /** Checks (again) whether the model is on the phone. Cheap; call when the setting is shown. */
    fun refresh() {
        scope.launch {
            // Phones without AICore may never answer; treat silence as "not available".
            val status = withTimeoutOrNull(5_000) { runCatching { client()?.checkStatus() }.getOrNull() }
            _state.value = when (status) {
                FeatureStatus.AVAILABLE -> ExpanderState.Ready
                FeatureStatus.DOWNLOADABLE -> ExpanderState.NeedsDownload
                FeatureStatus.DOWNLOADING -> ExpanderState.Downloading(0, 0)
                else -> ExpanderState.Unsupported
            }
        }
    }

    /** Asks Android to download Gemini Nano (Android does the download; this app stays offline). */
    fun download() {
        val m = client() ?: return
        scope.launch {
            var total = 0L
            runCatching {
                m.download().collect { status ->
                    _state.value = when (status) {
                        is DownloadStatus.DownloadStarted -> ExpanderState.Downloading(0, status.bytesToDownload.also { total = it })
                        is DownloadStatus.DownloadProgress -> ExpanderState.Downloading(status.totalBytesDownloaded, total)
                        is DownloadStatus.DownloadCompleted -> ExpanderState.Ready
                        is DownloadStatus.DownloadFailed -> ExpanderState.Failed(status.e.message ?: "Download failed")
                        else -> _state.value
                    }
                }
            }.onFailure { _state.value = ExpanderState.Failed(it.message ?: "Download failed") }
        }
    }

    /** A full sentence for [words], or null (not ready, too slow, or the answer didn't pass the check). */
    suspend fun expand(words: String): String? {
        if (_state.value != ExpanderState.Ready) return null
        val m = client() ?: return null
        val answer = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { m.generateContent(prompt(words)).candidates.firstOrNull()?.text }
                .onFailure { Log.w(TAG, "Expansion failed", it) }
                .getOrNull()
        }
        return ExpansionCheck.accept(words, answer)
    }

    private fun prompt(words: String) = """
        You help someone who talks using a picture-symbol communication app. They tap a few words;
        you turn them into one short, natural sentence they would say, in the first person when
        the words don't say otherwise. Keep every one of their words (you may change word forms,
        like "go" to "went"). Add only small words such as "I", "the", "some", "to", "please".
        Don't add new ideas, names, places or details. Reply with only the sentence.

        Words: want juice
        Sentence: I want some juice, please.

        Words: he go school yesterday
        Sentence: He went to school yesterday.

        Words: $words
        Sentence:
    """.trimIndent()

    private companion object {
        const val TAG = "WordExpander"
        const val TIMEOUT_MS = 6_000L
    }
}
