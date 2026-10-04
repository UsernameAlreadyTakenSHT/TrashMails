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

    /** Reverses the secret behind the sealed prefix: enough to see what is sealed and what is not. */
    private val reversing = object : SecretBox {
        override fun seal(plain: String) = "k1:" + plain.reversed()
        override fun open(stored: String) = if (stored.startsWith("k1:")) stored.removePrefix("k1:").reversed() else stored
    }

    @Test
    fun tokensAreSealed_andClearOnesFromBeforeAreSealedOnLoad() {
        prefs.put("list" to """[{"id":"a","provider":"MAIL_TM","address":"a@mail.tm","token":"pw","createdAt":1}]""")
        val sealing = InboxStore(prefs, reversing)
        assertEquals("pw", sealing.load().single().token)
        val stored = prefs.getString("list")!!
        assertTrue(stored.contains("k1:wp") && !stored.contains("\"pw\""))
        assertEquals("pw", sealing.load().single().token)
    }

    @Test
    fun aKeystoreFailingForAMoment_losesNoToken() {
        var failing = true
        val flaky = object : SecretBox {
            override fun seal(plain: String) = reversing.seal(plain)
            override fun open(stored: String) = if (failing) null else reversing.open(stored)
        }
        InboxStore(prefs, reversing).save(listOf(Inbox("a", Provider.MAIL_TM, "a@mail.tm", token = "pw", createdAt = 1L)))
        // Loaded while the keystore fails, then saved (an address created meanwhile).
        val store = InboxStore(prefs, flaky)
        val loaded = store.load()
        assertEquals(null, loaded.single().token)
        store.save(loaded + Inbox("b", Provider.MAILDROP, "b@maildrop.cc", createdAt = 2L))
        failing = false
        assertEquals("pw", InboxStore(prefs, flaky).load().first { it.id == "a" }.token)
    }

    @Test
    fun aTokenThatCannotBeOpened_costsOnlyThatAddressItsAccess() {
        val failing = object : SecretBox {
            override fun seal(plain: String) = "k1:x"
            override fun open(stored: String): String? = null
        }
        val sealing = InboxStore(prefs, failing)
        sealing.save(listOf(Inbox("a", Provider.MAIL_TM, "a@mail.tm", token = "pw", createdAt = 1L)))
        assertEquals(listOf(Inbox("a", Provider.MAIL_TM, "a@mail.tm", token = null, createdAt = 1L)), sealing.load())
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
    fun aDamagedSetting_fallsBackAlone() {
        val prefs = object : Prefs by MemoryPrefs() {
            override fun getBoolean(key: String, default: Boolean): Boolean =
                if (key == "confirm_links") throw ClassCastException() else default
            override fun getInt(key: String, default: Int): Int = if (key == "poll_interval_sec") -5 else default
        }
        assertEquals(Settings(), SettingsStore(prefs).load())
    }

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

class MessageCacheTest {
    private class CountingPrefs(private val inner: MemoryPrefs = MemoryPrefs()) : Prefs by inner {
        var puts = 0
        override fun put(vararg entries: Pair<String, Any>) { puts++; inner.put(*entries) }
    }

    private val prefs = CountingPrefs()
    private val cache = MessageCache(prefs)
    private val now = System.currentTimeMillis()
    private val long = MailSummary(id = "1", from = "a", subject = "s", date = now, html = "x".repeat(200_000))

    @Test
    fun anOversizedBody_isNotRewrittenAtEveryRefresh() {
        cache.update("k") { listOf(long) }
        cache.update("k") { known -> (listOf(long) + known).distinctBy { it.id } }
        assertEquals(1, prefs.puts)
    }

    @Test
    fun aForgottenAddress_isNotWrittenBack() {
        cache.update("k") { listOf(long) }
        cache.clear("k")
        cache.update("k") { listOf(long) }
        assertTrue(cache.load("k").isEmpty())
        assertTrue(cache.isForgotten("k"))
    }

    @Test
    fun anAddressStaysWithinItsBudget_oldestDroppedFirst() {
        // Quotes double in JSON: 50 bodies of 64 Ki quotes would be 6 M characters.
        val kept = cache.update("k") { (1..50).map { long.copy(id = "$it", date = now - it, html = "\"".repeat(100_000)) } }
        assertTrue((prefs.getString("k")?.length ?: 0) <= 2 * 1024 * 1024)
        assertTrue(kept.size in 1 until 50)
        assertEquals("1", kept.first().id)
    }

    @Test
    fun aMessageKeptOverAWeek_isDropped_fromTheFileToo() {
        val week = 7 * 24 * 3_600_000L
        prefs.put("k" to """[{"id":"old","from":"","subject":"","date":$now,"kept":${now - week - 1}},
            {"id":"new","from":"","subject":"","date":1,"kept":$now}]""")
        assertEquals(listOf("new"), cache.load("k").map { it.id })
        assertTrue(prefs.getString("k")!!.contains("\"new\"") && !prefs.getString("k")!!.contains("\"old\""))
    }

    @Test
    fun aDamagedFile_readsAsEmpty() {
        prefs.put("k" to "[{\"id\":")
        assertTrue(cache.load("k").isEmpty())
        assertEquals(1, cache.update("k") { listOf(long) }.size)
    }
}

class FileStoreTest {
    private val dir = java.nio.file.Files.createTempDirectory("store").toFile()
    private val store = FileStore(dir)

    @Test
    fun oneFilePerKey_removedWithIt() {
        store.put("tempmail_lol:a@b" to "x", "dropmail:c@d" to "y")
        assertEquals("x", store.getString("tempmail_lol:a@b"))
        assertEquals(2, dir.listFiles()!!.size)
        store.remove("tempmail_lol:a@b")
        assertNull(store.getString("tempmail_lol:a@b"))
        assertEquals(1, dir.listFiles()!!.size)
        store.put("dropmail:c@d" to "z")
        assertEquals("z", store.getString("dropmail:c@d"))
    }
}
