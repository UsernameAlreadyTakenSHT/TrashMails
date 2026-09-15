package io.github.usernamealreadytakensht.trashmails.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTest {
    private val o = JSONObject().put("s", "hello").put("blank", "  ").put("none", JSONObject.NULL).put("n", 3)

    @Test
    fun text_isNullForAbsentNullOrBlank() {
        assertEquals("hello", o.text("s"))
        assertEquals("3", o.text("n"))
        assertNull(o.text("blank"))
        // A JSON null must never come out as the word "null" (which Android's optString would give).
        assertNull(o.text("none"))
        assertNull(o.text("missing"))
    }

    @Test
    fun textOrEmpty_isEmptyInsteadOfNull() {
        assertEquals("", o.textOrEmpty("none"))
        assertEquals("", o.textOrEmpty("missing"))
        assertEquals("hello", o.textOrEmpty("s"))
    }
}
