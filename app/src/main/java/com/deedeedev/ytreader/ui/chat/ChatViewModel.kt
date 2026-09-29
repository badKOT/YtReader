package com.deedeedev.ytreader.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.ChatExchange
import com.deedeedev.ytreader.data.ChatRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import com.deedeedev.ytreader.domain.SubtitleParser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val content: String
)

data class ChatUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val transcript: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

class ChatViewModel(
    private val stringProvider: StringProvider,
    private val subtitleRepository: SubtitleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatRepository: ChatRepository,
    private val subtitleId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var nextMessageId: Long = 0L

    init {
        loadTranscript()
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isBlank() || _uiState.value.isLoading) return
        val userMessage = ChatMessage(id = nextMessageId++, isUser = true, content = message)
        _uiState.update {
            it.copy(messages = it.messages + userMessage, isLoading = true, error = null)
        }
        sendMessage(userMessage)
    }

    private fun loadTranscript() {
        viewModelScope.launch {
            val subtitle = subtitleRepository.getById(subtitleId) ?: return@launch
            val transcript = subtitle.studyContent ?: SubtitleParser.parse(subtitle.content)
            _uiState.update {
                it.copy(
                    title = subtitle.title,
                    transcript = transcript.trim()
                )
            }
        }
    }

    private fun sendMessage(userMessage: ChatMessage) {
        viewModelScope.launch {
            val endpoint = userPreferencesRepository.getAiEndpoint().trim()
            val apiKey = userPreferencesRepository.getAiApiKey().trim()
            val model = userPreferencesRepository.getAiModel().trim()
            if (endpoint.isBlank() || apiKey.isBlank() || model.isBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, error = stringProvider.getString(R.string.chat_error_missing_config))
                }
                return@launch
            }

            val requestMessages = buildRequestMessages(_uiState.value.messages)

            try {
                val response = chatRepository.complete(
                    endpointBaseUrl = endpoint,
                    apiKey = apiKey,
                    model = model,
                    messages = requestMessages
                )
                val assistantMessage = ChatMessage(
                    id = nextMessageId++,
                    isUser = false,
                    content = response.trim()
                )
                _uiState.update {
                    it.copy(messages = it.messages + assistantMessage, isLoading = false, error = null)
                }
            } catch (e: com.deedeedev.ytreader.data.ChatException) {
                val error = if (e.httpCode != null) {
                    stringProvider.getString(R.string.chat_error_request_failed, e.httpCode)
                } else {
                    stringProvider.getString(R.string.chat_error_empty_response)
                }
                _uiState.update { it.copy(isLoading = false, error = error) }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = stringProvider.getString(
                            R.string.chat_error_network,
                            e.message ?: e.javaClass.simpleName
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = stringProvider.getString(
                            R.string.chat_error_network,
                            e.message ?: e.javaClass.simpleName
                        )
                    )
                }
            }
        }
    }

    internal fun buildRequestMessages(messages: List<ChatMessage>): List<ChatExchange> {
        val transcript = _uiState.value.transcript
        val conversation = messages.map { ChatExchange(role = if (it.isUser) ROLE_USER else ROLE_ASSISTANT, content = it.content) }
        if (transcript.isBlank()) return conversation
        return listOf(buildTranscriptExchange(transcript)) + conversation
    }

    internal fun buildTranscriptExchange(transcript: String): ChatExchange {
        return ChatExchange(
            role = ROLE_USER,
            content = "The transcript of a video is attached below as a text file named transcript.txt. Use it as context for the conversation.\n\n<transcript.txt>\n$transcript\n</transcript.txt>"
        )
    }

    companion object {
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"

        fun provideFactory(
            stringProvider: StringProvider,
            subtitleRepository: SubtitleRepository,
            userPreferencesRepository: UserPreferencesRepository,
            chatRepository: ChatRepository,
            subtitleId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    stringProvider,
                    subtitleRepository,
                    userPreferencesRepository,
                    chatRepository,
                    subtitleId
                ) as T
            }
        }
    }
}
