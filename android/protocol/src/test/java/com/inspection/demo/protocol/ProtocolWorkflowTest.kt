package com.inspection.demo.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A device-free contract test for the phone ↔ Glass3 message sequence. */
class ProtocolWorkflowTest {
    @Test
    fun realisticFiveStepInspectionEnforcesOrderEvidenceAndFinalReview() {
        val steps = listOf(
            InspectionStep(
                "asset",
                "asset_check",
                "确认设备",
                "核对资产码和位置",
                StepRequirements(confirmationRequired = true),
            ),
            InspectionStep(
                "pressure",
                "photo_check",
                "记录压力",
                "读取压力并拍照",
                StepRequirements(minPhotos = 1, resultRequired = true, abnormalNoteRequired = true),
                fields = listOf(ReadingField("pressure-value", "压力", "number", unit = "kPa", max = 958.0, maxExclusive = true)),
            ),
            InspectionStep(
                "mode",
                "photo_check",
                "确认运行模式",
                "选择运行模式并拍照",
                StepRequirements(minPhotos = 1, resultRequired = true),
                fields = listOf(ReadingField("mode-value", "模式", "select", options = listOf("自动", "手动"))),
            ),
            InspectionStep(
                "appearance",
                "photo_check",
                "检查外观",
                "记录外观状态",
                StepRequirements(resultRequired = true),
            ),
            InspectionStep(
                "review",
                "review_submit",
                "提交记录",
                "核对全部巡检记录后提交",
                StepRequirements(confirmationRequired = true),
            ),
        )
        val task = TaskDispatchPayload(
            template = InspectionTemplate(
                id = "building4-realistic",
                name = "四号楼设备巡检",
                version = 1,
                assetIds = listOf("asset-1"),
                timeSlots = listOf("10:00"),
            ),
            asset = InspectionAsset("asset-1", "B4-A-001", "冷水机组 1", "四号楼机房"),
            assignee = InspectionAssignee("worker-1", "张工"),
            steps = steps,
            schedule = InspectionSchedule("2026-11-06", "10:00", "常规班"),
        )
        assertTrue(TaskGuard.validateDispatch(task).isEmpty())

        val skipped = StepCompletePayload("pressure", 2, result = "normal", readings = mapOf("pressure-value" to "950"))
        assertTrue(TaskGuard.validateStepCompletion(task, skipped, emptySet(), emptyMap()).isNotEmpty())

        val assetCompletion = StepCompletePayload("asset", 1, assetCode = "B4-A-001", confirmed = true)
        assertTrue(TaskGuard.validateStepCompletion(task, assetCompletion, emptySet(), emptyMap()).isEmpty())
        val completed = setOf("asset")

        val photo = PhotoUploadPayload("photo-pressure", "pressure", "photo-pressure.jpg", "image/jpeg", 1280, 720)
        assertTrue(TaskGuard.validatePhotoMetadata(task, photo, completed, emptyMap()).isEmpty())
        val available = mapOf(photo.photoId to photo)
        val pressureCompletion = StepCompletePayload(
            "pressure",
            2,
            result = "abnormal",
            readings = mapOf("pressure-value" to "950"),
            note = "接近上限，待复核",
            photoIds = listOf(photo.photoId),
        )
        assertTrue(TaskGuard.validateStepCompletion(task, pressureCompletion, completed, available).isEmpty())

        val misboundPhoto = photo.copy(photoId = "photo-misbound", stepId = "pressure")
        val modeCompletion = StepCompletePayload("mode", 3, result = "normal", readings = mapOf("mode-value" to "自动"), photoIds = listOf(misboundPhoto.photoId))
        assertTrue(TaskGuard.validateStepCompletion(task, modeCompletion, completed + "pressure", mapOf(misboundPhoto.photoId to misboundPhoto)).isNotEmpty())
        val finalReview = StepCompletePayload("review", 5, confirmed = true)
        assertTrue(TaskGuard.validateStepCompletion(task, finalReview, completed + setOf("pressure", "mode", "appearance"), emptyMap()).isEmpty())
    }

    @Test
    fun dispatchPhotoStepAndTaskCompletionRemainIdempotent() {
        val step = InspectionStep(
            id = "pressure",
            type = "photo_check",
            title = "冷凝压力",
            instruction = "读取仪表并拍照",
            requirements = StepRequirements(minPhotos = 1, resultRequired = true, abnormalNoteRequired = true),
            fields = listOf(ReadingField("value-1", "冷凝压力", "number", unit = "kPa", max = 958.0, maxExclusive = true)),
        )
        val task = TaskDispatchPayload(
            template = InspectionTemplate("chiller", "冷水机组巡检", 1),
            asset = InspectionAsset("chiller-1", "B4-CHILLER-1", "1#冷机", "冷源系统"),
            assignee = InspectionAssignee("u1", "张工"),
            steps = listOf(step),
        )
        val taskId = "task-workflow-1"
        val dispatch = messageEnvelope("task.dispatch", taskId, "dispatch-1", ProtocolCodec.json(task))

        val glass = FakeGlass()
        val phone = FakePhone(task)
        val taskAck = glass.receiveDispatch(dispatch)
        assertEquals("task.ack", taskAck.type)
        assertEquals(taskId, taskAck.taskId)
        assertEquals(ProtocolCommandIds.taskAck(taskId, accepted = true), taskAck.commandId)

        val photoId = "photo-1"
        val photoUpload = messageEnvelope(
            "photo.upload",
            taskId,
            "photo-$photoId",
            ProtocolCodec.json(PhotoUploadPayload(photoId, step.id, "photo-1.jpg", "image/jpeg", 1280, 720)),
        )
        val photoAck = phone.receive(photoUpload)
        assertEquals("photo.ack", photoAck.type)
        assertTrue(phone.photoIds.contains(photoId))

        val readings = mapOf("value-1" to "950")
        assertTrue(StepValidator.validate(step, "abnormal", readings, "接近上限，待复核", listOf(photoId)).isEmpty())
        val completion = messageEnvelope(
            "step.complete",
            taskId,
            "complete-$taskId-${step.id}-1",
            ProtocolCodec.json(StepCompletePayload(step.id, 1, "abnormal", readings, "接近上限，待复核", listOf(photoId))),
        )
        val stepAck = phone.receive(completion)
        assertEquals("step.ack", stepAck.type)
        assertEquals(setOf(step.id), phone.completedStepIds)

        val duplicateAck = phone.receive(completion)
        assertEquals(stepAck.commandId, duplicateAck.commandId)
        assertEquals(1, phone.completedStepIds.size)

        val taskComplete = phone.completeTaskIfReady(taskId)
        assertEquals("task.complete", taskComplete.type)
        assertEquals("complete-task-$taskId", taskComplete.commandId)
    }

    private class FakeGlass {
        private var task: TaskDispatchPayload? = null

        fun receiveDispatch(envelope: ProtocolEnvelope): ProtocolEnvelope {
            task = ProtocolCodec.payload(envelope, TaskDispatchPayload::class.java) as TaskDispatchPayload
            return messageEnvelope(
                "task.ack",
                envelope.taskId.orEmpty(),
                ProtocolCommandIds.taskAck(envelope.taskId.orEmpty(), accepted = true),
                ProtocolCodec.json(mapOf("accepted" to true)),
            )
        }
    }

    private class FakePhone(private val task: TaskDispatchPayload) {
        val completedStepIds = linkedSetOf<String>()
        val photoIds = linkedSetOf<String>()

        fun receive(envelope: ProtocolEnvelope): ProtocolEnvelope {
            return when (envelope.type) {
                "photo.upload" -> {
                    val photo = ProtocolCodec.payload(envelope, PhotoUploadPayload::class.java) as PhotoUploadPayload
                    photoIds += photo.photoId
                    messageEnvelope("photo.ack", envelope.taskId.orEmpty(), ProtocolCommandIds.photoAck(photo.photoId, accepted = true), ProtocolCodec.json(mapOf("accepted" to true, "photoId" to photo.photoId)))
                }
                "step.complete" -> {
                    val completion = ProtocolCodec.payload(envelope, StepCompletePayload::class.java) as StepCompletePayload
                    completedStepIds += completion.expectedStepId
                    messageEnvelope(
                        "step.ack",
                        envelope.taskId.orEmpty(),
                        ProtocolCommandIds.stepAck(envelope.taskId.orEmpty(), completion.expectedStepId, completion.revision, accepted = true),
                        ProtocolCodec.json(mapOf("accepted" to true, "expectedStepId" to completion.expectedStepId)),
                    )
                }
                else -> error("unsupported message ${envelope.type}")
            }
        }

        fun completeTaskIfReady(taskId: String): ProtocolEnvelope {
            check(completedStepIds.containsAll(task.steps.map { it.id }))
            return messageEnvelope("task.complete", taskId, "complete-task-$taskId", ProtocolCodec.json(mapOf("accepted" to true)))
        }
    }
}

private fun messageEnvelope(type: String, taskId: String, commandId: String, payload: com.google.gson.JsonObject) =
    ProtocolEnvelope(type, commandId = commandId, taskId = taskId, sentAt = "2026-11-05T10:00:00+08:00", payload = payload)
