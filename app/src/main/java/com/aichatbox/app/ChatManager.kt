package com.aichatbox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "新对话",
    val messages: MutableList<Message> = mutableListOf(),
    var updatedAt: Long = System.currentTimeMillis()
)

data class ApiConfig(
    var endpoint: String = "https://api.openai.com/v1/chat/completions",
    var apiKey: String = "",
    var model: String = "gpt-3.5-turbo",
    var temperature: Float = 0.7f,
    var maxTokens: Int = 4096,
    var systemPrompt: String = ""
)

class ChatManager(private val context: Context) {
    
    private val sessions = mutableListOf<ChatSession>()
    private var currentSession: ChatSession? = null
    private var apiConfig = ApiConfig()
    
    private val sessionsFile = File(context.filesDir, "sessions.json")
    private val configFile = File(context.filesDir, "config.json")
    
    init {
        loadData()
    }
    
    // Session Management
    fun createNewSession(): ChatSession {
        val session = ChatSession()
        sessions.add(0, session)
        currentSession = session
        saveData()
        return session
    }
    
    fun getCurrentSession(): ChatSession? = currentSession
    
    fun setCurrentSession(sessionId: String) {
        currentSession = sessions.find { it.id == sessionId }
    }
    
    fun getAllSessions(): List<ChatSession> = sessions.sortedByDescending { it.updatedAt }
    
    fun deleteSession(sessionId: String) {
        sessions.removeAll { it.id == sessionId }
        if (currentSession?.id == sessionId) {
            currentSession = sessions.firstOrNull()
        }
        saveData()
    }
    
    // Message Management
    fun addMessage(role: String, content: String): Message {
        val session = currentSession ?: createNewSession()
        val message = Message(role = role, content = content)
        session.messages.add(message)
        session.updatedAt = System.currentTimeMillis()
        
        // Update title from first user message
        if (session.title == "新对话" && role == "user") {
            session.title = content.take(30) + if (content.length > 30) "..." else ""
        }
        
        saveData()
        return message
    }
    
    fun updateLastMessage(content: String) {
        currentSession?.messages?.lastOrNull()?.let {
            val index = currentSession!!.messages.indexOf(it)
            currentSession!!.messages[index] = it.copy(content = content)
        }
    }
    
    fun clearCurrentSession() {
        currentSession?.messages?.clear()
        currentSession?.title = "新对话"
        saveData()
    }
    
    // API Configuration
    fun getApiConfig(): ApiConfig = apiConfig
    
    fun setApiConfig(config: ApiConfig) {
        apiConfig = config
        saveConfig()
    }
    
    // Data Persistence
    fun saveData() {
        try {
            val json = JSONArray()
            sessions.forEach { session ->
                val sessionJson = JSONObject().apply {
                    put("id", session.id)
                    put("title", session.title)
                    put("updatedAt", session.updatedAt)
                    val messagesArray = JSONArray()
                    session.messages.forEach { msg ->
                        messagesArray.put(JSONObject().apply {
                            put("id", msg.id)
                            put("role", msg.role)
                            put("content", msg.content)
                            put("timestamp", msg.timestamp)
                        })
                    }
                    put("messages", messagesArray)
                }
                json.put(sessionJson)
            }
            sessionsFile.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadData() {
        try {
            if (sessionsFile.exists()) {
                val json = JSONArray(sessionsFile.readText())
                sessions.clear()
                for (i in 0 until json.length()) {
                    val sessionJson = json.getJSONObject(i)
                    val session = ChatSession(
                        id = sessionJson.getString("id"),
                        title = sessionJson.getString("title"),
                        updatedAt = sessionJson.getLong("updatedAt")
                    )
                    val messagesArray = sessionJson.getJSONArray("messages")
                    for (j in 0 until messagesArray.length()) {
                        val msgJson = messagesArray.getJSONObject(j)
                        session.messages.add(Message(
                            id = msgJson.getString("id"),
                            role = msgJson.getString("role"),
                            content = msgJson.getString("content"),
                            timestamp = msgJson.getLong("timestamp")
                        ))
                    }
                    sessions.add(session)
                }
                currentSession = sessions.firstOrNull()
            }
            
            if (configFile.exists()) {
                val configJson = JSONObject(configFile.readText())
                apiConfig = ApiConfig(
                    endpoint = configJson.optString("endpoint", "https://api.openai.com/v1/chat/completions"),
                    apiKey = configJson.optString("apiKey", ""),
                    model = configJson.optString("model", "gpt-3.5-turbo"),
                    temperature = configJson.optDouble("temperature", 0.7).toFloat(),
                    maxTokens = configJson.optInt("maxTokens", 4096),
                    systemPrompt = configJson.optString("systemPrompt", "")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveConfig() {
        try {
            val json = JSONObject().apply {
                put("endpoint", apiConfig.endpoint)
                put("apiKey", apiConfig.apiKey)
                put("model", apiConfig.model)
                put("temperature", apiConfig.temperature)
                put("maxTokens", apiConfig.maxTokens)
                put("systemPrompt", apiConfig.systemPrompt)
            }
            configFile.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Export/Import
    fun exportData(): String {
        val data = JSONObject().apply {
            put("sessions", JSONArray().apply {
                sessions.forEach { session ->
                    put(JSONObject().apply {
                        put("id", session.id)
                        put("title", session.title)
                        put("messages", JSONArray().apply {
                            session.messages.forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("content", msg.content)
                                    put("timestamp", msg.timestamp)
                                })
                            }
                        })
                    })
                }
            })
            put("config", JSONObject().apply {
                put("endpoint", apiConfig.endpoint)
                put("model", apiConfig.model)
                put("temperature", apiConfig.temperature)
                put("maxTokens", apiConfig.maxTokens)
            })
        }
        return data.toString(2)
    }
    
    fun importData(jsonString: String) {
        try {
            val data = JSONObject(jsonString)
            sessions.clear()
            
            val sessionsArray = data.getJSONArray("sessions")
            for (i in 0 until sessionsArray.length()) {
                val sessionJson = sessionsArray.getJSONObject(i)
                val session = ChatSession(
                    id = sessionJson.getString("id"),
                    title = sessionJson.getString("title")
                )
                val messagesArray = sessionJson.getJSONArray("messages")
                for (j in 0 until messagesArray.length()) {
                    val msgJson = messagesArray.getJSONObject(j)
                    session.messages.add(Message(
                        role = msgJson.getString("role"),
                        content = msgJson.getString("content"),
                        timestamp = msgJson.optLong("timestamp", System.currentTimeMillis())
                    ))
                }
                sessions.add(session)
            }
            
            currentSession = sessions.firstOrNull()
            saveData()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
