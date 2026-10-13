package dev.metis.agent.domain.agent

internal data class ReadFormV16(val kind: AgentIntent, val pattern: Regex,
    val status: String, val mode: String, val negative: Boolean, val field: String,
    val upcoming: Boolean, val meetings: Boolean, val appointments: Boolean)

/** Generated closed productions; no benchmark IDs or complete-example lookup. */
internal object ReadFormsV16 {
    val activity = Regex(
        "(?:turn in|go and check on|prepare before visiting|purc" +
        "hase in terms of|send|review|submit|prepare|return|fini" +
        "sh|help|bring|share|email|call|buy|purchase|print|rewri" +
        "te|edit|update|deploy|push|test|register|pay|reimburse|" +
        "include|meet|check|train|run|upload|address|fix|visit|a" +
        "dd|post|make) (?<object>.{1,250}?)", RegexOption.IGNORE_CASE)
    val modifier = Regex(
        "^(?:(?:the|a|an|my) )?(?:latest|new) ", RegexOption.IGNORE_CASE)
    val forms = listOf(
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?(?:have i (?:already got|created|made|planned|saved|de" +
            "signed)|did i (?:create|have)|do i have|was there|is th" +
            "ere) (?:a |an |any )?(?:task|assignment)(?: already)? t" +
            "o (?<subject>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "activity", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i create a task for (?<subject>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find (?:my|the) task (?:about|to) (?<subject>.{1,300}?" +
            ")[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "OPEN", "activity_or_plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find some task about (?<subject>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "OPEN", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find some task which relates to (?<subject>.{1,300}?)[" +
            "?!.]?\\s*", RegexOption.IGNORE_CASE),
            "OPEN", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find all tasks related to (?<subject>.{1,300}?)[?!.]?\\" +
            "s*", RegexOption.IGNORE_CASE),
            "OPEN", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?locate uncompleted work on (?<subject>.{1,300}?)[?!.]?" +
            "\\s*", RegexOption.IGNORE_CASE),
            "OPEN", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what are tasks that are not completed with (?<subject>" +
            ".{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "OPEN", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what completed tasks are related to (?<subject>.{1,300" +
            "}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "COMPLETED", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find tasks related to (?<subject>.{1,300}?) that are m" +
            "arked completed[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "COMPLETED", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?do i have a completed task for (?<subject>.{1,300}?)[?" +
            "!.]?\\s*", RegexOption.IGNORE_CASE),
            "COMPLETED", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what (?:is|are) the status of my (?<subject>.{1,290}? " +
            "task)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "plain", false, "status", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?is there (?:a|an) (?<subject>.{1,300}?) related task[?" +
            "!.]?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find any unfinished (?<subject>.{1,300}?) tasks[?!.]?\\" +
            "s*", RegexOption.IGNORE_CASE),
            "OPEN", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?is there also (?:a|an) (?<subject>.{1,300}?) task[?!.]" +
            "?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?is there something i need to (?<subject>purchase in te" +
            "rms of .{1,260}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "activity", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_TASKS, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?look for the task, in which i said i need to (?<subjec" +
            "t>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "OPEN", "activity", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what were the (?<subject>prep steps for .{1,270}?)[?!." +
            "]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what did i discuss with (?<subject>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i mention that (?<subject>the reviewer was .{1,240" +
            "}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what (?<subject>topics of .{1,260}?) did i mention i[\\" +
            "x27’]d like to revise[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "revision", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i say that i (?<subject>already have .{1,260}?)[?!" +
            ".]?\\s*", RegexOption.IGNORE_CASE),
            "", "possession", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?find some record about (?<subject>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?is (?<subject>.{1,300}?) mentioned in saved record[?!." +
            "]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i mention (?<subject>submitting .{1,260}? physical" +
            "ly)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i explicitly say (?<subject>.{1,240}? was not my r" +
            "esponsibility)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "negative_role", true, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i say (?<subject>.{1,260}? (?:was|were) optional)[" +
            "?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?(?:can you )?remember what i told you in (?<subject>.{" +
            "1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?(?:so, )?what can you recall from (?<subject>what i sa" +
            "id to .{1,100}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?what was your recollection of (?<subject>my talk with " +
            ".{1,100}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_MEMORY, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i leave any notes behind for (?<subject>(?:a |the " +
            ")?meeting with .{1,100}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.WHAT_DID_I_PROMISE, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i tell (?<person>.{1,100}?) that i would (?<subjec" +
            "t>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "ANY", "activity", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i set an event for (?<subject>.{1,300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?have i made an appointment with (?<person>.{1,100}?)[?" +
            "!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, true),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?have i arranged a meeting with (?<person>.{1,100}?)[?!" +
            ".]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, true, false),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i schedule (?:a|an) (?<subject>.{1,260}? meeting)[" +
            "?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, true, false),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?did i schedule anything related to (?<subject>.{1,300}" +
            "?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", false, false, false),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?when should my next (?<subject>.{1,260}? meeting) be[?" +
            "!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", true, true, false),
        ReadFormV16(AgentIntent.CHECK_CALENDAR, Regex(
            "\\s*(?:please )?(?:(?:can|could|would) you (?:please )?)" +
            "?when do i have to meet again concerning (?<subject>.{1" +
            ",300}?)[?!.]?\\s*", RegexOption.IGNORE_CASE),
            "", "plain", false, "", true, true, false),
    )
}
