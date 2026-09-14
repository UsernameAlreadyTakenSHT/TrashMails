package com.example.trashmails.data

import android.content.Context

/**
 * Courtesy rate limit on inbox creation: at most [Provider.dailyLimit] per rolling 24 h window,
 * tracked locally (creation timestamps in SharedPreferences). Not a security measure.
 */
class CreationQuota(context: Context) {
    private val prefs = context.getSharedPreferences("creation_quota", Context.MODE_PRIVATE)

    data class Status(val used: Int, val limit: Int, val nextSlotAt: Long?) {
        val remaining: Int get() = (limit - used).coerceAtLeast(0)
        val exhausted: Boolean get() = remaining == 0
    }

    fun status(provider: Provider, now: Long = System.currentTimeMillis()): Status {
        val stamps = recent(provider, now)
        val nextSlot = if (stamps.size >= provider.dailyLimit) stamps.min() + WINDOW_MS else null
        return Status(used = stamps.size, limit = provider.dailyLimit, nextSlotAt = nextSlot)
    }

    fun record(provider: Provider, now: Long = System.currentTimeMillis()) {
        save(provider, recent(provider, now) + now)
    }

    private fun recent(provider: Provider, now: Long): List<Long> =
        prefs.getString(provider.name, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            // A stamp in the future (clock set back) would block creation for up to a day: dropped.
            ?.filter { it <= now && now - it < WINDOW_MS }
            .orEmpty()

    private fun save(provider: Provider, stamps: List<Long>) {
        prefs.edit().putString(provider.name, stamps.joinToString(",")).apply()
    }

    private companion object {
        const val WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
