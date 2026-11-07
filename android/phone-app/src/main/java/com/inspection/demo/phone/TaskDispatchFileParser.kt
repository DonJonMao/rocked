package com.inspection.demo.phone

import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.TaskDispatchPayload
import com.inspection.demo.protocol.TaskGuard

data class ImportedTaskDispatch(
    val taskId: String,
    val payload: TaskDispatchPayload,
)

/** Pure parser used by the file picker and unit tests; persistence happens after this succeeds. */
object TaskDispatchFileParser {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun parse(raw: String): Result<ImportedTaskDispatch> = runCatching {
        require(raw.isNotBlank()) { "任务文件为空" }
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "任务文件过大（上限 2 MB）" }
        val envelope = ProtocolCodec.decode(raw)
        val identityErrors = TaskGuard.validateDispatchEnvelope(envelope)
        require(identityErrors.isEmpty()) { identityErrors.joinToString("；") }
        val taskId = envelope.taskId!!.trim()
        val payload = ProtocolCodec.payload(envelope, TaskDispatchPayload::class.java) as TaskDispatchPayload
        val errors = TaskGuard.validateDispatch(payload)
        require(errors.isEmpty()) { errors.joinToString("；") }
        ImportedTaskDispatch(taskId, payload)
    }.recoverCatching { error ->
        throw IllegalArgumentException(error.message ?: "任务文件无法解析", error)
    }
}
