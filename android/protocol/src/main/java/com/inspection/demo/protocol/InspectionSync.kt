package com.inspection.demo.protocol

/** A durable, backend-independent report upload queue entry. */
data class InspectionSyncEntry(
    val reportId: String,
    val taskId: String,
    val reportJson: String,
    val enqueuedAtEpochMs: Long,
    val state: String = STATE_PENDING,
    val attempts: Int = 0,
    val nextAttemptAtEpochMs: Long = 0L,
    val lastError: String? = null,
    /** Non-zero only while an upload is actively in flight. */
    val sendingAtEpochMs: Long = 0L,
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_SENDING = "sending"
        const val STATE_FAILED = "failed"
        const val STATE_REJECTED = "rejected"
        const val STATE_SUCCEEDED = "succeeded"
    }
}

/**
 * Pure queue transitions used by the phone persistence layer and by a future
 * WorkManager uploader. Keeping these transitions outside Android makes retry,
 * idempotency and backoff testable without a device or a network endpoint.
 */
object InspectionSyncQueue {
    private const val INITIAL_BACKOFF_MS = 30_000L
    private const val MAX_BACKOFF_MS = 6L * 60L * 60L * 1000L
    private const val SENDING_TIMEOUT_MS = 5L * 60L * 1000L

    fun enqueue(
        entries: List<InspectionSyncEntry>,
        report: InspectionReport,
        nowEpochMs: Long,
    ): List<InspectionSyncEntry> {
        if (entries.any { it.reportId == report.taskId }) return entries
        return entries + InspectionSyncEntry(
            reportId = report.taskId,
            taskId = report.taskId,
            reportJson = InspectionReportCodec.encode(report),
            enqueuedAtEpochMs = nowEpochMs,
        )
    }

    fun due(entries: List<InspectionSyncEntry>, nowEpochMs: Long): List<InspectionSyncEntry> =
        entries.filter {
            it.state == InspectionSyncEntry.STATE_PENDING ||
                (it.state == InspectionSyncEntry.STATE_FAILED && it.nextAttemptAtEpochMs <= nowEpochMs)
        }

    fun markSending(
        entries: List<InspectionSyncEntry>,
        reportId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<InspectionSyncEntry> =
        entries.map { entry ->
            if (entry.reportId == reportId && entry.state != InspectionSyncEntry.STATE_SUCCEEDED) {
                entry.copy(
                    state = InspectionSyncEntry.STATE_SENDING,
                    lastError = null,
                    sendingAtEpochMs = nowEpochMs,
                )
            } else entry
        }

    /**
     * A process can die after marking an item sending and before persisting a
     * response. Release only entries that have exceeded the in-flight timeout;
     * an active upload remains untouched. Idempotency-Key makes a retry safe.
     */
    fun recoverStaleSending(
        entries: List<InspectionSyncEntry>,
        nowEpochMs: Long,
    ): List<InspectionSyncEntry> = entries.map { entry ->
        if (entry.state != InspectionSyncEntry.STATE_SENDING ||
            entry.sendingAtEpochMs <= 0L ||
            nowEpochMs - entry.sendingAtEpochMs < SENDING_TIMEOUT_MS
        ) {
            entry
        } else {
            val attempts = entry.attempts + 1
            entry.copy(
                state = InspectionSyncEntry.STATE_FAILED,
                attempts = attempts,
                nextAttemptAtEpochMs = nowEpochMs + backoffMs(attempts),
                lastError = "上次上传中断，已自动恢复".take(500),
                sendingAtEpochMs = 0L,
            )
        }
    }

    fun markSucceeded(entries: List<InspectionSyncEntry>, reportId: String): List<InspectionSyncEntry> =
        entries.map { entry ->
            if (entry.reportId == reportId) {
                entry.copy(
                    state = InspectionSyncEntry.STATE_SUCCEEDED,
                    nextAttemptAtEpochMs = 0L,
                    lastError = null,
                    sendingAtEpochMs = 0L,
                )
            } else entry
        }

    fun markFailed(
        entries: List<InspectionSyncEntry>,
        reportId: String,
        nowEpochMs: Long,
        reason: String,
    ): List<InspectionSyncEntry> = entries.map { entry ->
        if (entry.reportId != reportId) return@map entry
        val attempts = entry.attempts + 1
        entry.copy(
            state = InspectionSyncEntry.STATE_FAILED,
            attempts = attempts,
            nextAttemptAtEpochMs = nowEpochMs + backoffMs(attempts),
            lastError = reason.take(500),
            sendingAtEpochMs = 0L,
        )
    }

    fun markRejected(
        entries: List<InspectionSyncEntry>,
        reportId: String,
        reason: String,
    ): List<InspectionSyncEntry> = entries.map { entry ->
        if (entry.reportId == reportId) {
            entry.copy(
                state = InspectionSyncEntry.STATE_REJECTED,
                nextAttemptAtEpochMs = Long.MAX_VALUE,
                lastError = reason.take(500),
                sendingAtEpochMs = 0L,
            )
        } else entry
    }

    private fun backoffMs(attempts: Int): Long {
        val exponent = attempts.coerceIn(1, 20) - 1
        val multiplier = 1L shl exponent
        return (INITIAL_BACKOFF_MS * multiplier).coerceAtMost(MAX_BACKOFF_MS)
    }
}
