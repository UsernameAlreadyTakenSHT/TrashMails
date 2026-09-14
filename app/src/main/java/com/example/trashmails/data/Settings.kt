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
)

/** Persistence of [Settings] (SharedPreferences). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            renderHtml = prefs.getBoolean(RENDER_HTML, defaults.renderHtml),
            loadImages = prefs.getBoolean(LOAD_IMAGES, defaults.loadImages),
            confirmLinks = prefs.getBoolean(CONFIRM_LINKS, defaults.confirmLinks),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putBoolean(RENDER_HTML, s.renderHtml)
            .putBoolean(LOAD_IMAGES, s.loadImages)
            .putBoolean(CONFIRM_LINKS, s.confirmLinks)
            .apply()
    }

    private companion object {
        const val RENDER_HTML = "render_html"
        const val LOAD_IMAGES = "load_images"
        const val CONFIRM_LINKS = "confirm_links"
    }
}
