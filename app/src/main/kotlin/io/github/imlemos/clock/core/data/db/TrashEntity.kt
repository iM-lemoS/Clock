package io.github.imlemos.clock.core.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * A deleted item kept for restore (docs/PLANO.md 3.10). [payload] is a JSON snapshot of the
 * deleted row; [type] tells how to read it ("ALARM"; groups arrive in phase 4).
 */
@Entity(tableName = "trash", indices = [Index("deletedAt")])
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val deletedAt: Long,
    /** Deleted by the app (single use, last date) rather than by the user. */
    val automatic: Boolean,
)
