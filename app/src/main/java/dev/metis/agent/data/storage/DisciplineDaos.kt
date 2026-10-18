package dev.metis.agent.data.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HabitDefinitionDao {
    @Query("SELECT * FROM habit_definitions WHERE id = :id")
    suspend fun find(id: String): HabitDefinitionEntity?
    @Query("SELECT * FROM habit_definitions WHERE start_local_date <= :through " +
        "AND (archived_at IS NULL OR archived_at > :from) ORDER BY start_local_date, id")
    suspend fun range(from: String, through: String): List<HabitDefinitionEntity>
    @Insert suspend fun insert(row: HabitDefinitionEntity)
    @Query("UPDATE habit_definitions SET name = :name, description = :description, " +
        "updated_at = :at, revision = revision + 1 WHERE id = :id AND revision = :revision AND active = 1")
    suspend fun rename(id: String, revision: Long, name: ByteArray, description: ByteArray?, at: Long): Int
    @Query("UPDATE habit_definitions SET archived_at = :date, active = 0, updated_at = :at, " +
        "revision = revision + 1 WHERE id = :id AND revision = :revision AND active = 1")
    suspend fun archive(id: String, revision: Long, date: String, at: Long): Int
    @Query("DELETE FROM habit_definitions WHERE id = :id AND revision = :revision")
    suspend fun delete(id: String, revision: Long): Int
}

@Dao
interface DailyCheckinDao {
    @Query("SELECT * FROM daily_checkins WHERE id = :id")
    suspend fun find(id: String): DailyCheckinEntity?
    @Query("SELECT * FROM daily_checkins WHERE local_date BETWEEN :from AND :through ORDER BY local_date")
    suspend fun range(from: String, through: String): List<DailyCheckinEntity>
    @Insert suspend fun insert(row: DailyCheckinEntity)
    @Query("UPDATE daily_checkins SET mood = :mood, note = :note, updated_at = :at, " +
        "revision = revision + 1 WHERE id = :id AND revision = :revision")
    suspend fun update(id: String, revision: Long, mood: ByteArray?, note: ByteArray?, at: Long): Int
    @Query("DELETE FROM daily_checkins WHERE id = :id AND revision = :revision")
    suspend fun delete(id: String, revision: Long): Int
    @Query("UPDATE daily_checkins SET revision = revision + 1, updated_at = MAX(updated_at, :at) " +
        "WHERE id IN (SELECT checkin_id FROM habit_ticks WHERE habit_id = :habitId)")
    suspend fun touchForHabitDeletion(habitId: String, at: Long)
}

@Dao
interface HabitTickDao {
    @Query("SELECT * FROM habit_ticks WHERE checkin_id = :checkinId AND habit_id = :habitId")
    suspend fun find(checkinId: String, habitId: String): HabitTickEntity?
    @Query("SELECT t.* FROM habit_ticks t JOIN daily_checkins c ON c.id = t.checkin_id " +
        "WHERE c.local_date BETWEEN :from AND :through ORDER BY c.local_date, t.habit_id")
    suspend fun range(from: String, through: String): List<HabitTickEntity>
    @Query("SELECT MAX(c.local_date) FROM habit_ticks t JOIN daily_checkins c ON c.id = t.checkin_id " +
        "WHERE t.habit_id = :habitId")
    suspend fun latestDate(habitId: String): String?
    @Insert suspend fun insert(row: HabitTickEntity)
    @Query("UPDATE habit_ticks SET completed = :completed WHERE checkin_id = :checkinId AND habit_id = :habitId")
    suspend fun update(checkinId: String, habitId: String, completed: Boolean): Int
}
