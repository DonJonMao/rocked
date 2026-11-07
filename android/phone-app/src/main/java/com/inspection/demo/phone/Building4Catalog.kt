package com.inspection.demo.phone

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.inspection.demo.protocol.InspectionAssignee
import com.inspection.demo.protocol.InspectionSchedule
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolCommandIds
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.TaskDispatchPayload
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** Loads the controlled catalog extracted from the two Building 4 inspection PDFs. */
class Building4Catalog(private val context: Context) {
    data class TemplateOption(val id: String, val name: String)
    data class AssetOption(val id: String, val name: String, val code: String)

    fun listTemplates(): List<TemplateOption> = loadRoot().getAsJsonArray("templates").map {
        val template = it.asJsonObject
        TemplateOption(template.get("id").asString, template.get("name").asString)
    }

    fun listAssets(templateId: String): List<AssetOption> {
        val root = loadRoot()
        val template = root.getAsJsonArray("templates").first { it.asJsonObject.get("id")?.asString == templateId }.asJsonObject
        val assetIds = template.getAsJsonArray("assetIds").map { it.asString }.toSet()
        return root.getAsJsonArray("assets").map { it.asJsonObject }
            .filter { it.get("id")?.asString in assetIds }
            .map { AssetOption(it.get("id").asString, it.get("name").asString, it.get("code").asString) }
    }

    fun listTimeSlots(templateId: String): List<String> {
        val template = loadRoot().getAsJsonArray("templates").first { it.asJsonObject.get("id")?.asString == templateId }.asJsonObject
        return template.getAsJsonArray("timeSlots")?.map { it.asString } ?: emptyList()
    }

    fun loadTask(
        templateId: String = "chiller",
        assetId: String? = null,
        taskId: String,
        assignee: InspectionAssignee = InspectionAssignee("assignee-android", "张工（Android）"),
        schedule: InspectionSchedule? = null,
    ): TaskDispatchPayload {
        val root = loadRoot()
        val template = root.getAsJsonArray("templates").first { it.asJsonObject.get("id")?.asString == templateId }.asJsonObject
        val selectedAssetId = assetId ?: template.getAsJsonArray("assetIds").first().asString
        val asset = root.getAsJsonArray("assets").first { it.asJsonObject.get("id")?.asString == selectedAssetId }.asJsonObject
        val dispatchPayload = JsonObject().apply {
            add("template", template)
            add("asset", asset)
            add("assignee", ProtocolCodec.json(assignee))
            add("steps", template.getAsJsonArray("steps"))
            schedule?.let { add("schedule", ProtocolCodec.json(it)) }
        }
        val envelope = ProtocolEnvelope(
            type = "task.dispatch",
            commandId = ProtocolCommandIds.dispatch(taskId),
            taskId = taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = dispatchPayload,
        )
        return ProtocolCodec.payload(envelope, TaskDispatchPayload::class.java) as TaskDispatchPayload
    }

    private fun loadRoot() = context.assets.open(FILE_NAME).use { stream ->
        InputStreamReader(stream, StandardCharsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    }

    private companion object {
        const val FILE_NAME = "building4-catalog.json"
    }
}
