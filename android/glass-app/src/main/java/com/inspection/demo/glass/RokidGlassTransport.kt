package com.inspection.demo.glass

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.inspection.demo.protocol.DeviceHeartbeatPayload
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.PhotoAckPayload
import com.inspection.demo.protocol.ProtocolCommandIds
import com.inspection.demo.protocol.ProtocolReplayLedger
import com.inspection.demo.protocol.StepAckPayload
import com.inspection.demo.protocol.StepCompletePayload
import com.inspection.demo.protocol.StepDraftPayload
import com.inspection.demo.protocol.TaskCompletePayload
import com.rokid.security.glass3.open.sdk.GlassSdk
import com.rokid.security.glass3.open.sdk.client.IServiceConnectionCallback
import com.rokid.security.system.server.IClientCallback
import com.rokid.security.system.server.message.listener.IMessageListener
import com.rokid.security.system.server.wifip2p.callback.IResultCallback as P2pResultCallback

class RokidGlassTransport(private val context: Context) {
    companion object {
        private const val DEFAULT_CLIENT_ID = "InspectionGlass"
        private const val KEY_REPLAY_KEYS = "keys"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    var onStatus: (String) -> Unit = {}
    var onDispatch: (ProtocolEnvelope) -> Unit = {}
    /** Return true only after the caller has durably applied the ACK. */
    var onStepAck: (String, StepAckPayload) -> Boolean = { _, _ -> true }
    var onPhotoAck: (String, PhotoAckPayload) -> Boolean = { _, _ -> true }
    /** Return true only after the caller has durably applied task completion. */
    var onTaskComplete: (String, TaskCompletePayload) -> Boolean = { _, _ -> true }
    var onReady: () -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private val rokidClientId = BuildConfig.ROKID_CLIENT_ID.trim().ifBlank { DEFAULT_CLIENT_ID }
    private val replayPreferences = context.getSharedPreferences("inspection_message_replay", Context.MODE_PRIVATE)
    private val replayLedger = ProtocolReplayLedger(
        replayPreferences.getStringSet(KEY_REPLAY_KEYS, emptySet()).orEmpty(),
    )
    @Volatile private var listenersRegistered = false
    @Volatile private var registrationInFlight = false
    @Volatile private var clientReady = false
    @Volatile private var readyNotified = false
    @Volatile private var released = false
    private val retryRegistration = Runnable {
        if (!released) initialize()
    }
    private val heartbeat = object : Runnable {
        override fun run() {
            if (released || !clientReady) return
            sendHeartbeat()
            main.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun scheduleRegistrationRetry(reason: String) {
        if (released) return
        main.removeCallbacks(retryRegistration)
        onStatus(reason)
        main.postDelayed(retryRegistration, 2_000L)
    }
    private val clientCallback = object : IClientCallback.Stub() {
        override fun onReady() {
            main.post {
                if (released) return@post
                clientReady = true
                onStatus("眼镜 SDK client 已就绪")
                if (!listenersRegistered) register()
                else notifyReady()
            }
        }
    }
    private val messageListener = object : IMessageListener.Stub() {
        override fun onTextMessage(msg: String) {
            runCatching { ProtocolCodec.decode(msg) }
                .onSuccess { envelope -> main.post { handleInboundEnvelope(envelope) } }
                .onFailure { error ->
                    main.post {
                        if (!released) onStatus("收到无效巡检消息：${error.message.orEmpty()}")
                    }
                }
        }
        override fun onAudioStream(buffer: ByteArray) = Unit
        override fun onStreamDataReceived(tag: String, data: ByteArray) = Unit
    }

    /** Keep malformed payloads and application callback failures inside the SDK boundary. */
    private fun handleInboundEnvelope(envelope: ProtocolEnvelope) {
        if (released) return
        runCatching {
            when (envelope.type) {
                "task.dispatch" -> onDispatch(envelope)
                "step.ack" -> {
                    if (!replayLedger.isRemembered(envelope)) {
                        val payload = ProtocolCodec.payload(envelope, StepAckPayload::class.java) as StepAckPayload
                        val expectedCommandId = ProtocolCommandIds.stepAck(
                            envelope.taskId.orEmpty(),
                            payload.expectedStepId,
                            payload.revision,
                            payload.accepted,
                        )
                        check(envelope.commandId == expectedCommandId) { "步骤 ACK commandId 不匹配" }
                        val applied = runCatching { onStepAck(envelope.taskId.orEmpty(), payload) }.getOrDefault(false)
                        if (applied) rememberReplay(envelope)
                    }
                }
                "photo.ack" -> {
                    if (!replayLedger.isRemembered(envelope)) {
                        val payload = ProtocolCodec.payload(envelope, PhotoAckPayload::class.java) as PhotoAckPayload
                        val expectedCommandId = ProtocolCommandIds.photoAck(payload.photoId, payload.accepted)
                        check(envelope.commandId == expectedCommandId) { "照片 ACK commandId 不匹配" }
                        val applied = runCatching { onPhotoAck(envelope.taskId.orEmpty(), payload) }.getOrDefault(false)
                        if (applied) rememberReplay(envelope)
                    }
                }
                "task.complete" -> {
                    if (!replayLedger.isRemembered(envelope)) {
                        val payload = ProtocolCodec.payload(envelope, TaskCompletePayload::class.java) as TaskCompletePayload
                        val taskId = envelope.taskId.orEmpty()
                        val expectedCommandId = ProtocolCommandIds.taskComplete(taskId)
                        check(envelope.commandId == expectedCommandId) { "任务完成 commandId 不匹配" }
                        val applied = runCatching { onTaskComplete(taskId, payload) }.getOrDefault(false)
                        if (applied) rememberReplay(envelope)
                    }
                }
            }
        }.onFailure { error ->
            if (!released) onStatus("巡检消息无法处理：${error.message.orEmpty()}")
        }
    }

    fun initialize() {
        if (released) return
        if (listenersRegistered) {
            notifyReady()
            return
        }
        if (GlassSdk.isReady()) { register(); return }
        GlassSdk.bindSecurityService(context, object : IServiceConnectionCallback {
            override fun onServiceConnected() {
                if (!released) register()
            }
            override fun onServiceDisconnected() {
                if (released) return
                main.removeCallbacks(heartbeat)
                listenersRegistered = false
                registrationInFlight = false
                clientReady = false
                readyNotified = false
                scheduleRegistrationRetry("眼镜 SDK 服务断开，正在重试绑定")
            }
            override fun onBindingDied() {
                if (released) return
                main.removeCallbacks(heartbeat)
                listenersRegistered = false
                registrationInFlight = false
                clientReady = false
                readyNotified = false
                scheduleRegistrationRetry("眼镜 SDK 绑定失效，正在重试")
            }
        })
    }

    private fun register() {
        if (released || listenersRegistered || registrationInFlight) return
        val messageService = GlassSdk.getGlassMessageService() ?: run {
            scheduleRegistrationRetry("眼镜消息服务尚未就绪，等待 SDK 回调重试")
            return
        }
        registrationInFlight = true
        runCatching {
            GlassSdk.registerClient(rokidClientId, clientCallback)
            messageService.setMessageListener(messageListener)
            listenersRegistered = true
            onStatus("眼镜消息监听已注册，等待 SDK client 就绪")
            // registerClient is asynchronous. If the callback was delivered
            // synchronously, the posted callback will see listenersRegistered;
            // otherwise notifyReady is called by clientCallback.onReady().
            notifyReady()
        }.onFailure {
            if (!released) {
                // Registration can fail after the message listener has been
                // attached. Remove it before retrying, otherwise every
                // reconnect may deliver the same payload multiple times.
                runCatching { messageService.removeMessageListener(messageListener) }
                listenersRegistered = false
                scheduleRegistrationRetry("眼镜消息监听注册失败：${it.message.orEmpty()}；正在重试")
            }
        }
        registrationInFlight = false
    }

    private fun notifyReady() {
        if (released || !listenersRegistered || !clientReady || readyNotified) return
        readyNotified = true
        onStatus("眼镜端已就绪，等待任务")
        main.removeCallbacks(heartbeat)
        main.post(heartbeat)
        onReady()
    }

    /** Periodically expose real device liveness; this is not a task ACK. */
    private fun sendHeartbeat() {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "glass-${Build.MODEL.ifBlank { "unknown" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        if (battery == null) {
            // BatteryManager returns Integer.MIN_VALUE when the property is not
            // supported. Never turn an unknown reading into 0%, because the
            // phone UI would present that as a real low-battery warning. Keep
            // the next scheduled heartbeat as the retry point.
            onStatus("无法读取眼镜电量，本次心跳已跳过")
            return
        }
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "unknown" }
        val envelope = ProtocolEnvelope(
            type = "device.heartbeat",
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            deviceId = deviceId,
            payload = ProtocolCodec.json(DeviceHeartbeatPayload(appVersion, "ready", battery)),
        )
        sendControlEnvelope(envelope, "SecurityPhone", "device.heartbeat")
    }

    fun sendTaskAck(taskId: String, accepted: Boolean = true, reason: String? = null) {
        send("task.ack", taskId, ProtocolCommandIds.taskAck(taskId, accepted), mapOf("accepted" to accepted, "reason" to reason))
    }

    fun sendTaskStart(taskId: String) {
        send("task.start", taskId, ProtocolCommandIds.taskStart(taskId), mapOf("accepted" to true))
    }

    fun sendTaskPause(taskId: String) {
        send("task.pause", taskId, ProtocolCommandIds.taskPause(taskId), mapOf("accepted" to true))
    }

    fun sendTaskResume(taskId: String) {
        send("task.resume", taskId, ProtocolCommandIds.taskResume(taskId), mapOf("accepted" to true))
    }

    fun sendStepComplete(taskId: String, payload: StepCompletePayload) {
        send("step.complete", taskId, ProtocolCommandIds.stepComplete(taskId, payload.expectedStepId, payload.revision), payload)
    }

    fun sendStepDraft(taskId: String, payload: StepDraftPayload) {
        // Drafts are best-effort and may be emitted repeatedly as the user
        // leaves/re-enters the Activity. They never advance the step.
        send("step.draft", taskId, "draft-$taskId-${payload.expectedStepId}-${payload.revision}-${System.currentTimeMillis()}", payload)
    }

    private fun send(type: String, taskId: String, commandId: String, payload: Any) {
        if (released) return
        val envelope = ProtocolEnvelope(
            type = type,
            commandId = commandId,
            taskId = taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = ProtocolCodec.json(payload),
        )
        sendControlEnvelope(envelope, "SecurityPhone", type)
    }

    private fun sendControlEnvelope(envelope: ProtocolEnvelope, clientId: String, label: String) {
        if (released) return
        val service = GlassSdk.getGlassMessageService() ?: run {
            onStatus("$label 未发送：消息服务不可用")
            return
        }
        val message = ProtocolCodec.encode(envelope)
        val classicReady = runCatching {
            GlassSdk.getClassicBluetoothService()?.isConnect() == true
        }.getOrDefault(false)
        if (classicReady) {
            runCatching {
                service.sendTextMessageByClassicBTWithClient(message, clientId)
            }.onSuccess {
                if (!released) onStatus("已发送 $label")
            }.onFailure { error ->
                if (!released) onStatus("$label 发送失败：${error.message.orEmpty()}")
            }
            return
        }
        val p2p = GlassSdk.getP2PGoService() ?: run {
            onStatus("$label 未发送：蓝牙和 P2P 消息通道均未连接")
            return
        }
        runCatching {
            p2p.isConnect(object : P2pResultCallback.Stub() {
                override fun onResult(connected: Boolean) {
                    if (released) return
                    if (!connected) {
                        onStatus("$label 未发送：蓝牙和 P2P 消息通道均未连接")
                        return
                    }
                    runCatching {
                        service.sendTextMessageByP2PWithClient(message, clientId)
                    }.onSuccess {
                        if (!released) onStatus("已通过 P2P 发送 $label")
                    }.onFailure { error ->
                        if (!released) onStatus("$label P2P 发送失败：${error.message.orEmpty()}")
                    }
                }
            })
        }.onFailure { error ->
            if (!released) onStatus("$label P2P 状态查询失败：${error.message.orEmpty()}")
        }
    }

    fun release() {
        released = true
        main.removeCallbacks(heartbeat)
        main.removeCallbacksAndMessages(null)
        if (listenersRegistered) {
            GlassSdk.getGlassMessageService()?.removeMessageListener(messageListener)
        }
        listenersRegistered = false
        registrationInFlight = false
        clientReady = false
        readyNotified = false
        runCatching {
            if (GlassSdk.isReady()) GlassSdk.release()
        }
    }

    private fun rememberReplay(envelope: ProtocolEnvelope): Boolean {
        val isNew = replayLedger.remember(envelope)
        if (isNew) {
            // ACKs are only UI/state notifications on the glasses side. A
            // duplicate is safe to suppress, and the ledger survives an
            // Activity/SDK restart so reconnects do not replay old ACKs.
            replayPreferences.edit()
                .putStringSet(KEY_REPLAY_KEYS, replayLedger.snapshot())
                // The callback is only considered applied after the replay
                // identity has crossed the process-death boundary. This is
                // a tiny set (bounded by ProtocolReplayLedger), so the
                // synchronous commit is preferable to a crash window here.
                .commit()
        }
        return isNew
    }

}
