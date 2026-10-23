package io.github.imlemos.clock.core.scheduling

import java.time.DayOfWeek
import java.time.LocalDate

/** When an alarm repeats. Exactly one kind per alarm (docs/PLANO.md, section 3.1). */
public sealed interface Recurrence {

    /** Next occurrence of the time of day, then the alarm is switched off. */
    public data object Once : Recurrence

    /** Every selected day of the week. */
    public data class Weekly(val days: Set<DayOfWeek>) : Recurrence {
        init {
            require(days.isNotEmpty()) { "Weekly recurrence needs at least one day" }
        }
    }

    /** Only on the given calendar dates; the alarm ends after the last one. */
    public data class Dates(val dates: Set<LocalDate>) : Recurrence {
        init {
            require(dates.isNotEmpty()) { "Dates recurrence needs at least one date" }
        }
    }
}
