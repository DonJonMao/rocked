package com.inspection.demo.protocol

import com.google.gson.Gson

/**
 * Stable, device-independent report snapshot produced after a task is received
 * and executed. Photo bytes stay in the app-private photo directory; the report
 * carries the validated metadata and the step references.
 */
data class InspectionReport(
    val schemaVersion: Int = 1,
    val taskId: String,
    val generatedAt: String,
    val status: String,
    val completedStepCount: Int,
    val totalStepCount: Int,
    val flaggedStepCount: Int,
    val template: InspectionTemplate,
    val asset: InspectionAsset,
    val assignee: InspectionAssignee,
    val schedule: InspectionSchedule? = null,
    val steps: List<InspectionReportStep>,
)

data class InspectionReportStep(
    val step: InspectionStep,
    val completion: StepCompletePayload? = null,
    val photos: List<PhotoUploadPayload> = emptyList(),
)

object InspectionReportBuilder {
    private val flaggedResults = setOf("abnormal", "shutdown", "standby", "not_applicable")

    fun build(
        taskId: String,
        task: TaskDispatchPayload,
        completions: Map<String, StepCompletePayload>,
        photoMetadata: Map<String, PhotoUploadPayload>,
        generatedAt: String,
    ): InspectionReport {
        val reportSteps = task.steps.map { step ->
            val completion = completions[step.id]
            InspectionReportStep(
                step = step,
                completion = completion,
                photos = completion?.photoIds.orEmpty().mapNotNull(photoMetadata::get),
            )
        }
        val completedCount = reportSteps.count { it.completion != null }
        val flaggedCount = reportSteps.count { it.completion?.result in flaggedResults }
        return InspectionReport(
            taskId = taskId,
            generatedAt = generatedAt,
            status = if (completedCount == task.steps.size) "completed" else "in_progress",
            completedStepCount = completedCount,
            totalStepCount = task.steps.size,
            flaggedStepCount = flaggedCount,
            template = task.template,
            asset = task.asset,
            assignee = task.assignee,
            schedule = task.schedule,
            steps = reportSteps,
        )
    }
}

object InspectionReportCodec {
    private val gson = Gson()

    fun encode(report: InspectionReport): String = gson.toJson(report)
}
