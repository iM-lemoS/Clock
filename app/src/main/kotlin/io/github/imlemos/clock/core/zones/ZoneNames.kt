package io.github.imlemos.clock.core.zones

import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A time zone as the user picks it: by city, with its country. */
data class ZoneOption(val zone: ZoneId, val city: String, val country: String) {
    private val searchKey = normalize("$city $country ${zone.id}")
    private val cityKey = normalize(city)

    fun matches(query: String): Boolean = normalize(query).split(' ').filter(String::isNotBlank).all { it in searchKey }

    /** Lower is better: the city starts with [query], then contains it, then only the rest matches. */
    fun rank(query: String): Int {
        val key = normalize(query).trim()
        return when {
            cityKey.startsWith(key) -> 0
            key in cityKey -> 1
            else -> 2
        }
    }
}

/**
 * Localized zone names from the system's ICU data ("Nova Iorque" in Portuguese), so nothing is
 * bundled or downloaded.
 */
object ZoneNames {

    /** The zone's city in [locale], e.g. "New York" for America/New_York. */
    fun city(zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        TimeZoneNames.getInstance(locale).getExemplarLocationName(zone.id)
            ?: zone.id.substringAfterLast('/').replace('_', ' ')

    /** One option per canonical location, sorted by city in [locale]. */
    fun all(locale: Locale = Locale.getDefault()): List<ZoneOption> {
        val names = TimeZoneNames.getInstance(locale)
        val collator = Collator.getInstance(locale)
        return TimeZone.getAvailableIDs(TimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null)
            .mapNotNull { id ->
                val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@mapNotNull null
                val region = TimeZone.getRegion(id)
                ZoneOption(
                    zone = zone,
                    city = names.getExemplarLocationName(id) ?: id.substringAfterLast('/').replace('_', ' '),
                    country = runCatching { Locale.Builder().setRegion(region).build().getDisplayCountry(locale) }.getOrDefault(""),
                )
            }
            .sortedWith(compareBy(collator) { it.city })
    }

    /** "GMT-4" at [at]; offsets change with daylight saving time. */
    fun offset(zone: ZoneId, at: Instant, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("O", locale).format(at.atZone(zone))
}

/** Lower case without accents, so "sao paulo" finds "São Paulo". */
private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
