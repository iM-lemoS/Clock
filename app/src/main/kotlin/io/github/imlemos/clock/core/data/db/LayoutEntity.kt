package io.github.imlemos.clock.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A user's alarm screen layout (docs/PLANO.md 3.7). [spec] is LayoutSpec as JSON. Built-in presets
 * live in code and are not stored. Added in schema 5.
 */
@Entity(tableName = "alarm_layout")
data class LayoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val spec: String,
    val createdAt: Long,
)
