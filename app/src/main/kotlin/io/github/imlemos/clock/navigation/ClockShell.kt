package io.github.imlemos.clock.navigation

import android.app.KeyguardManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.trash.TrashScreen
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.imlemos.clock.alarm.layout.LayoutLibraryScreen
import io.github.imlemos.clock.alarm.layout.editor.LayoutEditorScreen
import io.github.imlemos.clock.alarm.ui.AlarmEditorScreen
import io.github.imlemos.clock.alarm.ui.AlarmEditorViewModel
import io.github.imlemos.clock.alarm.ui.AlarmsScreen
import io.github.imlemos.clock.alarm.ui.BatchEditScreen
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

private const val UNDO_SEEN_AFTER_MS = 1_500L

/** Space reserved at the bottom of tab screens for the floating "+" and navigation capsule. */
private val FloatingChromeHeight = 64.dp + 12.dp + 64.dp + 16.dp

/**
 * @param showReliabilityIntro Opens "Set up reliability" once, on the first launch.
 */
@Composable
fun ClockShell(
    showReliabilityIntro: Boolean,
    onReliabilityIntroShown: () -> Unit,
    shellViewModel: ShellViewModel = hiltViewModel(),
) {
    val backStack = rememberNavBackStack(AlarmsDestination)
    val chrome = remember { ShellChrome() }
    val pendingUndo by shellViewModel.pendingUndo.collectAsStateWithLifecycle()
    // The pop-up disappears if the app is closed and reopened without touching it (docs/PLANO.md 3.1).
    // "Seen" means on screen for a moment with the phone unlocked: when an alarm screen closes over
    // the lock screen, this activity is briefly resumed behind the keyguard, which must not count.
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyguard = LocalContext.current.getSystemService(KeyguardManager::class.java)
    LaunchedEffect(pendingUndo.isNotEmpty()) {
        if (pendingUndo.isEmpty()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(UNDO_SEEN_AFTER_MS)
            if (!keyguard.isKeyguardLocked) shellViewModel.onUndoVisibleWhileResumed()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { shellViewModel.onAppStopped() }
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
        CompositionLocalProvider(LocalShellChrome provides chrome) {
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
                            onBatchEdit = { ids -> if (ids.isNotEmpty()) backStack.add(BatchEditDestination(ids)) },
                        )
                    }
                    entry<TimersDestination> { TimersScreen(tabPadding, onOpenSettings = openSettings) }
                    entry<StopwatchDestination> { StopwatchScreen(tabPadding, onOpenSettings = openSettings) }
                    entry<SettingsDestination> {
                        SettingsScreen(
                            onBack = pop,
                            onOpenReliability = openReliability,
                            onOpenTrash = { backStack.add(TrashDestination) },
                            onOpenLayouts = { backStack.add(LayoutsDestination) },
                            onOpenGlassLab = if (BuildConfig.DEBUG) ({ backStack.add(GlassLabDestination) }) else null,
                        )
                    }
                    entry<GlassLabDestination> { GlassLabScreen(onBack = pop) }
                    entry<AlarmEditorDestination> { key ->
                        AlarmEditorScreen(
                            alarmId = key.alarmId,
                            onClose = pop,
                            onEditLayout = { backStack.add(LayoutEditorDestination(it)) },
                        )
                    }
                    entry<LayoutsDestination> {
                        LayoutLibraryScreen(onBack = pop, onEdit = { backStack.add(LayoutEditorDestination(it)) })
                    }
                    entry<LayoutEditorDestination> { key -> LayoutEditorScreen(layoutId = key.layoutId, onClose = pop) }
                    entry<ReliabilityDestination> { ReliabilityScreen(onBack = pop) }
                    entry<TrashDestination> { TrashScreen(onBack = pop) }
                    entry<BatchEditDestination> { key -> BatchEditScreen(alarmIds = key.alarmIds, onClose = pop) }
                },
            )
        }

        val actionBar = chrome.actionBar
        AnimatedVisibility(
            visible = currentTab != null && actionBar != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
                    .widthIn(max = 480.dp)
                    .heightIn(min = 64.dp),
            ) {
                actionBar?.invoke()
            }
        }

        AnimatedVisibility(
            visible = currentTab != null && actionBar == null,
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = pendingUndo.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        UndoPopup(
                            backdrop = backdrop,
                            count = pendingUndo.size,
                            onUndo = shellViewModel::undo,
                            onDismiss = shellViewModel::dismissUndo,
                        )
                    }
                    Spacer(Modifier.weight(1f).width(12.dp))
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

/** Discreet glass pop-up offering to restore alarms deleted moments ago. */
@Composable
private fun UndoPopup(backdrop: GlassBackdrop, count: Int, onUndo: () -> Unit, onDismiss: () -> Unit) {
    val colors = ClockTheme.colors
    GlassSurface(backdrop = backdrop, modifier = Modifier.heightIn(min = 56.dp)) {
        Row(
            Modifier.padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.undo_alarms_deleted, count, count),
                style = ClockTheme.typography.label,
                color = colors.content,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_undo),
                style = ClockTheme.typography.label,
                color = colors.accent,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .clickable(role = Role.Button, onClick = onUndo)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close),
                tint = colors.contentMuted,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(12.dp)
                    .size(20.dp),
            )
        }
    }
}
