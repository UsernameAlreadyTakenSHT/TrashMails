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
    /** FLAG_SECURE: no screenshots, no preview in the recent apps (verification codes stay in the app). */
    val blockScreenshots: Boolean = false,
    /** Addresses created more than this many hours ago are forgotten (locally only); 0 keeps them. */
    val forgetAfterHours: Int = 0,
    /** "system", "light" or "dark". */
    val theme: String = THEME_SYSTEM,
) {
    val pollIntervalMs: Long get() = pollIntervalSec * 1000L
    val autoRefresh: Boolean get() = pollIntervalSec > 0
    val forgetAfterMs: Long? get() = forgetAfterHours.takeIf { it > 0 }?.let { it * 3_600_000L }

    companion object {
        /** Choices offered for [pollIntervalSec], with their labels. */
        val POLL_INTERVALS = listOf(30 to "Every 30 s", 60 to "Every minute", 300 to "Every 5 min", 0 to "Manual only")
        /** Choices offered for [forgetAfterHours], with their labels. */
        val FORGET_AFTER = listOf(0 to "Never", 24 to "After 24 hours", 7 * 24 to "After 7 days", 30 * 24 to "After 30 days")
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        val THEMES = listOf(THEME_SYSTEM to "Follow the system", THEME_LIGHT to "Light", THEME_DARK to "Dark")
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
            blockScreenshots = prefs.getBoolean(BLOCK_SCREENSHOTS, defaults.blockScreenshots),
            forgetAfterHours = prefs.getInt(FORGET_AFTER, defaults.forgetAfterHours),
            theme = prefs.getString(THEME, null) ?: defaults.theme,
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putBoolean(RENDER_HTML, s.renderHtml)
            .putBoolean(LOAD_IMAGES, s.loadImages)
            .putBoolean(CONFIRM_LINKS, s.confirmLinks)
            .putInt(POLL_INTERVAL, s.pollIntervalSec)
            .putBoolean(BLOCK_SCREENSHOTS, s.blockScreenshots)
            .putInt(FORGET_AFTER, s.forgetAfterHours)
            .putString(THEME, s.theme)
            .apply()
    }

    private companion object {
        const val RENDER_HTML = "render_html"
        const val LOAD_IMAGES = "load_images"
        const val CONFIRM_LINKS = "confirm_links"
        const val POLL_INTERVAL = "poll_interval_sec"
        const val BLOCK_SCREENSHOTS = "block_screenshots"
        const val FORGET_AFTER = "forget_after_hours"
        const val THEME = "theme"
    }
}
