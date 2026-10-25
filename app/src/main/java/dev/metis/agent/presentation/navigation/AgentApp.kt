package dev.metis.agent.presentation.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.metis.agent.PersonalStorage
import dev.metis.agent.data.storage.LocalAgentReads
import dev.metis.agent.domain.agent.ConfirmedTaskAgent
import dev.metis.agent.domain.agent.ReadCandidate
import dev.metis.agent.domain.agent.ConfirmedReminderAgent
import dev.metis.agent.domain.agent.ConfirmedPromiseAgent
import dev.metis.agent.domain.decision.DecisionAgent
import dev.metis.agent.platform.ReminderRuntime
import dev.metis.agent.platform.AndroidContactsResolver
import dev.metis.agent.platform.ContactsPermissionPolicy
import androidx.compose.runtime.LaunchedEffect
import dev.metis.agent.presentation.designsystem.AgentTheme
import java.util.UUID


@Composable
@Suppress("CyclomaticComplexMethod") // Permission and navigation transitions are wired in this composition root.
fun AgentApp(viewModel: ShellViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uiContext = LocalContext.current
    val context = uiContext.applicationContext
    val contactsPermission = remember(context) { ContactsPermissionPolicy(context) }
    val requestModel: RequestViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val repository = PersonalStorage.repository(context)
            val contacts = AndroidContactsResolver(context)
            val reminders = ConfirmedReminderAgent(
                ConfirmedTaskAgent(LocalAgentReads(repository), repository.taskActions, localCalendar = true,
                    readCandidate = ReadCandidate.V16),
                    ReminderRuntime.store(context), contacts = contacts,
                    mayRequestContacts = contactsPermission::mayAutoRequest)
            val promises = ConfirmedPromiseAgent(reminders, repository.promiseActions, contacts = contacts,
                mayRequestContacts = contactsPermission::mayAutoRequest)
            RequestViewModel(dev.metis.agent.data.storage.registerGraphReadsV18(
                dev.metis.agent.domain.planner.PlannerAgent(DecisionAgent(promises,
                    repository.decisions), repository.planner), repository), reminderClarification = reminders,
                promiseClarification = promises,
                contactsResolver = contacts,
                contactsPermissionBlocked = contactsPermission::isPermanentlyDenied,
                explainPermanentContactsDenial = contactsPermission::shouldExplainPermanentDenial)
        }
    })
    val requestState by requestModel.uiState.collectAsStateWithLifecycle()
    var launchedContactQuestion by remember { mutableStateOf<UUID?>(null) }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        val questionId = launchedContactQuestion
        launchedContactQuestion = null
        if (questionId != null && requestModel.hasPendingContactPermission(questionId)) {
            val canAskAgain = uiContext.findActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ?: true
            val permanent = contactsPermission.noteResult(granted, canAskAgain)
            requestModel.onContactPermissionResult(questionId, granted, permanent)
        } else if (questionId != null && requestModel.hasPendingActionContactPermission(questionId)) {
            val canAskAgain = uiContext.findActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ?: true
            contactsPermission.noteResult(granted, canAskAgain)
            requestModel.onActionContactPermissionResult(questionId, granted)
        }
    }
    LaunchedEffect(requestState.contactLookup) {
        val lookup = requestState.contactLookup
        val explain = when (lookup) {
            is ContactLookupUiState.PermissionNeeded ->
                lookup.permanentlyDenied && lookup.explainPermanentDenial
            is ContactLookupUiState.Denied -> lookup.permanentlyDenied && lookup.explainPermanentDenial
            else -> false
        }
        if (explain) contactsPermission.markPermanentExplanationShown()
    }
    LaunchedEffect(context) {
        try { ReminderRuntime.store(context).reconcile() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Storage/permission failure remains visible through reminder history; never reset storage.
        }
    }
    val recordsViewModel: RecordsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val repository = PersonalStorage.repository(context)
            RecordsViewModel(repository, repository, repository, repository.dependencies, repository.preferences,
                repository.planning)
        }
    })
    val records by recordsViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    AgentTheme {
        val preferences = remember(context) {
            context.getSharedPreferences(dev.metis.agent.AppIsolation.name("metis.welcome"),
                android.content.Context.MODE_PRIVATE)
        }
        var welcomed by remember { mutableStateOf(preferences.getBoolean("welcomeCompleted", false)) }
        if (!welcomed) {
            WelcomeScreen {
                preferences.edit { putBoolean("welcomeCompleted", true) }
                welcomed = true
            }
        } else {
            MetisAppShell(
                state = state,
                request = requestState,
                onSelect = {
                    focusManager.clearFocus(); keyboard?.hide()
                    if (viewModel.uiState.value.workspace != it) {
                        requestModel.cancelReminderClarification()
                        requestModel.cancelPromiseClarification()
                        if (viewModel.uiState.value.workspace == Workspace.PEOPLE) requestModel.cancelContactLookup()
                    }
                    viewModel.selectWorkspace(it)
                },
                onDraft = viewModel::updateDraft,
                onSubmit = { source ->
                    val current = viewModel.uiState.value
                    if (current.draft.isNotBlank() && !requestModel.uiState.value.busy) {
                        val origin = ConversationOrigin(current.workspace, current.selectedEntity)
                        focusManager.clearFocus()
                        keyboard?.hide()
                        requestModel.submit(current.draft, origin, source)
                        if (current.workspace == Workspace.PEOPLE) requestModel.cancelContactLookup()
                        viewModel.updateDraft("")
                        viewModel.selectWorkspace(Workspace.CHAT)
                    }
                },
                onNewChat = {
                    if (!requestModel.uiState.value.accepting) {
                        requestModel.dismiss()
                        viewModel.updateDraft("")
                        viewModel.selectWorkspace(Workspace.CHAT)
                    }
                },
                onAccept = requestModel::accept,
                onUndo = requestModel::undo,
                onReject = requestModel::reject,
                onRejectProposal = requestModel::reject,
                onBack = {
                    requestModel.cancelReminderClarification()
                    requestModel.cancelPromiseClarification()
                    requestModel.cancelContactLookup()
                    viewModel.goBack()
                },
                onPrivacy = {
                    requestModel.cancelReminderClarification()
                    requestModel.cancelPromiseClarification()
                    requestModel.cancelContactLookup()
                    viewModel.openPrivacy()
                },
                records = records,
                onReload = recordsViewModel::reload,
                onSearch = recordsViewModel::searchMemories,
                onSearchMore = recordsViewModel::expandSearch,
                onMemoryFilter = recordsViewModel::filterMemory,
                onEntity = { requestModel.cancelReminderClarification();
                    requestModel.cancelPromiseClarification(); viewModel.selectEntity(it) },
                onFindContact = {
                    contactsPermission.isPermanentlyDenied()
                    requestModel.findContact(it)
                },
                onContactChoice = requestModel::answerContact,
                onContactPermission = { questionId ->
                    if (launchedContactQuestion == null && requestModel.hasPendingContactPermission(questionId) &&
                        !contactsPermission.isPermanentlyDenied()) {
                        launchedContactQuestion = questionId
                        contactsPermission.markRequested()
                        contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                onCancelContact = requestModel::cancelContactLookup,
                onContactSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri())
                    try { uiContext.startActivity(intent) } catch (_: RuntimeException) { Unit }
                },
                onActionPersonChoice = requestModel::chooseActionPerson,
                onActionContactPermission = { questionId ->
                    if (launchedContactQuestion == null &&
                        requestModel.hasPendingActionContactPermission(questionId)) {
                        if (contactsPermission.mayAutoRequest()) {
                            launchedContactQuestion = questionId
                            contactsPermission.markRequested()
                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        } else requestModel.onActionContactPermissionResult(questionId, false)
                    }
                },
                onActionContactSkip = { questionId ->
                    if (requestModel.hasPendingActionContactPermission(questionId)) {
                        contactsPermission.markRequested()
                        requestModel.onActionContactPermissionResult(questionId, false)
                    }
                },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
