package io.github.imlemos.clock.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.net.toUri
import io.github.imlemos.clock.R

/**
 * One manual step the user has to take in the manufacturer's settings.
 *
 * @property intents Screens that may perform the step, tried in order. Vendors move and protect
 *   these screens between versions, so each is checked before use and the app details page is the
 *   final fallback.
 * @property isDone Reads whether the step is done, for the few vendor settings Android exposes;
 *   `null` when it cannot be checked.
 */
data class OemStep(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val isDone: ((Context) -> Boolean)? = null,
    val intents: (Context) -> List<Intent> = { emptyList() },
)

/**
 * Manufacturer-specific behaviour (background restrictions, autostart screens, ...).
 *
 * Everything vendor-specific lives in this package behind this interface, and every capability
 * has a generic fallback in [GenericOemProfile].
 */
interface OemProfile {
    /** Stable identifier, used in logs and tests. Never shown to users. */
    val id: String

    /** Brand shown to the user, e.g. "OPPO / ColorOS"; `null` for the generic profile. */
    @get:StringRes val displayName: Int?

    val steps: List<OemStep>

    /** Page on dontkillmyapp.com with up-to-date instructions for this vendor. */
    val moreInfoUrl: String
}

object GenericOemProfile : OemProfile {
    override val id = "generic"
    override val displayName: Int? = null
    override val steps = emptyList<OemStep>()
    override val moreInfoUrl = "https://dontkillmyapp.com/"
}

internal fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

internal fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

internal fun componentIntent(pkg: String, cls: String): Intent = Intent().setComponent(ComponentName(pkg, cls))

/** "Lock" the app in the recent apps screen so the vendor's cleaner does not kill it. */
internal val LockInRecentsStep = OemStep(R.string.oem_step_lock_recents_title, R.string.oem_step_lock_recents_body)
