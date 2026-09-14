package com.example.trashmails.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    private val json = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder().url(url).post(body.toRequestBody(json))
                .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        )

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ProviderException("HTTP ${resp.code} from ${request.url.host}")
            text
        }
    }
}

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

fun randomName(length: Int = 10): String =
    (1..length).map { ALPHABET.random() }.joinToString("")

/** Cleans a user-typed mailbox name: lowercase, alphanumeric, . _ - */
fun sanitizeName(raw: String?): String? =
    raw?.trim()?.substringBefore('@')?.lowercase()
        ?.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        ?.takeIf { it.isNotBlank() }
