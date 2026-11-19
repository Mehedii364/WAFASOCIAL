package com.example.data.repository

import android.util.Log
import com.example.data.model.AiDraftSuggestion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val AI_TAG = "WafaAiRepository"

enum class WafaAiModel(val modelId: String, val label: String, val badge: String) {
    FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Balanced"),
    FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra Fast")
}

class WafaAiRepository {

    private val flashModel by lazy {
        Firebase.ai.generativeModel(modelName = WafaAiModel.FLASH.modelId)
    }

    private val flashLiteModel by lazy {
        Firebase.ai.generativeModel(modelName = WafaAiModel.FLASH_LITE.modelId)
    }

    private val draftSchema = Schema.obj(
        properties = mapOf(
            "polishedText" to Schema.string(description = "Improved, engaging version of the user's post ready for review"),
            "caption" to Schema.string(description = "Punchy short caption alternative"),
            "hashtags" to Schema.array(
                items = Schema.string(),
                description = "3 to 6 relevant lowercase hashtags without the # prefix"
            ),
            "tips" to Schema.string(description = "One concise creator tip for clarity or engagement")
        )
    )

    private val structuredFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = WafaAiModel.FLASH.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = draftSchema
            }
        )
    }

    private val structuredLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = WafaAiModel.FLASH_LITE.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = draftSchema
            }
        )
    }

    suspend fun generateStructuredPostDraft(
        draftInput: String,
        tone: String,
        modelChoice: WafaAiModel = WafaAiModel.FLASH
    ): Result<AiDraftSuggestion> {
        return try {
            val prompt = """
                You are the Wafa Social writing assistant. Help the creator refine their draft without publishing automatically.
                Tone requested: $tone
                User draft or topic: $draftInput
                Return clean JSON matching the schema. Keep polishedText authentic, concise, and ready for social publishing.
            """.trimIndent()
            val model = if (modelChoice == WafaAiModel.FLASH) structuredFlashModel else structuredLiteModel
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Model returned an empty response.")
            val json = JSONObject(jsonText)
            val tagsArr = json.optJSONArray("hashtags")
            val tags = buildList {
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val cleaned = tagsArr.optString(i).trim().removePrefix("#").lowercase()
                        if (cleaned.isNotEmpty()) add(cleaned)
                    }
                }
            }.take(6)

            Result.success(
                AiDraftSuggestion(
                    polishedText = json.optString("polishedText", draftInput),
                    caption = json.optString("caption", ""),
                    hashtags = tags,
                    tips = json.optString("tips", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Structured draft timed out", e)
            Result.failure(Exception("AI draft timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Structured draft failed", e)
            Result.failure(e)
        }
    }

    fun streamAssistantTask(
        taskPrompt: String,
        modelChoice: WafaAiModel = WafaAiModel.FLASH
    ): Flow<String> = flow {
        val model = if (modelChoice == WafaAiModel.FLASH) flashModel else flashLiteModel
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(taskPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Model returned an empty response.")
        }
    }
}
