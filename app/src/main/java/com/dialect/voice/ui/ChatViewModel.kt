package com.dialect.voice.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.domain.Dialect
import com.dialect.voice.domain.DIALECTS
import com.dialect.voice.domain.Message
import com.dialect.voice.domain.MessageRole
import com.dialect.voice.domain.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val openAiClient: OpenAIClient,
    private val elevenLabsClient: ElevenLabsClient
) : ViewModel() {

    private val _selectedDialect = MutableStateFlow("geordie")
    val selectedDialect: StateFlow<String> = _selectedDialect.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setDialect(dialectId: String) {
        _selectedDialect.value = dialectId
    }

    fun getCurrentDialect(): Dialect? {
        return DIALECTS[_selectedDialect.value]
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        val userMsgId = "u_${UUID.randomUUID()}"
        val assistantMsgId = "a_${UUID.randomUUID()}"
        val dialectId = _selectedDialect.value

        // Add user message
        _messages.value = _messages.value + Message(
            id = userMsgId,
            role = MessageRole.USER,
            text = userText,
            status = MessageStatus.DONE
        )

        // Add pending assistant message
        _messages.value = _messages.value + Message(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            text = "…",
            dialect = dialectId,
            status = MessageStatus.PENDING
        )

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val dialect = getCurrentDialect() 
                    ?: throw Exception("Dialect not found")

                // Convert to dialect
                val dialectText = openAiClient.convertToDialect(
                    text = userText,
                    systemPrompt = dialect.systemPrompt
                )

                // Synthesize speech
                val audioUrl = elevenLabsClient.synthesizeSpeech(
                    text = dialectText,
                    voiceId = dialect.elevenLabsVoiceId
                )

                // Update assistant message with response
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            text = dialectText,
                            audioUrl = audioUrl,
                            status = MessageStatus.DONE
                        )
                    } else {
                        msg
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                _error.value = errorMsg

                // Update assistant message with error
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            text = "Couldn't translate that.",
                            status = MessageStatus.ERROR,
                            errorMessage = errorMsg
                        )
                    } else {
                        msg
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        openAiClient.close()
        elevenLabsClient.close()
    }
}
