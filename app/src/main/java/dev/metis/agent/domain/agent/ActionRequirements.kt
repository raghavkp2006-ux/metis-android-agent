package dev.metis.agent.domain.agent

/** Risk and required capabilities are derived from sealed payloads; callers cannot lower them. */
object ActionRequirements {
    fun risk(action: Action): RiskLevel = when (action) {
        is TaskAction -> if (action.mutation is TaskMutation.Delete) RiskLevel.R3 else RiskLevel.R1
        is MemoryAction -> if (action.mutation is MemoryMutation.Delete) RiskLevel.R3 else RiskLevel.R1
        is CalendarAction -> if (action.operation == CalendarOperation.DELETE) RiskLevel.R3 else RiskLevel.R2
        is CallAction, is MessageAction, is NavigationAction -> RiskLevel.R2
        else -> RiskLevel.R1
    }

    fun capabilities(action: Action): Set<Capability> = when (action) {
        is TaskAction, is MemoryAction, is GoalAction, is PreferenceAction -> setOf(Capability.LOCAL_WRITE)
        is CreateReminderAction -> if (action.precision == SchedulingPrecision.EXACT) {
            setOf(Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE, Capability.EXACT_ALARM,
                Capability.NOTIFICATIONS)
        } else setOf(Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE, Capability.NOTIFICATIONS)
        is CancelReminderAction -> setOf(Capability.LOCAL_WRITE, Capability.REMINDER_SCHEDULE)
        is CreateAlarmAction -> setOf(Capability.EXACT_ALARM)
        is CreateTimerAction -> setOf(Capability.TIMER)
        is CalendarAction -> if (action.mode == CalendarMode.PROVIDER) {
            setOf(Capability.CALENDAR_READ, Capability.CALENDAR_WRITE)
        } else setOf(Capability.CALENDAR_EDITOR)
        is CallAction -> setOf(Capability.DIALER)
        is MessageAction -> setOf(Capability.MESSAGE_COMPOSER)
        is NavigationAction -> setOf(Capability.MAPS)
        is FocusAction -> setOf(Capability.LOCAL_WRITE, Capability.FOCUS)
        is AcceptPlanAction -> setOf(Capability.LOCAL_WRITE, Capability.PLAN_ACCEPTANCE)
    }

    fun targets(action: Action): List<RevisionTarget> = when (action) {
        is TaskAction -> listOfNotNull(taskTarget(action.mutation))
        is MemoryAction -> listOfNotNull(memoryTarget(action.mutation))
        is GoalAction -> listOfNotNull((action.mutation as? GoalMutation.Update)?.target)
        is PreferenceAction -> listOfNotNull(action.target)
        is CancelReminderAction -> listOf(action.target)
        is FocusAction -> listOfNotNull((action.operation as? FocusOperation.Stop)?.target)
        is AcceptPlanAction -> action.acceptedBlocks
        else -> emptyList()
    }

    private fun taskTarget(mutation: TaskMutation): RevisionTarget? = when (mutation) {
        is TaskMutation.Create -> null
        is TaskMutation.Update -> mutation.target
        is TaskMutation.Complete -> mutation.target
        is TaskMutation.Postpone -> mutation.target
        is TaskMutation.Delete -> mutation.target
    }

    private fun memoryTarget(mutation: MemoryMutation): RevisionTarget? = when (mutation) {
        is MemoryMutation.Create -> null
        is MemoryMutation.Update -> mutation.target
        is MemoryMutation.Delete -> mutation.target
    }
}
