package dev.metis.agent

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import dev.metis.agent.presentation.navigation.RecordsViewModel

internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.enterFrontend() {
    waitForIdle()
    if (onAllNodesWithTag("welcome_start").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithTag("welcome_start").performScrollTo().performClick()
    }
}

internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.showAllTasks() {
    waitUntil(10_000) { onAllNodesWithTag("all_tasks_filter").fetchSemanticsNodes().isNotEmpty() }
    val filter = onNodeWithTag("all_tasks_filter")
    if (!filter.fetchSemanticsNode().config[SemanticsProperties.Selected]) {
        filter.performSemanticsAction(SemanticsActions.OnClick)
        waitUntil(10_000) { filter.fetchSemanticsNode().config[SemanticsProperties.Selected] }
    }
}

internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.reloadWorkspaceRecords() {
    val model = runOnIdle { ViewModelProvider(activity)[RecordsViewModel::class.java] }
    runOnIdle { model.reload() }
    waitUntil(10_000) { !model.uiState.value.loading }
    check(!model.uiState.value.failed) { "Workspace fixture failed to load" }
    waitForIdle()
}

internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.navigateWorkspace(
    workspace: String, section: String? = null,
) {
    enterFrontend()
    onNodeWithTag("open_navigation").performClick()
    onNodeWithTag("workspace_$workspace").performScrollTo().performClick()
    reloadWorkspaceRecords()
    if (section != null) onNodeWithText(section).performScrollTo().performClick()
}

/** Wait for the real action, then expose its result in the lazy conversation before asserting text. */
internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.awaitRequestText(
    text: String,
) {
    val model = runOnIdle { ViewModelProvider(activity)[dev.metis.agent.presentation.navigation.RequestViewModel::class.java] }
    waitUntil(10_000) { !model.uiState.value.busy && model.uiState.value.result?.message?.contains(text) == true }
    waitForIdle()
    onNodeWithTag("chat_messages").performScrollToNode(androidx.compose.ui.test.hasText(text, substring = true))
    onNodeWithText(text, substring = true).assertIsDisplayed()
}

/** Scroll after async content has reached semantics; account for a pending viewport/layout change. */
internal fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollToDisplayed(
    rule: androidx.compose.ui.test.junit4.ComposeTestRule,
): androidx.compose.ui.test.SemanticsNodeInteraction {
    rule.waitUntil(10_000) {
        performScrollTo()
        isDisplayed()
    }
    assertIsDisplayed()
    return this
}
