package dev.metis.agent.data.storage

import dev.metis.agent.domain.storage.RoutineRule
import dev.metis.agent.domain.storage.RuleHardness
import dev.metis.agent.domain.storage.ScheduleBlock
import dev.metis.agent.domain.storage.ScheduleKind

internal class ScheduleCodec(private val cipher: FieldCipher) {
    fun encodeBlock(block: ScheduleBlock): ScheduleBlockEntity = ScheduleBlockEntity(
        id = block.id,
        weekday = block.weekday,
        startMin = block.startMin,
        endMin = block.endMin,
        title = cipher.encrypt(block.title, "timetable_blocks/${block.id}/title"),
        kind = block.kind.name,
        fixed = block.fixed,
        tag = block.tag?.let { cipher.encrypt(it, "timetable_blocks/${block.id}/tag") },
    )

    fun decodeBlock(entity: ScheduleBlockEntity): ScheduleBlock = ScheduleBlock(
        id = entity.id,
        weekday = entity.weekday,
        startMin = entity.startMin,
        endMin = entity.endMin,
        title = cipher.decrypt(entity.title, "timetable_blocks/${entity.id}/title"),
        kind = ScheduleKind.valueOf(entity.kind),
        fixed = entity.fixed,
        tag = entity.tag?.let { cipher.decrypt(it, "timetable_blocks/${entity.id}/tag") },
    )

    fun encodeRule(rule: RoutineRule): RoutineRuleEntity = RoutineRuleEntity(
        id = rule.id,
        key = rule.key,
        windowStartMin = rule.windowStartMin,
        windowEndMin = rule.windowEndMin,
        weekdaysMask = rule.weekdaysMask,
        hardness = rule.hardness.name,
        note = rule.note?.let { cipher.encrypt(it, "routine_rules/${rule.id}/note") },
    )

    fun decodeRule(entity: RoutineRuleEntity): RoutineRule = RoutineRule(
        id = entity.id,
        key = entity.key,
        windowStartMin = entity.windowStartMin,
        windowEndMin = entity.windowEndMin,
        weekdaysMask = entity.weekdaysMask,
        hardness = RuleHardness.valueOf(entity.hardness),
        note = entity.note?.let { cipher.decrypt(it, "routine_rules/${entity.id}/note") },
    )

    fun toBackup(entity: ScheduleBlockEntity): ScheduleBlockBackupEntity = ScheduleBlockBackupEntity(
        id = entity.id,
        weekday = entity.weekday,
        startMin = entity.startMin,
        endMin = entity.endMin,
        title = entity.title,
        kind = entity.kind,
        fixed = entity.fixed,
        tag = entity.tag,
    )

    fun fromBackup(backup: ScheduleBlockBackupEntity): ScheduleBlockEntity = ScheduleBlockEntity(
        id = backup.id,
        weekday = backup.weekday,
        startMin = backup.startMin,
        endMin = backup.endMin,
        title = backup.title,
        kind = backup.kind,
        fixed = backup.fixed,
        tag = backup.tag,
    )

    fun toBackup(entity: RoutineRuleEntity): RoutineRuleBackupEntity = RoutineRuleBackupEntity(
        id = entity.id,
        key = entity.key,
        windowStartMin = entity.windowStartMin,
        windowEndMin = entity.windowEndMin,
        weekdaysMask = entity.weekdaysMask,
        hardness = entity.hardness,
        note = entity.note,
    )

    fun fromBackup(backup: RoutineRuleBackupEntity): RoutineRuleEntity = RoutineRuleEntity(
        id = backup.id,
        key = backup.key,
        windowStartMin = backup.windowStartMin,
        windowEndMin = backup.windowEndMin,
        weekdaysMask = backup.weekdaysMask,
        hardness = backup.hardness,
        note = backup.note,
    )
}

