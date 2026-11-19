package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiDraftSuggestion
import com.example.data.repository.WafaAiModel
import com.example.data.repository.WafaAiRepository
import com.example.data.repository.WafaSocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val AI_VM_TAG = "AiAssistantVM"

enum class AiTaskPreset(val wireKey: String, val label: String, val hint: String) {
    CAPTION("caption", "Write & Hashtags", "Describe what you want to share..."),
    IMPROVE("improve", "Polish Draft", "Paste your draft post to refine..."),
    SUMMARIZE("summarize", "Summarize", "Paste a long post or discussion thread..."),
    TRANSLATE("translate", "Translate", "Enter text + target language (e.g. 'to Spanish')..."),
    COMMUNITY("community", "Community Guide", "Describe your group topic for rules & welcome copy...")
}

sealed interface AiAssistantUiState {
    data object Idle : AiAssistantUiState
    data object Loading : AiAssistantUiState
    data class Streaming(val text: String) : AiAssistantUiState
    data class StructuredSuccess(val suggestion: AiDraftSuggestion) : AiAssistantUiState
    data class TextSuccess(val text: String) : AiAssistantUiState
    data class Error(val message: String) : AiAssistantUiState
}

class AiAssistantViewModel(
    private val aiRepo: WafaAiRepository,
    private val socialRepo: WafaSocialRepository
) : ViewModel() {

    private val _selectedModel = MutableStateFlow(WafaAiModel.FLASH)
    val selectedModel: StateFlow<WafaAiModel> = _selectedModel.asStateFlow()

    private val _selectedPreset = MutableStateFlow(AiTaskPreset.CAPTION)
    val selectedPreset: StateFlow<AiTaskPreset> = _selectedPreset.asStateFlow()

    private val _uiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    fun selectModel(model: WafaAiModel) {
        _selectedModel.value = model
    }

    fun selectPreset(preset: AiTaskPreset) {
        _selectedPreset.value = preset
    }

    fun resetState() {
        _uiState.value = AiAssistantUiState.Idle
    }

    fun runStructuredDraft(input: String, tone: String = "Engaging & Authentic") {
        val clean = input.trim()
        if (clean.isEmpty()) {
            _uiState.value = AiAssistantUiState.Error("Enter a topic or draft before running AI assistance.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Loading
            val model = _selectedModel.value
            val result = aiRepo.generateStructuredPostDraft(clean, tone, model)
            result.onSuccess { suggestion ->
                _uiState.value = AiAssistantUiState.StructuredSuccess(suggestion)
                socialRepo.logAiUsage(
                    taskType = "caption",
                    modelName = model.modelId,
                    promptChars = clean.length,
                    responseChars = suggestion.polishedText.length + suggestion.caption.length
                )
            }.onFailure { err ->
                _uiState.value = AiAssistantUiState.Error(
                    err.localizedMessage ?: "AI generation failed."
                )
            }
        }
    }

    fun runStreamingTask(preset: AiTaskPreset, userInput: String) {
        val clean = userInput.trim()
        if (clean.isEmpty()) {
            _uiState.value = AiAssistantUiState.Error("Enter text or a prompt first.")
            return
        }
        val prompt = when (preset) {
            AiTaskPreset.CAPTION -> "Write an engaging social post and 5 relevant hashtags for Wafa Social about: $clean"
            AiTaskPreset.IMPROVE -> "Improve clarity, flow, and impact of this social post while preserving the author's voice. Provide only the refined post and 3 optional hashtags:\n\n$clean"
            AiTaskPreset.SUMMARIZE -> "Summarize the key takeaways of this discussion into 3 crisp bullet points:\n\n$clean"
            AiTaskPreset.TRANSLATE -> "Translate the following social post accurately, preserving tone and hashtags. If no target language is stated, translate to clear English:\n\n$clean"
            AiTaskPreset.COMMUNITY -> "Create a concise community welcome announcement and 4 fair community rules for a Wafa Social group about: $clean"
        }

        viewModelScope.launch {
            _uiState.value = AiAssistantUiState.Loading
            val model = _selectedModel.value
            var finalOutput = ""
            try {
                aiRepo.streamAssistantTask(prompt, model).collect { partial ->
                    finalOutput = partial
                    _uiState.value = AiAssistantUiState.Streaming(partial)
                }
                if (finalOutput.isNotBlank()) {
                    _uiState.value = AiAssistantUiState.TextSuccess(finalOutput)
                    socialRepo.logAiUsage(
                        taskType = preset.wireKey,
                        modelName = model.modelId,
                        promptChars = prompt.length,
                        responseChars = finalOutput.length
                    )
                } else {
                    _uiState.value = AiAssistantUiState.Error("Model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_VM_TAG, "Streaming timed out", e)
                _uiState.value = AiAssistantUiState.Error("AI response timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_VM_TAG, "Streaming failed", e)
                _uiState.value = AiAssistantUiState.Error(e.localizedMessage ?: "AI request failed.")
            }
        }
    }
}
