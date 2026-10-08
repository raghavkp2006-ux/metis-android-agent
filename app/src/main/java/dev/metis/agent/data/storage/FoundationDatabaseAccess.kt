package dev.metis.agent.data.storage

interface FoundationGroupPeople {
    fun person(): PersonDao
    fun relationship(): RelationshipDao
    fun userProfile(): UserProfileDao
}

interface FoundationGroupTime {
    fun reminder(): ReminderDao
    fun focusSession(): FocusSessionDao
    fun event(): EventDao
}

interface FoundationGroupLinks {
    fun promise(): PromiseDao
    fun routine(): RoutineDao
    fun actionRun(): ActionRunDao
}

interface FoundationGroupHistory {
    fun actionAudit(): ActionAuditDao
    fun agentSession(): AgentSessionDao
    fun recommendation(): RecommendationDao
}

interface FoundationGroupAnalysis {
    fun experiment(): ExperimentDao
    fun habit(): HabitDao
    fun derivedInsight(): DerivedInsightDao
}
