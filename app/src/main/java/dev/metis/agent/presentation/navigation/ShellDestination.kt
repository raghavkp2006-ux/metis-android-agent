package dev.metis.agent.presentation.navigation

import androidx.annotation.StringRes
import dev.metis.agent.R

enum class ShellDestination(@StringRes val label: Int, @StringRes val description: Int) {
    TODAY(R.string.destination_today, R.string.today_description),
    PLAN(R.string.destination_plan, R.string.plan_description),
    AGENT(R.string.destination_agent, R.string.agent_description),
    TIMELINE(R.string.destination_timeline, R.string.timeline_description),
    YOU(R.string.destination_you, R.string.you_description),
}

data class ShellUiState(
    val destination: ShellDestination = ShellDestination.TODAY,
    val draft: String = "",
    val composerOpen: Boolean = false,
    val privacyOpen: Boolean = false,
) {
    val handlesBack: Boolean
        get() = composerOpen || privacyOpen || destination != ShellDestination.TODAY
}
