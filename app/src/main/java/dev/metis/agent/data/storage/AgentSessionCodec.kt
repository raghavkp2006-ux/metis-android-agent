package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RecordMetadata
import dev.metis.agent.domain.storage.SavedAgentSession

internal class AgentSessionCodec(private val codec: RecordCodec) : FoundationCodec<SavedAgentSession,
    AgentSessionEntity> {
    override fun encode(record: SavedAgentSession, metadata: StoredMetadata) = AgentSessionEntity(
        metadata.id,
        record.startedAt,
        record.endedAt,
        record.summary?.let { codec.encrypt(it, "agent_sessions", metadata.id, "summary") },
    )

    override fun decode(row: AgentSessionEntity): SavedAgentSession {
        val metadata = row.recordMetadata()
        return SavedAgentSession(
            startedAt = row.startedAt,
            endedAt = row.endedAt,
            summary = row.summary?.let { codec.decrypt(it, "agent_sessions", metadata.id, "summary") },
            metadata = RecordMetadata(metadata.id, metadata.createdAt, metadata.updatedAt, metadata.revision),
        )
    }
}
