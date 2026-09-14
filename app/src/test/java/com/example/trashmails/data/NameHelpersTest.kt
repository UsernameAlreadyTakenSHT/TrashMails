package com.example.trashmails.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun randomName_hasTheRequestedLengthAndAlphabet() {
        val name = randomName(24)
        assertEquals(24, name.length)
        assertTrue(name.all { it in 'a'..'z' || it in '0'..'9' })
        assertNotEquals(name, randomName(24))
    }
}
