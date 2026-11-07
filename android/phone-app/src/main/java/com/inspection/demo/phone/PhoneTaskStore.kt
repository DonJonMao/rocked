package com.inspection.demo.phone

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inspection.demo.protocol.InspectionReport
import com.inspection.demo.protocol.InspectionReportBuilder
import com.inspection.demo.protocol.InspectionReportCodec
import com.inspection.demo.protocol.PhotoUploadPayload
import com.inspection.demo.protocol.ProtocolCommandIds
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.StepCompletePayload
import com.inspection.demo.protocol.StepDraftPayload
import com.inspection.demo.protocol.TaskDispatchPayload
import com.inspection.demo.protocol.TaskGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Durable offline snapshot for the first phone slice.
 *
 * A single Room row is committed for each mutation; this avoids partially
 * updated task, photo and completion fields when Android kills the process.
 * The previous SharedPreferences formats are read once and migrated so an
 * update does not discard an in-progress field inspection.
 */
class PhoneTaskStore(context: Context) {
    data class CompletionResult(
        val accepted: Boolean,
        val reason: String? = null,
    )

    data class State(
        val taskId: String,
        val payload: TaskDispatchPayload,
        val completedStepIds: Set<String>,
        val photoIds: Set<String>,
        val photoMetadata: Map<String, PhotoUploadPayload>,
        val completions: Map<String, StepCompletePayload>,
        val drafts: Map<String, StepDraftPayload>,
    )

    data class HistoryRecord(
        val taskId: String,
        val status: String,
        val startedAtEpochMs: Long,
        val updatedAtEpochMs: Long,
        val completedAtEpochMs: Long?,
        val completedStepCount: Int,
        val totalStepCount: Int,
    )

    private data class PersistedSnapshot(
        val envelope: ProtocolEnvelope,
        val createdAtEpochMs: Long = 0L,
        val completedStepIds: Set<String> = emptySet(),
        val photoIds: Set<String> = emptySet(),
        val photoMetadata: Map<String, PhotoUploadPayload> = emptyMap(),
        val completions: Map<String, StepCompletePayload> = emptyMap(),
        val drafts: Map<String, StepDraftPayload> = emptyMap(),
    )

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val database = InspectionDatabase.get(appContext)
    private val dao = database.taskSnapshotDao()
    private val historyDao = database.taskHistoryDao()
    private val gson = Gson()
    private val lock = Any()

    fun saveDispatch(taskId: String, payload: TaskDispatchPayload): CompletionResult {
        synchronized(lock) {
            if (taskId.isBlank()) return CompletionResult(false, "任务编号不能为空")
            val dispatchErrors = TaskGuard.validateDispatch(payload)
            if (dispatchErrors.isNotEmpty()) return CompletionResult(false, dispatchErrors.joinToString("；"))
            val existing = loadSnapshotLocked()
            if (existing?.envelope?.taskId == taskId) {
                val existingTask = decodeTask(existing.envelope)
                    ?: return CompletionResult(false, "当前任务记录损坏，不能确认重复下发")
                return if (existingTask == payload) {
                    CompletionResult(true)
                } else {
                    CompletionResult(false, "同一任务编号对应的任务内容不一致")
                }
            }
            if (existing != null) {
                val existingTask = decodeTask(existing.envelope)
                    ?: return CompletionResult(false, "当前任务记录损坏，不能覆盖")
                TaskGuard.activeDispatchRejection(
                    existingTaskId = existing.envelope.taskId,
                    existingStepIds = existingTask.steps.map { it.id },
                    completedStepIds = existing.completedStepIds.orEmpty(),
                    incomingTaskId = taskId,
                )?.let { return CompletionResult(false, it) }
            }
            val envelope = ProtocolEnvelope(
                type = "task.dispatch",
                commandId = ProtocolCommandIds.dispatch(taskId),
                taskId = taskId,
                sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
                payload = ProtocolCodec.json(payload),
            )
            return if (persistLocked(PersistedSnapshot(envelope, createdAtEpochMs = System.currentTimeMillis()))) {
                CompletionResult(true)
            } else {
                CompletionResult(false, "手机本地保存失败")
            }
        }
    }

    fun markStepComplete(taskId: String, completion: StepCompletePayload): CompletionResult {
        synchronized(lock) {
            val snapshot = loadSnapshotLocked()
            if (snapshot?.envelope?.taskId != taskId) return CompletionResult(false, "任务不存在或已切换")
            val task = decodeTask(snapshot.envelope) ?: return CompletionResult(false, "任务记录损坏")
            val errors = TaskGuard.validateStepCompletion(
                task = task,
                completion = completion,
                completedStepIds = snapshot.completedStepIds,
                availablePhotos = snapshot.photoMetadata,
                existingCompletion = snapshot.completions[completion.expectedStepId],
            )
            if (errors.isNotEmpty()) return CompletionResult(false, errors.joinToString("；"))
            if (completion.expectedStepId in snapshot.completedStepIds) return CompletionResult(true)

            val updated = snapshot.copy(
                completedStepIds = snapshot.completedStepIds + completion.expectedStepId,
                photoIds = snapshot.photoIds + completion.photoIds,
                completions = snapshot.completions + (completion.expectedStepId to completion),
                drafts = snapshot.drafts.orEmpty() - completion.expectedStepId,
            )
            return if (persistLocked(updated)) CompletionResult(true) else CompletionResult(false, "手机本地保存失败")
        }
    }

    /** Save partial values without advancing the task or requiring all evidence. */
    fun markStepDraft(taskId: String, draft: StepDraftPayload): CompletionResult {
        synchronized(lock) {
            val snapshot = loadSnapshotLocked()
            if (snapshot?.envelope?.taskId != taskId) return CompletionResult(false, "任务不存在或已切换")
            val task = decodeTask(snapshot.envelope) ?: return CompletionResult(false, "任务记录损坏")
            val draftErrors = TaskGuard.validateStepDraft(task, draft, snapshot.completedStepIds)
            if (draftErrors.isNotEmpty()) {
                return CompletionResult(false, draftErrors.joinToString("；"))
            }
            // A late draft for an already completed step cannot mutate the
            // committed record; treating it as accepted makes retries safe.
            if (draft.expectedStepId in snapshot.completedStepIds) return CompletionResult(true)
            val updated = snapshot.copy(drafts = snapshot.drafts.orEmpty() + (draft.expectedStepId to draft))
            return if (persistLocked(updated)) CompletionResult(true) else CompletionResult(false, "手机本地保存失败")
        }
    }

    fun markPhoto(taskId: String, photoId: String, metadata: PhotoUploadPayload? = null): Boolean {
        synchronized(lock) {
            val snapshot = loadSnapshotLocked()
            if (snapshot?.envelope?.taskId != taskId) return false
            val task = decodeTask(snapshot.envelope) ?: return false
            val existingMetadata = snapshot.photoMetadata[photoId]
            if (metadata != null) {
                if (metadata.photoId != photoId) return false
                if (TaskGuard.validatePhotoMetadata(task, metadata, snapshot.completedStepIds, snapshot.photoMetadata).isNotEmpty()) return false
            } else if (existingMetadata == null) {
                return false
            }
            val updatedMetadata = if (metadata == null) snapshot.photoMetadata else snapshot.photoMetadata + (photoId to metadata)
            return persistLocked(
                snapshot.copy(
                    photoIds = snapshot.photoIds + photoId,
                    photoMetadata = updatedMetadata,
                ),
            )
        }
    }

    fun load(): State? {
        val snapshot = synchronized(lock) { loadSnapshotLocked() } ?: return null
        val payload = decodeTask(snapshot.envelope) ?: return null
        return State(
            taskId = snapshot.envelope.taskId.orEmpty(),
            payload = payload,
            completedStepIds = snapshot.completedStepIds,
            photoIds = snapshot.photoIds,
            photoMetadata = snapshot.photoMetadata,
            completions = snapshot.completions,
            drafts = snapshot.drafts.orEmpty(),
        )
    }

    fun buildReport(generatedAt: String): InspectionReport? {
        val state = load() ?: return null
        return InspectionReportBuilder.build(
            taskId = state.taskId,
            task = state.payload,
            completions = state.completions,
            photoMetadata = state.photoMetadata,
            generatedAt = generatedAt,
        )
    }

    fun loadHistory(): List<HistoryRecord> = synchronized(lock) {
        // Also trigger one-way migration when the user opens history before
        // the first new task mutation after upgrading the app.
        loadSnapshotLocked()
        runBlocking(Dispatchers.IO) {
            historyDao.loadAll().map { entry ->
                val snapshot = runCatching { gson.fromJson(entry.stateJson, PersistedSnapshot::class.java) }.getOrNull()
                val task = snapshot?.let { decodeTask(it.envelope) }
                HistoryRecord(
                    taskId = entry.taskId,
                    status = entry.status,
                    startedAtEpochMs = entry.startedAtEpochMs,
                    updatedAtEpochMs = entry.updatedAtEpochMs,
                    completedAtEpochMs = entry.completedAtEpochMs,
                    completedStepCount = snapshot?.completedStepIds?.size ?: 0,
                    totalStepCount = task?.steps?.size ?: 0,
                )
            }
        }
    }

    fun exportReport(directory: File, generatedAt: String): File? {
        val report = buildReport(generatedAt) ?: return null
        return runCatching {
            directory.mkdirs()
            val safeTaskId = report.taskId.replace(Regex("[^A-Za-z0-9._-]"), "_")
            File(directory, "$safeTaskId.json").apply {
                writeText(InspectionReportCodec.encode(report), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    fun clear() {
        synchronized(lock) {
            runBlocking(Dispatchers.IO) { dao.clearActive() }
            // Remove legacy keys after Room has committed. If the process dies
            // before this point, the next read can safely retry migration.
            preferences.edit().clear().commit()
        }
    }

    private fun decodeTask(envelope: ProtocolEnvelope): TaskDispatchPayload? =
        runCatching { ProtocolCodec.payload(envelope, TaskDispatchPayload::class.java) as TaskDispatchPayload }.getOrNull()

    private fun loadSnapshotLocked(): PersistedSnapshot? {
        val roomSnapshot = runBlocking(Dispatchers.IO) { dao.loadActive() }
        if (roomSnapshot != null) {
            runCatching { gson.fromJson(roomSnapshot.stateJson, PersistedSnapshot::class.java) }.getOrNull()?.let { return it }
        }
        val migrated = loadLegacySnapshot()
        if (migrated != null) {
            // Migration is performed synchronously before the caller can ACK a
            // message, so the old snapshot cannot be lost on process death.
            if (persistLocked(migrated)) {
                preferences.edit().clear().commit()
            }
        }
        return migrated
    }

    private fun loadLegacySnapshot(): PersistedSnapshot? {
        // Version introduced during the initial Android slice: one JSON
        // document in SharedPreferences. Migrate it before checking the older
        // five-key representation.
        preferences.getString(KEY_STATE, null)?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { gson.fromJson(raw, PersistedSnapshot::class.java) }.getOrNull()?.let { return it }
        }
        val raw = preferences.getString(KEY_ENVELOPE, null) ?: return null
        val envelope = runCatching { ProtocolCodec.decode(raw) }.getOrNull() ?: return null
        return PersistedSnapshot(
            envelope = envelope,
            completedStepIds = readStringSet(KEY_COMPLETED),
            photoIds = readStringSet(KEY_PHOTOS),
            photoMetadata = readMap(KEY_PHOTO_METADATA),
            completions = readMap(KEY_COMPLETIONS),
        )
    }

    // Snapshot writes are intentionally synchronous: the caller must not send
    // an ACK before task/photo state is durable across process death.
    private fun persistLocked(snapshot: PersistedSnapshot): Boolean {
        val taskId = snapshot.envelope.taskId ?: return false
        return runCatching {
            val now = System.currentTimeMillis()
            val normalized = if (snapshot.createdAtEpochMs > 0L) snapshot else snapshot.copy(createdAtEpochMs = now)
            val task = decodeTask(normalized.envelope)
            val completed = normalized.completedStepIds.size
            val total = task?.steps?.size ?: 0
            val isComplete = total > 0 && completed >= total
            val history = TaskHistoryEntity(
                taskId = taskId,
                stateJson = gson.toJson(normalized),
                status = if (isComplete) "completed" else "in_progress",
                startedAtEpochMs = normalized.createdAtEpochMs,
                updatedAtEpochMs = now,
                completedAtEpochMs = if (isComplete) now else null,
            )
            runBlocking(Dispatchers.IO) {
                database.withTransaction {
                    dao.saveActive(TaskSnapshotEntity(taskId = taskId, stateJson = gson.toJson(normalized), updatedAt = now))
                    historyDao.upsert(history)
                }
            }
            true
        }.getOrDefault(false)
    }

    private inline fun <reified T> readMap(key: String): Map<String, T> {
        val type = object : TypeToken<Map<String, T>>() {}.type
        return runCatching {
            gson.fromJson<Map<String, T>>(preferences.getString(key, "{}"), type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun readStringSet(key: String): Set<String> {
        val type = object : TypeToken<Set<String>>() {}.type
        return runCatching {
            gson.fromJson<Set<String>>(preferences.getString(key, "[]"), type) ?: emptySet()
        }.getOrDefault(emptySet())
    }

    private companion object {
        const val PREFERENCES = "phone_inspection_task"
        const val KEY_STATE = "state"
        // Legacy keys retained solely for one-way migration.
        const val KEY_ENVELOPE = "envelope"
        const val KEY_COMPLETED = "completed_step_ids"
        const val KEY_PHOTOS = "photo_ids"
        const val KEY_PHOTO_METADATA = "photo_metadata"
        const val KEY_COMPLETIONS = "completions"
    }
}
