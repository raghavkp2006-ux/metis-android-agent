package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "relationships", primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["person_id"],
            onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["person_id", "kind"], unique = true)],
)
data class RelationshipEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "description") val description: ByteArray?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
