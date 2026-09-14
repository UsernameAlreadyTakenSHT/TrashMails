package io.github.usernamealreadytakensht.trashmails.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** The HTTP calls the providers make: the real one is OkHttp ([Http]); tests substitute canned replies. */
interface HttpApi {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String
    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): String
}

object Http : HttpApi {
    /** Error bodies are kept for the provider to explain the failure, but never at full size. */
    private const val MAX_ERROR_BODY = 2_000
    /** Largest reply read into memory; an email with big attachments must not take the app down. */
    private const val MAX_BODY_BYTES = 8L * 1024 * 1024

    private val json = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String, headers: Map<String, String>): String =
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())

    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        execute(
            Request.Builder().url(url).post(body.toRequestBody(json))
                .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        )

    override suspend fun delete(url: String, headers: Map<String, String>): String =
        execute(Request.Builder().url(url).delete().apply { headers.forEach { (k, v) -> header(k, v) } }.build())

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.let { body ->
                val source = body.source()
                if (source.request(MAX_BODY_BYTES + 1)) throw ProviderException("The reply from ${request.url.host} is too large")
                source.readUtf8()
            }.orEmpty()
            if (!resp.isSuccessful) throw HttpException(resp.code, request.url.host, text.take(MAX_ERROR_BODY))
            text
        }
    }
}

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
private val random = SecureRandom()

/** Random mailbox name or password: unguessable, since a public inbox is only as private as its name. */
fun randomName(length: Int = 10): String =
    (1..length).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

/**
 * Cleans a user-typed mailbox name into something every provider accepts: lowercase ASCII
 * letters and digits plus . _ - (accents stripped, "josé" -> "jose"), no dot at either end and no
 * run of dots. Null when nothing is left.
 */
fun sanitizeName(raw: String?): String? =
    raw?.trim()?.substringBefore('@')?.lowercase()
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        ?.filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }
        ?.replace(Regex("""\.{2,}"""), ".")
        ?.trim('.')
        ?.takeIf { it.isNotBlank() }

/** Non-2xx response; [code] lets a provider react (e.g. refresh a token on 401), [body] explain. */
class HttpException(val code: Int, host: String, val body: String = "") : ProviderException("$host answered HTTP $code")
