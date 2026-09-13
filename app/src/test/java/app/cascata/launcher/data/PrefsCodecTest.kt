package app.cascata.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefsCodecTest {

    @Test
    fun `lista de chaves vai e volta`() {
        val keys = listOf("com.a/.Main#0", "com.b/.Main#0", "com.c/.Main#10")
        assertEquals(keys, decodeKeys(encodeKeys(keys)))
    }

    @Test
    fun `a ordem e preservada`() {
        val keys = listOf("z", "a", "m")
        assertEquals(keys, decodeKeys(encodeKeys(keys)))
    }

    @Test
    fun `lista vazia e ausencia dao o mesmo`() {
        assertEquals("", encodeKeys(emptyList()))
        assertEquals(emptyList<String>(), decodeKeys(""))
        assertEquals(emptyList<String>(), decodeKeys(null))
    }

    @Test
    fun `linhas vazias sao descartadas`() {
        assertEquals(listOf("a", "b"), decodeKeys("a\n\nb\n"))
    }

    @Test
    fun `prefixo de apelido vai e volta`() {
        val key = "com.a/.Main#0"
        assertEquals("alias:com.a/.Main#0", aliasPrefName(key))
        assertEquals(key, aliasEntryKey(aliasPrefName(key)))
    }

    @Test
    fun `outras preferencias nao sao apelido`() {
        assertNull(aliasEntryKey("favorites_order"))
        assertNull(aliasEntryKey("hidden"))
        assertNull(aliasEntryKey("alias:"))
    }
}
