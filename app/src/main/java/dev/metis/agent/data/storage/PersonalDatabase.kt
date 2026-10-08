package dev.metis.agent.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, ScheduleEntity::class, MemoryEntity::class, TaskDependencyEntity::class],
    version = 3, exportSchema = true,
)
abstract class PersonalDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    abstract fun dependencies(): TaskDependencyDao

    companion object {
        fun open(context: Context): PersonalDatabase = Room.databaseBuilder(
            context.applicationContext, PersonalDatabase::class.java, "metis-personal.db",
        ).addMigrations(PersonalMigrations.FROM_1_TO_2, PersonalMigrations.FROM_2_TO_3)
            .addCallback(RecordConstraints).build()
    }
}
