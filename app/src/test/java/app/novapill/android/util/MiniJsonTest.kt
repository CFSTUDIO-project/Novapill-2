package app.novapill.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {
    @Test
    fun parsesNestedStructures() {
        val v = MiniJson.parse("""{"a":1,"b":[true,false,null,"x"],"c":{"d":2.5,"e":-3}}""").asJsonMap()!!
        assertEquals(1L, v.jsonLong("a"))
        assertEquals(listOf<Any?>(true, false, null, "x"), v["b"].asJsonList())
        val c = v["c"].asJsonMap()!!
        assertEquals(2.5, c.jsonDouble("d")!!, 0.0)
        assertEquals(-3, c.jsonInt("e"))
    }

    @Test
    fun parsesEscapesAndUnicode() {
        val s = MiniJson.parse(""""line\nbreak \"q\" \\ é \/ tab\t"""") as String
        assertEquals("line\nbreak \"q\" \\ é / tab\t", s)
    }

    @Test
    fun roundTripsTrickyStrings() {
        val original = mapOf("k" to "quote\" back\\ nl\n tab\t ctrl\u0001 uniü", "n" to listOf(1, 2.5, true, null))
        val back = MiniJson.parse(MiniJson.stringify(original)).asJsonMap()!!
        assertEquals("quote\" back\\ nl\n tab\t ctrl\u0001 uniü", back["k"])
        assertEquals(listOf<Any?>(1L, 2.5, true, null), back["n"].asJsonList())
    }

    @Test
    fun invalidInputYieldsNullNotExceptions() {
        for (bad in listOf("{", "[1,", "{\"a\":}", "nul", "\"unterminated", "{} trailing", "{'a':1}", "01x")) {
            assertNull("should reject: $bad", MiniJson.parseOrNull(bad))
        }
        assertNull(MiniJson.parseOrNull(null))
        assertNull(MiniJson.parseOrNull("   "))
    }

    @Test
    fun deepNestingIsRejected() {
        val deep = "[".repeat(500) + "]".repeat(500)
        assertNull(MiniJson.parseOrNull(deep))
    }

    @Test
    fun numbersKeepTheirKind() {
        assertEquals(10L, MiniJson.parse("10"))
        assertEquals(10.0, MiniJson.parse("1e1"))
        assertEquals(-0.5, MiniJson.parse("-0.5"))
        assertTrue(MiniJson.stringify(Double.NaN) == "null")
    }

    @Test
    fun enumsAndMapsAreWritten() {
        assertEquals("{\"x\":\"B\",\"y\":[1,2]}", MiniJson.stringify(linkedMapOf("x" to E.B, "y" to listOf(1, 2))))
    }

    private enum class E { A, B }
}
