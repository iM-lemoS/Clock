package io.github.imlemos.clock.core.oem

import io.github.imlemos.clock.R

/**
 * OPPO, realme and OnePlus (ColorOS / OxygenOS / realme UI). Verified on ColorOS 16: the autostart
 * and battery screens are protected by a vendor permission, so only the app details page can be
 * opened directly.
 */
class ColorOsProfile(brand: String) : OemProfile {
    override val id = "coloros"
    override val displayName = R.string.oem_name_coloros
    override val moreInfoUrl = "https://dontkillmyapp.com/$brand"
    override val steps = listOf(
        OemStep(R.string.oem_coloros_background_title, R.string.oem_coloros_background_body) { context ->
            listOf(appDetailsIntent(context))
        },
        LockInRecentsStep,
    )
}

/** Xiaomi, Redmi and POCO (HyperOS / MIUI). Not yet verified on a device. */
class XiaomiProfile : OemProfile {
    override val id = "xiaomi"
    override val displayName = R.string.oem_name_xiaomi
    override val moreInfoUrl = "https://dontkillmyapp.com/xiaomi"
    override val steps = listOf(
        OemStep(R.string.oem_xiaomi_autostart_title, R.string.oem_xiaomi_autostart_body) { context ->
            listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                appDetailsIntent(context),
            )
        },
        OemStep(R.string.oem_xiaomi_battery_title, R.string.oem_xiaomi_battery_body) { context ->
            listOf(appDetailsIntent(context))
        },
        LockInRecentsStep,
    )
}

/** Samsung (One UI). Not yet verified on a device. */
class SamsungProfile : OemProfile {
    override val id = "samsung"
    override val displayName = R.string.oem_name_samsung
    override val moreInfoUrl = "https://dontkillmyapp.com/samsung"
    override val steps = listOf(
        OemStep(R.string.oem_samsung_battery_title, R.string.oem_samsung_battery_body) { context ->
            listOf(appDetailsIntent(context))
        },
        OemStep(R.string.oem_samsung_sleeping_title, R.string.oem_samsung_sleeping_body),
    )
}

/** Picks the profile for a device from `Build.MANUFACTURER` / `Build.BRAND`. */
object OemProfiles {
    fun forDevice(manufacturer: String, brand: String): OemProfile {
        val names = setOf(manufacturer.lowercase(), brand.lowercase())
        return when {
            names.any { it in setOf("oppo", "realme", "oneplus") } ->
                ColorOsProfile(names.first { it in setOf("oppo", "realme", "oneplus") })
            names.any { it in setOf("xiaomi", "redmi", "poco") } -> XiaomiProfile()
            "samsung" in names -> SamsungProfile()
            else -> GenericOemProfile
        }
    }
}
