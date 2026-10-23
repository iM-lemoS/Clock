package io.github.imlemos.clock.core.oem

/**
 * Manufacturer-specific behaviour (background restrictions, autostart screens, ...).
 *
 * Everything vendor-specific lives in this package behind this interface, and every capability
 * has a generic fallback in [GenericOemProfile]. The reliability checklist in phase 1 builds on it.
 */
interface OemProfile {
    /** Stable identifier, used in logs and tests. Never shown to users. */
    val id: String
}

object GenericOemProfile : OemProfile {
    override val id: String = "generic"
}
