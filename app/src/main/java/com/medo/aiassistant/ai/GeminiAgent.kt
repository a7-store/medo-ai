package com.medo.aiassistant.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.medo.aiassistant.data.NotificationEntity
import com.medo.aiassistant.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiAgent {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateSummary(
        notifications: List<NotificationEntity>,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (notifications.isEmpty()) {
            return@withContext Result.success("لا توجد إشعارات أو رسائل مسجلة في هذه الفترة لتلخيصها.")
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("مفتاح Gemini API غير موجود. يرجى إدخاله في تبويب الإعدادات."))
        }

        val prompt = PromptBuilder.buildSummaryPrompt(notifications)
        callGeminiApi(prompt, apiKey)
    }

    suspend fun extractTasks(
        notifications: List<NotificationEntity>,
        apiKey: String
    ): Result<List<TaskEntity>> = withContext(Dispatchers.IO) {
        if (notifications.isEmpty()) {
            return@withContext Result.success(emptyList())
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("مفتاح Gemini API غير موجود. يرجى إدخاله في تبويب الإعدادات."))
        }

        val prompt = PromptBuilder.buildTaskExtractionPrompt(notifications)
        val apiResult = callGeminiApi(prompt, apiKey)

        if (apiResult.isFailure) {
            return@withContext Result.failure(apiResult.exceptionOrNull() ?: Exception("Unknown error"))
        }

        val rawText = apiResult.getOrNull()?.trim() ?: "[]"
        // Clean markdown formatting if present
        val cleanJson = cleanJsonResponse(rawText)

        try {
            val jsonArray = JsonParser.parseString(cleanJson).asJsonArray
            val taskList = mutableListOf<TaskEntity>()
            for (element in jsonArray) {
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    val requester = obj.get("requester")?.asString ?: "غير محدد"
                    val desc = obj.get("taskDescription")?.asString ?: ""
                    val deadline = if (obj.has("deadline") && !obj.get("deadline").isJsonNull) {
                        obj.get("deadline").asString
                    } else null
                    val isUrgent = obj.has("isUrgent") && obj.get("isUrgent").asBoolean

                    if (desc.isNotBlank()) {
                        taskList.add(
                            TaskEntity(
                                requester = requester,
                                taskDescription = desc,
                                deadline = deadline,
                                isUrgent = isUrgent,
                                isCompleted = false
                            )
                        )
                    }
                }
            }
            Result.success(taskList)
        } catch (e: Exception) {
            Result.failure(Exception("فشل قراءة المهام من استجابة الذكاء الاصطناعي: ${e.localizedMessage}"))
        }
    }

    private fun cleanJsonResponse(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1)
        }
        return text
    }

    private fun callGeminiApi(prompt: String, apiKey: String): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

        val rootObject = JsonObject().apply {
            val contentsArray = JsonArray().apply {
                val contentObj = JsonObject().apply {
                    val partsArray = JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", prompt)
                        }
                        add(partObj)
                    }
                    add("parts", partsArray)
                }
                add(contentObj)
            }
            add("contents", contentsArray)
        }

        val requestBody = rootObject.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(Exception("خطأ في الاتصال بـ Gemini (${response.code}): $responseBody"))
            }

            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val candidates = jsonResponse.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val candidate = candidates.get(0).asJsonObject
                val content = candidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val text = parts.get(0).asJsonObject.get("text").asString
                    return Result.success(text)
                }
            }
            Result.failure(Exception("لم يرجع النموذج أي محتوى نصي."))
        } catch (e: Exception) {
            Result.failure(Exception("تعذر الاتصال بالإنترنت أو بخدمة Gemini: ${e.localizedMessage}"))
        }
    }
}
