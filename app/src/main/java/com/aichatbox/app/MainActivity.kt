package com.aichatbox.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aichatbox.app.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatManager: ChatManager
    private lateinit var messageAdapter: MessageAdapter
    private var apiClient: ApiClient? = null
    private var isStreaming = false

    private val models = arrayOf(
        "gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o",
        "claude-3-opus", "claude-3-sonnet", "claude-3-haiku",
        "gemini-pro", "gemini-1.5-pro",
        "自定义模型"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize
        chatManager = ChatManager(this)
        setupUI()
        loadCurrentSession()
    }

    private fun setupUI() {
        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }

        // Setup Navigation Drawer
        binding.navigationView.setNavigationItemSelectedListener(this)

        // Setup Model Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.modelSpinner.adapter = adapter

        // Setup RecyclerView
        messageAdapter = MessageAdapter(this)
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter
        }

        // Setup Send Button
        binding.sendButton.setOnClickListener {
            if (isStreaming) {
                abortRequest()
            } else {
                sendMessage()
            }
        }

        // Setup Input
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        // Setup Navigation Drawer Header
        binding.navigationView.getHeaderView(0)
            .findViewById<com.google.android.material.button.MaterialButton>(R.id.newChatButton)
            .setOnClickListener {
                createNewChat()
            }

        // Setup API Client
        updateApiClient()
    }

    private fun loadCurrentSession() {
        val session = chatManager.getCurrentSession()
        if (session != null) {
            session.messages.forEach { messageAdapter.addMessage(it) }
            binding.welcomeScreen.visibility = View.GONE
            binding.messagesRecyclerView.visibility = View.VISIBLE
        } else {
            binding.welcomeScreen.visibility = View.VISIBLE
            binding.messagesRecyclerView.visibility = View.GONE
        }
    }

    private fun createNewChat() {
        chatManager.createNewSession()
        messageAdapter.clearMessages()
        binding.welcomeScreen.visibility = View.GONE
        binding.messagesRecyclerView.visibility = View.VISIBLE
        binding.drawerLayout.closeDrawers()
        Toast.makeText(this, "新对话已创建", Toast.LENGTH_SHORT).show()
    }

    private fun sendMessage() {
        val content = binding.messageInput.text.toString().trim()
        if (content.isEmpty()) return

        if (apiClient == null) {
            showSettingsDialog()
            return
        }

        // Hide welcome screen
        binding.welcomeScreen.visibility = View.GONE
        binding.messagesRecyclerView.visibility = View.VISIBLE

        // Add user message
        messageAdapter.addMessage(chatManager.addMessage("user", content))
        binding.messageInput.text?.clear()
        scrollToBottom()

        // Add placeholder for AI response
        messageAdapter.addMessage(Message(role = "assistant", content = "..."))
        scrollToBottom()

        // Send to API
        isStreaming = true
        updateSendButton()

        lifecycleScope.launch {
            try {
                val messages = messageAdapter.getMessages()
                    .filter { it.content != "..." }
                    .map { Message(role = it.role, content = it.content) }

                val response = apiClient!!.sendMessage(
                    messages = messages,
                    stream = true,
                    callback = object : ApiClient.StreamCallback {
                        override fun onChunk(chunk: String) {
                            runOnUiThread {
                                messageAdapter.updateLastMessage(
                                    messageAdapter.getMessages().last().content.replace("...", "") + chunk
                                )
                                scrollToBottom()
                            }
                        }

                        override fun onComplete(fullResponse: String) {
                            runOnUiThread {
                                chatManager.updateLastMessage(fullResponse)
                                isStreaming = false
                                updateSendButton()
                            }
                        }

                        override fun onError(error: String) {
                            runOnUiThread {
                                messageAdapter.updateLastMessage("错误: $error")
                                isStreaming = false
                                updateSendButton()
                            }
                        }
                    }
                )

                // Save assistant message
                chatManager.addMessage("assistant", response)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    messageAdapter.updateLastMessage("请求失败: ${e.message}")
                    isStreaming = false
                    updateSendButton()
                }
            }
        }
    }

    private fun abortRequest() {
        apiClient?.abort()
        isStreaming = false
        updateSendButton()
    }

    private fun updateSendButton() {
        if (isStreaming) {
            binding.sendButton.setIconResource(R.drawable.ic_stop)
        } else {
            binding.sendButton.setIconResource(R.drawable.ic_send)
        }
    }

    private fun scrollToBottom() {
        binding.messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
    }

    private fun updateApiClient() {
        val config = chatManager.getApiConfig()
        if (config.apiKey.isNotEmpty()) {
            apiClient = ApiClient(config)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_chat -> createNewChat()
            R.id.action_settings -> showSettingsDialog()
            R.id.action_export -> exportData()
        }
        binding.drawerLayout.closeDrawers()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(binding.navigationView)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val config = chatManager.getApiConfig()
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        // Populate current settings
        // ... (would need to implement dialog layout)

        MaterialAlertDialogBuilder(this)
            .setTitle("设置")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                // Save settings
                updateApiClient()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportData() {
        try {
            val data = chatManager.exportData()
            val file = java.io.File(getExternalFilesDir(null), "chatbox_export_${System.currentTimeMillis()}.json")
            file.writeText(data)
            Toast.makeText(this, "导出成功: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
