package com.example.trashmails.data

import android.content.Context

/** User preferences; the defaults are the safe side of each choice. */
data class Settings(
    /** Render HTML emails in a WebView; off shows every email as plain text. */
    val renderHtml: Boolean = false,
    /** Fetch images, styles and fonts hosted on the sender's servers when rendering HTML. */
    val loadImages: Boolean = false,
    /** Show the link's address and ask before leaving the app. */
    val confirmLinks: Boolean = true,
    /** Seconds between two automatic listings of the open inbox; 0 lists only on demand. */
    val pollIntervalSec: Int = 60,
) {
    val pollIntervalMs: Long get() = pollIntervalSec * 1000L
    val autoRefresh: Boolean get() = pollIntervalSec > 0

    companion object {
        /** Choices offered for [pollIntervalSec], with their labels. */
        val POLL_INTERVALS = listOf(30 to "Every 30 s", 60 to "Every minute", 300 to "Every 5 min", 0 to "Manual only")
    }
}

/** Persistence of [Settings] (SharedPreferences). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            renderHtml = prefs.getBoolean(RENDER_HTML, defaults.renderHtml),
            loadImages = prefs.getBoolean(LOAD_IMAGES, defaults.loadImages),
            confirmLinks = prefs.getBoolean(CONFIRM_LINKS, defaults.confirmLinks),
            pollIntervalSec = prefs.getInt(POLL_INTERVAL, defaults.pollIntervalSec),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putBoolean(RENDER_HTML, s.renderHtml)
            .putBoolean(LOAD_IMAGES, s.loadImages)
            .putBoolean(CONFIRM_LINKS, s.confirmLinks)
            .putInt(POLL_INTERVAL, s.pollIntervalSec)
            .apply()
    }

    private companion object {
        const val RENDER_HTML = "render_html"
        const val LOAD_IMAGES = "load_images"
        const val CONFIRM_LINKS = "confirm_links"
        const val POLL_INTERVAL = "poll_interval_sec"
    }
}
