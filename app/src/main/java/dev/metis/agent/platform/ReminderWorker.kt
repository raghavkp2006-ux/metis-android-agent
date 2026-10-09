package dev.metis.agent.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.metis.agent.PersonalStorage
import dev.metis.agent.data.storage.LocalAcceptedReminderStore
import dev.metis.agent.data.storage.RecordCodec
import dev.metis.agent.data.storage.KeystoreFieldCipher
import dev.metis.agent.data.storage.ReminderDelivery
import java.util.UUID

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val id = UUID.fromString(requireNotNull(inputData.getString(AndroidReminderPlatform.REMINDER_ID)))
        val service = ReminderRuntime.store(applicationContext)
        val finished = ReminderDelivery(service.records, AndroidReminderPlatform(applicationContext)).deliver(id)
        if (finished) Result.success() else if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) { Result.failure() }

    private companion object { const val MAX_ATTEMPTS = 5 }
}

object ReminderRuntime {
    fun store(context: Context): LocalAcceptedReminderStore {
        val repository = PersonalStorage.repository(context)
        return LocalAcceptedReminderStore(repository, repository.database, RecordCodec(KeystoreFieldCipher()),
            AndroidReminderPlatform(context))
    }
}
