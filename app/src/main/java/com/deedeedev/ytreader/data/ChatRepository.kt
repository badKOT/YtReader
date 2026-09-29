package com.deedeedev.ytreader.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatExchange(
    val role: String,
    val content: String
)

class ChatException(
    val httpCode: Int? = null,
    cause: Throwable? = null
) : Exception(cause)

class ChatRepository(
    private val client: OkHttpClient,
    private val gson: Gson = Gson()
) {

    suspend fun complete(
        endpointBaseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatExchange>
    ): String = withContext(Dispatchers.IO) {
        val endpoint = buildEndpoint(endpointBaseUrl)

        val payload = ChatCompletionsRequest(
            model = model,
            messages = messages.map { ChatMessage(role = it.role, content = it.content) }
        )

        val httpRequest = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val requestClient = client.newBuilder()
            .readTimeout(
                AiCleaningRepository.computeReadTimeout(messages.sumOf { it.content.length }),
                TimeUnit.SECONDS
            )
            .callTimeout(
                AiCleaningRepository.computeCallTimeout(messages.sumOf { it.content.length }),
                TimeUnit.SECONDS
            )
            .build()

        executeCancellable(requestClient, httpRequest).use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ChatException(httpCode = response.code)
            }
            parseResponseText(body) ?: throw ChatException()
        }
    }

    internal fun parseResponseText(responseBody: String): String? {
        return try {
            val response = gson.fromJson(responseBody, ChatCompletionsResponse::class.java)
            response.choices.firstOrNull()?.message?.content
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    internal fun buildEndpoint(baseOrFullUrl: String): String {
        val normalized = baseOrFullUrl.trim().trimEnd('/')
        return if (normalized.endsWith(CHAT_COMPLETIONS_PATH)) {
            normalized
        } else {
            normalized + CHAT_COMPLETIONS_PATH
        }
    }

    private suspend fun executeCancellable(
        requestClient: OkHttpClient,
        request: Request
    ): Response =
        suspendCancellableCoroutine { continuation ->
            val call = requestClient.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }

    private data class ChatCompletionsRequest(
        val model: String,
        val messages: List<ChatMessage>
    )

    private data class ChatMessage(
        val role: String,
        val content: String
    )

    private data class ChatCompletionsResponse(
        val choices: List<ChatChoice> = emptyList()
    )

    private data class ChatChoice(
        val message: ChatMessageContent? = null
    )

    private data class ChatMessageContent(
        val content: String? = null
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"
    }
}
