package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedUserProfile

internal class UserProfileCodec(private val codec: RecordCodec) : FoundationCodec<SavedUserProfile, UserProfileEntity> {
    override fun encode(record: SavedUserProfile, metadata: StoredMetadata) = UserProfileEntity(
        metadata,
        codec.encrypt(record.displayName, "user_profile", metadata.id, "display_name"),
        record.zoneId,
        record.locale,
        record.autonomyLevel,
        record.behavioralAnalysisConsent,
        record.active,
    )

    override fun decode(row: UserProfileEntity): SavedUserProfile {
        val metadata = row.recordMetadata()
        return SavedUserProfile(
            displayName = codec.decrypt(row.displayName, "user_profile", metadata.id, "display_name"),
            zoneId = row.zoneId,
            locale = row.locale,
            autonomyLevel = row.autonomyLevel,
            behavioralAnalysisConsent = row.behavioralAnalysisConsent,
            active = row.active,
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
