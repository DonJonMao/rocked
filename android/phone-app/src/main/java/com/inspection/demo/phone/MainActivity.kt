package com.inspection.demo.phone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.File
import com.inspection.demo.protocol.InspectionAsset
import com.inspection.demo.protocol.InspectionAssignee
import com.inspection.demo.protocol.InspectionStep
import com.inspection.demo.protocol.InspectionTemplate
import com.inspection.demo.protocol.InspectionSchedule
import com.inspection.demo.protocol.DeviceHeartbeatPayload
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolCommandIds
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.StepRequirements
import com.inspection.demo.protocol.StepCompletePayload
import com.inspection.demo.protocol.StepDraftPayload
import com.inspection.demo.protocol.TaskAckPayload
import com.inspection.demo.protocol.TaskDispatchPayload

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var deviceStatus: TextView
    private lateinit var transport: RokidPhoneTransport
    private lateinit var dispatchButton: Button
    private lateinit var building4Button: Button
    private lateinit var building4Template: Spinner
    private lateinit var building4Asset: Spinner
    private lateinit var building4Time: Spinner
    private lateinit var building4Date: EditText
    private lateinit var building4Shift: EditText
    private lateinit var connectButton: Button
    private lateinit var p2pButton: Button
    private lateinit var exportReportButton: Button
    private lateinit var importTaskButton: Button
    private lateinit var syncStatusButton: Button
    private lateinit var historyButton: Button
    private var building4Options = emptyList<Building4Catalog.TemplateOption>()
    private var building4Assets = emptyList<Building4Catalog.AssetOption>()
    private lateinit var taskStore: PhoneTaskStore
    private lateinit var syncStore: PhoneSyncStore
    private var ready = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var lastHeartbeatAtEpochMs = 0L
    private val heartbeatTimeout = Runnable {
        if (::deviceStatus.isInitialized) {
            deviceStatus.text = "眼镜状态：心跳超时（90 秒未收到）"
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { granted -> !granted }) {
            status.text = "需要蓝牙和附近设备权限才能连接 Glass3"
            return@registerForActivityResult
        }
        initializeTransport()
    }

    private val importTaskLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importAndroidTask)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 16f; setPadding(32, 32, 32, 24); text = "准备初始化 Rokid SDK" }
        deviceStatus = TextView(this).apply {
            textSize = 15f
            setPadding(32, 0, 32, 16)
            text = "眼镜状态：未收到心跳"
        }
        val init = Button(this).apply { text = "初始化 SDK"; setOnClickListener { requestPermissionsThenInitialize() } }
        connectButton = Button(this).apply { text = "扫描并连接 Glass3"; isEnabled = false; setOnClickListener { transport.scanAndConnect() } }
        p2pButton = Button(this).apply { text = "建立/重试 P2P"; isEnabled = false; setOnClickListener { transport.connectP2p() } }
        importTaskButton = Button(this).apply {
            text = "导入 Web task.dispatch JSON"
            setOnClickListener { importTaskLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
        exportReportButton = Button(this).apply { text = "导出本地巡检报告 JSON"; isEnabled = false; setOnClickListener { exportReport() } }
        syncStatusButton = Button(this).apply { text = "查看待同步报告"; setOnClickListener { showSyncStatus() } }
        historyButton = Button(this).apply { text = "查看本地任务记录"; setOnClickListener { showTaskHistory() } }
        dispatchButton = Button(this).apply { text = "下发演示巡检任务"; isEnabled = false; setOnClickListener { sendDemoTask() } }
        building4Options = runCatching { Building4Catalog(this).listTemplates() }.getOrDefault(emptyList())
        building4Template = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, building4Options.map { it.name })
            val chillerIndex = building4Options.indexOfFirst { it.id == "chiller" }
            if (chillerIndex >= 0) setSelection(chillerIndex)
            isEnabled = false
        }
        building4Asset = Spinner(this).apply { isEnabled = false }
        building4Time = Spinner(this).apply { isEnabled = false }
        building4Date = EditText(this).apply {
            setText(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
            hint = "巡检日期 yyyy-MM-dd"
            isEnabled = false
        }
        building4Shift = EditText(this).apply {
            setText("常规班")
            hint = "班次（可选）"
            isEnabled = false
        }
        building4Template.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshBuilding4Selections()
            }
        }
        refreshBuilding4Selections()
        building4Button = Button(this).apply { text = "下发所选 4 号楼任务"; isEnabled = false; setOnClickListener { sendBuilding4Task() } }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 24)
            addView(deviceStatus)
            addView(status)
            addView(init)
            addView(connectButton)
            addView(p2pButton)
            addView(importTaskButton)
            addView(exportReportButton)
            addView(syncStatusButton)
            addView(historyButton)
            addView(dispatchButton)
            addView(TextView(this@MainActivity).apply { text = "四号楼模板" })
            addView(building4Template)
            addView(TextView(this@MainActivity).apply { text = "巡检对象" })
            addView(building4Asset)
            addView(TextView(this@MainActivity).apply { text = "计划时段" })
            addView(building4Time)
            addView(building4Date)
            addView(building4Shift)
            addView(building4Button)
        }
        // The four号楼 selectors plus status and sync controls exceed the
        // height of common handheld screens. Keep every action reachable
        // without shrinking text or relying on a hardware keyboard.
        setContentView(ScrollView(this).apply { addView(content) })
        taskStore = PhoneTaskStore(this)
        syncStore = PhoneSyncStore(this)
        taskStore.load()?.let { state ->
            exportReportButton.isEnabled = true
            val completed = state.completedStepIds.size
            val total = state.payload.steps.size
            status.text = "已恢复本地任务：${state.payload.template.name}；进度 $completed/$total"
        }
        InspectionSyncScheduler.enqueue(this)
        transport = RokidPhoneTransport(this).also {
            it.onStatus = { message -> runOnUiThread { status.text = message } }
            it.onMessage = { envelope -> runOnUiThread { handleMessage(envelope) } }
            it.onHeartbeat = { _, heartbeat: DeviceHeartbeatPayload ->
                runOnUiThread {
                    lastHeartbeatAtEpochMs = System.currentTimeMillis()
                    heartbeatHandler.removeCallbacks(heartbeatTimeout)
                    deviceStatus.text = "眼镜在线：${heartbeat.connection}；电量 ${heartbeat.batteryPercent}%；版本 ${heartbeat.appVersion}"
                    heartbeatHandler.postDelayed(heartbeatTimeout, HeartbeatPolicy.TIMEOUT_MS)
                }
            }
            it.onLinkReady = {
                runOnUiThread {
                    if (HeartbeatPolicy.isStale(lastHeartbeatAtEpochMs, System.currentTimeMillis())) {
                        deviceStatus.text = "眼镜连接已恢复，等待心跳"
                    }
                    retryActiveDispatchIfNeeded()
                }
            }
            it.onPhotoSaved = { taskId, file, metadata ->
                // The SDK may rename the received file. The protocol photoId
                // is the durable identity used by step validation and ACKs.
                val stored = taskStore.markPhoto(taskId, metadata.photoId, metadata)
                runOnUiThread {
                    status.text = if (stored) {
                        "照片已保存：${file.name}"
                    } else {
                        "照片未关联到当前任务：${metadata.photoId}"
                    }
                }
                stored
            }
        }
        incomingTaskUri(intent)?.let(::importAndroidTask)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingTaskUri(intent)?.let(::importAndroidTask)
    }

    @SuppressLint("InlinedApi")
    private fun requestPermissionsThenInitialize() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            else -> arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
        permissionLauncher.launch(permissions)
    }

    private fun initializeTransport() {
        transport.initialize { success ->
            runOnUiThread {
                ready = success
                connectButton.isEnabled = success
                p2pButton.isEnabled = success
                dispatchButton.isEnabled = success
                building4Template.isEnabled = success
                building4Asset.isEnabled = success
                building4Time.isEnabled = success
                building4Date.isEnabled = success
                building4Shift.isEnabled = success
                building4Button.isEnabled = success
                status.text = if (success) "SDK 已就绪，请连接 Glass3" else "SDK 初始化失败"
                if (success) transport.notifyLinkReadyIfConnected()
            }
        }
    }

    private fun sendDemoTask() {
        if (!ready) return
        val taskId = "task-android-demo"
        val payload = TaskDispatchPayload(
                template = InspectionTemplate("demo-cabinet", "机柜巡检", 1),
                asset = InspectionAsset("cabinet-a03", "ASSET-A03", "机柜 A03", "示例机房 1 / A 区"),
                assignee = InspectionAssignee("assignee-demo", "张工（演示）"),
                steps = listOf(
                    InspectionStep("asset-confirm", "asset_check", "确认巡检设备", "核对资产码、设备名称和位置", StepRequirements(confirmationRequired = true)),
                    InspectionStep("cabinet-check", "photo_check", "检查柜门与外观", "检查柜门状态与外观，填写结果", StepRequirements(minPhotos = 1, resultRequired = true, abnormalNoteRequired = true)),
                    InspectionStep("review-submit", "review_submit", "提交巡检记录", "核对记录后提交", StepRequirements(confirmationRequired = true)),
                ),
            )
        dispatchTask(payload, taskId)
    }

    private fun sendBuilding4Task() {
        if (!ready) return
        val taskId = "task-building4-${System.currentTimeMillis()}"
        val templateId = building4Options.getOrNull(building4Template.selectedItemPosition)?.id ?: "chiller"
        val assetId = building4Assets.getOrNull(building4Asset.selectedItemPosition)?.id
        val timeSlot = building4Time.selectedItem?.toString().orEmpty()
        val schedule = InspectionSchedule(building4Date.text.toString().trim(), timeSlot, building4Shift.text.toString().trim().ifBlank { null })
        runCatching {
            Building4Catalog(this).loadTask(templateId = templateId, assetId = assetId, taskId = taskId, schedule = schedule)
        }.onSuccess { payload ->
            dispatchTask(payload, taskId)
            status.text = "已下发：${payload.template.name} / ${payload.asset.name}，共 ${payload.steps.size} 步"
        }.onFailure { error ->
            status.text = "读取四号楼目录失败：${error.message}"
        }
    }

    private fun refreshBuilding4Selections() {
        val templateId = building4Options.getOrNull(building4Template.selectedItemPosition)?.id ?: return
        val catalog = Building4Catalog(this)
        building4Assets = runCatching { catalog.listAssets(templateId) }.getOrDefault(emptyList())
        building4Asset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, building4Assets.map { "${it.name} (${it.code})" })
        val slots = runCatching { catalog.listTimeSlots(templateId) }.getOrDefault(emptyList())
        building4Time.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, slots)
    }

    private fun dispatchTask(payload: TaskDispatchPayload, taskId: String) {
        val result = taskStore.saveDispatch(taskId, payload)
        if (!result.accepted) {
            status.text = "任务未下发：${result.reason.orEmpty()}"
            return
        }
        exportReportButton.isEnabled = true
        transport.sendDispatch(payload, taskId)
    }

    /** File managers use VIEW/data; share sheets commonly use SEND/EXTRA_STREAM. */
    private fun incomingTaskUri(incoming: Intent): Uri? {
        incoming.data?.let { return it }
        incoming.clipData?.getItemAt(0)?.uri?.let { return it }
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 33) {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    /** Import the Web manager's actionable task envelope and keep it durable before sending. */
    private fun importAndroidTask(uri: Uri) {
        val raw = runCatching {
            contentResolver.openInputStream(uri)?.use(::readTaskFile)
                ?: error("无法读取所选文件")
        }.getOrElse { error ->
            status.text = "任务文件读取失败：${error.message.orEmpty()}"
            return
        }
        val imported = TaskDispatchFileParser.parse(raw).getOrElse { error ->
            status.text = "任务文件无法导入：${error.message.orEmpty()}"
            return
        }
        val taskId = imported.taskId
        val payload = imported.payload
        val saved = taskStore.saveDispatch(taskId, payload)
        if (!saved.accepted) {
            status.text = "任务导入失败：${saved.reason.orEmpty()}"
            return
        }
        exportReportButton.isEnabled = true
        if (ready) {
            transport.sendDispatch(payload, taskId)
            status.text = "任务已导入并下发：${payload.template.name} / ${payload.asset.name}"
        } else {
            status.text = "任务已导入并本地保存：${payload.template.name}；连接 Glass3 后自动下发"
        }
    }

    private fun readTaskFile(input: java.io.InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > TaskDispatchFileParser.MAX_BYTES) {
                error("任务文件过大（上限 2 MB）")
            }
            bytes.write(buffer, 0, count)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    /** Re-send the durable active task after a control channel reconnects. */
    private fun retryActiveDispatchIfNeeded() {
        if (!ready) return
        val state = taskStore.load() ?: return
        if (state.payload.steps.isEmpty()) return
        if (state.payload.steps.all { it.id in state.completedStepIds }) {
            // The final task.complete notification is idempotent. Re-send it
            // after a reconnect/process restart so a lost terminal message
            // cannot leave the glasses screen waiting forever.
            status.text = "连接恢复，正在重试任务完成通知…"
            transport.sendTaskComplete(state.taskId)
            return
        }
        status.text = "连接恢复，正在重试下发任务…"
        transport.sendDispatch(state.payload, state.taskId)
    }

    private fun exportReport() {
        val generatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            .format(java.util.Date())
        val file = taskStore.exportReport(File(filesDir, "reports"), generatedAt)
        status.text = if (file == null) {
            "暂无可导出的巡检任务"
        } else {
            "报告已导出：${file.name}"
        }
    }

    private fun handleMessage(envelope: ProtocolEnvelope) {
        runCatching { handleMessageSafely(envelope) }.onFailure { error ->
            // This callback runs on the Activity's main thread after the SDK
            // boundary. Keep malformed payloads and unexpected UI/domain
            // errors visible without crashing the inspection app.
            status.text = "收到无效巡检消息：${error.message.orEmpty()}"
        }
    }

    private fun handleMessageSafely(envelope: ProtocolEnvelope) {
        val taskId = envelope.taskId.orEmpty()
        when (envelope.type) {
            "task.ack" -> {
                val ack = ProtocolCodec.payload(envelope, TaskAckPayload::class.java) as TaskAckPayload
                check(envelope.commandId == ProtocolCommandIds.taskAck(taskId, ack.accepted)) {
                    "任务 ACK commandId 不匹配"
                }
                status.text = if (ack.accepted) {
                    "眼镜已确认任务：$taskId"
                } else {
                    "眼镜未接受任务：${ack.reason.orEmpty()}"
                }
            }
            "task.start" -> {
                val ack = ProtocolCodec.payload(envelope, TaskAckPayload::class.java) as TaskAckPayload
                check(envelope.commandId == ProtocolCommandIds.taskStart(taskId)) { "开始消息 commandId 不匹配" }
                status.text = if (ack.accepted) "眼镜已开始巡检：$taskId" else "眼镜未开始巡检：${ack.reason.orEmpty()}"
            }
            "task.pause" -> {
                val ack = ProtocolCodec.payload(envelope, TaskAckPayload::class.java) as TaskAckPayload
                check(envelope.commandId == ProtocolCommandIds.taskPause(taskId)) { "暂停消息 commandId 不匹配" }
                status.text = if (ack.accepted) "眼镜已暂停巡检：$taskId" else "眼镜未暂停巡检：${ack.reason.orEmpty()}"
            }
            "task.resume" -> {
                val ack = ProtocolCodec.payload(envelope, TaskAckPayload::class.java) as TaskAckPayload
                check(envelope.commandId == ProtocolCommandIds.taskResume(taskId)) { "继续消息 commandId 不匹配" }
                status.text = if (ack.accepted) "眼镜已继续巡检：$taskId" else "眼镜未继续巡检：${ack.reason.orEmpty()}"
            }
            "step.complete" -> {
                val payload = ProtocolCodec.payload(envelope, StepCompletePayload::class.java) as StepCompletePayload
                if (envelope.commandId != ProtocolCommandIds.stepComplete(taskId, payload.expectedStepId, payload.revision)) {
                    status.text = "步骤消息身份无效，已拒绝"
                    transport.sendStepAck(taskId, payload, accepted = false, reason = "步骤 commandId 不匹配")
                    return
                }
                val result = taskStore.markStepComplete(taskId, payload)
                val state = taskStore.load()
                val total = state?.payload?.steps?.size ?: 0
                val completed = state?.completedStepIds?.size ?: 0
                val taskIsComplete = result.accepted && total > 0 && completed >= total
                // Keep the terminal notification behind the step ACK. The
                // SDK/P2P send path is asynchronous and can otherwise deliver
                // task.complete before the glasses have advanced past the last
                // step. Recovery still re-sends this idempotently if the ACK
                // callback or link drops after the local commit.
                transport.sendStepAck(taskId, payload, result.accepted, result.reason) {
                    if (taskIsComplete) transport.sendTaskComplete(taskId)
                }
                if (!result.accepted) {
                    status.text = "步骤未接受：${result.reason.orEmpty()}"
                    return
                }
                if (taskIsComplete) {
                    val generatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                        .format(java.util.Date())
                    val queued = taskStore.buildReport(generatedAt)?.let {
                        val enqueued = syncStore.enqueue(it)
                        InspectionSyncScheduler.enqueue(this)
                        enqueued
                    } == true
                    status.text = if (queued) {
                        "眼镜已完成任务：$completed/$total；报告已加入待同步队列"
                    } else {
                        "眼镜已完成任务：$completed/$total"
                    }
                } else {
                    status.text = "眼镜完成步骤：$completed/$total（${payload.expectedStepId}）"
                }
            }
            "step.draft" -> {
                val payload = ProtocolCodec.payload(envelope, StepDraftPayload::class.java) as StepDraftPayload
                val result = taskStore.markStepDraft(taskId, payload)
                status.text = if (result.accepted) {
                    "已保存眼镜端草稿：${payload.expectedStepId}"
                } else {
                    "眼镜端草稿未保存：${result.reason.orEmpty()}"
                }
            }
            "photo.upload" -> status.text = "眼镜正在回传照片：$taskId"
            "device.heartbeat" -> Unit
            else -> status.text = "收到眼镜消息：${envelope.type}"
        }
    }

    private fun showSyncStatus() {
        val entries = syncStore.load()
        val pending = entries.count { it.state != com.inspection.demo.protocol.InspectionSyncEntry.STATE_SUCCEEDED }
        val due = syncStore.due().size
        status.text = "待同步报告：$pending 条；当前可重试：$due 条（WorkManager 已启用，等待网络或服务端响应）"
    }

    private fun showTaskHistory() {
        val records = taskStore.loadHistory()
        status.text = if (records.isEmpty()) {
            "暂无本地任务记录"
        } else {
            val latest = records.take(3).joinToString("；") { record ->
                "${record.taskId} ${record.status} ${record.completedStepCount}/${record.totalStepCount}"
            }
            "本地任务记录 ${records.size} 条：$latest"
        }
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacksAndMessages(null)
        if (::transport.isInitialized) transport.release()
        super.onDestroy()
    }
}
