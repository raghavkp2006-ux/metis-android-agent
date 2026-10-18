package dev.metis.agent.presentation.navigation

import dev.metis.agent.domain.agent.AgentDestination
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.ScreenContext

/** UI routes deliberately do not double as protocol destinations. */
enum class Workspace(val title: String, val subtitle: String) {
    CHAT("METIS", "Your personal agent"),
    TODAY("Today", "A little clarity for your day"),
    HABITS("Habits", "Your daily check-in"),
    PLAN("Plan", "Make room for what matters"),
    MEMORY("Memory", "The things you choose to remember"),
    PEOPLE("People", "Your saved connections"),
    PROJECTS_GOALS("Projects & Goals", "Keep your intentions in view"),
    INSIGHTS("Insights", "Reflect on your own terms"),
    ACTIVITY("Activity", "Your local actions and outcomes"),
    SETTINGS("Settings", "Make METIS yours"),
}

data class ConversationOrigin(val workspace: Workspace, val entityReference: EntityReference? = null) {
    fun screenContext() = ScreenContext(when (workspace) {
        Workspace.CHAT -> AgentDestination.AGENT
        Workspace.TODAY -> AgentDestination.TODAY
        Workspace.HABITS -> AgentDestination.TODAY
        Workspace.PLAN, Workspace.PROJECTS_GOALS -> AgentDestination.PLAN
        Workspace.ACTIVITY -> AgentDestination.TIMELINE
        Workspace.MEMORY, Workspace.PEOPLE, Workspace.INSIGHTS, Workspace.SETTINGS -> AgentDestination.YOU
    }, entityReference)
}
