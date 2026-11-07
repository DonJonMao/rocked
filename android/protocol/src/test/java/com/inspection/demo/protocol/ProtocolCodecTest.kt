package com.inspection.demo.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun replayLedgerRecognizesCommandRetriesAcrossNewMessageIds() {
        val ledger = ProtocolReplayLedger(maxKeys = 8)
        val first = ProtocolEnvelope(
            type = "step.ack",
            messageId = "message-1",
            commandId = "ack-step-1",
            taskId = "task-1",
            sentAt = "2026-11-06T10:00:00+08:00",
        )
        val retryWithNewMessageId = first.copy(messageId = "message-2")

        assertTrue(ledger.remember(first))
        assertTrue(!ledger.remember(retryWithNewMessageId))
        assertEquals(2, ledger.snapshot().size)
    }

    @Test
    fun replayLedgerRecognizesExactMessageRetriesWithoutCommandId() {
        val ledger = ProtocolReplayLedger(maxKeys = 4)
        val envelope = ProtocolEnvelope(
            type = "task.start",
            messageId = "message-1",
            commandId = null,
            taskId = "task-1",
            sentAt = "2026-11-06T10:00:00+08:00",
        )

        assertTrue(ledger.remember(envelope))
        assertTrue(!ledger.remember(envelope))
        assertEquals(setOf("message|message-1"), ledger.snapshot())
    }

    @Test
    fun acknowledgementOutcomeChangesReplayIdentity() {
        val ledger = ProtocolReplayLedger(maxKeys = 8)
        val rejected = ProtocolEnvelope(
            type = "step.ack",
            commandId = ProtocolCommandIds.stepAck("task-1", "step-1", 1, accepted = false),
            taskId = "task-1",
            sentAt = "2026-11-06T10:00:00+08:00",
        )
        val accepted = rejected.copy(
            commandId = ProtocolCommandIds.stepAck("task-1", "step-1", 1, accepted = true),
            messageId = "message-2",
        )

        assertTrue(ledger.remember(rejected))
        assertTrue(ledger.remember(accepted))
        assertTrue(!ledger.remember(accepted.copy(messageId = "message-3")))
    }

    @Test
    fun replayLedgerEvictsOldestKeysAtBound() {
        val ledger = ProtocolReplayLedger(maxKeys = 2)
        assertTrue(ledger.remember(ProtocolEnvelope("task.start", messageId = "m1", sentAt = "now")))
        assertTrue(ledger.remember(ProtocolEnvelope("task.start", messageId = "m2", sentAt = "now")))
        assertTrue(ledger.remember(ProtocolEnvelope("task.start", messageId = "m3", sentAt = "now")))

        assertEquals(setOf("message|m2", "message|m3"), ledger.snapshot())
    }

    @Test
    fun dispatchRoundTripKeepsTemplateSnapshotAndTaskIdentity() {
        val payload = TaskDispatchPayload(
            InspectionTemplate("t1", "机柜巡检", 2),
            InspectionAsset("a1", "ASSET-A01", "机柜 A01", "示例机房"),
            InspectionAssignee("u1", "张工"),
            listOf(InspectionStep("s1", "asset_check", "确认设备", "核对资产码", StepRequirements())),
            schedule = InspectionSchedule("2026-11-05", "10:00", "常规班"),
        )
        val raw = ProtocolCodec.encode(
            ProtocolEnvelope("task.dispatch", commandId = "cmd-1", taskId = "task-1", sentAt = "2026-11-05T09:30:00+08:00", payload = ProtocolCodec.json(payload)),
        )
        val decoded = ProtocolCodec.decode(raw)
        val restored = ProtocolCodec.payload(decoded, TaskDispatchPayload::class.java) as TaskDispatchPayload
        assertEquals("task-1", decoded.taskId)
        assertEquals("t1", restored.template.id)
        assertEquals("ASSET-A01", restored.asset.code)
        assertEquals("s1", restored.steps.single().id)
        assertEquals("10:00", restored.schedule!!.timeSlot)
    }

    @Test
    fun dispatchIdentityGateIsSharedByFileAndGlassReceivers() {
        val valid = ProtocolEnvelope(
            type = "task.dispatch",
            version = 1,
            commandId = ProtocolCommandIds.dispatch("task-1"),
            taskId = "task-1",
            sentAt = "2026-11-05T09:30:00+08:00",
        )
        assertTrue(TaskGuard.validateDispatchEnvelope(valid).isEmpty())

        val mismatched = valid.copy(commandId = "cmd-dispatch-other-task")
        assertTrue(TaskGuard.validateDispatchEnvelope(mismatched).any { it.contains("不匹配") })

        val invalid = valid.copy(version = 2, commandId = null, taskId = null)
        val errors = TaskGuard.validateDispatchEnvelope(invalid)
        assertTrue(errors.any { it.contains("协议版本") })
        assertTrue(errors.any { it.contains("commandId") })
        assertTrue(errors.any { it.contains("taskId") })
    }

    @Test
    fun commandIdFactoryKeepsControlMessageIdentitiesStable() {
        assertEquals("cmd-dispatch-task-1", ProtocolCommandIds.dispatch("task-1"))
        assertEquals("ack-task-1-accepted", ProtocolCommandIds.taskAck("task-1", accepted = true))
        assertEquals("ack-task-1-rejected", ProtocolCommandIds.taskAck("task-1", accepted = false))
        assertEquals("start-task-1", ProtocolCommandIds.taskStart("task-1"))
        assertEquals("pause-task-1", ProtocolCommandIds.taskPause("task-1"))
        assertEquals("resume-task-1", ProtocolCommandIds.taskResume("task-1"))
        assertEquals("complete-task-1-step-2-3", ProtocolCommandIds.stepComplete("task-1", "step-2", 3))
        assertEquals("photo-photo-1", ProtocolCommandIds.photoUpload("photo-1"))
        assertEquals("complete-task-task-1", ProtocolCommandIds.taskComplete("task-1"))
    }

    @Test
    fun stepCompleteCarriesIdempotencyAndExpectedStepFields() {
        val payload = StepCompletePayload(
            "s2",
            revision = 4,
            result = "abnormal",
            note = "待复核",
            completedAt = "2026-11-05T09:35:30+08:00",
        )
        val envelope = ProtocolEnvelope("step.complete", commandId = "cmd-2", taskId = "task-1", sentAt = "2026-11-05T09:35:00+08:00", payload = ProtocolCodec.json(payload))
        val restored = ProtocolCodec.payload(ProtocolCodec.decode(ProtocolCodec.encode(envelope)), StepCompletePayload::class.java) as StepCompletePayload
        assertEquals("s2", restored.expectedStepId)
        assertEquals(4, restored.revision)
        assertEquals("abnormal", restored.result)
        assertTrue(restored.note!!.contains("复核"))
        assertEquals("2026-11-05T09:35:30+08:00", restored.completedAt)
    }

    @Test
    fun photoUploadRoundTripKeepsStreamCorrelationMetadata() {
        val payload = PhotoUploadPayload(
            photoId = "photo-0001",
            stepId = "chiller-pressure",
            fileName = "photo-0001.jpg",
            mimeType = "image/jpeg",
            width = 1280,
            height = 720,
            sha256 = "abc123",
        )
        val envelope = ProtocolEnvelope(
            "photo.upload",
            commandId = "photo-cmd-1",
            taskId = "task-1",
            sentAt = "2026-11-05T09:36:00+08:00",
            payload = ProtocolCodec.json(payload),
        )

        val restored = ProtocolCodec.payload(
            ProtocolCodec.decode(ProtocolCodec.encode(envelope)),
            PhotoUploadPayload::class.java,
        ) as PhotoUploadPayload

        assertEquals("photo-0001", restored.photoId)
        assertEquals("chiller-pressure", restored.stepId)
        assertEquals("image/jpeg", restored.mimeType)
        assertEquals(1280, restored.width)
        assertEquals(720, restored.height)
        assertEquals("abc123", restored.sha256)
    }

    @Test
    fun photoAckRoundTripPreservesTerminalRejectionReason() {
        val envelope = ProtocolEnvelope(
            type = "photo.ack",
            commandId = "ack-photo-1",
            taskId = "task-1",
            sentAt = "2026-11-05T09:36:30+08:00",
            payload = ProtocolCodec.json(PhotoAckPayload("photo-1", accepted = false, reason = "照片无法解码")),
        )

        val restored = ProtocolCodec.payload(
            ProtocolCodec.decode(ProtocolCodec.encode(envelope)),
            PhotoAckPayload::class.java,
        ) as PhotoAckPayload

        assertEquals("photo-1", restored.photoId)
        assertTrue(!restored.accepted)
        assertEquals("照片无法解码", restored.reason)
    }

    @Test
    fun taskCompleteRoundTripPreservesAcceptanceAndStableCommand() {
        val taskId = "task-complete-1"
        val envelope = ProtocolEnvelope(
            type = "task.complete",
            commandId = ProtocolCommandIds.taskComplete(taskId),
            taskId = taskId,
            sentAt = "2026-11-06T09:40:00+08:00",
            payload = ProtocolCodec.json(TaskCompletePayload(accepted = true)),
        )

        val decoded = ProtocolCodec.decode(ProtocolCodec.encode(envelope))
        val restored = ProtocolCodec.payload(decoded, TaskCompletePayload::class.java) as TaskCompletePayload

        assertEquals(taskId, decoded.taskId)
        assertEquals(ProtocolCommandIds.taskComplete(taskId), decoded.commandId)
        assertTrue(restored.accepted)
    }

    @Test
    fun taskCompleteRejectsMissingOrNonBooleanAcceptance() {
        val base = ProtocolEnvelope(
            type = "task.complete",
            commandId = ProtocolCommandIds.taskComplete("task-1"),
            taskId = "task-1",
            sentAt = "2026-11-06T09:40:00+08:00",
        )

        val missing = runCatching {
            ProtocolCodec.payload(base, TaskCompletePayload::class.java)
        }
        val nonBoolean = runCatching {
            ProtocolCodec.payload(base.copy(payload = ProtocolCodec.json(mapOf("accepted" to "true"))), TaskCompletePayload::class.java)
        }

        assertTrue(missing.isFailure)
        assertTrue(nonBoolean.isFailure)
    }

    @Test
    fun heartbeatRoundTripRejectsInvalidConnectionAndBattery() {
        val valid = ProtocolEnvelope(
            type = "device.heartbeat",
            deviceId = "glass-1",
            sentAt = "2026-11-06T09:41:00+08:00",
            payload = ProtocolCodec.json(DeviceHeartbeatPayload("0.1.0", "ready", 87)),
        )
        val restored = ProtocolCodec.payload(
            ProtocolCodec.decode(ProtocolCodec.encode(valid)),
            DeviceHeartbeatPayload::class.java,
        ) as DeviceHeartbeatPayload
        assertEquals("ready", restored.connection)
        assertEquals(87, restored.batteryPercent)

        val invalidConnection = valid.copy(payload = ProtocolCodec.json(DeviceHeartbeatPayload("0.1.0", "online", 87)))
        val invalidBattery = valid.copy(payload = ProtocolCodec.json(DeviceHeartbeatPayload("0.1.0", "ready", 101)))
        val fractionalBattery = valid.copy(
            payload = com.google.gson.JsonObject().apply {
                addProperty("appVersion", "0.1.0")
                addProperty("connection", "ready")
                addProperty("batteryPercent", 87.5)
            },
        )
        val numericVersion = valid.copy(
            payload = com.google.gson.JsonObject().apply {
                addProperty("appVersion", 1)
                addProperty("connection", "ready")
                addProperty("batteryPercent", 87)
            },
        )
        assertTrue(runCatching { ProtocolCodec.payload(invalidConnection, DeviceHeartbeatPayload::class.java) }.isFailure)
        assertTrue(runCatching { ProtocolCodec.payload(invalidBattery, DeviceHeartbeatPayload::class.java) }.isFailure)
        assertTrue(runCatching { ProtocolCodec.payload(fractionalBattery, DeviceHeartbeatPayload::class.java) }.isFailure)
        assertTrue(runCatching { ProtocolCodec.payload(numericVersion, DeviceHeartbeatPayload::class.java) }.isFailure)
    }

    @Test
    fun photoTransferMatcherAcceptsOriginalNameOrExactPhotoIdOnly() {
        val payload = PhotoUploadPayload(
            photoId = "photo-0001",
            stepId = "s1",
            fileName = "photo-0001.webp",
            mimeType = "image/webp",
            width = 10,
            height = 10,
        )

        assertTrue(PhotoTransferMatcher.matches(payload, "/sdk/cache/photo-0001.webp"))
        assertTrue(PhotoTransferMatcher.matches(payload, "photo-0001.png"))
        assertTrue(!PhotoTransferMatcher.matches(payload, "photo-0002.webp"))
        assertTrue(!PhotoTransferMatcher.matches(payload, "photo-0001-copy.webp"))
        assertTrue(!PhotoTransferMatcher.matches(payload, ""))
    }

    @Test
    fun photoIntegrityRejectsWrongHashAndAcceptsValidJpegMetadata() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val payload = PhotoUploadPayload(
            photoId = "photo-1",
            stepId = "s1",
            fileName = "photo-1.jpg",
            mimeType = "image/jpeg",
            width = 2,
            height = 2,
            sha256 = PhotoIntegrity.sha256(bytes),
            sizeBytes = bytes.size.toLong(),
        )
        assertTrue(PhotoIntegrity.validate(payload, bytes).isEmpty())
        assertTrue(PhotoIntegrity.validate(payload.copy(sha256 = "bad"), bytes).any { it.contains("SHA") })
        assertTrue(PhotoIntegrity.validate(payload.copy(mimeType = "text/plain"), bytes).any { it.contains("格式") })
    }

    @Test
    fun stepCompleteDefaultsEvidenceCollectionsForMinimalMessage() {
        val raw = """
            {"type":"step.complete","version":1,"messageId":"m1","commandId":"c1","taskId":"t1","sentAt":"2026-11-05T09:37:00+08:00","payload":{"expectedStepId":"s1","revision":1}}
        """.trimIndent()

        val restored = ProtocolCodec.payload(ProtocolCodec.decode(raw), StepCompletePayload::class.java) as StepCompletePayload

        assertEquals("s1", restored.expectedStepId)
        assertTrue(restored.readings.isEmpty())
        assertTrue(restored.photoIds.isEmpty())
    }

    @Test
    fun stepDraftRoundTripKeepsPartialValuesWithoutCompletionSemantics() {
        val payload = StepDraftPayload(
            expectedStepId = "pressure",
            revision = 1,
            readings = mapOf("value-1" to "950"),
            note = "现场待复核",
        )
        val envelope = ProtocolEnvelope(
            type = "step.draft",
            commandId = "draft-1",
            taskId = "task-1",
            sentAt = "2026-11-05T09:37:00+08:00",
            payload = ProtocolCodec.json(payload),
        )

        val restored = ProtocolCodec.payload(
            ProtocolCodec.decode(ProtocolCodec.encode(envelope)),
            StepDraftPayload::class.java,
        ) as StepDraftPayload

        assertEquals("pressure", restored.expectedStepId)
        assertEquals("950", restored.readings["value-1"])
        assertEquals("现场待复核", restored.note)
    }

    @Test
    fun dispatchDecodesBuilding4FieldsAndSourceWithoutNullCollections() {
        val raw = """
            {
              "type":"task.dispatch","version":1,"messageId":"m1","commandId":"c1","taskId":"t1","sentAt":"2026-11-05T09:38:00+08:00",
              "payload":{
                "template":{"id":"chiller","name":"冷水机组巡检","version":1,"category":"暖通","source":{"file":"4号楼暖通巡检表2024.6.1.pdf","page":7,"section":"冷水机房"},"timeSlots":["10:00"]},
                "asset":{"id":"chiller-1","code":"B4-CHILLER-1","name":"1#冷机","location":"4号楼地下机房"},
                "assignee":{"id":"u1","name":"张工"},
                "steps":[{"id":"pressure","type":"photo_check","title":"冷凝压力","instruction":"读取仪表","requirements":{"resultRequired":true,"abnormalNoteRequired":true},"fields":[{"id":"value-1","label":"冷凝压力","kind":"number","unit":"kPa","max":958,"maxExclusive":true}]}]
              }
            }
        """.trimIndent()

        val decoded = ProtocolCodec.decode(raw)
        val payload = ProtocolCodec.payload(decoded, TaskDispatchPayload::class.java) as TaskDispatchPayload
        val field = payload.steps.single().fields.single()

        assertEquals("4号楼暖通巡检表2024.6.1.pdf", payload.template.source!!.file)
        assertEquals("kPa", field.unit)
        assertEquals(958.0, field.max!!, 0.0)
        assertTrue(payload.steps.single().requirements.resultRequired)
    }

    @Test
    fun stepValidatorRejectsMissingEvidenceAndExclusiveBounds() {
        val step = InspectionStep(
            id = "pressure",
            type = "photo_check",
            title = "冷凝压力",
            instruction = "读取仪表",
            requirements = StepRequirements(minPhotos = 1, resultRequired = true, abnormalNoteRequired = true),
            fields = listOf(ReadingField("value-1", "冷凝压力", "number", unit = "kPa", max = 958.0, maxExclusive = true)),
        )

        val errors = StepValidator.validate(step, "abnormal", mapOf("value-1" to "958"), null, emptyList())
        assertEquals(3, errors.size)
        assertTrue(errors.any { it.contains("异常") })
        assertTrue(errors.any { it.contains("照片") })
        assertTrue(errors.any { it.contains("超过") })
    }

    @Test
    fun stepValidatorRequiresReasonForInactiveStates() {
        val step = InspectionStep(
            id = "mode",
            type = "photo_check",
            title = "运行状态",
            instruction = "记录现场状态",
            requirements = StepRequirements(resultRequired = true, abnormalNoteRequired = true),
        )

        val errors = StepValidator.validate(step, "standby", emptyMap(), "", emptyList())

        assertEquals(listOf("停机/待机/不涉及需要填写状态原因"), errors)
    }

    @Test
    fun inactiveStateDoesNotRequireReadingButStillRequiresReason() {
        val step = InspectionStep(
            id = "temperature",
            type = "reading",
            title = "温度",
            instruction = "记录温度",
            requirements = StepRequirements(resultRequired = true, abnormalNoteRequired = true),
            fields = listOf(ReadingField("temp", "温度", "number", unit = "℃")),
        )

        assertTrue(StepValidator.validate(step, "standby", emptyMap(), "设备待机", emptyList()).isEmpty())
    }

    @Test
    fun stepValidatorRejectsReadingsThatAreNotDeclaredByTheTask() {
        val step = InspectionStep(
            id = "temperature",
            type = "reading",
            title = "温度",
            instruction = "记录温度",
            requirements = StepRequirements(resultRequired = true),
            fields = listOf(ReadingField("temp", "温度", "number", unit = "℃")),
        )
        val errors = StepValidator.validate(
            step,
            "normal",
            readings = mapOf("temp" to "20", "forged-field" to "999"),
            note = null,
            photoIds = emptyList(),
        )
        assertTrue(errors.any { it.contains("未定义检查字段") })
    }

    @Test
    fun draftValidatorRejectsUnknownReadingsBeforeCompletion() {
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(
                InspectionStep("s1", "reading", "读数", "记录", StepRequirements(), listOf(ReadingField("temp", "温度", "number"))),
                InspectionStep("s2", "review_submit", "提交", "核对", StepRequirements()),
            ),
        )
        val draft = StepDraftPayload("s1", 1, readings = mapOf("forged" to "1"))
        assertTrue(TaskGuard.validateStepDraft(task, draft, emptySet()).any { it.contains("未定义检查字段") })
    }

    @Test
    fun stepValidatorChecksOptionalFaultTimeFormat() {
        val step = InspectionStep("s1", "reading", "异常", "记录", StepRequirements())
        assertTrue(StepValidator.validate(step, "abnormal", emptyMap(), null, emptyList(), "09:30").isEmpty())
        assertTrue(StepValidator.validate(step, "abnormal", emptyMap(), null, emptyList(), "9:30").any { it.contains("HH:mm") })
    }

    @Test
    fun taskGuardRejectsUnknownAndOutOfOrderStepButAllowsDuplicate() {
        val first = InspectionStep("s1", "asset_check", "设备", "确认设备", StepRequirements())
        val second = InspectionStep("s2", "review_submit", "提交", "提交记录", StepRequirements())
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(first, second),
        )
        val outOfOrder = StepCompletePayload("s2", 1)
        val duplicate = StepCompletePayload("s1", 2)
        val unknown = StepCompletePayload("missing", 1)

        assertTrue(!TaskGuard.acceptsStepCompletion(task, outOfOrder, emptySet()))
        assertTrue(TaskGuard.acceptsStepCompletion(task, duplicate, setOf("s1")))
        assertTrue(!TaskGuard.acceptsStepCompletion(task, unknown, emptySet()))
    }

    @Test
    fun taskGuardDoesNotReplaceAnUnfinishedActiveDispatch() {
        val steps = listOf(
            InspectionStep("s1", "reading", "第一步", "记录", StepRequirements()),
            InspectionStep("s2", "reading", "第二步", "记录", StepRequirements()),
        )
        val ids = steps.map { it.id }
        assertEquals(
            "已有未完成任务，请先完成或恢复当前任务",
            TaskGuard.activeDispatchRejection("old-task", ids, setOf("s1"), "new-task"),
        )
        assertEquals(null, TaskGuard.activeDispatchRejection("old-task", ids, setOf("s1", "s2"), "new-task"))
        assertEquals(null, TaskGuard.activeDispatchRejection("old-task", ids, setOf("s1"), "old-task"))
    }

    @Test
    fun taskGuardRejectsMalformedDispatchBeforePersistence() {
        val malformed = TaskDispatchPayload(
            template = InspectionTemplate("", "", 0),
            asset = InspectionAsset("a1", "", "设备", "位置"),
            assignee = InspectionAssignee("u1", "张工"),
            steps = listOf(
                InspectionStep(
                    id = "step-1",
                    type = "reading",
                    title = "读数",
                    instruction = "记录",
                    requirements = StepRequirements(minPhotos = -1),
                    fields = listOf(ReadingField("field-1", "字段", "unsupported")),
                ),
                InspectionStep("step-1", "reading", "重复", "记录", StepRequirements()),
            ),
        )
        val errors = TaskGuard.validateDispatch(malformed)
        assertTrue(errors.any { it.contains("模板") })
        assertTrue(errors.any { it.contains("设备") })
        assertTrue(errors.any { it.contains("重复") })
        assertTrue(errors.any { it.contains("字段类型") })
    }

    @Test
    fun taskGuardValidatesDispatchShapeAndScheduleBeforePersistence() {
        val asset = InspectionStep("asset", "asset_check", "确认设备", "核对资产码", StepRequirements())
        val check = InspectionStep("check", "photo_check", "检查状态", "填写结果", StepRequirements(resultRequired = true))
        val review = InspectionStep("review", "review_submit", "提交记录", "核对并提交", StepRequirements())
        val template = InspectionTemplate(
            id = "t1",
            name = "测试模板",
            version = 1,
            assetIds = listOf("a1"),
            timeSlots = listOf("10:00"),
        )
        val valid = TaskDispatchPayload(
            template,
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(asset, check, review),
            InspectionSchedule("2026-11-06", "10:00", "白班"),
        )
        assertTrue(TaskGuard.validateDispatch(valid).isEmpty())

        val invalid = valid.copy(
            asset = valid.asset.copy(id = "other"),
            steps = listOf(check, asset, review),
            schedule = InspectionSchedule("2026-02-30", "21:00", ""),
        )
        val errors = TaskGuard.validateDispatch(invalid)
        assertTrue(errors.any { it.contains("不属于") })
        assertTrue(errors.any { it.contains("日期") })
        assertTrue(errors.any { it.contains("时段") })
        assertTrue(errors.any { it.contains("班次") })
        assertTrue(errors.any { it.contains("首个步骤") })
        assertTrue(errors.any { it.contains("之间只能") })
    }

    @Test
    fun taskGuardAcceptsOnlyCurrentStepDraftsAndAllowsLateDuplicate() {
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(
                InspectionStep("s1", "reading", "第一步", "记录", StepRequirements()),
                InspectionStep("s2", "reading", "第二步", "记录", StepRequirements()),
            ),
        )
        assertTrue(TaskGuard.acceptsStepDraft(task, StepDraftPayload("s1", 1), emptySet()))
        assertTrue(!TaskGuard.acceptsStepDraft(task, StepDraftPayload("s2", 1), emptySet()))
        assertTrue(TaskGuard.acceptsStepDraft(task, StepDraftPayload("s1", 1), setOf("s1")))
    }

    @Test
    fun taskGuardRejectsUnstoredPhotoAndInvalidReadingBeforeCommit() {
        val step = InspectionStep(
            id = "s1",
            type = "reading",
            title = "压力",
            instruction = "记录压力",
            requirements = StepRequirements(minPhotos = 1, resultRequired = true, abnormalNoteRequired = true),
            fields = listOf(ReadingField("pressure", "压力", "number", unit = "kPa", max = 958.0, maxExclusive = true)),
        )
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(step),
        )
        val completion = StepCompletePayload("s1", 1, "normal", mapOf("pressure" to "950"), photoIds = listOf("p1"))
        assertTrue(TaskGuard.validateStepCompletion(task, completion, emptySet(), emptyMap()).any { it.contains("尚未落盘") })
        val photo = PhotoUploadPayload("p1", "s1", "p1.jpg", "image/jpeg", 100, 80)
        assertTrue(TaskGuard.validateStepCompletion(task, completion, emptySet(), mapOf("p1" to photo)).isEmpty())
    }

    @Test
    fun taskGuardRequiresAssetConfirmationAndFinalReviewConfirmation() {
        val asset = InspectionStep("asset", "asset_check", "确认设备", "核对资产码", StepRequirements())
        val review = InspectionStep("review", "review_submit", "提交记录", "核对记录", StepRequirements())
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "ASSET-01", "设备 01", "四号楼"),
            InspectionAssignee("u1", "张工"),
            listOf(asset, review),
        )
        val wrongAsset = StepCompletePayload("asset", 1, assetCode = "WRONG", confirmed = true)
        assertTrue(TaskGuard.validateStepCompletion(task, wrongAsset, emptySet(), emptyMap()).any { it.contains("资产码") })

        val acceptedAsset = StepCompletePayload("asset", 1, assetCode = "ASSET-01", confirmed = true)
        assertTrue(TaskGuard.validateStepCompletion(task, acceptedAsset, emptySet(), emptyMap()).isEmpty())

        val unconfirmedReview = StepCompletePayload("review", 2, confirmed = false)
        assertTrue(TaskGuard.validateStepCompletion(task, unconfirmedReview, setOf("asset"), emptyMap()).any { it.contains("核对") })
    }

    @Test
    fun taskGuardRejectsDuplicateAndWrongStepPhotos() {
        val first = InspectionStep("s1", "photo_check", "第一步", "拍照", StepRequirements(minPhotos = 1))
        val second = InspectionStep("s2", "photo_check", "第二步", "拍照", StepRequirements(minPhotos = 1))
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(first, second),
        )
        val photo = PhotoUploadPayload("p1", "s1", "p1.jpg", "image/jpeg", 100, 80)
        val duplicate = StepCompletePayload("s1", 1, photoIds = listOf("p1", "p1"))
        val wrongStep = StepCompletePayload("s2", 2, photoIds = listOf("p1"))

        assertTrue(TaskGuard.validateStepCompletion(task, duplicate, emptySet(), mapOf("p1" to photo)).any { it.contains("重复") })
        assertTrue(TaskGuard.validateStepCompletion(task, wrongStep, setOf("s1"), mapOf("p1" to photo)).any { it.contains("不匹配") })
    }

    @Test
    fun taskGuardAcceptsExactCompletionReplayButRejectsConflictingPayload() {
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(InspectionStep("s1", "review_submit", "确认", "核对", StepRequirements())),
        )
        val original = StepCompletePayload("s1", 1, result = "normal", note = "已核对", confirmed = true)
        assertTrue(
            TaskGuard.validateStepCompletion(
                task,
                original,
                setOf("s1"),
                emptyMap(),
                existingCompletion = original,
            ).isEmpty(),
        )
        val conflicting = original.copy(result = "abnormal", note = "改写")
        assertTrue(
            TaskGuard.validateStepCompletion(
                task,
                conflicting,
                setOf("s1"),
                emptyMap(),
                existingCompletion = original,
            ).any { it.contains("内容不一致") },
        )
    }

    @Test
    fun taskGuardRejectsLateNewPhotoButAllowsExactMetadataReplay() {
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试", 1),
            InspectionAsset("a1", "A1", "设备", "位置"),
            InspectionAssignee("u1", "张工"),
            listOf(InspectionStep("s1", "photo_check", "照片", "拍照", StepRequirements())),
        )
        val photo = PhotoUploadPayload("p1", "s1", "p1.jpg", "image/jpeg", 100, 80)
        assertTrue(TaskGuard.validatePhotoMetadata(task, photo, setOf("s1"), emptyMap()).isNotEmpty())
        assertTrue(TaskGuard.validatePhotoMetadata(task, photo, setOf("s1"), mapOf("p1" to photo)).isEmpty())
        assertTrue(TaskGuard.validatePhotoMetadata(task, photo.copy(stepId = "missing"), emptySet(), emptyMap()).isNotEmpty())
    }

    @Test
    fun reportBuilderKeepsStepOrderAndPhotoMetadata() {
        val first = InspectionStep("s1", "reading", "压力", "记录压力", StepRequirements())
        val second = InspectionStep("s2", "review_submit", "确认", "确认记录", StepRequirements())
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试模板", 3),
            InspectionAsset("a1", "A1", "设备 A1", "四号楼"),
            InspectionAssignee("u1", "张工"),
            listOf(first, second),
            schedule = InspectionSchedule("2026-11-05", "上午", "常规班"),
        )
        val photo = PhotoUploadPayload("p1", "s1", "p1.jpg", "image/jpeg", 100, 80)
        val report = InspectionReportBuilder.build(
            taskId = "task-1",
            task = task,
            completions = mapOf("s1" to StepCompletePayload("s1", 1, "abnormal", note = "待复核", photoIds = listOf("p1"))),
            photoMetadata = mapOf("p1" to photo),
            generatedAt = "2026-11-05T10:00:00+08:00",
        )

        assertEquals("in_progress", report.status)
        assertEquals(1, report.completedStepCount)
        assertEquals(1, report.flaggedStepCount)
        assertEquals(listOf("s1", "s2"), report.steps.map { it.step.id })
        assertEquals("p1", report.steps.first().photos.single().photoId)
        assertTrue(InspectionReportCodec.encode(report).contains("\"taskId\":\"task-1\""))
    }

    @Test
    fun syncQueueIsIdempotentAndUsesRetryBackoff() {
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试模板", 1),
            InspectionAsset("a1", "A1", "设备 A1", "四号楼"),
            InspectionAssignee("u1", "张工"),
            listOf(InspectionStep("s1", "review_submit", "确认", "确认记录", StepRequirements())),
        )
        val report = InspectionReportBuilder.build("task-1", task, emptyMap(), emptyMap(), "2026-11-05T10:00:00+08:00")
        val queued = InspectionSyncQueue.enqueue(emptyList(), report, 1_000L)
        val duplicate = InspectionSyncQueue.enqueue(queued, report, 2_000L)

        assertEquals(1, duplicate.size)
        assertEquals(1, InspectionSyncQueue.due(duplicate, 1_000L).size)
        val sending = InspectionSyncQueue.markSending(duplicate, "task-1", 1_000L)
        val failed = InspectionSyncQueue.markFailed(sending, "task-1", 1_000L, "网络暂不可用")
        assertEquals(1, failed.single().attempts)
        assertTrue(InspectionSyncQueue.due(failed, 1_000L).isEmpty())
        assertEquals(1, InspectionSyncQueue.due(failed, 31_000L).size)
        val rejected = InspectionSyncQueue.markRejected(failed, "task-1", "凭证无效")
        assertTrue(InspectionSyncQueue.due(rejected, Long.MAX_VALUE).isEmpty())
        assertEquals(InspectionSyncEntry.STATE_REJECTED, rejected.single().state)
        assertEquals(InspectionSyncEntry.STATE_SUCCEEDED, InspectionSyncQueue.markSucceeded(failed, "task-1").single().state)
    }

    @Test
    fun staleSendingEntryIsRecoveredAfterProcessRestart() {
        val task = TaskDispatchPayload(
            InspectionTemplate("t1", "测试模板", 1),
            InspectionAsset("a1", "A1", "设备 A1", "四号楼"),
            InspectionAssignee("u1", "张工"),
            listOf(InspectionStep("s1", "review_submit", "确认", "确认记录", StepRequirements())),
        )
        val report = InspectionReportBuilder.build("task-restart", task, emptyMap(), emptyMap(), "2026-11-05T10:00:00+08:00")
        val sending = InspectionSyncQueue.markSending(
            InspectionSyncQueue.enqueue(emptyList(), report, 1_000L),
            "task-restart",
            1_000L,
        )
        val fresh = InspectionSyncQueue.recoverStaleSending(sending, 1_000L + 60_000L)
        assertEquals(InspectionSyncEntry.STATE_SENDING, fresh.single().state)

        val recovered = InspectionSyncQueue.recoverStaleSending(sending, 1_000L + 5 * 60_000L)
        assertEquals(InspectionSyncEntry.STATE_FAILED, recovered.single().state)
        assertEquals(1, recovered.single().attempts)
        assertTrue(InspectionSyncQueue.due(recovered, 1_000L + 5 * 60_000L).isEmpty())
        assertTrue(recovered.single().lastError!!.contains("自动恢复"))
    }
}
