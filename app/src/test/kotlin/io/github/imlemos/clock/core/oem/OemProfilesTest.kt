package io.github.imlemos.clock.core.oem

import org.junit.Test
import kotlin.test.assertEquals

class OemProfilesTest {

    @Test
    fun `ColorOS family is detected from manufacturer or brand`() {
        assertEquals("coloros", OemProfiles.forDevice("OPPO", "OPPO").id)
        assertEquals("coloros", OemProfiles.forDevice("realme", "realme").id)
        assertEquals("coloros", OemProfiles.forDevice("OnePlus", "OnePlus").id)
        assertEquals("https://dontkillmyapp.com/realme", OemProfiles.forDevice("realme", "realme").moreInfoUrl)
    }

    @Test
    fun `other vendors map to their profiles`() {
        assertEquals("xiaomi", OemProfiles.forDevice("Xiaomi", "Redmi").id)
        assertEquals("xiaomi", OemProfiles.forDevice("Xiaomi", "POCO").id)
        assertEquals("samsung", OemProfiles.forDevice("samsung", "samsung").id)
    }

    @Test
    fun `unknown vendors fall back to the generic profile`() {
        assertEquals("generic", OemProfiles.forDevice("Google", "google").id)
        assertEquals("generic", OemProfiles.forDevice("Fairphone", "Fairphone").id)
    }
}
