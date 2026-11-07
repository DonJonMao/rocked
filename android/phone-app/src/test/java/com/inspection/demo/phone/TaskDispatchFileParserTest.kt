package com.inspection.demo.phone

import com.inspection.demo.protocol.InspectionAsset
import com.inspection.demo.protocol.InspectionAssignee
import com.inspection.demo.protocol.InspectionStep
import com.inspection.demo.protocol.InspectionTemplate
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.StepRequirements
import com.inspection.demo.protocol.TaskDispatchPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDispatchFileParserTest {
    @Test
    fun parsesWebCompatibleDispatchAndKeepsTaskIdentity() {
        val payload = TaskDispatchPayload(
            template = InspectionTemplate("t1", "现场巡检", 1, assetIds = listOf("a1"), timeSlots = listOf("10:00")),
            asset = InspectionAsset("a1", "A1", "设备 A1", "四号楼"),
            assignee = InspectionAssignee("u1", "张工"),
            steps = listOf(
                InspectionStep("asset", "asset_check", "确认设备", "核对资产码", StepRequirements()),
                InspectionStep("check", "photo_check", "检查状态", "记录结果", StepRequirements(resultRequired = true)),
                InspectionStep("review", "review_submit", "提交记录", "核对后提交", StepRequirements()),
            ),
        )
        val raw = ProtocolCodec.encode(
            ProtocolEnvelope(
                type = "task.dispatch",
                commandId = "cmd-dispatch-task-web-1",
                taskId = "task-web-1",
                sentAt = "2026-11-06T10:00:00+08:00",
                payload = ProtocolCodec.json(payload),
            ),
        )

        val parsed = TaskDispatchFileParser.parse(raw).getOrThrow()
        assertEquals("task-web-1", parsed.taskId)
        assertEquals("现场巡检", parsed.payload.template.name)
        assertEquals("A1", parsed.payload.asset.code)
    }

    @Test
    fun rejectsWrongEnvelopeTypeAndInvalidTaskShape() {
        val wrongType = ProtocolEnvelope(
            type = "inspection.report",
            taskId = "task-1",
            sentAt = "2026-11-06T10:00:00+08:00",
        )
        assertTrue(TaskDispatchFileParser.parse(ProtocolCodec.encode(wrongType)).isFailure)

        val invalid = ProtocolEnvelope(
            type = "task.dispatch",
            taskId = "task-2",
            sentAt = "2026-11-06T10:00:00+08:00",
            payload = ProtocolCodec.json(
                TaskDispatchPayload(
                    InspectionTemplate("t1", "不完整", 1),
                    InspectionAsset("a1", "A1", "设备", "位置"),
                    InspectionAssignee("u1", "张工"),
                    listOf(InspectionStep("only", "photo_check", "检查", "记录", StepRequirements())),
                ),
            ),
        )
        assertTrue(TaskDispatchFileParser.parse(ProtocolCodec.encode(invalid)).isFailure)
    }

    @Test
    fun rejectsUnsupportedVersionAndMissingCommandIdentity() {
        val versioned = ProtocolEnvelope(
            type = "task.dispatch",
            version = 2,
            commandId = "cmd-2",
            taskId = "task-2",
            sentAt = "2026-11-06T10:00:00+08:00",
        )
        assertTrue(TaskDispatchFileParser.parse(ProtocolCodec.encode(versioned)).isFailure)

        val missingCommand = versioned.copy(version = 1, commandId = null)
        assertTrue(TaskDispatchFileParser.parse(ProtocolCodec.encode(missingCommand)).isFailure)
    }

    @Test
    fun rejectsOversizedTaskFileBeforeDecoding() {
        val oversized = "{" + "x".repeat(TaskDispatchFileParser.MAX_BYTES) + "}"
        val result = TaskDispatchFileParser.parse(oversized)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("2 MB"))
    }
}
