package io.github.usernamealreadytakensht.trashmails.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory [Prefs] for the JVM tests. */
class MemoryPrefs : Prefs {
    val map = mutableMapOf<String, Any>()
    override fun getString(key: String): String? = map[key] as? String
    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = map[key] as? Boolean ?: default
    override fun put(vararg entries: Pair<String, Any>) { entries.forEach { (k, v) -> map[k] = v } }
    override fun remove(vararg keys: String) { keys.forEach { map.remove(it) } }
}

class InboxStoreTest {
    private val prefs = MemoryPrefs()
    private val store = InboxStore(prefs)

    @Test
    fun roundTrip_keepsEveryField() {
        val inboxes = listOf(
            Inbox("a", Provider.MAIL_TM, "a@mail.tm", token = "pw", createdAt = 1L, expiresAt = null),
            Inbox("b", Provider.BURNER_KIWI, "b@deceit.pro", token = "jwt", createdAt = 2L, expiresAt = 3L),
            Inbox("c", Provider.INBOX_KITTEN, "c@inboxkitten.com", token = null, createdAt = 4L),
        )
        store.save(inboxes)
        assertEquals(inboxes, store.load())
    }

    @Test
    fun aMalformedEntryIsDroppedAlone_andAGuerrillaTokenIsNotKeptAround() {
        prefs.put("list" to """[{"id":"x","provider":"MAILDROP","address":"x@maildrop.cc","createdAt":5},{"provider":"NOPE"},{"id":"g","provider":"GUERRILLA_MAIL","address":"g@guerrillamailblock.com","token":"oldsid","createdAt":6},{"nonsense":true}]""")
        val loaded = store.load()
        assertEquals(listOf("x", "g"), loaded.map { it.id })
        assertNull(loaded[1].token)
    }

    @Test
    fun garbageMeansAnEmptyList() {
        prefs.put("list" to "not json")
        assertTrue(store.load().isEmpty())
    }
}

class ReadStoreTest {
    @Test
    fun roundTrip_andTrimToTheLast200Ids() {
        val prefs = MemoryPrefs()
        val store = ReadStore(prefs)
        val many = (1..250).map { "m$it" }.toSet()
        store.save(ReadStore.State(read = mapOf("k1" to many, "k2" to setOf("z")), unread = mapOf("k1" to 3)))

        val loaded = store.load()
        assertEquals(200, loaded.read.getValue("k1").size)
        assertTrue(loaded.read.getValue("k1").contains("m250"))
        assertFalse(loaded.read.getValue("k1").contains("m1"))
        assertEquals(setOf("z"), loaded.read["k2"])
        assertEquals(mapOf("k1" to 3), loaded.unread)
    }

    @Test
    fun nothingStoredMeansEmptyState() {
        assertEquals(ReadStore.State(emptyMap(), emptyMap()), ReadStore(MemoryPrefs()).load())
    }
}

class SettingsStoreTest {
    @Test
    fun roundTrip_andDefaultsWhenEmptyOrUnknown() {
        val prefs = MemoryPrefs()
        val store = SettingsStore(prefs)
        assertEquals(Settings(), store.load())

        val custom = Settings(
            renderHtml = true, loadImages = true, confirmLinks = false, pollIntervalSec = 300,
            blockScreenshots = true, forgetAfterHours = 24, theme = Settings.THEME_DARK,
            lastProvider = Provider.MAIL_TM, copyOnCreate = false,
        )
        store.save(custom)
        assertEquals(custom, store.load())

        prefs.put("last_provider" to "GONE_PROVIDER")
        assertEquals(Settings().lastProvider, store.load().lastProvider)
    }
}

class CreationQuotaTest {
    private val quota = CreationQuota(MemoryPrefs())
    private val day = 24 * 3_600_000L

    @Test
    fun countsWithinTheRollingWindow_andReportsTheNextSlot() {
        val t0 = 1_000_000_000_000L
        repeat(Provider.BURNER_KIWI.dailyLimit) { quota.record(Provider.BURNER_KIWI, now = t0 + it) }

        val full = quota.status(Provider.BURNER_KIWI, now = t0 + 10)
        assertTrue(full.exhausted)
        assertEquals(t0 + day, full.nextSlotAt)

        // A day and a millisecond later the stamps at t0 and t0 + 1 have left the window.
        val later = quota.status(Provider.BURNER_KIWI, now = t0 + day + 1)
        assertEquals(Provider.BURNER_KIWI.dailyLimit - 2, later.used)
        assertFalse(later.exhausted)
    }

    @Test
    fun aStampInTheFutureIsIgnored() {
        val now = 5_000_000L
        quota.record(Provider.MAILDROP, now = now + 3_600_000L)
        assertEquals(0, quota.status(Provider.MAILDROP, now = now).used)
    }

    @Test
    fun providersAreIndependent() {
        quota.record(Provider.MAIL_TM, now = 1L)
        assertEquals(0, quota.status(Provider.MAILDROP, now = 2L).used)
        assertEquals(1, quota.status(Provider.MAIL_TM, now = 2L).used)
    }
}
