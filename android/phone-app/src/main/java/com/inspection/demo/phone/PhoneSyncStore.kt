package com.inspection.demo.phone

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inspection.demo.protocol.InspectionReport
import com.inspection.demo.protocol.InspectionSyncEntry
import com.inspection.demo.protocol.InspectionSyncQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Offline report queue. It deliberately has no network dependency: the
 * WorkManager worker reads due entries, uploads reportJson, then applies the
 * same success/failure transitions.
 */
class PhoneSyncStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val dao = InspectionDatabase.get(context).syncQueueDao()
    private val gson = Gson()

    fun enqueue(report: InspectionReport, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(LOCK) {
            val updated = InspectionSyncQueue.enqueue(loadLocked(), report, nowEpochMs)
            return persistLocked(updated)
        }
    }

    fun load(): List<InspectionSyncEntry> {
        synchronized(LOCK) { return loadLocked() }
    }

    private fun loadLocked(): List<InspectionSyncEntry> {
        val stored = runBlocking(Dispatchers.IO) { dao.loadAll() }
        if (stored.isNotEmpty()) return stored.map(SyncQueueEntity::toProtocol)
        val type = object : TypeToken<List<InspectionSyncEntry>>() {}.type
        val legacy = runCatching {
            gson.fromJson<List<InspectionSyncEntry>>(preferences.getString(KEY_ENTRIES, "[]"), type)
                ?: emptyList()
        }.getOrDefault(emptyList())
        if (preferences.contains(KEY_ENTRIES) && persistLocked(legacy)) {
            preferences.edit().clear().commit()
        }
        return legacy
    }

    fun due(nowEpochMs: Long = System.currentTimeMillis()): List<InspectionSyncEntry> =
        InspectionSyncQueue.due(load(), nowEpochMs)

    fun recoverStaleSending(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(LOCK) { persistLocked(InspectionSyncQueue.recoverStaleSending(loadLocked(), nowEpochMs)) }

    fun markSending(reportId: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(LOCK) { persistLocked(InspectionSyncQueue.markSending(loadLocked(), reportId, nowEpochMs)) }

    fun markSucceeded(reportId: String): Boolean = synchronized(LOCK) {
        persistLocked(InspectionSyncQueue.markSucceeded(loadLocked(), reportId))
    }

    fun markFailed(reportId: String, reason: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(LOCK) { persistLocked(InspectionSyncQueue.markFailed(loadLocked(), reportId, nowEpochMs, reason)) }

    fun markRejected(reportId: String, reason: String): Boolean = synchronized(LOCK) {
        persistLocked(InspectionSyncQueue.markRejected(loadLocked(), reportId, reason))
    }

    fun pendingCount(): Int = load().count {
        it.state != InspectionSyncEntry.STATE_SUCCEEDED
    }

    private fun persistLocked(entries: List<InspectionSyncEntry>): Boolean = runCatching {
        runBlocking(Dispatchers.IO) {
            dao.replaceAll(entries.map(SyncQueueEntity::fromProtocol))
        }
        true
    }.getOrDefault(false)

    private companion object {
        val LOCK = Any()
        const val PREFERENCES = "phone_inspection_sync"
        const val KEY_ENTRIES = "entries"
    }
}
