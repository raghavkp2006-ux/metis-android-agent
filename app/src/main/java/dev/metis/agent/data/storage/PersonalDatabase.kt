package dev.metis.agent.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class, ScheduleEntity::class, MemoryEntity::class], version = 1, exportSchema = true)
abstract class PersonalDatabase : RoomDatabase() {
    abstract fun records(): RecordDao

    companion object {
        fun open(context: Context): PersonalDatabase = Room.databaseBuilder(
            context.applicationContext, PersonalDatabase::class.java, "metis-personal.db",
        ).addCallback(RecordConstraints).build()
    }
}
