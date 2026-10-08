package dev.metis.agent.domain.agent

import java.net.URI

enum class CalendarMode { INTENT, PROVIDER }
enum class CalendarOperation { INSERT, UPDATE, DELETE }
data class CalendarAction(
    override val identity: ActionIdentity,
    val operation: CalendarOperation,
    val mode: CalendarMode,
    val details: CalendarDetails,
    val providerTarget: CalendarTarget? = null,
) : Action {
    override val type = ActionType.CALENDAR
    init {
        require((mode == CalendarMode.PROVIDER) == (providerTarget != null))
        require(mode != CalendarMode.INTENT || operation == CalendarOperation.INSERT)
        require(operation == CalendarOperation.INSERT || providerTarget?.eventId != null)
        require(operation != CalendarOperation.INSERT || providerTarget?.eventId == null)
    }
}
data class CalendarDetails(val title: String, val start: ResolvedTime, val end: ResolvedTime) {
    init {
        requireText(title)
        require(end.instant > start.instant && end.zone == start.zone)
    }
}
data class CalendarTarget(val calendarId: Long, val eventId: Long? = null) {
    init { require(calendarId >= 0 && (eventId == null || eventId >= 0)) }
}

/** Only handoff modes exist; no direct call/SMS modes can be selected by a parser. */
enum class CallMode { DIAL }
enum class MessageMode { COMPOSER }
data class CallAction(
    override val identity: ActionIdentity,
    val targetLabel: String,
    val phoneNumber: String,
    val mode: CallMode = CallMode.DIAL,
) : Action {
    override val type = ActionType.CALL
    init { requireText(targetLabel); require(PHONE_PATTERN.matches(phoneNumber)) }
}
data class MessageAction(
    override val identity: ActionIdentity,
    val recipient: String,
    val destination: URI,
    val body: String,
    val mode: MessageMode = MessageMode.COMPOSER,
) : Action {
    override val type = ActionType.MESSAGE
    init {
        requireText(recipient); requireText(body)
        require(destination.scheme in setOf("sms", "smsto", "mailto"))
        require(when (destination.scheme) {
            "mailto" -> EMAIL_PATTERN.matches(destination.schemeSpecificPart)
            else -> PHONE_PATTERN.matches(destination.schemeSpecificPart)
        })
        require(destination.rawFragment == null && destination.toString().length <= TEXT_LIMIT)
    }
}
enum class NavigationScheme { GEO, GOOGLE_NAVIGATION }
data class NavigationAction(
    override val identity: ActionIdentity,
    val destination: String,
    val scheme: NavigationScheme,
) : Action {
    override val type = ActionType.NAVIGATION
    init { requireText(destination) }
}
private val PHONE_PATTERN = Regex("[+]?[0-9][0-9 ()-]{1,30}[0-9]")
private val EMAIL_PATTERN = Regex("[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}")
