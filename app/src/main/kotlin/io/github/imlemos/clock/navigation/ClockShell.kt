package io.github.imlemos.clock.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.imlemos.clock.BuildConfig
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.ui.AlarmEditorScreen
import io.github.imlemos.clock.alarm.ui.AlarmEditorViewModel
import io.github.imlemos.clock.alarm.ui.AlarmsScreen
import io.github.imlemos.clock.reliability.ReliabilityScreen
import io.github.imlemos.clock.core.designsystem.component.FloatingNavBar
import io.github.imlemos.clock.core.designsystem.component.GlassIconButton
import io.github.imlemos.clock.core.designsystem.component.NavBarItem
import io.github.imlemos.clock.core.designsystem.glass.GlassLabScreen
import io.github.imlemos.clock.core.designsystem.glass.glassBackdropSource
import io.github.imlemos.clock.core.designsystem.glass.rememberGlassBackdrop
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.settings.SettingsScreen
import io.github.imlemos.clock.stopwatch.StopwatchScreen
import io.github.imlemos.clock.timer.TimersScreen

/** Space reserved at the bottom of tab screens for the floating "+" and navigation capsule. */
private val FloatingChromeHeight = 64.dp + 12.dp + 64.dp + 16.dp

/**
 * @param showReliabilityIntro Opens "Set up reliability" once, on the first launch.
 */
@Composable
fun ClockShell(showReliabilityIntro: Boolean, onReliabilityIntroShown: () -> Unit) {
    val backStack = rememberNavBackStack(AlarmsDestination)
    LaunchedEffect(showReliabilityIntro) {
        if (showReliabilityIntro) {
            backStack.add(ReliabilityDestination)
            onReliabilityIntroShown()
        }
    }
    val backdrop = rememberGlassBackdrop()
    val currentTab = backStack.lastOrNull() as? TopLevelDestination
    val tabPadding = PaddingValues(bottom = FloatingChromeHeight)
    val openSettings: () -> Unit = { backStack.add(SettingsDestination) }
    val openReliability: () -> Unit = { backStack.add(ReliabilityDestination) }
    val pop: () -> Unit = { backStack.removeLastOrNull() }

    Box(
        Modifier
            .fillMaxSize()
            .background(ClockTheme.colors.background),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            popTransitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            entryProvider = entryProvider {
                entry<AlarmsDestination> {
                    AlarmsScreen(
                        contentPadding = tabPadding,
                        onOpenSettings = openSettings,
                        onEditAlarm = { backStack.add(AlarmEditorDestination(it)) },
                        onOpenReliability = openReliability,
                    )
                }
                entry<TimersDestination> { TimersScreen(tabPadding, onOpenSettings = openSettings) }
                entry<StopwatchDestination> { StopwatchScreen(tabPadding, onOpenSettings = openSettings) }
                entry<SettingsDestination> {
                    SettingsScreen(
                        onBack = pop,
                        onOpenReliability = openReliability,
                        onOpenGlassLab = if (BuildConfig.DEBUG) ({ backStack.add(GlassLabDestination) }) else null,
                    )
                }
                entry<GlassLabDestination> { GlassLabScreen(onBack = pop) }
                entry<AlarmEditorDestination> { key -> AlarmEditorScreen(alarmId = key.alarmId, onClose = pop) }
                entry<ReliabilityDestination> { ReliabilityScreen(onBack = pop) }
            },
        )

        AnimatedVisibility(
            visible = currentTab != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val addAction = currentTab?.addAction
                AnimatedVisibility(visible = addAction != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    if (addAction != null) {
                        GlassIconButton(
                            backdrop = backdrop,
                            icon = R.drawable.ic_add,
                            contentDescription = stringResource(addAction),
                            onClick = {
                                // Timers arrive in phase 7.
                                if (currentTab == AlarmsDestination) {
                                    backStack.add(AlarmEditorDestination(AlarmEditorViewModel.NEW_ALARM))
                                }
                            },
                        )
                    }
                }
                FloatingNavBar(
                    backdrop = backdrop,
                    items = TopLevelDestinations.map { NavBarItem(it, it.icon, stringResource(it.label)) },
                    selectedKey = currentTab,
                    onSelect = { item -> backStack.selectTab(item.key as TopLevelDestination) },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Alarms is the start tab: back from any other tab returns to it, and back from Alarms leaves
 * the app.
 */
private fun NavBackStack<NavKey>.selectTab(tab: TopLevelDestination) {
    if (lastOrNull() == tab) return
    clear()
    add(AlarmsDestination)
    if (tab != AlarmsDestination) add(tab)
}
