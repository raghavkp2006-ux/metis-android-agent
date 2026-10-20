package dev.metis.agent

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
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
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.presentation.navigation.RecordsViewModel
import dev.metis.agent.presentation.navigation.RequestViewModel

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
    text: String, substring: Boolean = false,
) {
    val model = runOnIdle { ViewModelProvider(activity)[RequestViewModel::class.java] }
    waitUntil(10_000) {
        val message = model.uiState.value.result?.message
        !model.uiState.value.busy && if (substring) message?.contains(text) == true else message == text
    }
    waitForIdle()
    onNodeWithTag("chat_messages").performScrollToNode(androidx.compose.ui.test.hasText(text, substring = substring))
    onNodeWithText(text, substring = substring).assertIsDisplayed()
}

/** Wait for the completed request before scrolling its proposal into the lazy chat viewport. */
internal fun <A : ComponentActivity> AndroidComposeTestRule<ActivityScenarioRule<A>, A>.awaitRequestProposal(
    tag: String, expectedAction: (ActionProposal) -> Boolean,
) {
    val model = runOnIdle { ViewModelProvider(activity)[RequestViewModel::class.java] }
    val requestFinished = object : IdlingResource {
        override val isIdleNow: Boolean
            get() = model.uiState.value.let { !it.busy && it.result != null }

        override fun getDiagnosticMessageIfBusy(): String =
            "Request has not published a result: ${model.uiState.value}"
    }
    registerIdlingResource(requestFinished)
    try {
        waitForIdle()
    } finally {
        unregisterIdlingResource(requestFinished)
    }
    val result = model.uiState.value.result
    check(result?.proposals?.singleOrNull()?.let(expectedAction) == true) {
        "Expected $tag proposal; request ended with ${result?.status}: ${result?.message}"
    }
    waitForIdle()
    onNodeWithTag("chat_messages").performScrollToNode(hasTestTag(tag))
    onNodeWithTag(tag).assertIsDisplayed()
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

/** Ordinary workspace Columns compose offscreen rows; wait for their data, then expose the row.
 * Lazy chat results must use awaitRequestText so the item is composed by scrolling its container.
 */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.scrollToDisplayed(
    text: String, substring: Boolean = false,
): androidx.compose.ui.test.SemanticsNodeInteraction {
    waitUntil(10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    return onNodeWithText(text, substring = substring).scrollToDisplayed(this)
}
