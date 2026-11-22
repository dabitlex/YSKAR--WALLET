package com.yskar.wallet.otc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der strenge Leser fuer `d` (FlachJson). Liegt unter src/sharedTest und
 * laeuft auf der JVM und auf dem Geraet - beide Male muss dasselbe
 * herauskommen, denn FlachJson benutzt kein org.json.
 */
class FlachJsonTest {

    private fun ok(text: String, erwartet: Map<String, Any>) = assertEquals(text, erwartet, FlachJson.lesen(text))
    private fun nein(text: String) = assertNull(text, FlachJson.lesen(text))

    @Test
    fun gueltig() {
        ok("""{"a":"x","b":12}""", mapOf("a" to "x", "b" to 12L))
        ok("{}", emptyMap())
        ok(" \t\r\n{ \"a\" : 0 , \"b\" : \"\" }\n ", mapOf("a" to 0L, "b" to ""))
        ok("""{"max":9223372036854775807}""", mapOf("max" to Long.MAX_VALUE))
    }

    @Test
    fun zahlenSindLong() {
        val n = FlachJson.lesen("""{"n":72}""")!!.getValue("n")
        assertTrue("n ist ${n::class}", n is Long)
        assertEquals(72L, n)
    }

    @Test
    fun reihenfolgeBleibt() {
        assertEquals(listOf("z", "a", "m"), FlachJson.lesen("""{"z":1,"a":2,"m":3}""")!!.keys.toList())
    }

    @Test
    fun escapes() {
        ok("""{"s":"\"\\\/\b\f\n\r\t"}""", mapOf("s" to "\"\\/\b\u000C\n\r\t"))
        ok("""{"s":"\u0079\u00e4\u00C4"}""", mapOf("s" to "yäÄ"))
        ok("{\"s\":\"ä€\"}", mapOf("s" to "ä€"))
        ok("""{"\u0061":1}""", mapOf("a" to 1L))
    }

    @Test
    fun schlechteEscapes() {
        nein("""{"s":"\x41"}""")
        nein("""{"s":"\u00g1"}""")
        nein("""{"s":"\u00"}""")
        nein("""{"s":"\"}""")
        nein("{\"s\":\"\\u\u0661\u0662\u0663\u0664\"}") // arabisch-indische Ziffern sind kein Hex
    }

    @Test
    fun roheSteuerzeichen() {
        nein("{\"s\":\"a\nb\"}")
        nein("{\"s\":\"a\tb\"}")
        nein("{\"s\":\"a\u0000b\"}")
        nein("{\"a\nb\":1}")
    }

    @Test
    fun doppelterSchluessel() {
        nein("""{"a":1,"a":1}""")
        nein("""{"a":1,"a":"1"}""")
        nein("""{"a":1,"\u0061":2}""")
    }

    @Test
    fun zahlenDieJsonNichtKennt() {
        for (z in listOf("0x48", "0X48", "010", "00", "0110", "-1", "+1", "-0", "1.0", "1.", ".5", "1e3", "1E3", "7.2e1", "Infinity", "NaN"))
            nein("""{"n":$z}""")
        nein("""{"n":9223372036854775808}""")
        nein("""{"n":99999999999999999999999}""")
    }

    @Test
    fun andereWerte() {
        for (w in listOf("true", "false", "null", "[]", "[1]", "{}", """{"a":1}""", "'x'", "x"))
            nein("""{"a":$w}""")
    }

    @Test
    fun rahmen() {
        nein("")
        nein("   ")
        nein("[]")
        nein(""""a"""")
        nein("1")
        nein("{")
        nein("}")
        nein("""{"a":1""")
        nein("""{"a":1,}""")
        nein("""{,"a":1}""")
        nein("""{"a" 1}""")
        nein("""{"a":}""")
        nein("""{a:1}""")
        nein("""{'a':1}""")
        nein("""{"a":1};""")
        nein("""{"a":1} x""")
        nein("""{"a":1}{}""")
        nein("""{"a":1}""" + "\u00a0") // nur Leerzeichen, Tab, CR, LF zaehlen als Leerraum
    }

    @Test
    fun hoechstens2000Zeichen() {
        val rahmen = """{"s":""}""".length
        val passt = """{"s":"${"a".repeat(FlachJson.MAX_ZEICHEN - rahmen)}"}"""
        assertEquals(FlachJson.MAX_ZEICHEN, passt.length)
        assertEquals(FlachJson.MAX_ZEICHEN - rahmen, (FlachJson.lesen(passt)!!["s"] as String).length)
        nein("""{"s":"${"a".repeat(FlachJson.MAX_ZEICHEN - rahmen + 1)}"}""")
    }
}
