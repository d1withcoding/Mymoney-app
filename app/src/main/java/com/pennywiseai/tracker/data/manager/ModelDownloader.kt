package com.pennywiseai.tracker.data.manager

import com.pennywiseai.tracker.core.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Offline build stub: model downloads are not supported. */
@Singleton
class ModelDownloader @Inject constructor() {
    enum class Failure { NO_SPACE, NETWORK, INTEGRITY, ROAMING }

    data class Progress(
        val downloadedBytes: Long = 0,
        val totalBytes: Long = Constants.ModelDownload.MODEL_SIZE_BYTES,
        val failure: Failure? = null,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    val isRunning: Boolean = false

    fun start() = Unit
    fun resumeIfInterrupted(): Boolean = false
    suspend fun adoptLegacyDownload(): Boolean = false
    fun cancel() = Unit
}
