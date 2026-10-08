package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "preferences", primaryKeys = ["id"], indices = [Index(value = ["key"], unique = true)])
data class PreferenceEntity(
    @Embedded val metadata: StoredMetadata,
    val key: String,
    @ColumnInfo(name = "typed_value") val typedValue: ByteArray,
    @ColumnInfo(name = "value_kind") val valueKind: String,
    @ColumnInfo(name = "value_schema_version") val valueSchemaVersion: Int,
    val source: String,
)
