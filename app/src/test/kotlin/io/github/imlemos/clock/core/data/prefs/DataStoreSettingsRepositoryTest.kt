package io.github.imlemos.clock.core.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private fun repository() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) {
            tmp.root.resolve("settings.preferences_pb")
        },
    )

    @Test
    fun `defaults follow the system theme with the default accent and glass on`() = testScope.runTest {
        assertEquals(AppearanceSettings(), repository().appearance.first())
    }

    @Test
    fun `persists appearance changes`() = testScope.runTest {
        val repository = repository()
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAccent(0xFF2266FF.toInt())
        repository.setReduceTransparency(true)

        assertEquals(
            AppearanceSettings(ThemeMode.DARK, 0xFF2266FF.toInt(), reduceTransparency = true),
            repository.appearance.first(),
        )
    }

    @Test
    fun `clearing the accent restores the default`() = testScope.runTest {
        val repository = repository()
        repository.setAccent(0xFF2266FF.toInt())
        repository.setAccent(null)

        assertEquals(null, repository.appearance.first().accentArgb)
    }

    @Test
    fun `the manual zone is automatic by default and can be set and cleared`() = testScope.runTest {
        val repository = repository()
        assertEquals(null, repository.manualZone.first())
        repository.setManualZone(java.time.ZoneId.of("America/New_York"))
        assertEquals(java.time.ZoneId.of("America/New_York"), repository.manualZone.first())
        repository.setManualZone(null)
        assertEquals(null, repository.manualZone.first())
    }
}
