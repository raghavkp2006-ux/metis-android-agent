@file:Suppress("MagicNumber") // Responsive dimensions follow the approved UI contract.
package dev.metis.agent.presentation.navigation
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.metis.agent.domain.agent.ActionProposal
import dev.metis.agent.domain.agent.ActionReceipt
import dev.metis.agent.domain.agent.EntityReference
import dev.metis.agent.domain.agent.InputSource
import dev.metis.agent.domain.storage.MemoryOrigin
import dev.metis.agent.presentation.designsystem.AgentSpacing
import dev.metis.agent.presentation.designsystem.PrimaryButton
import kotlinx.coroutines.launch
import java.util.UUID
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod") // Drawer/sidebar, workspace and reset rendering share one shell.
@Composable
internal fun MetisAppShell(
    state: ShellUiState, request: RequestUiState,
    onSelect: (Workspace) -> Unit, onDraft: (String) -> Unit, onSubmit: (InputSource) -> Unit,
    onNewChat: () -> Unit, onAccept: (ActionProposal) -> Unit, onUndo: (ActionReceipt) -> Unit,
    onReject: () -> Unit, onBack: () -> Unit, onPrivacy: () -> Unit,
    records: RecordsUiState, onReload: () -> Unit, onSearch: (String) -> Unit, onSearchMore: () -> Unit,
    onMemoryFilter: (MemoryOrigin?) -> Unit, onEntity: (EntityReference?) -> Unit,
    onFindContact: (String) -> Unit = {}, onContactChoice: (UUID, Int) -> Unit = { _, _ -> },
    onContactPermission: (UUID) -> Unit = {}, onCancelContact: () -> Unit = {},
    onContactSettings: () -> Unit = {},
    onActionPersonChoice: (Int) -> Unit = {}, onActionContactPermission: (UUID) -> Unit = {},
    onActionContactSkip: (UUID) -> Unit = {},
    onRejectProposal: ((ActionProposal) -> Unit)? = null,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }
    var resetDialog by remember { mutableStateOf(false) }
    val savedPages = rememberSaveableStateHolder()
    val chatScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(request.turns.size, request.sessionId) {
        // Sending is an explicit request to view the new turn, including from another workspace.
        chatScroll.requestScrollToItem(request.turns.size)
    }
    val reset: () -> Unit = {
        if (!request.accepting) {
            scope.launch { drawer.close() }
            if (request.turns.isNotEmpty() || state.draft.isNotBlank()) resetDialog = true else onNewChat()
        }
    }
    val navigate: (Workspace) -> Unit = {
        onSelect(it)
        scope.launch { drawer.close() }
    }
    BackHandler(enabled = !drawer.isOpen && state.handlesBack, onBack = onBack)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wide = constraints.maxWidth >= with(density) { 840.dp.roundToPx() }
        LaunchedEffect(wide) { if (wide && drawer.isOpen) drawer.close() }
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = !wide,
            drawerContent = {
                if (!wide) ModalDrawerSheet(Modifier.width(280.dp).testTag("navigation_drawer")) {
                    MetisNavigation(state.workspace, navigate, reset, !request.accepting)
                }
            },
        ) {
            Row(Modifier.fillMaxSize()) {
                if (wide && sidebarExpanded) PermanentDrawerSheet(
                    Modifier.width(280.dp).testTag("navigation_sidebar"),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    MetisNavigation(state.workspace, navigate, reset, !request.accepting)
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        MetisTopBar(state.workspace, {
                            if (wide) sidebarExpanded = !sidebarExpanded else scope.launch { drawer.open() }
                        }, reset, !request.accepting)
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Crossfade(targetState = state.workspace, animationSpec = tween(180),
                                label = "workspace") { workspace ->
                            if (workspace == Workspace.CHAT && !state.privacyOpen) {
                                ChatWorkspace(request, onDraft, onAccept, onUndo, onReject,
                                    Modifier.widthIn(max = 800.dp).fillMaxSize(), chatScroll,
                                    onActionPersonChoice, onActionContactPermission, onActionContactSkip,
                                    onRejectProposal)
                            } else savedPages.SaveableStateProvider(workspace.name) {
                                WorkspacePage(state.copy(workspace = workspace), records, onReload,
                                    onSearch, onSearchMore, onMemoryFilter,
                                    onDraft, onSelect, onPrivacy, onBack, onEntity,
                                    request.contactLookup, onFindContact, onContactChoice,
                                    onContactPermission, onCancelContact, onContactSettings,
                                    Modifier.widthIn(max = 1120.dp).fillMaxSize())
                            }
                            }
                        }
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(Modifier.widthIn(max = 800.dp).fillMaxWidth()
                                    .padding(horizontal = AgentSpacing.screen, vertical = AgentSpacing.small)) {
                                    MetisComposer(state.draft, onDraft, request, onSubmit)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (resetDialog) AlertDialog(
        onDismissRequest = { resetDialog = false },
        title = { Text("New chat") },
        text = { Text("Starts a fresh temporary conversation. Your tasks, memories, people, projects and other " +
            "saved METIS data won't be affected.") },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm_new_chat"), enabled = !request.accepting,
            onClick = {
            resetDialog = false
            scope.launch { drawer.close() }
            onNewChat()
        }) { Text("New chat") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancel") } },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetisTopBar(workspace: Workspace, onMenu: () -> Unit, onReset: () -> Unit, canReset: Boolean) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(workspace.title, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("screen_title"))
                Text(if (workspace == Workspace.CHAT) "Temporary chat" else "METIS · on your device",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = { IconButton(onClick = onMenu, modifier = Modifier.testTag("open_navigation")) {
            Icon(Icons.Default.Menu, "Open navigation")
        } },
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("New chat") }, enabled = canReset,
                    onClick = { menu = false; onReset() })
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
@Composable
private fun MetisNavigation(selected: Workspace, onSelect: (Workspace) -> Unit, onReset: () -> Unit,
    canReset: Boolean) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("M E T I S", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
        Text("Plan. Remember. Achieve.", Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("New chat", onReset, Modifier.fillMaxWidth().testTag("new_chat"), enabled = canReset)
        Spacer(Modifier.height(16.dp))
        Workspace.entries.forEach { workspace ->
            NavigationDrawerItem(
                label = { Text(if (workspace == Workspace.CHAT) "METIS Chat" else workspace.title) },
                selected = selected == workspace, onClick = { onSelect(workspace) },
                icon = { Icon(workspace.icon(), null) },
                modifier = Modifier.testTag("workspace_${workspace.name}"),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Private by design.\nSaved data stays on your device.", Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun Workspace.icon(): ImageVector = when (this) {
    Workspace.CHAT -> Icons.AutoMirrored.Filled.Send
    Workspace.TODAY -> Icons.Default.Home
    Workspace.HABITS -> Icons.Default.CheckCircle
    Workspace.PLAN -> Icons.Default.DateRange
    Workspace.MEMORY -> Icons.Default.Favorite
    Workspace.PEOPLE -> Icons.Default.Person
    Workspace.PROJECTS_GOALS -> Icons.Default.Star
    Workspace.INSIGHTS -> Icons.Default.Info
    Workspace.ACTIVITY -> Icons.AutoMirrored.Filled.List
    Workspace.SETTINGS -> Icons.Default.Settings
}
