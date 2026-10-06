package dev.metis.agent.presentation.designsystem

data class ComposerState(
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val listening: Boolean = false,
    val error: String? = null,
)
