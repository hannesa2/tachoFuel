package com.example.vespatacho.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A tank-capacity period for a [Vehicle].
 *
 * Lets the user record that the tank had a certain size during a date range,
 * e.g. after swapping to a bigger tank. [validFrom]/[validTo] are epoch millis
 * and both optional — null means "no lower/upper bound".
 */
@Entity(tableName = "tank_size_entries")
data class TankSizeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val tankLiters: Double,
    val validFrom: Long? = null,
    val validTo: Long? = null,
) {
    /** True if [timestamp] falls within this entry's (optionally open-ended) date range. */
    fun covers(timestamp: Long): Boolean =
        (validFrom == null || timestamp >= validFrom) && (validTo == null || timestamp <= validTo)
}

/**
 * Resolves the tank capacity that applied at [timestamp]: the most specific
 * matching [TankSizeEntry] (narrowest date range), falling back to [defaultLiters]
 * — the vehicle's base tank size — when no entry covers that date.
 */
fun resolveTankLiters(entries: List<TankSizeEntry>, timestamp: Long, defaultLiters: Double): Double {
    return entries
        .filter { it.covers(timestamp) }
        .minByOrNull { (it.validTo ?: Long.MAX_VALUE).toDouble() - (it.validFrom ?: Long.MIN_VALUE).toDouble() }
        ?.tankLiters
        ?: defaultLiters
}
