package io.github.imlemos.clock.core.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ZoneOptionTest {
    private val newYork = ZoneOption(ZoneId.of("America/New_York"), "Nova Iorque", "Estados Unidos")
    private val auckland = ZoneOption(ZoneId.of("Pacific/Auckland"), "Auckland", "Nova Zelândia")
    private val saoPaulo = ZoneOption(ZoneId.of("America/Sao_Paulo"), "São Paulo", "Brasil")

    @Test
    fun `matches city, country and zone id, ignoring case and accents`() {
        assertTrue(saoPaulo.matches("sao paulo"))
        assertTrue(auckland.matches("zelandia"))
        assertTrue(newYork.matches("new_york"))
        assertTrue(newYork.matches("iorque estados"))
        assertFalse(newYork.matches("tokyo"))
    }

    @Test
    fun `cities that start with the query come before country matches`() {
        val sorted = listOf(auckland, newYork).filter { it.matches("nova") }.sortedBy { it.rank("nova") }
        assertEquals(listOf(newYork, auckland), sorted)
    }
}
