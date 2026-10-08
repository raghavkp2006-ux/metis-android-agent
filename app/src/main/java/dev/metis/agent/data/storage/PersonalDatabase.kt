package dev.metis.agent.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class, ScheduleEntity::class, MemoryEntity::class,
        TaskDependencyEntity::class, PreferenceEntity::class,
        ProjectEntity::class, GoalEntity::class,
        PersonEntity::class, RelationshipEntity::class, UserProfileEntity::class, ReminderEntity::class,
        FocusSessionEntity::class, EventEntity::class, PromiseEntity::class, RoutineEntity::class,
        ActionRunEntity::class, ActionAuditEntity::class, AgentSessionEntity::class,
        RecommendationEntity::class, ExperimentEntity::class, HabitEntity::class,
        DerivedInsightEntity::class,
    ],
    version = 6, exportSchema = true,
)
abstract class PersonalDatabase : RoomDatabase(), FoundationGroupPeople, FoundationGroupTime,
    FoundationGroupLinks, FoundationGroupHistory, FoundationGroupAnalysis {
    abstract fun records(): RecordDao
    abstract fun dependencies(): TaskDependencyDao
    abstract fun preferences(): PreferenceDao
    abstract fun planning(): PlanningDao

    companion object {
        fun open(context: Context): PersonalDatabase = Room.databaseBuilder(
            context.applicationContext, PersonalDatabase::class.java, "metis-personal.db",
        ).addMigrations(PersonalMigrations.FROM_1_TO_2, PersonalMigrations.FROM_2_TO_3,
            PersonalMigrations.FROM_3_TO_4, PlanningMigration.FROM_4_TO_5, FoundationMigration.FROM_5_TO_6)
            .addCallback(RecordConstraints).build()
    }
}
