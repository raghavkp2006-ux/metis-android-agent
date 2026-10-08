package dev.metis.agent.domain.storage

import java.util.UUID

interface FoundationRecord { val metadata: RecordMetadata }

internal fun validateFoundationText(value: String) { require(value.isNotBlank() &&
    value.length <= FOUNDATION_CONTENT_LIMIT) }
internal fun validateFoundationId(value: String) { require(UUID.fromString(value).toString() == value) }
internal fun validateFoundationTag(value: String) { require(Regex("[A-Z][A-Z0-9_]{0,63}").matches(value)) }

internal const val FOUNDATION_CONTENT_LIMIT = 4_000
internal const val MAX_AUTONOMY_LEVEL = 4
