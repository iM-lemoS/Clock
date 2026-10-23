package io.github.imlemos.clock.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import io.github.imlemos.clock.BuildConfig
import io.github.imlemos.clock.R
import io.github.imlemos.clock.core.designsystem.component.ClockTopBar
import io.github.imlemos.clock.core.designsystem.component.TopBarIconButton
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** A bundled asset's licence, read from the app's assets (copied from licenses/). */
private data class ThirdParty(val name: String, val licence: String, val assetPath: String)

/** Version, privacy, source code and the licences of bundled fonts and icons. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = ClockTheme.colors
    var reading by rememberSaveable { mutableStateOf<String?>(null) }
    val thirdParty = remember {
        val assets = context.assets
        val fonts = assets.list("fonts").orEmpty().sorted().map { file ->
            ThirdParty(file.substringBefore("-OFL").splitCamelCase(), "SIL Open Font License 1.1", "fonts/$file")
        }
        val icons = assets.list("icons").orEmpty().sorted().map { file ->
            ThirdParty(file.substringBefore("-LICENSE").splitCamelCase(), "Apache License 2.0", "icons/$file")
        }
        fonts + icons
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ClockTopBar(
            title = stringResource(R.string.settings_about),
            navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.app_name), style = ClockTheme.typography.headline, color = colors.content)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = ClockTheme.typography.body, color = colors.contentMuted)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.about_privacy_title), style = ClockTheme.typography.title, color = colors.content)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_privacy_body), style = ClockTheme.typography.body, color = colors.contentMuted)
            }
            Spacer(Modifier.height(8.dp))
            AboutRow(stringResource(R.string.about_source), stringResource(R.string.about_source_summary)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
            }
            Text(
                stringResource(R.string.about_licences).uppercase(),
                style = ClockTheme.typography.label,
                color = colors.contentMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
            )
            thirdParty.forEach { item ->
                AboutRow(item.name, item.licence) { reading = item.assetPath }
            }
        }
    }
    reading?.let { path ->
        val text = remember(path) { runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("") }
        Dialog(onDismissRequest = { reading = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(colors.background)) {
                ClockTopBar(
                    title = stringResource(R.string.about_licences),
                    navigationIcon = { TopBarIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back)) { reading = null } },
                )
                Text(
                    text,
                    style = ClockTheme.typography.caption,
                    color = colors.content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(title, style = ClockTheme.typography.body, color = ClockTheme.colors.content)
        Text(summary, style = ClockTheme.typography.caption, color = ClockTheme.colors.contentMuted)
    }
}

/** "BigShouldersDisplay" -> "Big Shoulders Display"; names that do not follow the rule are listed. */
private fun String.splitCamelCase(): String = KnownNames[this] ?: replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

private val KnownNames = mapOf("JetBrainsMono" to "JetBrains Mono")

private const val SOURCE_URL = "https://github.com/iM-lemoS/Clock"
