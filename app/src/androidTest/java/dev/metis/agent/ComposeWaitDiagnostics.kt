package dev.metis.agent

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot

internal const val CENTRAL_TIMEOUT_MS = 10_000L
private const val WAIT_LOG_TAG = "METIS_WAIT"

/** Keep the original wait and exception; a delegated wait preserves existing idling behavior. */
internal fun ComposeTestRule.awaitCondition(
    name: String,
    timeoutMs: Long = CENTRAL_TIMEOUT_MS,
    waitFor: ((() -> Boolean) -> Unit)? = null,
    condition: () -> Boolean,
) {
    val startedAt = SystemClock.elapsedRealtime()
    val result = runCatching {
        if (waitFor == null) waitUntil(timeoutMs, condition) else waitFor(condition)
    }
    result.exceptionOrNull()?.let { failure ->
        runCatching {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val limit = if (waitFor == null) timeoutMs.toString() else "delegated"
            Log.e(WAIT_LOG_TAG, "condition=$name elapsedMs=$elapsedMs timeoutMs=$limit")
            logRedactedTree(name, useUnmergedTree = false)
            logRedactedTree(name, useUnmergedTree = true)
        }
        throw failure
    }
}

private fun ComposeTestRule.logRedactedTree(name: String, useUnmergedTree: Boolean) {
    val mode = if (useUnmergedTree) "unmerged" else "merged"
    runCatching {
        val root = onRoot(useUnmergedTree = useUnmergedTree).fetchSemanticsNode()
        logRedactedTree(name, mode, root)
    }
        .onFailure { failure ->
            // Exception messages can contain semantics values, so log only the exception type.
            Log.e(WAIT_LOG_TAG, "condition=$name tree=$mode unavailable=${failure.javaClass.simpleName}")
        }
}

private fun logRedactedTree(name: String, mode: String, root: SemanticsNode) {
    var requestResults = 0
    var reminderProposals = 0
    var decisionHistory = 0
    Log.e(WAIT_LOG_TAG, "condition=$name tree=$mode begin")

    fun visit(node: SemanticsNode, depth: Int) {
        val semantics = node.config
        val tag = semantics.getOrNull(SemanticsProperties.TestTag)
        when {
            tag == "request_result" -> requestResults++
            tag == "reminder_proposal" -> reminderProposals++
            tag?.startsWith("decision_history_") == true -> decisionHistory++
        }
        // ContactSemanticsPrivacyTest checks both trees. This dump exposes only testTags and
        // lengths, never Text, ContentDescription, EditableText, or other property values.
        val textLength = semantics.getOrNull(SemanticsProperties.Text)?.sumOf { it.text.length } ?: 0
        val descriptionLength = semantics.getOrNull(SemanticsProperties.ContentDescription)
            ?.sumOf(String::length) ?: 0
        val editableLength = semantics.getOrNull(SemanticsProperties.EditableText)?.text?.length ?: 0
        val stateLength = semantics.getOrNull(SemanticsProperties.StateDescription)?.length ?: 0
        Log.e(WAIT_LOG_TAG, "condition=$name tree=$mode depth=$depth node=${node.id} " +
            "tag=${tag ?: "<none>"} textLength=$textLength contentDescriptionLength=$descriptionLength " +
            "editableTextLength=$editableLength stateDescriptionLength=$stateLength")
        node.children.forEach { visit(it, depth + 1) }
    }

    visit(root, 0)
    // The semantics API exposes composed nodes, not LazyListState.layoutInfo.visibleItemsInfo.
    Log.e(WAIT_LOG_TAG, "condition=$name tree=$mode request_result=$requestResults " +
        "reminder_proposal=$reminderProposals decision_history_*=$decisionHistory " +
        "firstVisibleLazyIndex=unavailable lastVisibleLazyIndex=unavailable")
    Log.e(WAIT_LOG_TAG, "condition=$name tree=$mode end")
}
