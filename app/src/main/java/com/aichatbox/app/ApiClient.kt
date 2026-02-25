package com.aichatbox.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val config: ApiConfig) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    interface StreamCallback {
        fun onChunk(chunk: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }
    
    suspend fun sendMessage(
        messages: List<Message>,
        stream: Boolean = true,
        callback: StreamCallback? = null
    ): String = withContext(Dispatchers.IO) {
        
        // Build request body
        val messagesArray = JSONArray()
        
        // Add system prompt if configured
        if (config.systemPrompt.isNotEmpty()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", config.systemPrompt)
            })
        }
        
        // Add conversation messages
        messages.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }
        
        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("messages", messagesArray)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            put("stream", stream)
        }
        
        val request = Request.Builder()
            .url(config.endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        
        if (stream && callback != null) {
            streamRequest(request, callback)
        } else {
            normalRequest(request)
        }
    }
    
    private fun normalRequest(request: Request): String {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        
        val responseBody = response.body?.string() ?: throw IOException("Empty response")
        val json = JSONObject(responseBody)
        
        // Parse OpenAI format
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    private fun streamRequest(request: Request, callback: StreamCallback): String {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            callback.onError("HTTP ${response.code}: ${response.message}")
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        
        val reader = response.body?.source()?.buffer() ?: throw IOException("Empty response")
        val fullResponse = StringBuilder()
        
        try {
            while (!reader.exhausted()) {
                val line = reader.readUtf8Line() ?: continue
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data == "[DONE]") break
                    
                    try {
                        val json = JSONObject(data)
                        val delta = json
                            .optJSONArray("choices")
                            ?.getJSONObject(0)
                            ?.optJSONObject("delta")
                        
                        val content = delta?.optString("content", "") ?: ""
                        if (content.isNotEmpty()) {
                            fullResponse.append(content)
                            callback.onChunk(content)
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors for incomplete chunks
                    }
                }
            }
            
            callback.onComplete(fullResponse.toString())
            return fullResponse.toString()
            
        } catch (e: Exception) {
            callback.onError(e.message ?: "Unknown error")
            throw e
        }
    }
    
    fun abort() {
        client.dispatcher.cancelAll()
    }
    
    companion object {
        // Preset configurations
        val PRESETS = mapOf(
            "openai" to ApiConfig(
                endpoint = "https://api.openai.com/v1/chat/completions",
                model = "gpt-3.5-turbo"
            ),
            "anthropic" to ApiConfig(
                endpoint = "https://api.anthropic.com/v1/messages",
                model = "claude-3-sonnet"
            ),
            "google" to ApiConfig(
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent",
                model = "gemini-pro"
            ),
            "local" to ApiConfig(
                endpoint = "http://localhost:11434/api/chat",
                model = "llama3"
            )
        )
    }
}
