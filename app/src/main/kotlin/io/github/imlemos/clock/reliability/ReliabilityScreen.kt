package io.github.imlemos.clock.reliability

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme
import io.github.imlemos.clock.core.oem.OemProfile
import io.github.imlemos.clock.core.oem.OemStep
import javax.inject.Inject

@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    val checker: ReliabilityChecker,
    val oemProfile: OemProfile,
) : ViewModel()

/**
 * "Set up reliability": what Android lets us verify (with live status), then the steps for the
 * detected manufacturer, which can only be explained, not checked.
 */
@Composable
fun ReliabilityScreen(onBack: () -> Unit, viewModel: ReliabilityViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    var status by remember { mutableStateOf(viewModel.checker.status()) }
    var resumeTick by remember { mutableStateOf(0) }
    // Re-check whenever the user comes back from a system settings screen.
    LifecycleResumeEffect(Unit) {
        status = viewModel.checker.status()
        resumeTick++
        onPauseOrDispose { }
    }
    val profile = viewModel.oemProfile
    // Android 13+: ask with the system dialog first; if the user already refused, open settings.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.launchFirst(listOf(viewModel.checker.fixIntent(ReliabilityCheck.Notifications)))
        status = viewModel.checker.status()
    }
    val fix: (ReliabilityCheck) -> Unit = { check ->
        if (check == ReliabilityCheck.Notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.launchFirst(listOf(viewModel.checker.fixIntent(check)))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ClockTopBar(
            title = stringResource(R.string.reliability_title),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.reliability_intro),
                style = ClockTheme.typography.body,
                color = colors.contentMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Header(stringResource(R.string.reliability_section_system))
            ReliabilityCheck.entries.forEach { check ->
                val ok = status[check] == true
                CheckRow(
                    check = check,
                    ok = ok,
                    onClick = { fix(check) },
                )
            }

            Header(
                profile.displayName?.let { stringResource(R.string.reliability_section_vendor, stringResource(it)) }
                    ?: stringResource(R.string.reliability_section_generic),
            )
            profile.steps.forEachIndexed { index, step -> StepRow(index + 1, step, resumeTick) }
            LinkRow(
                title = stringResource(R.string.reliability_more_info),
                summary = stringResource(R.string.reliability_more_info_summary),
                onClick = { context.launchFirst(listOf(Intent(Intent.ACTION_VIEW, profile.moreInfoUrl.toUri()))) },
            )
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text.uppercase(),
        style = ClockTheme.typography.label,
        color = ClockTheme.colors.contentMuted,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun CheckRow(check: ReliabilityCheck, ok: Boolean, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    val (title, body) = when (check) {
        ReliabilityCheck.Notifications -> R.string.reliability_notifications to R.string.reliability_notifications_body
        ReliabilityCheck.FullScreen -> R.string.reliability_full_screen to R.string.reliability_full_screen_body
        ReliabilityCheck.ExactAlarms -> R.string.reliability_exact to R.string.reliability_exact_body
        ReliabilityCheck.Battery -> R.string.reliability_battery to R.string.reliability_battery_body
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !ok, onClickLabel = stringResource(R.string.reliability_fix), onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error),
            contentDescription = stringResource(if (ok) R.string.reliability_ok else R.string.reliability_needs_action),
            tint = if (ok) colors.content else colors.accent,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = ClockTheme.typography.body, color = colors.content)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(body), style = ClockTheme.typography.caption, color = colors.contentMuted)
            if (!ok) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.reliability_fix), style = ClockTheme.typography.label, color = colors.accent)
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, step: OemStep, resumeTick: Int) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    val intents = step.intents(context)
    val done = remember(resumeTick) { step.isDone?.invoke(context) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = intents.isNotEmpty() && done != true) { context.launchFirst(intents) }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (done == null) {
            Text(number.toString(), style = ClockTheme.typography.title, color = colors.contentMuted, modifier = Modifier.width(28.dp))
        } else {
            Icon(
                painter = painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_error),
                contentDescription = stringResource(if (done) R.string.reliability_ok else R.string.reliability_needs_action),
                tint = if (done) colors.content else colors.accent,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(24.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(step.title), style = ClockTheme.typography.body, color = colors.content)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(step.description), style = ClockTheme.typography.caption, color = colors.contentMuted)
            if (intents.isNotEmpty() && done != true) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.reliability_open_settings), style = ClockTheme.typography.label, color = colors.accent)
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, summary: String, onClick: () -> Unit) {
    val colors = ClockTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = ClockTheme.typography.body, color = colors.content)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = ClockTheme.typography.caption, color = colors.contentMuted)
        }
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, tint = colors.contentMuted)
    }
}

/** Starts the first intent the system accepts (vendor screens may be missing or protected). */
private fun Context.launchFirst(intents: List<Intent>) {
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            Log.w("Reliability", "No activity for $intent", e)
        } catch (e: SecurityException) {
            Log.w("Reliability", "Not allowed to open $intent", e)
        }
    }
}
