package com.inspection.demo.glass

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.inspection.demo.protocol.InspectionStep
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.PhotoAckPayload
import com.inspection.demo.protocol.ReadingField
import com.inspection.demo.protocol.StepAckPayload
import com.inspection.demo.protocol.StepCompletePayload
import com.inspection.demo.protocol.StepDraftPayload
import com.inspection.demo.protocol.StepValidator
import com.inspection.demo.protocol.TaskGuard
import com.inspection.demo.protocol.TaskDispatchPayload
import com.inspection.demo.protocol.TaskCompletePayload

class MainActivity : AppCompatActivity() {
    companion object {
        private const val ACTION_BUTTON_CLICK = "com.rokid.glass3.action.button.CLICK"
        private const val ACTION_BUTTON_DOUBLE_CLICK = "com.rokid.glass3.action.button.DOUBLE_CLICK"
        private const val BG = 0xFF101820.toInt()
        private const val FG = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFB8C7D1.toInt()
    }

    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var form: LinearLayout
    private lateinit var noteInput: EditText
    private lateinit var assetCodeInput: EditText
    private lateinit var confirmationCheck: CheckBox
    private lateinit var faultTimeInput: EditText
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var photoButton: Button
    private lateinit var completeButton: Button
    private lateinit var transport: RokidGlassTransport
    private lateinit var taskStore: GlassTaskStore
    private lateinit var photoCapture: GlassPhotoCapture
    private val readingInputs = linkedMapOf<String, View>()
    private val photoIdsByStep = mutableMapOf<String, MutableList<String>>()
    private val draftsByStep = mutableMapOf<String, GlassTaskStore.DraftState>()
    private val pendingPhotoSteps = mutableMapOf<String, String>()
    private var selectedResult: String? = null
    private var currentTask: ProtocolEnvelope? = null
    private var currentStepIndex = 0
    private var taskStarted = false
    private var taskPaused = false
    private var taskCompleted = false
    private var awaitingStepAck = false
    private var awaitingCompletion: StepCompletePayload? = null
    private val ackTimeoutHandler = Handler(Looper.getMainLooper())
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            captureCurrentPhoto()
        } else {
            detail.text = "未获得相机权限，无法拍照；可在系统设置中授权后重试"
        }
    }
    private val ackTimeout = Runnable {
        if (awaitingStepAck) {
            awaitingStepAck = false
            completeButton.isEnabled = true
            photoButton.isEnabled = true
            detail.text = "暂未收到手机确认，可再次点击完成本步骤重试；提交资料已保留"
        }
    }

    private val buttonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_BUTTON_CLICK -> {
                    completeCurrentStep()
                    // Glass3's system service sends an ordered broadcast. Once
                    // the inspection app handles it, stop the default system
                    // action from firing as well (for example, leaving the
                    // activity or changing the system selection).
                    runCatching { abortBroadcast() }
                }
                ACTION_BUTTON_DOUBLE_CLICK -> {
                    finish()
                    runCatching { abortBroadcast() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = TextView(this).apply {
            textSize = 24f
            setTextColor(FG)
        }
        detail = TextView(this).apply {
            textSize = 18f
            setTextColor(FG)
            setPadding(0, 20, 0, 20)
        }
        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        noteInput = EditText(this).apply {
            textSize = 17f
            setTextColor(FG)
            setHintTextColor(MUTED)
            hint = "状态说明（选择非正常状态后必填）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            visibility = View.GONE
        }
        faultTimeInput = EditText(this).apply {
            textSize = 17f
            setTextColor(FG)
            setHintTextColor(MUTED)
            hint = "故障发现时间（HH:mm，可选）"
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            visibility = View.GONE
        }
        completeButton = Button(this).apply {
            text = "确认并完成当前步骤"
            isEnabled = false
            setOnClickListener { completeCurrentStep() }
        }
        photoButton = Button(this).apply {
            text = "拍照并回传手机"
            isEnabled = false
            setOnClickListener { capturePhoto() }
        }
        startButton = Button(this).apply {
            text = "开始巡检"
            visibility = View.GONE
            setOnClickListener { startTask() }
        }
        pauseButton = Button(this).apply {
            text = "保存草稿并暂停"
            visibility = View.GONE
            setOnClickListener { pauseTask() }
        }
        assetCodeInput = EditText(this).apply {
            textSize = 17f
            setTextColor(FG)
            setHintTextColor(MUTED)
            hint = "请输入或扫描资产码"
            visibility = View.GONE
        }
        confirmationCheck = CheckBox(this).apply {
            text = "我已核对设备名称和位置"
            setTextColor(FG)
            visibility = View.GONE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 28, 36, 28)
            setBackgroundColor(BG)
            addView(title)
            addView(detail)
            addView(startButton)
            addView(pauseButton)
            addView(form)
            addView(assetCodeInput)
            addView(confirmationCheck)
            addView(noteInput)
            addView(faultTimeInput)
            addView(photoButton)
            addView(completeButton)
        }
        setContentView(ScrollView(this).apply { addView(content) })

        transport = RokidGlassTransport(this).also {
            it.onStatus = { message -> runOnUiThread { detail.text = message } }
            it.onDispatch = { envelope -> runOnUiThread { receiveTask(envelope) } }
            // RokidGlassTransport dispatches protocol callbacks on the main
            // looper. Returning the durable-apply result lets it remember an
            // ACK only after this Activity has safely committed its snapshot.
            it.onStepAck = { taskId, ack -> handleStepAck(taskId, ack) }
            it.onPhotoAck = { taskId, ack -> handlePhotoAck(taskId, ack) }
            it.onTaskComplete = { taskId, payload -> handleTaskComplete(taskId, payload) }
        }
        taskStore = GlassTaskStore(this)
        photoCapture = GlassPhotoCapture(this).also {
            it.onStatus = { message -> runOnUiThread { detail.text = message } }
            it.onPhotoSent = { photoId, stepId ->
                runOnUiThread {
                    pendingPhotoSteps[photoId] = stepId
                    detail.text = "照片已发送，等待手机校验…"
                }
            }
        }
        transport.onReady = {
            // Rebuild the photo-to-step map before retrying the binary. The
            // map is intentionally in memory during normal capture, but the
            // outbox is the source of truth after a process restart.
            val taskId = currentTask?.taskId?.takeIf { it.isNotBlank() }
            if (taskId != null) {
                pendingPhotoSteps.clear()
                photoCapture.pendingPhotos(taskId).forEach { pending ->
                    pendingPhotoSteps[pending.photoId] = pending.stepId
                }
                photoCapture.retryPending(taskId)
            }
            // Control messages are idempotent. Re-announce the durable task
            // state after an SDK/process restart so the phone can rebuild its
            // UI even when the original ACK was lost during a disconnect.
            retryDurableControlState()
            retryDurableDraft()
            // A reconnect can happen before the normal 8-second ACK timeout.
            // Force one idempotent resend so the inspector does not have to
            // press "complete" again just because the control channel dipped.
            retryPendingCompletion(force = true)
        }
        title.text = "等待巡检任务"
        detail.text = "请在手机端连接 Glass3 后下发任务"
        taskStore.load()?.let { saved ->
            currentTask = saved.envelope
            currentStepIndex = saved.stepIndex
            taskStarted = saved.started
            taskPaused = saved.paused
            taskCompleted = saved.completed
            awaitingCompletion = saved.pendingCompletion
            awaitingStepAck = false
            photoIdsByStep.putAll(saved.photoIdsByStep.mapValues { it.value.toMutableList() })
            draftsByStep.putAll(saved.draftsByStep.orEmpty())
            renderTask(saved.envelope)
        }
        transport.initialize()
    }

    private fun retryDurableControlState() {
        val task = currentTask ?: return
        val taskId = task.taskId?.takeIf { it.isNotBlank() } ?: return
        transport.sendTaskAck(taskId)
        if (taskStarted) {
            if (taskPaused) transport.sendTaskPause(taskId) else transport.sendTaskStart(taskId)
        }
    }

    /** Re-send the current locally saved draft after a link/process recovery. */
    private fun retryDurableDraft() {
        val task = currentTask ?: return
        if (!taskStarted || taskCompleted) return
        val taskId = task.taskId?.takeIf { it.isNotBlank() } ?: return
        val payload = runCatching {
            ProtocolCodec.payload(task, TaskDispatchPayload::class.java) as TaskDispatchPayload
        }.getOrNull() ?: return
        val step = payload.steps.getOrNull(currentStepIndex) ?: return
        val draft = draftsByStep[step.id] ?: return
        transport.sendStepDraft(
            taskId,
            StepDraftPayload(
                expectedStepId = step.id,
                revision = currentStepIndex + 1,
                result = draft.result,
                readings = draft.readings,
                note = draft.note,
                photoIds = photoIdsByStep[step.id].orEmpty(),
                assetCode = draft.assetCode,
                confirmed = draft.confirmed,
                faultTime = draft.faultTime,
            ),
        )
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ACTION_BUTTON_CLICK)
            addAction(ACTION_BUTTON_DOUBLE_CLICK)
            priority = 100
        }
        // Glass3 emits these actions from the system/Rokid service. Use the
        // AndroidX compatibility API on every platform level so Android 14's
        // explicit receiver-export rule is satisfied while older Glass3
        // firmware keeps receiving the same broadcasts.
        ContextCompat.registerReceiver(this, buttonReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(buttonReceiver)
        if (::taskStore.isInitialized && currentTask != null && !isChangingConfigurations) {
            persistCurrentDraft()
        }
        super.onStop()
    }

    private fun receiveTask(envelope: ProtocolEnvelope) {
        ackTimeoutHandler.removeCallbacks(ackTimeout)
        val identityErrors = TaskGuard.validateDispatchEnvelope(envelope)
        if (identityErrors.isNotEmpty()) {
            val reason = identityErrors.joinToString("；")
            detail.text = "新任务未接受：$reason"
            transport.sendTaskAck(envelope.taskId.orEmpty(), accepted = false, reason = reason)
            return
        }
        if (envelope.taskId.isNullOrBlank()) {
            val reason = "任务编号不能为空"
            detail.text = "新任务未接受：$reason"
            transport.sendTaskAck("", accepted = false, reason = reason)
            return
        }
        val incomingPayload = runCatching {
            ProtocolCodec.payload(envelope, TaskDispatchPayload::class.java) as TaskDispatchPayload
        }.getOrNull()
        if (incomingPayload == null) {
            val reason = "任务消息格式无法解析"
            detail.text = "新任务未接受：$reason"
            transport.sendTaskAck(envelope.taskId.orEmpty(), accepted = false, reason = reason)
            return
        }
        val dispatchErrors = TaskGuard.validateDispatch(incomingPayload)
        if (dispatchErrors.isNotEmpty()) {
            val reason = dispatchErrors.joinToString("；")
            detail.text = "新任务未接受：$reason"
            transport.sendTaskAck(envelope.taskId.orEmpty(), accepted = false, reason = reason)
            return
        }
        if (currentTask?.taskId == envelope.taskId && envelope.taskId != null) {
            val existingPayload = runCatching {
                ProtocolCodec.payload(currentTask!!, TaskDispatchPayload::class.java) as TaskDispatchPayload
            }.getOrNull()
            if (existingPayload == incomingPayload) {
                detail.text = "任务已存在，重复下发已确认"
                transport.sendTaskAck(envelope.taskId.orEmpty())
            } else {
                val reason = "同一任务编号对应的任务内容不一致"
                detail.text = "新任务未接受：$reason"
                transport.sendTaskAck(envelope.taskId.orEmpty(), accepted = false, reason = reason)
            }
            return
        }
        val existingTask = currentTask
        if (existingTask != null && envelope.taskId != null) {
            val existingPayload = runCatching {
                ProtocolCodec.payload(existingTask, TaskDispatchPayload::class.java) as TaskDispatchPayload
            }.getOrNull()
            val existingCompleted = if (existingPayload != null && currentStepIndex >= existingPayload.steps.size) {
                existingPayload.steps.map { it.id }.toSet()
            } else {
                emptySet()
            }
            TaskGuard.activeDispatchRejection(
                existingTaskId = existingTask.taskId,
                existingStepIds = existingPayload?.steps?.map { it.id },
                completedStepIds = existingCompleted,
                incomingTaskId = envelope.taskId.orEmpty(),
            )?.let { reason ->
                detail.text = "新任务未接受：$reason"
                transport.sendTaskAck(envelope.taskId.orEmpty(), accepted = false, reason = reason)
                return
            }
        }
        currentTask = envelope
        currentStepIndex = 0
        taskStarted = false
        taskPaused = false
        taskCompleted = false
        awaitingStepAck = false
        awaitingCompletion = null
        selectedResult = null
        readingInputs.clear()
        photoIdsByStep.clear()
        draftsByStep.clear()
        pendingPhotoSteps.clear()
        if (!taskStore.save(envelope, currentStepIndex, photoIdsByStep, null, draftsByStep, started = false, completed = false)) {
            detail.text = "任务本地保存失败，未向手机确认；请重试"
            currentTask = null
            return
        }
        renderTask(envelope)
        transport.sendTaskAck(envelope.taskId.orEmpty())
    }

    private fun startTask() {
        if (taskPaused) {
            resumeTask()
            return
        }
        if (taskStarted) {
            renderTask(currentTask ?: return)
            return
        }
        val task = currentTask ?: return
        taskStarted = true
        taskPaused = false
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, awaitingCompletion, draftsByStep, started = true, paused = false, completed = taskCompleted)) {
            taskStarted = false
            detail.text = "开始状态保存失败，请重试"
            return
        }
        transport.sendTaskStart(task.taskId.orEmpty())
        renderTask(task)
    }

    private fun pauseTask() {
        if (!taskStarted || taskPaused) return
        val task = currentTask ?: return
        persistCurrentDraft()
        taskPaused = true
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, awaitingCompletion, draftsByStep, started = true, paused = true, completed = taskCompleted)) {
            taskPaused = false
            detail.text = "暂停状态保存失败；请重试"
            return
        }
        transport.sendTaskPause(task.taskId.orEmpty())
        renderTask(task)
    }

    private fun resumeTask() {
        if (!taskStarted || !taskPaused) return
        val task = currentTask ?: return
        taskPaused = false
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, awaitingCompletion, draftsByStep, started = true, paused = false, completed = taskCompleted)) {
            taskPaused = true
            detail.text = "继续状态保存失败；请重试"
            return
        }
        transport.sendTaskResume(task.taskId.orEmpty())
        renderTask(task)
    }

    private fun renderTask(envelope: ProtocolEnvelope) {
        val payload = ProtocolCodec.payload(envelope, TaskDispatchPayload::class.java) as TaskDispatchPayload
        title.text = "收到任务：${payload.template.name}"
        val step = payload.steps.getOrNull(currentStepIndex)
        val schedule = payload.schedule?.let { "\n日期：${it.date}  时段：${it.timeSlot}${it.shift?.let { shift -> "  班次：$shift" }.orEmpty()}" }.orEmpty()
        if (step == null) {
            detail.text = if (taskCompleted) "任务已完成，手机已确认全部步骤保存" else "任务已提交，等待手机端保存"
            form.removeAllViews()
            startButton.visibility = View.GONE
            pauseButton.visibility = View.GONE
            assetCodeInput.visibility = View.GONE
            confirmationCheck.visibility = View.GONE
            noteInput.visibility = View.GONE
            faultTimeInput.visibility = View.GONE
            photoButton.isEnabled = false
            completeButton.isEnabled = false
            return
        }
        if (!taskStarted) {
            startButton.visibility = View.VISIBLE
            startButton.text = "开始巡检"
            startButton.isEnabled = true
            pauseButton.visibility = View.GONE
            form.removeAllViews()
            assetCodeInput.visibility = View.GONE
            confirmationCheck.visibility = View.GONE
            noteInput.visibility = View.GONE
            faultTimeInput.visibility = View.GONE
            photoButton.isEnabled = false
            completeButton.isEnabled = false
            detail.text = "设备：${payload.asset.name}$schedule\n共 ${payload.steps.size} 个步骤\n确认设备和日程后开始巡检"
            return
        }
        if (taskPaused) {
            startButton.visibility = View.VISIBLE
            startButton.text = "继续巡检"
            startButton.isEnabled = true
            pauseButton.visibility = View.GONE
            form.removeAllViews()
            assetCodeInput.visibility = View.GONE
            confirmationCheck.visibility = View.GONE
            noteInput.visibility = View.GONE
            photoButton.isEnabled = false
            completeButton.isEnabled = false
            detail.text = "巡检已暂停，当前步骤和草稿已保存；继续后从原步骤恢复"
            return
        }
        startButton.visibility = View.GONE
        pauseButton.visibility = View.VISIBLE
        pauseButton.isEnabled = !awaitingStepAck
        completeButton.isEnabled = !awaitingStepAck
        photoButton.isEnabled = !awaitingStepAck
        detail.text = "设备：${payload.asset.name}$schedule\n步骤 ${currentStepIndex + 1}/${payload.steps.size}：${step.title}\n${step.instruction}"
        renderStepForm(step)
    }

    private fun renderStepForm(step: InspectionStep) {
        form.removeAllViews()
        readingInputs.clear()
        val savedDraft = draftsByStep[step.id]
        selectedResult = savedDraft?.result
        noteInput.setText(savedDraft?.note.orEmpty())
        faultTimeInput.setText(savedDraft?.faultTime.orEmpty())
        assetCodeInput.setText(savedDraft?.assetCode.orEmpty())
        confirmationCheck.isChecked = savedDraft?.confirmed == true
        assetCodeInput.hint = "请输入或扫描现场资产码"
        assetCodeInput.visibility = if (step.type == "asset_check") View.VISIBLE else View.GONE
        confirmationCheck.visibility = if (step.type == "asset_check" || step.type == "review_submit") View.VISIBLE else View.GONE
        confirmationCheck.text = if (step.type == "review_submit") "我已核对以上巡检记录" else "我已核对设备名称和位置"
        noteInput.visibility = if (step.requirements.abnormalNoteRequired) View.VISIBLE else View.GONE
        updateFaultTimeVisibility(step)

        step.fields.forEach { field -> addField(field) }
        savedDraft?.readings.orEmpty().forEach { (fieldId, value) ->
            when (val input = readingInputs[fieldId]) {
                is EditText -> input.setText(value)
                is Spinner -> {
                    val position = (0 until input.count).firstOrNull { index ->
                        input.getItemAtPosition(index)?.toString() == value
                    }
                    if (position != null) input.setSelection(position)
                }
            }
        }
        if (step.requirements.resultRequired) {
            val label = TextView(this).apply {
                text = "检查结果"
                textSize = 17f
                setTextColor(FG)
                setPadding(0, 12, 0, 4)
            }
            form.addView(label)
            val resultRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(
                "normal" to "正常",
                "abnormal" to "异常",
                "shutdown" to "停机",
                "standby" to "待机",
                "not_applicable" to "不涉及",
            ).forEach { (value, labelText) ->
                resultRow.addView(Button(this).apply {
                    text = labelText
                    setOnClickListener {
                        selectedResult = value
                        val needsNote = value in setOf("abnormal", "shutdown", "standby", "not_applicable")
                        noteInput.hint = if (value == "abnormal") "异常说明（必填）" else "状态原因（必填）"
                        noteInput.visibility = if (needsNote && step.requirements.abnormalNoteRequired) View.VISIBLE else View.GONE
                        updateFaultTimeVisibility(step)
                        detail.text = "已选择：$labelText；确认后提交当前步骤"
                    }
                })
            }
            form.addView(resultRow)
        }
    }

    private fun currentReadings(): Map<String, String> = readingInputs.mapValues { (_, view) ->
        when (view) {
            is EditText -> view.text.toString()
            is Spinner -> if (view.selectedItemPosition == 0) "" else view.selectedItem?.toString().orEmpty()
            else -> ""
        }
    }

    private fun currentAssetCode(): String? = assetCodeInput.text.toString().trim().takeIf { it.isNotBlank() }

    private fun currentConfirmed(): Boolean = confirmationCheck.isChecked

    private fun currentFaultTime(): String? = faultTimeInput.text.toString().trim().takeIf { it.isNotBlank() }

    private fun updateFaultTimeVisibility(step: InspectionStep) {
        val hasSource = currentTask?.let {
            (ProtocolCodec.payload(it, TaskDispatchPayload::class.java) as TaskDispatchPayload).template.source != null
        } == true
        faultTimeInput.visibility = if (hasSource && selectedResult == "abnormal") View.VISIBLE else View.GONE
    }

    /** Keep a partially entered step recoverable across Activity/SDK restarts. */
    private fun persistCurrentDraft() {
        val task = currentTask ?: return
        val payload = ProtocolCodec.payload(task, TaskDispatchPayload::class.java) as TaskDispatchPayload
        val step = payload.steps.getOrNull(currentStepIndex) ?: return
        val readings = currentReadings().filterValues { it.isNotBlank() }
        val draft = GlassTaskStore.DraftState(
            result = selectedResult,
            readings = readings,
            note = noteInput.text.toString().takeIf { it.isNotBlank() },
            assetCode = currentAssetCode(),
            confirmed = currentConfirmed(),
            faultTime = currentFaultTime(),
        )
        draftsByStep[step.id] = draft
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, awaitingCompletion, draftsByStep, started = taskStarted, paused = taskPaused, completed = taskCompleted)) {
            detail.text = "当前步骤草稿保存失败；请重试后再离开页面"
            return
        }
        if (taskStarted && ::transport.isInitialized) {
            transport.sendStepDraft(
                task.taskId.orEmpty(),
                StepDraftPayload(
                    expectedStepId = step.id,
                    revision = currentStepIndex + 1,
                    result = draft.result,
                    readings = draft.readings,
                    note = draft.note,
                    photoIds = photoIdsByStep[step.id].orEmpty(),
                    assetCode = draft.assetCode,
                    confirmed = draft.confirmed,
                    faultTime = draft.faultTime,
                ),
            )
        }
    }

    private fun addField(field: ReadingField) {
        form.addView(TextView(this).apply {
            text = buildString {
                append(field.label)
                if (!field.unit.isNullOrBlank()) append(" (${field.unit})")
            }
            textSize = 17f
            setTextColor(FG)
            setPadding(0, 12, 0, 4)
        })
        if (field.kind == "select") {
            val choices = listOf("请选择") + field.options
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
            readingInputs[field.id] = spinner
            form.addView(spinner)
        } else {
            val input = EditText(this).apply {
                textSize = 17f
                setTextColor(FG)
                setHintTextColor(MUTED)
                hint = if (field.kind == "number") "请输入数值" else "请输入${field.label}"
                inputType = if (field.kind == "number") {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                } else {
                    InputType.TYPE_CLASS_TEXT
                }
            }
            readingInputs[field.id] = input
            form.addView(input)
        }
    }

    private fun completeCurrentStep() {
        if (taskPaused) {
            resumeTask()
            return
        }
        if (!taskStarted) {
            startTask()
            return
        }
        if (awaitingStepAck) return
        if (awaitingCompletion != null) {
            retryPendingCompletion()
            return
        }
        val task = currentTask ?: return
        val payload = ProtocolCodec.payload(task, TaskDispatchPayload::class.java) as TaskDispatchPayload
        val step = payload.steps.getOrNull(currentStepIndex) ?: return
        val readings = currentReadings()
        draftsByStep[step.id] = GlassTaskStore.DraftState(
            result = selectedResult,
            readings = readings.filterValues { it.isNotBlank() },
            note = noteInput.text.toString().takeIf { it.isNotBlank() },
            assetCode = currentAssetCode(),
            confirmed = currentConfirmed(),
            faultTime = currentFaultTime(),
        )
        val photoIds = photoIdsByStep[step.id].orEmpty()
        val errors = buildList {
            if (step.type == "asset_check") {
                if (currentAssetCode() != payload.asset.code) {
                    add("资产码不匹配，请核对目标设备 ${payload.asset.name}（${payload.asset.code}）")
                }
                if (!currentConfirmed()) add("请确认设备名称和位置一致")
            }
            if (step.type == "review_submit" && !currentConfirmed()) {
                add("请确认已核对全部巡检记录")
            }
            addAll(
                StepValidator.validate(
                    step,
                    selectedResult,
                    readings,
                    noteInput.text.toString(),
                    photoIds,
                    currentFaultTime(),
                ),
            )
        }
        if (errors.isNotEmpty()) {
            detail.text = "不能完成：\n" + errors.joinToString("\n")
            return
        }
        val completion = StepCompletePayload(
            expectedStepId = step.id,
            revision = currentStepIndex + 1,
            result = selectedResult,
            readings = readings.filterValues { it.isNotBlank() },
            note = noteInput.text.toString().takeIf { it.isNotBlank() },
            photoIds = photoIds,
            assetCode = currentAssetCode(),
            confirmed = currentConfirmed(),
            faultTime = currentFaultTime(),
            completedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                .format(java.util.Date()),
        )
        awaitingStepAck = true
        awaitingCompletion = completion
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, completion, draftsByStep, started = taskStarted, paused = taskPaused, completed = taskCompleted)) {
            awaitingStepAck = false
            awaitingCompletion = null
            completeButton.isEnabled = true
            photoButton.isEnabled = true
            detail.text = "本步骤本地保存失败，未发送；请重试"
            return
        }
        completeButton.isEnabled = false
        photoButton.isEnabled = false
        detail.text = "正在等待手机确认保存…"
        transport.sendStepComplete(task.taskId.orEmpty(), completion)
        scheduleAckTimeout()
    }

    private fun retryPendingCompletion(force: Boolean = false) {
        val task = currentTask ?: return
        val completion = awaitingCompletion ?: return
        if (awaitingStepAck && !force) return
        awaitingStepAck = true
        completeButton.isEnabled = false
        photoButton.isEnabled = false
        detail.text = "正在重试提交 ${completion.expectedStepId}…"
        transport.sendStepComplete(task.taskId.orEmpty(), completion)
        scheduleAckTimeout()
    }

    private fun scheduleAckTimeout() {
        ackTimeoutHandler.removeCallbacks(ackTimeout)
        ackTimeoutHandler.postDelayed(ackTimeout, 8_000L)
    }

    private fun handleStepAck(taskId: String, ack: StepAckPayload): Boolean {
        val task = currentTask ?: return false
        val completion = awaitingCompletion ?: return false
        if (taskId != task.taskId.orEmpty()) return false
        if (ack.expectedStepId != completion.expectedStepId || ack.revision != completion.revision) return false
        ackTimeoutHandler.removeCallbacks(ackTimeout)
        awaitingStepAck = false
        awaitingCompletion = null
        if (!ack.accepted) {
            if (!taskStore.save(task, currentStepIndex, photoIdsByStep, null, draftsByStep, started = taskStarted, paused = taskPaused, completed = taskCompleted)) {
                detail.text = "手机拒绝本步骤，但眼镜本地状态保存失败；请重试"
                awaitingCompletion = completion
                completeButton.isEnabled = true
                photoButton.isEnabled = true
                return false
            }
            detail.text = "手机未接受本步骤：${ack.reason.orEmpty()}"
            completeButton.isEnabled = true
            photoButton.isEnabled = true
            return true
        }
        currentStepIndex++
        val completedDraft = draftsByStep.remove(completion.expectedStepId)
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, null, draftsByStep, started = taskStarted, paused = taskPaused, completed = taskCompleted)) {
            // Keep the original completion so a retry is idempotent after the
            // phone has already accepted it.
            currentStepIndex--
            if (completedDraft != null) draftsByStep[completion.expectedStepId] = completedDraft
            awaitingCompletion = completion
            detail.text = "手机已确认，但眼镜本地保存失败；请重试同步"
            completeButton.isEnabled = true
            photoButton.isEnabled = true
            return false
        }
        renderTask(task)
        return true
    }

    private fun capturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        captureCurrentPhoto()
    }

    private fun captureCurrentPhoto() {
        val task = currentTask ?: return
        val payload = ProtocolCodec.payload(task, TaskDispatchPayload::class.java) as TaskDispatchPayload
        val step = payload.steps.getOrNull(currentStepIndex) ?: return
        photoCapture.captureAndSend(task.taskId.orEmpty(), step.id)
    }

    private fun handlePhotoAck(taskId: String, ack: PhotoAckPayload): Boolean {
        val task = currentTask ?: return false
        if (taskId != task.taskId.orEmpty()) return false
        val stepId = pendingPhotoSteps[ack.photoId] ?: return false
        if (!ack.accepted) {
            // A negative ACK is terminal for this photo. Invalid bytes and
            // task mismatches must not remain in the outbox and retry forever.
            if (!photoCapture.markAcknowledged(ack.photoId)) {
                detail.text = "照片拒绝结果未能落盘，等待重试"
                return false
            }
            pendingPhotoSteps.remove(ack.photoId)
            detail.text = "照片未被手机接受：${ack.reason.orEmpty()}"
            return true
        }
        val nextPhotos = photoIdsByStep.mapValues { (_, ids) -> ids.toMutableList() }.toMutableMap()
        val ids = nextPhotos.getOrPut(stepId) { mutableListOf() }
        if (ack.photoId !in ids) ids += ack.photoId
        if (!taskStore.save(task, currentStepIndex, nextPhotos, awaitingCompletion, draftsByStep, started = taskStarted, paused = taskPaused, completed = taskCompleted)) {
            detail.text = "照片已收到确认，但眼镜本地保存失败；请重试"
            return false
        }
        // Remove the durable outbox item only after the task snapshot is safe.
        if (!photoCapture.markAcknowledged(ack.photoId)) {
            detail.text = "照片确认已收到，但待发送队列清理失败；将自动重试"
            return false
        }
        pendingPhotoSteps.remove(ack.photoId)
        photoIdsByStep.clear()
        photoIdsByStep.putAll(nextPhotos)
        detail.text = "本步骤照片 ${ids.size} 张已校验保存"
        return true
    }

    private fun handleTaskComplete(taskId: String, payload: TaskCompletePayload): Boolean {
        val task = currentTask ?: return false
        if (taskId != task.taskId.orEmpty()) return false
        if (!payload.accepted) {
            detail.text = "手机暂未确认任务完成：${payload.reason.orEmpty()}"
            // task.complete keeps one stable commandId across retries. Do not
            // put a negative terminal notice in the replay ledger, otherwise
            // a later accepted retry with the same commandId could be hidden.
            return false
        }
        val dispatch = runCatching {
            ProtocolCodec.payload(task, TaskDispatchPayload::class.java) as TaskDispatchPayload
        }.getOrNull() ?: return false
        if (currentStepIndex != dispatch.steps.size || awaitingStepAck || awaitingCompletion != null) {
            detail.text = "手机发送了过早的任务完成通知，已等待本地步骤确认"
            return false
        }
        if (taskCompleted) {
            detail.text = "任务已完成，手机已确认全部步骤保存"
            return true
        }
        taskCompleted = true
        if (!taskStore.save(task, currentStepIndex, photoIdsByStep, null, draftsByStep, started = taskStarted, paused = taskPaused, completed = true)) {
            taskCompleted = false
            detail.text = "手机已确认任务完成，但眼镜本地状态保存失败；将等待重试"
            return false
        }
        renderTask(task)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                // Glass3's temple swipe is delivered as a DPAD event. Keep
                // it at the UI-navigation layer so a swipe never bypasses
                // validation by submitting a step implicitly.
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (moveFocus(forward = true)) return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (moveFocus(forward = false)) return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    completeCurrentStep()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveFocus(forward: Boolean): Boolean {
        val focused = window.decorView.findFocus()
        val direction = if (forward) View.FOCUS_RIGHT else View.FOCUS_LEFT
        val candidate = focused?.focusSearch(direction)
            ?: if (forward) startButton else completeButton
        if (candidate.visibility != View.VISIBLE || !candidate.isEnabled) {
            return false
        }
        candidate.requestFocus()
        candidate.post { candidate.parent?.requestChildFocus(candidate, candidate) }
        return true
    }

    override fun onResume() {
        super.onResume()
        // A field inspection can pause while waiting for a reading or photo;
        // the near-eye display must remain available for the active step.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        ackTimeoutHandler.removeCallbacks(ackTimeout)
        if (::photoCapture.isInitialized) photoCapture.release()
        if (::transport.isInitialized) transport.release()
        super.onDestroy()
    }
}
