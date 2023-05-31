package no.nav.dagpenger.rapportering.db

import kotliquery.Row
import org.postgresql.util.PGInterval
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal object DBUtils {
    fun Row.duration(columnLabel: String): Duration {
        val interval: PGInterval = this.underlying.getObject(columnLabel) as PGInterval
        return interval.days.toDuration(DurationUnit.DAYS) + interval.hours.toDuration(DurationUnit.HOURS) +
                interval.minutes.toDuration(DurationUnit.MINUTES)
    }

}