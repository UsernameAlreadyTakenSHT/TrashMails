package io.github.usernamealreadytakensht.trashmails.data

import org.json.JSONObject

/*
 * Android's org.json turns a JSON null into the string "null" in optString (the reference
 * library the JVM tests run on gives ""), so message fields go through these instead.
 */

/** The text at [name], or null when it is absent, a JSON null or blank. */
fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

/** The text at [name], or "" when it is absent, a JSON null or blank. */
fun JSONObject.textOrEmpty(name: String): String = text(name).orEmpty()

/** An ISO-8601 date-time ("…Z" or with an offset) in epoch milliseconds, or [fallback] when it is not one. */
fun parseIsoDate(iso: String, fallback: Long = System.currentTimeMillis()): Long =
    runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(fallback)
