package dev.metis.agent

import androidx.lifecycle.SavedStateHandle
import dev.metis.agent.domain.agent.AgentDestination
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.storage.MemoryEntityType
import dev.metis.agent.presentation.navigation.ConversationOrigin
import dev.metis.agent.presentation.navigation.ShellViewModel
import dev.metis.agent.presentation.navigation.Workspace
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceContextTest {
    @Test fun routesMapExplicitlyAndPreserveTheSelectedEntity() {
        val reference = EntityReference(MemoryEntityType.PERSON, UUID.randomUUID())
        val context = ConversationOrigin(Workspace.PEOPLE, reference).screenContext()
        assertEquals(AgentDestination.YOU, context.destination)
        assertEquals(reference, context.entity)
        assertEquals(AgentDestination.PLAN, ConversationOrigin(Workspace.PROJECTS_GOALS).screenContext().destination)
        assertEquals(AgentDestination.TIMELINE, ConversationOrigin(Workspace.ACTIVITY).screenContext().destination)
        assertEquals(AgentDestination.AGENT, ConversationOrigin(Workspace.CHAT).screenContext().destination)
    }

    @Test fun selectedPersonalEntityIsNotSavedAndNavigationClearsIt() {
        val handle = SavedStateHandle()
        val model = ShellViewModel(handle)
        model.selectWorkspace(Workspace.PEOPLE)
        model.selectEntity(EntityReference(MemoryEntityType.PERSON, UUID.randomUUID()))
        model.updateDraft("Synthetic draft")
        val restored = ShellViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        assertEquals(Workspace.PEOPLE, restored.uiState.value.workspace)
        assertEquals("Synthetic draft", restored.uiState.value.draft)
        assertNull(restored.uiState.value.selectedEntity)
        model.selectWorkspace(Workspace.CHAT)
        assertNull(model.uiState.value.selectedEntity)
    }
}
