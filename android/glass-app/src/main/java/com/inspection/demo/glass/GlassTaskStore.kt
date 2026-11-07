package com.inspection.demo.glass

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.StepCompletePayload

class GlassTaskStore(context: Context) {
    private val preferences = context.getSharedPreferences("inspection_task", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** A device-local copy of the values currently being entered. */
    data class DraftState(
        val result: String? = null,
        val readings: Map<String, String> = emptyMap(),
        val note: String? = null,
        val assetCode: String? = null,
        val confirmed: Boolean = false,
        val faultTime: String? = null,
    )

    data class SavedTask(
        val envelope: ProtocolEnvelope,
        val stepIndex: Int,
        val photoIdsByStep: Map<String, List<String>>,
        val pendingCompletion: StepCompletePayload? = null,
        /** Nullable for snapshots written by versions before draft recovery. */
        val draftsByStep: Map<String, DraftState>? = null,
        val started: Boolean = true,
        val paused: Boolean = false,
        /** True only after the phone has durably accepted task completion. */
        val completed: Boolean = false,
    )

    /** Persist before the caller acknowledges the corresponding protocol message. */
    fun save(
        envelope: ProtocolEnvelope,
        stepIndex: Int,
        photoIdsByStep: Map<String, List<String>> = emptyMap(),
        pendingCompletion: StepCompletePayload? = null,
        draftsByStep: Map<String, DraftState> = emptyMap(),
        started: Boolean = true,
        paused: Boolean = false,
        completed: Boolean = false,
    ): Boolean {
        return preferences.edit()
            .putString(KEY_ENVELOPE, ProtocolCodec.encode(envelope))
            .putInt(KEY_STEP_INDEX, stepIndex)
            .putString(KEY_PHOTOS, gson.toJson(photoIdsByStep))
            .putString(KEY_PENDING_COMPLETION, pendingCompletion?.let(gson::toJson))
            .putString(KEY_DRAFTS, gson.toJson(draftsByStep))
            .putBoolean(KEY_STARTED, started)
            .putBoolean(KEY_PAUSED, paused)
            .putBoolean(KEY_COMPLETED, completed)
            .commit()
    }

    fun load(): SavedTask? {
        val raw = preferences.getString(KEY_ENVELOPE, null) ?: return null
        val envelope = runCatching { ProtocolCodec.decode(raw) }.getOrNull() ?: return null
        val photoIds = runCatching {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson<Map<String, List<String>>>(preferences.getString(KEY_PHOTOS, "{}"), type) ?: emptyMap()
        }.getOrDefault(emptyMap())
        val pending = preferences.getString(KEY_PENDING_COMPLETION, null)?.let { raw ->
            runCatching { gson.fromJson(raw, StepCompletePayload::class.java) }.getOrNull()
        }
        val drafts = runCatching {
            val type = object : TypeToken<Map<String, DraftState>>() {}.type
            gson.fromJson<Map<String, DraftState>>(preferences.getString(KEY_DRAFTS, "{}"), type) ?: emptyMap()
        }.getOrDefault(emptyMap())
        // Snapshots written before the explicit start screen were already
        // executable; preserve that behavior when the new key is absent.
        val started = if (preferences.contains(KEY_STARTED)) {
            preferences.getBoolean(KEY_STARTED, true)
        } else {
            true
        }
        val paused = preferences.getBoolean(KEY_PAUSED, false)
        val completed = preferences.getBoolean(KEY_COMPLETED, false)
        return SavedTask(envelope, preferences.getInt(KEY_STEP_INDEX, 0), photoIds, pending, drafts, started, paused, completed)
    }

    /** Clear synchronously so a subsequent task cannot observe stale state. */
    fun clear() { preferences.edit().clear().commit() }

    private companion object {
        const val KEY_ENVELOPE = "envelope"
        const val KEY_STEP_INDEX = "step_index"
        const val KEY_PHOTOS = "photo_ids_by_step"
        const val KEY_PENDING_COMPLETION = "pending_completion"
        const val KEY_DRAFTS = "drafts_by_step"
        const val KEY_STARTED = "started"
        const val KEY_PAUSED = "paused"
        const val KEY_COMPLETED = "completed"
    }
}
