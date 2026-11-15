package io.github.usernamealreadytakensht.trashmails.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NameHelpersTest {
    @Test
    fun sanitizeName_keepsLowercaseAsciiAndSeparators() {
        assertEquals("john.doe_1-2", sanitizeName("  John.Doe_1-2 "))
    }

    @Test
    fun sanitizeName_dropsDomainAndForeignCharacters() {
        assertEquals("jose", sanitizeName("josé@example.com"))
        assertEquals("ab", sanitizeName("a b!#"))
    }

    @Test
    fun sanitizeName_normalisesDots() {
        assertEquals("a.b", sanitizeName(".a..b."))
        assertEquals("a.b", sanitizeName("a...b"))
    }

    @Test
    fun sanitizeName_isNullWhenNothingIsLeft() {
        assertNull(sanitizeName(null))
        assertNull(sanitizeName(""))
        assertNull(sanitizeName("..."))
        assertNull(sanitizeName("@example.com"))
    }

    @Test
    fun checkedName_refusesWhatTheServiceWouldNot() {
        // Counted once cleaned: "a b!" is "ab", two characters.
        val e = assertThrows(ProviderException::class.java) { checkedName("a b!", Provider.INBOX_KITTEN) }
        assertEquals("Inbox Kitten needs a name of 3 characters at least", e.message)
        assertEquals("abc", checkedName("abc", Provider.INBOX_KITTEN))
        assertEquals("ab", checkedName("ab", Provider.MAILDROP))
    }

    @Test
    fun checkedName_refusesALocalPartOverSixtyFourCharacters_forEveryProvider() {
        assertEquals("a".repeat(64), checkedName("a".repeat(64), Provider.MAIL_TM))
        Provider.entries.forEach { p -> assertThrows(ProviderException::class.java) { checkedName("a".repeat(65), p) } }
    }

    @Test
    fun checkedName_leavesAnEmptyNameToTheRandomOne() {
        assertNull(checkedName("  ", Provider.INBOX_KITTEN))
        assertNull(checkedName(null, Provider.INBOX_KITTEN))
    }

    @Test
    fun randomName_hasTheRequestedLengthAndAlphabet() {
        val name = randomName(24)
        assertEquals(24, name.length)
        assertTrue(name.all { it in 'a'..'z' || it in '0'..'9' })
        assertNotEquals(name, randomName(24))
    }
}
