package xyz.kurdcloud.englishmaster.core.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import xyz.kurdcloud.englishmaster.core.model.SyncReport.StopReason
import java.util.concurrent.TimeUnit

fun interface SyncScheduler {
    fun requestSync()
}

/** Enqueues a single chain of sync work that runs once the device has network access. */
class WorkManagerSyncScheduler(private val workManager: WorkManager) : SyncScheduler {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "practice-response-sync"
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncer: ResponseSyncer,
    private val awaitAppReady: suspend () -> Unit,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // WorkManager may start the process cold: wait until the stored session is loaded.
        awaitAppReady()
        val report = syncer.syncAll()
        return when (report.stoppedReason) {
            StopReason.OFFLINE, StopReason.SERVER_UNAVAILABLE -> Result.retry()
            // Needs the user (sign in again / contact support); retried on the next request.
            StopReason.SESSION_EXPIRED, StopReason.ACCOUNT_RESTRICTED, StopReason.NOT_SIGNED_IN, null -> Result.success()
        }
    }
}

class SyncWorkerFactory(
    private val syncer: () -> ResponseSyncer,
    private val awaitAppReady: suspend () -> Unit = {},
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, syncer(), awaitAppReady) else null
}
