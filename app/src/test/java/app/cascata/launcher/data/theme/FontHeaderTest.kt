package app.cascata.launcher.data.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontHeaderTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun tag(text: String) = text.toByteArray(Charsets.US_ASCII)

    @Test
    fun `ttf padrao e aceita`() {
        assertTrue(isFontHeader(bytes(0x00, 0x01, 0x00, 0x00)))
        // O resto do arquivo não importa para o cabeçalho.
        assertTrue(isFontHeader(bytes(0x00, 0x01, 0x00, 0x00, 0x99, 0x99)))
    }

    @Test
    fun `otf e ttf da apple sao aceitas`() {
        assertTrue(isFontHeader(tag("OTTO")))
        assertTrue(isFontHeader(tag("true")))
    }

    @Test
    fun `outros arquivos sao recusados`() {
        assertFalse(isFontHeader(tag("%PDF")))
        assertFalse(isFontHeader(tag("PK")))
        assertFalse(isFontHeader(tag("wOFF")))
        assertFalse(isFontHeader(tag("ttcf")))
        assertFalse(isFontHeader(bytes(0x00, 0x01, 0x00, 0x01)))
    }

    @Test
    fun `arquivo curto demais e recusado`() {
        assertFalse(isFontHeader(ByteArray(0)))
        assertFalse(isFontHeader(bytes(0x00, 0x01, 0x00)))
    }
}
