package com.example.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiCaptionSuggestionResult(
    val captions: List<String>,
    val hashtags: List<String>,
    val moodTip: String
)

class FirebaseAiRepository {

    private val captionSchema = Schema.obj(
        properties = mapOf(
            "captions" to Schema.array(Schema.string()),
            "hashtags" to Schema.array(Schema.string()),
            "moodTip" to Schema.string()
        )
    )

    private val captionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = captionSchema
                temperature = 0.8f
            }
        )
    }

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.2f
            }
        )
    }

    suspend fun suggestCaptionsAndHashtags(
        draftOrTopic: String,
        isReel: Boolean,
        isStory: Boolean
    ): Result<AiCaptionSuggestionResult> = withContext(Dispatchers.IO) {
        try {
            val contentType = when {
                isReel -> "short vertical Reel"
                isStory -> "24-hour Story"
                else -> "photo/carousel post"
            }
            val promptText = """
                You are an editorial creative director for a luxury dark-gold social app named SCROLL DOWN.
                Generate 3 concise, engaging, authentic captions and 8 relevant lowercase hashtags (without # prefix)
                plus 1 short styling/music mood tip for a $contentType.
                User context or draft: "${draftOrTopic.ifBlank { "Evening city lights and luxury lifestyle aesthetics" }}"
            """.trimIndent()

            val response = withTimeout(60_000L) {
                captionModel.generateContent(content { text(promptText) })
            }
            val raw = response.text?.trim() ?: return@withContext Result.failure(
                IllegalStateException("AI returned an empty suggestion response.")
            )
            val json = JSONObject(raw)
            val captionsArr = json.optJSONArray("captions")
            val tagsArr = json.optJSONArray("hashtags")
            val captions = buildList {
                if (captionsArr != null) {
                    for (i in 0 until captionsArr.length()) {
                        val item = captionsArr.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }
            val hashtags = buildList {
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val tag = tagsArr.optString(i).trim().removePrefix("#").lowercase()
                        if (tag.isNotEmpty()) add(tag)
                    }
                }
            }
            val moodTip = json.optString("moodTip").trim()
            if (captions.isEmpty()) {
                Result.failure(IllegalStateException("No caption suggestions were generated."))
            } else {
                Result.success(AiCaptionSuggestionResult(captions, hashtags, moodTip))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Unable to generate AI captions right now.", e))
        }
    }

    suspend fun translateContent(
        sourceText: String,
        targetLanguageLabel: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (sourceText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nothing to translate."))
        }
        try {
            val promptText = """
                Translate the following social media caption into $targetLanguageLabel.
                Preserve #hashtags, @mentions, and emojis naturally. Output only the translated text.
                Text:
                $sourceText
            """.trimIndent()

            val response = withTimeout(60_000L) {
                translationModel.generateContent(content { text(promptText) })
            }
            val translated = response.text?.trim()
            if (translated.isNullOrBlank()) {
                Result.failure(IllegalStateException("Translation returned empty output."))
            } else {
                Result.success(translated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Translation failed.", e))
        }
    }
}
