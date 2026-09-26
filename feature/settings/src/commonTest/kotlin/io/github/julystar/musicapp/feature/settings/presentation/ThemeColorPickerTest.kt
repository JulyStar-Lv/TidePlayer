package io.github.julystar.musicapp.feature.settings.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeColorPickerTest {

    @Test
    fun `hex parser accepts six digit values with an optional prefix`() {
        assertEquals(0xFFFF5B8AL, parseThemeSeedHex("FF5B8A"))
        assertEquals(0xFF3D9AFFL, parseThemeSeedHex("#3d9aff"))
    }

    @Test
    fun `hex parser rejects malformed values`() {
        assertNull(parseThemeSeedHex("FFF"))
        assertNull(parseThemeSeedHex("GG0000"))
        assertNull(parseThemeSeedHex("#1234567"))
    }

    @Test
    fun `formatter emits uppercase rgb without alpha`() {
        assertEquals("#FF5B8A", formatThemeSeedHex(0x12FF5B8AL))
        assertEquals("Display P3 #F31B34", formatThemePrimaryValue(0xFFF31B34L, false))
        assertEquals("sRGB #FF375F", formatThemePrimaryValue(0xFFFF375FL, true))
    }
}
