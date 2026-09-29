package com.deedeedev.ytreader.data

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRepositoryTest {

    private val repository = ChatRepository(OkHttpClient())

    @Test
    fun buildEndpoint_appendsChatCompletionsForBaseUrl() {
        val endpoint = repository.buildEndpoint("https://api.example.com/v1")

        assertEquals("https://api.example.com/v1/chat/completions", endpoint)
    }

    @Test
    fun buildEndpoint_keepsFullUrl() {
        val endpoint = repository.buildEndpoint("https://api.example.com/v1/chat/completions/")

        assertEquals("https://api.example.com/v1/chat/completions", endpoint)
    }

    @Test
    fun parseResponseText_returnsFirstChoiceMessageContent() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "Hello, world."
                  }
                }
              ]
            }
        """.trimIndent()

        val content = repository.parseResponseText(response)

        assertEquals("Hello, world.", content)
    }

    @Test
    fun parseResponseText_returnsNullForInvalidJson() {
        val content = repository.parseResponseText("not-json")

        assertNull(content)
    }

    @Test
    fun parseResponseText_returnsNullWhenNoChoices() {
        val content = repository.parseResponseText("""{"choices":[]}""")

        assertNull(content)
    }
}
