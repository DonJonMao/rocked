package com.inspection.demo.phone

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Uploads due report snapshots when an HTTPS endpoint is configured. The queue
 * remains entirely local when REPORT_SYNC_URL is blank; this is intentional for
 * offline demos and prevents accidental requests to an unknown server.
 */
class InspectionSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val endpoint = BuildConfig.REPORT_SYNC_URL.trim()
        if (endpoint.isBlank()) return Result.success()
        val store = PhoneSyncStore(applicationContext)
        if (!store.recoverStaleSending()) return Result.retry()
        val dueEntries = store.due().take(MAX_BATCH_SIZE)
        if (dueEntries.isEmpty()) return Result.success()

        for (entry in dueEntries) {
            if (!store.markSending(entry.reportId)) return Result.retry()
            when (val result = ReportUploadHttpClient.upload(endpoint, BuildConfig.REPORT_SYNC_TOKEN, entry)) {
                ReportUploadResult.Success -> {
                    // The server accepted the idempotent request. Keep the
                    // WorkManager run alive until the local acknowledgement is
                    // durable, otherwise a DB failure could strand the entry
                    // in `sending` without another scheduled attempt.
                    if (!store.markSucceeded(entry.reportId)) return Result.retry()
                }
                is ReportUploadResult.Retry -> {
                    if (!store.markFailed(entry.reportId, result.reason)) return Result.retry()
                    return Result.retry()
                }
                is ReportUploadResult.PermanentFailure -> {
                    if (!store.markRejected(entry.reportId, result.reason)) return Result.retry()
                }
            }
        }
        return if (store.due().isNotEmpty()) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_BATCH_SIZE = 10
    }
}
