package com.inspection.demo.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlin.math.floor
import java.security.MessageDigest
import java.util.UUID

data class ProtocolEnvelope(
    val type: String,
    val version: Int = 1,
    val messageId: String = UUID.randomUUID().toString(),
    val commandId: String? = null,
    val taskId: String? = null,
    val sentAt: String,
    val deviceId: String? = null,
    val payload: JsonObject = JsonObject(),
)

/**
 * Small, persistence-friendly replay ledger for protocol messages.
 *
 * A retry may reuse a command id while the SDK assigns a new message id, or
 * it may deliver the exact same message again. Recording both identities lets
 * a receiver suppress duplicate notifications after an Activity/SDK restart
 * without making the transport layer depend on Android storage.
 */
class ProtocolReplayLedger(
    initialKeys: Collection<String> = emptyList(),
    private val maxKeys: Int = 512,
) {
    init {
        require(maxKeys > 0) { "maxKeys must be positive" }
    }

    private val keys = LinkedHashSet<String>().apply {
        initialKeys.filter(String::isNotBlank).takeLast(maxKeys).forEach(::add)
    }

    /** Returns true only for the first delivery of this envelope identity. */
    @Synchronized
    fun remember(envelope: ProtocolEnvelope): Boolean {
        val candidates = keysFor(envelope)
        if (candidates.isEmpty() || candidates.any { it in keys }) return false
        candidates.forEach(keys::add)
        while (keys.size > maxKeys) keys.iterator().let { iterator -> iterator.next(); iterator.remove() }
        return true
    }

    @Synchronized
    fun isRemembered(envelope: ProtocolEnvelope): Boolean = keysFor(envelope).any { it in keys }

    @Synchronized
    fun snapshot(): Set<String> = keys.toSet()

    private fun keysFor(envelope: ProtocolEnvelope): List<String> = buildList {
        envelope.commandId?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add("command|${envelope.taskId.orEmpty()}|${envelope.type}|$it")
        }
        envelope.messageId.trim().takeIf { it.isNotEmpty() }?.let {
            add("message|$it")
        }
    }
}

data class InspectionTemplate(
    val id: String,
    val name: String,
    val version: Int,
    val category: String? = null,
    val description: String? = null,
    val source: InspectionSource? = null,
    val assetIds: List<String> = emptyList(),
    val timeSlots: List<String> = emptyList(),
)

data class InspectionSource(
    val file: String,
    val page: Int,
    val section: String,
)

data class InspectionAsset(
    val id: String,
    val code: String,
    val name: String,
    val location: String,
)

data class InspectionAssignee(
    val id: String,
    val name: String,
)

data class InspectionStep(
    val id: String,
    val type: String,
    val title: String,
    val instruction: String,
    val requirements: StepRequirements,
    val fields: List<ReadingField> = emptyList(),
)

data class ReadingField(
    val id: String,
    val label: String,
    val kind: String,
    val unit: String? = null,
    val options: List<String> = emptyList(),
    val expected: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val minExclusive: Boolean = false,
    val maxExclusive: Boolean = false,
)

data class StepRequirements(
    val minPhotos: Int = 0,
    val resultRequired: Boolean = false,
    val abnormalNoteRequired: Boolean = false,
    val confirmationRequired: Boolean = true,
)

data class TaskDispatchPayload(
    val template: InspectionTemplate,
    val asset: InspectionAsset,
    val assignee: InspectionAssignee,
    val steps: List<InspectionStep>,
    val schedule: InspectionSchedule? = null,
)

data class InspectionSchedule(
    val date: String,
    val timeSlot: String,
    val shift: String? = null,
)

data class StepCompletePayload(
    val expectedStepId: String,
    val revision: Int,
    val result: String? = null,
    val readings: Map<String, String> = emptyMap(),
    val note: String? = null,
    val photoIds: List<String> = emptyList(),
    val assetCode: String? = null,
    val confirmed: Boolean = false,
    val faultTime: String? = null,
    val completedAt: String? = null,
)

/** A resumable, non-committing copy of the current step values. */
data class StepDraftPayload(
    val expectedStepId: String,
    val revision: Int = 1,
    val result: String? = null,
    val readings: Map<String, String> = emptyMap(),
    val note: String? = null,
    val photoIds: List<String> = emptyList(),
    val assetCode: String? = null,
    val confirmed: Boolean = false,
    val faultTime: String? = null,
)

data class TaskAckPayload(
    val accepted: Boolean,
    val reason: String? = null,
)

data class StepAckPayload(
    val expectedStepId: String,
    val revision: Int,
    val accepted: Boolean,
    val reason: String? = null,
)

data class PhotoAckPayload(
    val photoId: String,
    val accepted: Boolean,
    val reason: String? = null,
)

data class TaskCompletePayload(
    val accepted: Boolean,
    val reason: String? = null,
)

data class DeviceHeartbeatPayload(
    val appVersion: String,
    val connection: String,
    val batteryPercent: Int,
)

/** Stable command identities for acknowledgements with different outcomes. */
object ProtocolCommandIds {
    fun dispatch(taskId: String): String = "cmd-dispatch-$taskId"

    fun taskAck(taskId: String, accepted: Boolean): String =
        "ack-$taskId-${if (accepted) "accepted" else "rejected"}"

    fun taskStart(taskId: String): String = "start-$taskId"

    fun taskPause(taskId: String): String = "pause-$taskId"

    fun taskResume(taskId: String): String = "resume-$taskId"

    fun stepComplete(taskId: String, stepId: String, revision: Int): String =
        "complete-$taskId-$stepId-$revision"

    fun photoUpload(photoId: String): String = "photo-$photoId"

    fun taskComplete(taskId: String): String = "complete-task-$taskId"

    fun stepAck(taskId: String, stepId: String, revision: Int, accepted: Boolean): String =
        "ack-step-$taskId-$stepId-$revision-${if (accepted) "accepted" else "rejected"}"

    fun photoAck(photoId: String, accepted: Boolean): String =
        "ack-photo-$photoId-${if (accepted) "accepted" else "rejected"}"
}

/** Text-channel metadata sent immediately before the JPEG/PNG/WebP stream. */
data class PhotoUploadPayload(
    val photoId: String,
    val stepId: String,
    val fileName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sha256: String? = null,
    val sizeBytes: Long = 0L,
)

/**
 * Correlates a file callback with its text-channel metadata without trusting
 * an SDK-generated temporary path. The photo id is the primary key; the
 * original file name is a second exact guard against cross-task misbinding.
 */
object PhotoTransferMatcher {
    fun matches(payload: PhotoUploadPayload, receivedFileName: String): Boolean {
        val received = receivedFileName.substringAfterLast('/').substringAfterLast('\\').trim()
        if (received.isBlank()) return false
        val expectedName = payload.fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        if (expectedName.isNotBlank() && received.equals(expectedName, ignoreCase = true)) return true

        val expectedId = payload.photoId.trim()
        if (expectedId.isBlank()) return false
        val receivedStem = received.substringBeforeLast('.', received)
        return receivedStem.equals(expectedId, ignoreCase = true)
    }
}

object PhotoIntegrity {
    const val maxBytes: Long = 10L * 1024L * 1024L
    private val allowedMimeTypes = setOf("image/jpeg", "image/png", "image/webp")

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    fun validate(payload: PhotoUploadPayload, bytes: ByteArray): List<String> {
        val errors = mutableListOf<String>()
        if (payload.mimeType !in allowedMimeTypes) errors += "不支持的图片格式：${payload.mimeType}"
        if (bytes.isEmpty()) errors += "照片文件为空"
        if (bytes.size > maxBytes) errors += "照片超过 10 MB"
        if (payload.sizeBytes > 0 && payload.sizeBytes != bytes.size.toLong()) {
            errors += "照片大小校验失败"
        }
        if (!payload.sha256.isNullOrBlank() && !payload.sha256.equals(sha256(bytes), ignoreCase = true)) {
            errors += "照片 SHA-256 校验失败"
        }
        if (payload.width <= 0 || payload.height <= 0) errors += "照片尺寸无效"
        return errors
    }
}

object StepValidator {
    private val allowedResults = setOf("normal", "abnormal", "shutdown", "standby", "not_applicable")
    private val inactiveResults = setOf("shutdown", "standby", "not_applicable")

    fun validate(
        step: InspectionStep,
        result: String?,
        readings: Map<String, String>,
        note: String?,
        photoIds: List<String>,
        faultTime: String? = null,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (step.requirements.resultRequired && result.isNullOrBlank()) {
            errors += "请选择检查结果"
        }
        if (!result.isNullOrBlank() && result !in allowedResults) {
            errors += "检查结果无效"
        }
        if (step.requirements.abnormalNoteRequired && result in setOf("abnormal", "shutdown", "standby", "not_applicable") && note.isNullOrBlank()) {
            errors += if (result == "abnormal") "异常结果需要填写说明" else "停机/待机/不涉及需要填写状态原因"
        }
        if (photoIds.size < step.requirements.minPhotos) {
            errors += "至少需要 ${step.requirements.minPhotos} 张照片"
        }
        if (!faultTime.isNullOrBlank() && !Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(faultTime.trim())) {
            errors += "故障发现时间格式应为 HH:mm"
        }
        errors += validateReadingKeys(step, readings)
        // The Web workflow treats shutdown/standby/not-applicable as a valid
        // state where numeric readings are intentionally not required.
        if (result !in inactiveResults) step.fields.forEach { field ->
            val raw = readings[field.id].orEmpty().trim()
            if (raw.isEmpty()) {
                errors += "请填写：${field.label}"
                return@forEach
            }
            when (field.kind) {
                "select" -> if (field.options.isNotEmpty() && raw !in field.options) {
                    errors += "${field.label}不在选项范围内"
                }
                "number" -> {
                    val value = raw.toDoubleOrNull()
                    if (value == null) {
                        errors += "${field.label}必须是数字"
                    } else {
                        if (field.min != null && (if (field.minExclusive) value <= field.min else value < field.min)) {
                            errors += "${field.label}低于下限"
                        }
                        if (field.max != null && (if (field.maxExclusive) value >= field.max else value > field.max)) {
                            errors += "${field.label}超过上限"
                        }
                    }
                }
            }
        }
        return errors
    }

    /** Validate the shape of partial readings without requiring completion. */
    fun validateReadingKeys(step: InspectionStep, readings: Map<String, String>): List<String> {
        val allowedFieldIds = step.fields.map { it.id }.toSet()
        val unknownFieldIds = readings.keys.filter { it !in allowedFieldIds }
        return if (unknownFieldIds.isEmpty()) {
            emptyList()
        } else {
            listOf("存在未定义检查字段：${unknownFieldIds.joinToString()}")
        }
    }
}

object TaskGuard {
    /** Identity checks shared by file import and direct Glass3 delivery. */
    fun validateDispatchEnvelope(envelope: ProtocolEnvelope): List<String> {
        val errors = mutableListOf<String>()
        if (envelope.type != "task.dispatch") errors += "消息类型必须是 task.dispatch"
        if (envelope.version != 1) errors += "不支持的 task.dispatch 协议版本：${envelope.version}"
        if (envelope.commandId.isNullOrBlank()) errors += "task.dispatch 缺少 commandId"
        if (envelope.taskId.isNullOrBlank()) errors += "task.dispatch 缺少 taskId"
        if (!envelope.commandId.isNullOrBlank() && !envelope.taskId.isNullOrBlank() &&
            envelope.commandId != ProtocolCommandIds.dispatch(envelope.taskId.trim())) {
            errors += "task.dispatch commandId 与 taskId 不匹配"
        }
        return errors
    }

    fun validateDispatch(task: TaskDispatchPayload): List<String> {
        val errors = mutableListOf<String>()
        if (task.template.id.isBlank() || task.template.name.isBlank() || task.template.version < 1) {
            errors += "任务模板信息不完整"
        }
        if (task.asset.id.isBlank() || task.asset.code.isBlank() || task.asset.name.isBlank()) {
            errors += "巡检设备信息不完整"
        }
        if (task.assignee.id.isBlank() || task.assignee.name.isBlank()) {
            errors += "执行人信息不完整"
        }
        if (task.template.assetIds.isNotEmpty() && task.asset.id !in task.template.assetIds) {
            errors += "巡检设备不属于所选模板"
        }
        task.schedule?.let { schedule ->
            val validDate = runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }.parse(schedule.date)
                schedule.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            }.getOrDefault(false)
            if (!validDate) errors += "巡检日期无效"
            if (schedule.timeSlot.isBlank()) errors += "巡检时段不能为空"
            if (task.template.timeSlots.isNotEmpty() && schedule.timeSlot !in task.template.timeSlots) {
                errors += "巡检时段不属于所选模板"
            }
            if (schedule.shift?.isBlank() == true) errors += "班次不能为空"
        }
        if (task.steps.isEmpty()) {
            errors += "任务没有检查步骤"
        }
        val stepIds = task.steps.map { it.id }
        if (stepIds.any(String::isBlank) || stepIds.size != stepIds.toSet().size) {
            errors += "检查步骤编号为空或重复"
        }
        if (task.steps.size < 3) errors += "检查步骤至少需要设备确认、检查项和提交记录"
        if (task.steps.firstOrNull()?.type != "asset_check") errors += "首个步骤必须确认巡检设备"
        if (task.steps.lastOrNull()?.type != "review_submit") errors += "末个步骤必须提交巡检记录"
        if (task.steps.size >= 3 && task.steps.drop(1).dropLast(1).any { it.type != "photo_check" }) {
            errors += "设备确认和提交记录之间只能是检查项"
        }
        task.steps.forEach { step ->
            if (step.type.isBlank() || step.title.isBlank() || step.instruction.isBlank()) errors += "检查步骤信息不完整：${step.id}"
            if (step.requirements.minPhotos < 0) errors += "照片数量要求无效：${step.id}"
            val fieldIds = step.fields.map { it.id }
            if (fieldIds.any(String::isBlank) || fieldIds.size != fieldIds.toSet().size) {
                errors += "检查字段编号为空或重复：${step.id}"
            }
            step.fields.forEach { field ->
                if (field.kind !in setOf("number", "select", "text")) {
                    errors += "检查字段类型无效：${field.id}"
                }
                if (field.label.isBlank()) errors += "检查字段名称不能为空：${field.id}"
                if (field.kind == "select" && field.options.isEmpty()) errors += "选择字段没有可用选项：${field.id}"
                if (field.min != null && field.max != null && field.min > field.max) errors += "检查字段范围无效：${field.id}"
            }
        }
        return errors
    }

    /**
     * A device may keep only one active task snapshot. A different dispatch
     * cannot replace it until every step in the existing snapshot is
     * committed. The same task id is an idempotent replay and is therefore
     * accepted.
     */
    fun activeDispatchRejection(
        existingTaskId: String?,
        existingStepIds: List<String>?,
        completedStepIds: Set<String>,
        incomingTaskId: String,
    ): String? {
        if (existingTaskId.isNullOrBlank() || existingTaskId == incomingTaskId) return null
        if (existingStepIds == null || existingStepIds.any { it !in completedStepIds }) {
            return "已有未完成任务，请先完成或恢复当前任务"
        }
        return null
    }

    fun validatePhotoMetadata(
        task: TaskDispatchPayload,
        metadata: PhotoUploadPayload,
        completedStepIds: Set<String>,
        existingPhotos: Map<String, PhotoUploadPayload>,
    ): List<String> {
        if (metadata.photoId.isBlank()) {
            return listOf("照片编号无效")
        }
        if (task.steps.none { it.id == metadata.stepId }) {
            return listOf("照片步骤不存在")
        }
        val existing = existingPhotos[metadata.photoId]
        if (existing != null && existing != metadata) {
            return listOf("照片编号对应的元数据不一致")
        }
        if (metadata.stepId in completedStepIds && existing == null) {
            return listOf("已完成步骤不接受新照片")
        }
        return emptyList()
    }

    fun acceptsStepDraft(
        task: TaskDispatchPayload,
        draft: StepDraftPayload,
        completedStepIds: Set<String>,
    ): Boolean {
        if (draft.revision < 1 || task.steps.none { it.id == draft.expectedStepId }) return false
        if (draft.expectedStepId in completedStepIds) return true
        return task.steps.firstOrNull { it.id !in completedStepIds }?.id == draft.expectedStepId
    }

    fun validateStepDraft(
        task: TaskDispatchPayload,
        draft: StepDraftPayload,
        completedStepIds: Set<String>,
    ): List<String> {
        if (!acceptsStepDraft(task, draft, completedStepIds)) {
            return listOf("草稿步骤顺序、版本或步骤编号不匹配")
        }
        val step = task.steps.firstOrNull { it.id == draft.expectedStepId }
            ?: return listOf("草稿步骤不存在")
        return StepValidator.validateReadingKeys(step, draft.readings)
    }

    fun acceptsStepCompletion(
        task: TaskDispatchPayload,
        completion: StepCompletePayload,
        completedStepIds: Set<String>,
    ): Boolean {
        if (completion.revision < 1 || task.steps.none { it.id == completion.expectedStepId }) return false
        if (completion.expectedStepId in completedStepIds) return true
        val expectedIndex = task.steps.indexOfFirst { it.id == completion.expectedStepId }
        if (completion.revision != expectedIndex + 1) return false
        return task.steps.firstOrNull { it.id !in completedStepIds }?.id == completion.expectedStepId
    }

    fun validateStepCompletion(
        task: TaskDispatchPayload,
        completion: StepCompletePayload,
        completedStepIds: Set<String>,
        availablePhotos: Map<String, PhotoUploadPayload>,
        existingCompletion: StepCompletePayload? = null,
    ): List<String> {
        if (!acceptsStepCompletion(task, completion, completedStepIds)) {
            return listOf("步骤顺序、版本或步骤编号不匹配")
        }
        // A completed step is replayed idempotently. Its original result is
        // already committed; do not let a retry mutate it. A conflicting
        // payload with the same step/revision is rejected so a buggy or
        // duplicated sender cannot get a positive ACK for data that was not
        // actually stored.
        if (completion.expectedStepId in completedStepIds) {
            return if (existingCompletion == null || existingCompletion == completion) {
                emptyList()
            } else {
                listOf("重复步骤提交内容不一致")
            }
        }
        val step = task.steps.first { it.id == completion.expectedStepId }
        if (step.type == "asset_check") {
            if (completion.assetCode?.trim() != task.asset.code) {
                return listOf("资产码不匹配，请核对目标设备 ${task.asset.name}（${task.asset.code}）")
            }
            if (!completion.confirmed) return listOf("请确认设备名称和位置一致")
        }
        if (step.type == "review_submit" && !completion.confirmed) {
            return listOf("请确认已核对全部巡检记录")
        }
        if (completion.photoIds.size != completion.photoIds.toSet().size) {
            return listOf("同一照片不能重复关联到本步骤")
        }
        val missingPhotos = completion.photoIds.filterNot { it in availablePhotos }
        if (missingPhotos.isNotEmpty()) return listOf("照片尚未落盘：${missingPhotos.joinToString()}")
        val wrongStepPhotos = completion.photoIds.filter { photoId ->
            availablePhotos[photoId]?.let { photo ->
                photo.photoId != photoId || photo.stepId != completion.expectedStepId
            } ?: false
        }
        if (wrongStepPhotos.isNotEmpty()) {
            return listOf("照片与当前步骤不匹配：${wrongStepPhotos.joinToString()}")
        }
        return StepValidator.validate(
            step,
            completion.result,
            completion.readings,
            completion.note,
            completion.photoIds,
            completion.faultTime,
        )
    }
}

object ProtocolCodec {
    private val gson = Gson()

    fun encode(envelope: ProtocolEnvelope): String = gson.toJson(envelope)

    fun decode(raw: String): ProtocolEnvelope = gson.fromJson(raw, ProtocolEnvelope::class.java)

    fun payload(envelope: ProtocolEnvelope, type: Class<out Any>): Any {
        // Gson allocates Kotlin data classes without calling their constructors, so
        // Kotlin default arguments are not applied to omitted JSON properties.
        // Normalize the optional evidence fields used by retry/legacy messages.
        if (type == StepCompletePayload::class.java || type == StepDraftPayload::class.java) {
            val json = envelope.payload
            val readings = json.getAsJsonObject("readings")?.entrySet()
                ?.associate { it.key to it.value.asString }
                ?: emptyMap()
            val photoIds = json.getAsJsonArray("photoIds")?.map { it.asString } ?: emptyList()
            val expectedStepId = json.get("expectedStepId")?.asString.orEmpty()
            val revision = json.get("revision")?.asInt ?: 1
            val result = json.get("result")?.takeUnless { it.isJsonNull }?.asString
            val note = json.get("note")?.takeUnless { it.isJsonNull }?.asString
            val assetCode = json.get("assetCode")?.takeUnless { it.isJsonNull }?.asString
            val confirmed = json.get("confirmed")?.takeUnless { it.isJsonNull }?.asBoolean ?: false
            val faultTime = json.get("faultTime")?.takeUnless { it.isJsonNull }?.asString
            val completedAt = json.get("completedAt")?.takeUnless { it.isJsonNull }?.asString
            return if (type == StepDraftPayload::class.java) {
                StepDraftPayload(expectedStepId, revision, result, readings, note, photoIds, assetCode, confirmed, faultTime)
            } else {
                StepCompletePayload(expectedStepId, revision, result, readings, note, photoIds, assetCode, confirmed, faultTime, completedAt)
            }
        }
        if (type == PhotoUploadPayload::class.java) {
            val json = envelope.payload
            return PhotoUploadPayload(
                photoId = json.get("photoId")?.asString.orEmpty(),
                stepId = json.get("stepId")?.asString.orEmpty(),
                fileName = json.get("fileName")?.asString.orEmpty(),
                mimeType = json.get("mimeType")?.asString.orEmpty(),
                width = json.get("width")?.asInt ?: 0,
                height = json.get("height")?.asInt ?: 0,
                sha256 = json.get("sha256")?.takeUnless { it.isJsonNull }?.asString,
                sizeBytes = json.get("sizeBytes")?.asLong ?: 0L,
            )
        }
        if (type == TaskCompletePayload::class.java) {
            val accepted = envelope.payload.get("accepted")
            check(accepted != null && accepted.isJsonPrimitive && accepted.asJsonPrimitive.isBoolean) {
                "task.complete payload 缺少 accepted 布尔值"
            }
            return TaskCompletePayload(
                accepted = accepted.asBoolean,
                reason = envelope.payload.get("reason")?.takeUnless { it.isJsonNull }?.asString,
            )
        }
        if (type == DeviceHeartbeatPayload::class.java) {
            val json = envelope.payload
            val appVersion = json.get("appVersion")
            val connection = json.get("connection")
            val battery = json.get("batteryPercent")
            check(appVersion != null && appVersion.isJsonPrimitive && appVersion.asJsonPrimitive.isString && appVersion.asString.isNotBlank()) {
                "device.heartbeat payload 缺少 appVersion"
            }
            check(connection != null && connection.isJsonPrimitive && connection.asJsonPrimitive.isString && connection.asString in setOf("disconnected", "bluetooth", "p2p", "ready")) {
                "device.heartbeat payload connection 无效"
            }
            check(battery != null && battery.isJsonPrimitive && battery.asJsonPrimitive.isNumber) {
                "device.heartbeat payload 缺少 batteryPercent 数值"
            }
            val batteryValue = battery.asDouble
            check(batteryValue.isFinite() && floor(batteryValue) == batteryValue) {
                "device.heartbeat payload batteryPercent 必须是整数"
            }
            val batteryPercent = batteryValue.toInt()
            check(batteryPercent in 0..100) { "device.heartbeat payload batteryPercent 超出范围" }
            return DeviceHeartbeatPayload(appVersion.asString, connection.asString, batteryPercent)
        }
        if (type == TaskDispatchPayload::class.java) {
            return decodeTaskDispatch(envelope.payload)
        }
        return gson.fromJson(envelope.payload, type)
    }

    private fun decodeTaskDispatch(json: JsonObject): TaskDispatchPayload {
        val templateJson = json.getAsJsonObject("template")
        val sourceJson = templateJson?.getAsJsonObject("source")
        val template = InspectionTemplate(
            id = templateJson?.get("id")?.asString.orEmpty(),
            name = templateJson?.get("name")?.asString.orEmpty(),
            version = templateJson?.get("version")?.asInt ?: 1,
            category = templateJson?.get("category")?.asString,
            description = templateJson?.get("description")?.asString,
            source = sourceJson?.let {
                InspectionSource(
                    file = it.get("file")?.asString.orEmpty(),
                    page = it.get("page")?.asInt ?: 0,
                    section = it.get("section")?.asString.orEmpty(),
                )
            },
            assetIds = templateJson?.getAsJsonArray("assetIds")?.map { it.asString } ?: emptyList(),
            timeSlots = templateJson?.getAsJsonArray("timeSlots")?.map { it.asString } ?: emptyList(),
        )
        val assetJson = json.getAsJsonObject("asset") ?: JsonObject()
        val assigneeJson = json.getAsJsonObject("assignee") ?: JsonObject()
        val scheduleJson = json.getAsJsonObject("schedule")
        val steps = json.getAsJsonArray("steps")?.map { rawStep ->
            val stepJson = rawStep.asJsonObject
            val reqJson = stepJson.getAsJsonObject("requirements")
            val fields = stepJson.getAsJsonArray("fields")?.map { rawField ->
                val fieldJson = rawField.asJsonObject
                ReadingField(
                    id = fieldJson.get("id")?.asString.orEmpty(),
                    label = fieldJson.get("label")?.asString.orEmpty(),
                    kind = fieldJson.get("kind")?.asString ?: "text",
                    unit = fieldJson.get("unit")?.takeUnless { it.isJsonNull }?.asString,
                    options = fieldJson.getAsJsonArray("options")?.map { it.asString } ?: emptyList(),
                    expected = fieldJson.get("expected")?.takeUnless { it.isJsonNull }?.asString,
                    min = fieldJson.get("min")?.takeUnless { it.isJsonNull }?.asDouble,
                    max = fieldJson.get("max")?.takeUnless { it.isJsonNull }?.asDouble,
                    minExclusive = fieldJson.get("minExclusive")?.asBoolean ?: false,
                    maxExclusive = fieldJson.get("maxExclusive")?.asBoolean ?: false,
                )
            } ?: emptyList()
            InspectionStep(
                id = stepJson.get("id")?.asString.orEmpty(),
                type = stepJson.get("type")?.asString.orEmpty(),
                title = stepJson.get("title")?.asString.orEmpty(),
                instruction = stepJson.get("instruction")?.asString.orEmpty(),
                requirements = StepRequirements(
                    minPhotos = reqJson?.get("minPhotos")?.asInt ?: 0,
                    resultRequired = reqJson?.get("resultRequired")?.asBoolean ?: false,
                    abnormalNoteRequired = reqJson?.get("abnormalNoteRequired")?.asBoolean ?: false,
                    confirmationRequired = reqJson?.get("confirmationRequired")?.asBoolean ?: true,
                ),
                fields = fields,
            )
        } ?: emptyList()
        return TaskDispatchPayload(
            template = template,
            asset = InspectionAsset(
                id = assetJson.get("id")?.asString.orEmpty(),
                code = assetJson.get("code")?.asString.orEmpty(),
                name = assetJson.get("name")?.asString.orEmpty(),
                location = assetJson.get("location")?.asString.orEmpty(),
            ),
            assignee = InspectionAssignee(
                id = assigneeJson.get("id")?.asString.orEmpty(),
                name = assigneeJson.get("name")?.asString.orEmpty(),
            ),
            steps = steps,
            schedule = scheduleJson?.let {
                InspectionSchedule(
                    date = it.get("date")?.asString.orEmpty(),
                    timeSlot = it.get("timeSlot")?.asString.orEmpty(),
                    shift = it.get("shift")?.takeUnless { value -> value.isJsonNull }?.asString,
                )
            },
        )
    }

    fun json(value: Any): JsonObject = gson.toJsonTree(value).asJsonObject
}
