package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.HttpException

/**
 * Canned HTTP for the provider tests: each rule matches a substring of the URL — or, for the GraphQL
 * providers whose calls all share one URL, of the request body — (first match wins, in registration
 * order) and answers with a sequence of steps — a fixture, or an HTTP error —
 * the last step repeating forever. Every call is recorded.
 */
class FakeHttp : HttpApi {
    data class Call(val method: String, val url: String, val body: String?, val headers: Map<String, String>)

    val calls = mutableListOf<Call>()
    private val rules = mutableListOf<Rule>()

    private class Rule(val part: String, val inBody: Boolean, val steps: ArrayDeque<() -> String>) {
        fun matches(url: String, body: String?) = if (inBody) body?.contains(part) == true else url.contains(part)
    }

    fun on(urlPart: String, vararg steps: () -> String) = apply { rules += Rule(urlPart, inBody = false, ArrayDeque(steps.toList())) }

    /** A rule matched against the request body (a GraphQL query, say) rather than the URL. */
    fun onBody(bodyPart: String, vararg steps: () -> String) = apply { rules += Rule(bodyPart, inBody = true, ArrayDeque(steps.toList())) }

    override suspend fun get(url: String, headers: Map<String, String>) = answer("GET", url, null, headers)
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>) = answer("POST", url, body, headers)
    override suspend fun delete(url: String, headers: Map<String, String>) = answer("DELETE", url, null, headers)

    private fun answer(method: String, url: String, body: String?, headers: Map<String, String>): String {
        calls += Call(method, url, body, headers)
        val steps = rules.firstOrNull { it.matches(url, body) }?.steps ?: error("No canned reply for $method $url ${body.orEmpty()}")
        val step = if (steps.size > 1) steps.removeFirst() else steps.first()
        return step()
    }

    fun urls(method: String? = null) = calls.filter { method == null || it.method == method }.map { it.url }

    companion object {
        /** A step answering with the fixture `src/test/resources/fixtures/<name>.json`. */
        fun fixture(name: String): () -> String = {
            FakeHttp::class.java.getResource("/fixtures/$name.json")?.readText() ?: error("Missing fixture $name.json")
        }

        /** A step answering with a literal body. */
        fun body(text: String): () -> String = { text }

        /** A step failing with an HTTP error. */
        fun httpError(code: Int, body: String = ""): () -> String = { throw HttpException(code, "fake.host", body) }
    }
}
