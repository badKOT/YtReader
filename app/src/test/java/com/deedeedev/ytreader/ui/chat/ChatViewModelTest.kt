package com.deedeedev.ytreader.ui.chat

import android.content.Context
import android.content.SharedPreferences
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.StringProvider
import com.deedeedev.ytreader.data.ChatRepository
import com.deedeedev.ytreader.data.SubtitleRepository
import com.deedeedev.ytreader.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var stringProvider: StringProvider
    private lateinit var subtitleRepository: SubtitleRepository
    private lateinit var chatRepository: ChatRepository

    @Before
    fun setUp() {
        stringProvider = mock()
        subtitleRepository = mock()
        chatRepository = mock()
        whenever(stringProvider.getString(R.string.chat_error_missing_config)).thenReturn("Missing config")
    }

    @Test
    fun buildTranscriptExchange_wrapsTranscriptWithFileMarker() {
        val viewModel = createViewModel()

        val exchange = viewModel.buildTranscriptExchange("Transcript text")

        assertEquals("user", exchange.role)
        assertTrue(exchange.content.contains("transcript.txt"))
        assertTrue(exchange.content.contains("Transcript text"))
    }

    @Test
    fun buildRequestMessages_mapsConversationRoles() {
        val viewModel = createViewModel()
        val messages = listOf(
            ChatMessage(id = 0, isUser = true, content = "Question 1"),
            ChatMessage(id = 1, isUser = false, content = "Answer 1"),
            ChatMessage(id = 2, isUser = true, content = "Question 2")
        )

        val requestMessages = viewModel.buildRequestMessages(messages)

        assertEquals(3, requestMessages.size)
        assertEquals("user", requestMessages[0].role)
        assertEquals("Question 1", requestMessages[0].content)
        assertEquals("assistant", requestMessages[1].role)
        assertEquals("Answer 1", requestMessages[1].content)
        assertEquals("user", requestMessages[2].role)
        assertEquals("Question 2", requestMessages[2].content)
    }

    @Test
    fun send_withMissingAiConfiguration_setsErrorAndKeepsMessage() = runTest {
        val viewModel = createViewModel()

        viewModel.send("What is this video about?")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertTrue(state.messages[0].isUser)
        assertEquals("What is this video about?", state.messages[0].content)
        assertEquals("Missing config", state.error)
        assertEquals(false, state.isLoading)
        verifyNoInteractions(chatRepository)
    }

    @Test
    fun send_withBlankInput_ignoresMessage() = runTest {
        val viewModel = createViewModel()

        viewModel.send("   ")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.messages.size)
    }

    private fun createViewModel(): ChatViewModel {
        return ChatViewModel(
            stringProvider = stringProvider,
            subtitleRepository = subtitleRepository,
            userPreferencesRepository = createUserPreferencesRepository(),
            chatRepository = chatRepository,
            subtitleId = SUBTITLE_ID
        )
    }

    private fun createUserPreferencesRepository(): UserPreferencesRepository {
        val context = mock<Context>()
        val preferences = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()

        whenever(context.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(preferences)
        whenever(preferences.getStringSet(any(), any())).thenReturn(emptySet())
        whenever(preferences.getFloat(any(), any())).thenAnswer { it.arguments[1] as Float }
        whenever(preferences.getString(any(), any())).thenAnswer { it.arguments[1] as String? }
        whenever(preferences.getBoolean(any(), any())).thenAnswer { it.arguments[1] as Boolean }
        whenever(preferences.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putStringSet(any(), any())).thenReturn(editor)
        whenever(editor.putFloat(any(), any())).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)

        return UserPreferencesRepository(context)
    }

    companion object {
        private const val SUBTITLE_ID = 42L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
