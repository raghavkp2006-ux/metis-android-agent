package dev.metis.agent.data.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

@Entity(
    tableName = "persons", primaryKeys = ["id"],
)
data class PersonEntity(
    @Embedded val metadata: StoredMetadata,
    @ColumnInfo(name = "display_name") val displayName: ByteArray,
    @ColumnInfo(name = "phone") val phone: ByteArray?,
    @ColumnInfo(name = "email") val email: ByteArray?,
    @ColumnInfo(name = "contact_lookup_key") val contactLookupKey: ByteArray?,
) : FoundationEntity {
    override fun recordMetadata() = metadata
}
