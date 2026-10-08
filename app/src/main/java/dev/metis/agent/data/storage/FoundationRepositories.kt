package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.FoundationRepository
import dev.metis.agent.domain.storage.SavedPerson
import dev.metis.agent.domain.storage.SavedRelationship
import dev.metis.agent.domain.storage.SavedUserProfile
import dev.metis.agent.domain.storage.SavedReminder
import dev.metis.agent.domain.storage.SavedFocusSession
import dev.metis.agent.domain.storage.SavedEvent
import dev.metis.agent.domain.storage.SavedPromise
import dev.metis.agent.domain.storage.SavedRoutine
import dev.metis.agent.domain.storage.SavedActionRun
import dev.metis.agent.domain.storage.SavedActionAudit
import dev.metis.agent.domain.storage.SavedAgentSession
import dev.metis.agent.domain.storage.SavedRecommendation
import dev.metis.agent.domain.storage.SavedExperiment
import dev.metis.agent.domain.storage.SavedHabit
import dev.metis.agent.domain.storage.SavedDerivedInsight

/** Storage APIs are dormant foundations. They do not infer, schedule or execute anything. */
class FoundationRepositories(private val database: PersonalDatabase, cipher: FieldCipher) {
    private val codec = RecordCodec(cipher)

    val person: FoundationRepository<SavedPerson> = FoundationStore(
        database, database.person(), PersonCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "PERSON", it) },
        ),
    )

    val relationship: FoundationRepository<SavedRelationship> = FoundationStore(
        database, database.relationship(), RelationshipCodec(codec), codec,
        FoundationPolicy(beforeDeletion = { FoundationPrivacy.detach(database, codec, "RELATIONSHIP", it) }),
    )

    val userProfile: FoundationRepository<SavedUserProfile> = FoundationStore(
        database, database.userProfile(), UserProfileCodec(codec), codec,
        FoundationPolicy(beforeDeletion = { FoundationPrivacy.detach(database, codec, "USER_PROFILE", it) }),
    )

    val reminder: FoundationRepository<SavedReminder> = FoundationStore(
        database, database.reminder(), ReminderCodec(codec), codec,
        FoundationPolicy(beforeDeletion = { FoundationPrivacy.detach(database, codec, "REMINDER", it) }),
    )

    val focusSession: FoundationRepository<SavedFocusSession> = FoundationStore(
        database, database.focusSession(), FocusSessionCodec(), codec,
        FoundationPolicy(beforeDeletion = { FoundationPrivacy.detach(database, codec, "FOCUS_SESSION", it) }),
    )

    val event: FoundationRepository<SavedEvent> = FoundationStore(
        database, database.event(), EventCodec(codec), codec,
        FoundationPolicy(
            appendOnly = true,
            validate = {
                validateFoundationReference(database, it.entityType, it.entityId)
                it.actionId?.let { id -> requireNotNull(database.actionRun().find(id)) }
            },
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "EVENT", it) },
        ),
    )

    val promise: FoundationRepository<SavedPromise> = FoundationStore(
        database, database.promise(), PromiseCodec(codec), codec,
        FoundationPolicy(beforeDeletion = { FoundationPrivacy.detach(database, codec, "PROMISE", it) }),
    )

    val routine: FoundationRepository<SavedRoutine> = FoundationStore(
        database, database.routine(), RoutineCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "ROUTINE", it) },
        ),
    )

    val actionRun: FoundationRepository<SavedActionRun> = FoundationStore(
        database, database.actionRun(), ActionRunCodec(codec), codec,
        FoundationPolicy(
            validate = {
                validateFoundationReference(database, it.entityType, it.entityId)
                validateActionUpdate(database, codec, it)
            },
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "ACTION_RUN", it) },
        ),
    )

    val actionAudit: FoundationRepository<SavedActionAudit> = FoundationStore(
        database, database.actionAudit(), ActionAuditCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "ACTION_AUDIT", it) },
            appendOnly = true,
        ),
    )

    val agentSession: FoundationRepository<SavedAgentSession> = FoundationStore(
        database, database.agentSession(), AgentSessionCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "AGENT_SESSION", it) },
            appendOnly = true,
        ),
    )

    val recommendation: FoundationRepository<SavedRecommendation> = FoundationStore(
        database, database.recommendation(), RecommendationCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "RECOMMENDATION", it) },
            validate = {
                validateFoundationReference(database, it.entityType, it.entityId)
            },
        ),
    )

    val experiment: FoundationRepository<SavedExperiment> = FoundationStore(
        database, database.experiment(), ExperimentCodec(codec), codec,
        FoundationPolicy(beforeDeletion = { FoundationPrivacy.detach(database, codec, "EXPERIMENT", it) }),
    )

    val habit: FoundationRepository<SavedHabit> = FoundationStore(
        database, database.habit(), HabitCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "HABIT", it) },
            validate = {
                error("Behavioral analysis is unavailable.")
            },
        ),
    )

    val derivedInsight: FoundationRepository<SavedDerivedInsight> = FoundationStore(
        database, database.derivedInsight(), DerivedInsightCodec(codec), codec,
        FoundationPolicy(
            beforeDeletion = { FoundationPrivacy.detach(database, codec, "DERIVED_INSIGHT", it) },
            appendOnly = true,
            validate = {
                validateFoundationReference(database, it.entityType, it.entityId)
                error("Behavioral analysis is unavailable.")
            },
        ),
    )
}
