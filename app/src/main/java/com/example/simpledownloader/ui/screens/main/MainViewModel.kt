package com.example.simpledownloader.ui.screens.main

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpledownloader.domain.model.AnalysisStep
import com.example.simpledownloader.domain.model.DownloadState
import com.example.simpledownloader.domain.model.VideoInfo
import com.example.simpledownloader.domain.model.DownloadSettings
import com.example.simpledownloader.domain.usecase.AnalyzeUrlUseCase
import com.example.simpledownloader.domain.usecase.DownloadVideoUseCase
import com.example.simpledownloader.data.preferences.DownloadSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

import kotlinx.coroutines.flow.receiveAsFlow

/**
 * ViewModel for the Main Screen
 * Manages URL input, analysis state, and video information
 */
class MainViewModel(
    private val analyzeUrlUseCase: AnalyzeUrlUseCase,
    private val downloadVideoUseCase: DownloadVideoUseCase,
    private val settingsManager: DownloadSettingsManager
) : ViewModel() {
    
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    private val _currentAnalysisStep = MutableStateFlow<AnalysisStep?>(null)
    val currentAnalysisStep: StateFlow<AnalysisStep?> = _currentAnalysisStep.asStateFlow()
    
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val videoInfo: StateFlow<VideoInfo?> = _videoInfo.asStateFlow()
    
    // Active downloads list
    private val _activeDownloads = MutableStateFlow<List<com.example.simpledownloader.domain.model.ActiveDownload>>(emptyList())
    val activeDownloads: StateFlow<List<com.example.simpledownloader.domain.model.ActiveDownload>> = _activeDownloads.asStateFlow()
    
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // URL validation error (for immediate feedback)
    private val _urlValidationError = MutableStateFlow<String?>(null)
    val urlValidationError: StateFlow<String?> = _urlValidationError.asStateFlow()

    private var analysisJob: kotlinx.coroutines.Job? = null

    // UI Events
    sealed interface UiEvent {
        data class ShowToast(val message: String) : UiEvent
        object RequestDownloadPath : UiEvent
    }
    
    private val _uiEvent = kotlinx.coroutines.channels.Channel<UiEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()
    
    // Download settings
    private val _downloadSettings = MutableStateFlow(settingsManager.getCurrentSettings())
    val downloadSettings: StateFlow<DownloadSettings> = _downloadSettings.asStateFlow()
    
    init {
        // Observe settings changes
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                _downloadSettings.value = settings
            }
        }
    }

    /**
     * Update URL input
     */
    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        
        // Validate URL
        validateUrl(newUrl)
        
        // Clear previous states when URL changes
        if (newUrl.isBlank()) {
            resetState()
        } else if (newUrl.trim().startsWith("http") && _urlValidationError.value == null) {
            // Auto-analyze if it looks like a valid URL
            analyzeUrl()
        }
    }
    
    /**
     * Validate URL format
     */
    private fun validateUrl(url: String) {
        if (url.isBlank()) {
            _urlValidationError.value = null
            return
        }
        
        val trimmedUrl = url.trim()
        
        // Basic URL validation
        val isValid = trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")
        
        _urlValidationError.value = if (!isValid && trimmedUrl.isNotEmpty()) {
            "URL geçersiz!"
        } else {
            null
        }
    }
    
    /**
     * Paste URL from clipboard
     */
    fun onPasteUrl(clipboardText: String) {
        // onUrlChanged will handle the analysis
        onUrlChanged(clipboardText.trim())
    }
    
    /**
     * Analyze the URL
     */
    fun analyzeUrl() {
        val url = _urlInput.value.trim()
        if (url.isBlank()) {
            _errorMessage.value = "Please enter a URL"
            return
        }
        
        // Cancel any existing analysis
        analysisJob?.cancel()
        
        _isAnalyzing.value = true
        _errorMessage.value = null
        
        analysisJob = viewModelScope.launch {
            try {
                Timber.d("Starting URL analysis for: $url")
                analyzeUrlUseCase(url).collect { state ->
                    Timber.d("Analysis state: $state")
                    _downloadState.value = state
                    
                    when (state) {
                        is DownloadState.Analyzing -> {
                            _currentAnalysisStep.value = state.step
                        }
                        is DownloadState.Ready -> {
                            _isAnalyzing.value = false
                            _videoInfo.value = state.videoInfo
                            _currentAnalysisStep.value = AnalysisStep.READY
                        }
                        is DownloadState.Failed -> {
                            _isAnalyzing.value = false
                            _errorMessage.value = state.error.getUserMessage()
                            _currentAnalysisStep.value = null
                        }
                        else -> { /* Other states */ }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Timber.e(e, "Error analyzing URL")
                _isAnalyzing.value = false
                _errorMessage.value = "URL analiz edilemedi: ${e.message}"
            }
        }
    }

    /**
     * Cancel the current analysis
     */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        _isAnalyzing.value = false
        _currentAnalysisStep.value = null
        // We don't clear the URL input so user can edit it
    }
    
    /**
     * Reset all states
     */
    fun resetState() {
        analysisJob?.cancel()
        _downloadState.value = DownloadState.Idle
        _currentAnalysisStep.value = null
        _videoInfo.value = null
        _isAnalyzing.value = false
        _errorMessage.value = null
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Trigger download
     * This will be implemented later with the download service
     */
    /**
     * Trigger download
     */
    fun onDownloadClick() {
        val videoInfo = _videoInfo.value
        if (videoInfo == null) {
            Timber.w("Cannot download: no video info available")
            _errorMessage.value = "No video information available"
            return
        }
        
        val settings = _downloadSettings.value
        
        if (settings.askEveryTime) {
            // Ask for path every time
            viewModelScope.launch {
                _uiEvent.send(UiEvent.RequestDownloadPath)
            }
        } else {
            // Use fixed folder if set, otherwise ask
            val fixedPath = settings.fixedFolderPath
            if (!fixedPath.isNullOrBlank()) {
                startDownload(videoInfo, fixedPath)
            } else {
                viewModelScope.launch {
                    _uiEvent.send(UiEvent.RequestDownloadPath)
                }
            }
        }
    }
    
    /**
     * Handle download path selection
     */
    fun onDownloadPathSelected(uri: android.net.Uri?) {
        if (uri == null) return
        
        val videoInfo = _videoInfo.value ?: return
        startDownload(videoInfo, uri.toString())
    }
    
    private fun startDownload(videoInfo: VideoInfo, destinationUri: String?) {
        Timber.d("Starting download for: ${videoInfo.title} to $destinationUri")
        
        // Immediately show preparing state for instant feedback
        _downloadState.value = DownloadState.Preparing
        
        viewModelScope.launch {
            try {
                downloadVideoUseCase(videoInfo, null, destinationUri).collect { state ->
                    Timber.d("Download state: $state")
                    _downloadState.value = state
                    
                    when (state) {
                        is DownloadState.Completed -> {
                            Timber.d("Download completed: ${state.filePath}")
                            // Clear URL input after successful download
                            _urlInput.value = ""
                            _urlValidationError.value = null
                            // Send one-time event for toast
                            _uiEvent.send(UiEvent.ShowToast("İndirme tamamlandı! Geçmiş sekmesine bakın."))
                        }
                        is DownloadState.Failed -> {
                            Timber.e("Download failed: ${state.error.getUserMessage()}")
                            _errorMessage.value = state.error.getUserMessage()
                        }
                        else -> { /* Progress states handled by UI */ }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during download")
                _errorMessage.value = "İndirme hatası: ${e.message}"
            }
        }
    }
    
    /**
     * Update download settings
     */
    fun updateDownloadSettings(settings: DownloadSettings) {
        settingsManager.saveSettings(settings)
        Timber.d("Updated download settings: $settings")
    }
}
