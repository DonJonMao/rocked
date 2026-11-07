package com.inspection.demo.phone

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.BitmapFactory
import android.net.wifi.p2p.WifiP2pDevice
import android.annotation.SuppressLint
import com.google.gson.Gson
import com.inspection.demo.protocol.DeviceHeartbeatPayload
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolEnvelope
import com.inspection.demo.protocol.ProtocolCommandIds
import com.inspection.demo.protocol.PhotoAckPayload
import com.inspection.demo.protocol.PhotoIntegrity
import com.inspection.demo.protocol.PhotoTransferMatcher
import com.inspection.demo.protocol.PhotoUploadPayload
import com.inspection.demo.protocol.StepAckPayload
import com.inspection.demo.protocol.StepCompletePayload
import com.inspection.demo.protocol.TaskCompletePayload
import com.inspection.demo.protocol.TaskDispatchPayload
import com.rokid.security.phone.sdk.api.PSecuritySDK
import com.rokid.security.phone.sdk.api.bluetooth.classic.listener.IClassicBTClientListener
import com.rokid.security.phone.sdk.api.msg.listener.IMessageListener
import com.rokid.security.phone.sdk.api.msg.listener.FileReceiveV2Listener
import com.rokid.security.phone.sdk.api.wifip2p.listener.IWifiP2PClientListener
import com.rokid.security.phone.sdk.base.data.EngineParam
import com.rokid.security.phone.sdk.base.data.EnvType
import com.rokid.security.phone.sdk.base.data.NetServiceType
import com.rokid.security.phone.sdk.base.data.UserAuthInfo
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RokidPhoneTransport(private val context: Context) {
    companion object {
        private const val DEFAULT_CLIENT_ID = "InspectionGlass"
        private const val ORPHAN_MAX_AGE_MS = 24L * 60L * 60L * 1000L
        private const val ORPHAN_MAX_BYTES = 64L * 1024L * 1024L
    }

    private data class PendingPhoto(
        val photoId: String,
        val taskId: String,
        val payload: PhotoUploadPayload,
    )

    private data class PhotoLock(
        val monitor: Any = Any(),
        var users: Int = 0,
    )

    var onStatus: (String) -> Unit = {}
    var onMessage: (ProtocolEnvelope) -> Unit = {}
    var onHeartbeat: (String, DeviceHeartbeatPayload) -> Unit = { _, _ -> }
    var onPhotoSaved: (String, File, PhotoUploadPayload) -> Boolean = { _, _, _ -> false }
    /** Called when a control channel becomes usable after a reconnect. */
    var onLinkReady: () -> Unit = {}
    private val pendingPhotoIds = ConcurrentHashMap<String, PendingPhoto>()
    private val photoLocks = ConcurrentHashMap<String, PhotoLock>()
    private val orphanReceivedFiles = ConcurrentHashMap<String, String>()
    private val gson = Gson()
    private val rokidClientId = BuildConfig.ROKID_CLIENT_ID.trim().ifBlank { DEFAULT_CLIENT_ID }
    private val pendingPreferences = context.getSharedPreferences("pending_photo_transfers", Context.MODE_PRIVATE)
    @Volatile private var p2pConnecting = false
    @Volatile private var listenersRegistered = false
    @Volatile private var initializationInFlight = false
    @Volatile private var released = false

    private val classicListener = object : IClassicBTClientListener {
        override fun onDeviceFound(device: BluetoothDevice) { if (!released) scanTarget?.invoke(device) }
        override fun onScanFinished() = Unit
        override fun onConnect(success: Boolean) {
            if (released) return
            onStatus(if (success) "蓝牙已连接" else "蓝牙连接失败")
            if (success) {
                // Classic Bluetooth is already a valid control channel. The
                // caller can safely retry the idempotent task.dispatch now;
                // P2P setup below is only needed for media and large payloads.
                onLinkReady()
                connectP2p()
            }
        }
        override fun onConnectionRejected(reason: String, code: Int) {
            if (!released) onStatus("蓝牙连接被拒绝：$reason ($code)")
        }
    }

    private val p2pListener = object : IWifiP2PClientListener {
        override fun onWifiP2pEnabled(enabled: Boolean) {
            if (released) return
            onStatus(if (enabled) "P2P 服务已启用" else "P2P 服务未启用")
        }
    }

    private val messageListener = object : IMessageListener {
        override fun onP2PTextMessage(msg: String, clientId: String) {
            handleTextMessage(msg, clientId)
        }

        override fun onClassicBTTextMessage(msg: String, clientId: String) {
            handleTextMessage(msg, clientId)
        }

        private fun handleTextMessage(msg: String, clientId: String) {
            if (released) return
            runCatching {
                val envelope = ProtocolCodec.decode(msg)
                if (released) return@runCatching
                if (envelope.type == "photo.upload") {
                    val payload = ProtocolCodec.payload(envelope, PhotoUploadPayload::class.java) as PhotoUploadPayload
                    check(payload.photoId.isNotBlank()) { "照片消息缺少 photoId" }
                    check(envelope.commandId == ProtocolCommandIds.photoUpload(payload.photoId)) { "照片消息 commandId 不匹配" }
                    payload.photoId.takeIf { it.isNotBlank() }?.let { photoId ->
                        val tag = "inspection-photo:$photoId"
                        registerPending(tag, PendingPhoto(photoId, envelope.taskId.orEmpty(), payload))
                        val orphanPath = takeOrphanFor(payload)
                        if (orphanPath != null) {
                            handleReceivedPhotoFile(orphanPath)
                        }
                    }
                }
                if (envelope.type == "device.heartbeat") {
                    val deviceId = envelope.deviceId?.trim().orEmpty()
                    check(deviceId.isNotBlank()) { "设备心跳缺少 deviceId" }
                    val heartbeat = ProtocolCodec.payload(envelope, DeviceHeartbeatPayload::class.java) as DeviceHeartbeatPayload
                    onHeartbeat(deviceId, heartbeat)
                }
                onMessage(envelope)
            }.onFailure { error ->
                if (!released) onStatus("收到无效巡检消息：${error.message.orEmpty()}")
            }
        }
        override fun onBTStreamDataReceived(tag: String, data: ByteArray, clientId: String) {
            if (released) return
            val pending = pendingPhotoIds[tag] ?: loadPending(tag)
            if (pending == null) { onStatus("收到未关联数据流：$tag，${data.size} bytes"); return }
            runCatching { processPhotoBytes(pending, data, clientId, tag) }
                .onFailure {
                    rejectPhoto(pending, clientId, tag, it.message ?: "照片校验或保存失败")
                }
        }
    }

    private val fileReceiveListener = object : FileReceiveV2Listener {
        override fun onStart(filePath: String) { if (!released) onStatus("开始接收文件：${File(filePath).name}") }
        override fun onProgressChanged(filePath: String, progress: Float) = Unit
        override fun onComplete(filePath: String) { if (!released) handleReceivedPhotoFile(filePath) }
        override fun onFail() { if (!released) onStatus("照片文件接收失败") }
        override fun onCancel(filePath: String) { if (!released) onStatus("照片文件接收被取消：${File(filePath).name}") }
    }

    private var scanTarget: ((BluetoothDevice) -> Boolean)? = null

    fun initialize(onReady: (Boolean) -> Unit) {
        if (released) {
            onStatus("手机 SDK 已释放，请重新创建页面")
            onReady(false)
            return
        }
        if (initializationInFlight) {
            onStatus("手机 SDK 正在初始化，请稍候")
            return
        }
        initializationInFlight = true
        if (BuildConfig.ROKID_APP_ID.isBlank() || BuildConfig.ROKID_APP_SECRET.isBlank()) {
            onStatus("未配置企业 AK/SK；请填写 android/local.properties")
            initializationInFlight = false
            onReady(false)
            return
        }
        val service = PSecuritySDK.getMobileEngineService() ?: run {
            onStatus("Rokid 手机 SDK 服务不可用")
            initializationInFlight = false
            onReady(false)
            return
        }
        if (service?.isInit() == true) {
            finishInitialization(onReady, registerListeners())
            return
        }
        val params = EngineParam(
            clientIds = arrayListOf("SecurityPhone", rokidClientId),
            userAuthInfo = UserAuthInfo(BuildConfig.ROKID_APP_ID, BuildConfig.ROKID_APP_SECRET),
            envType = EnvType.Companion.PUBLIC,
            banServiceList = arrayListOf(NetServiceType.TranslateService),
        )
        runCatching {
            service.initSDK(params) { result ->
                if (result.isSuccess) {
                    finishInitialization(onReady, registerListeners())
                } else {
                    initializationInFlight = false
                    if (!released) {
                        onStatus("Rokid SDK 初始化失败")
                        onReady(false)
                    }
                }
            }
        }.onFailure { error ->
            initializationInFlight = false
            if (!released) {
                onStatus("Rokid SDK 初始化异常：${error.message.orEmpty()}")
                onReady(false)
            }
        }
    }

    private fun registerListeners(): Boolean {
        if (released) return false
        if (listenersRegistered) return true
        val messageService = PSecuritySDK.getMessageService() ?: run {
            onStatus("Rokid 消息服务尚未就绪，请稍后重试初始化")
            return false
        }
        return runCatching {
            PSecuritySDK.getClassicBlueToothClientService()?.addClientListener(classicListener)
            PSecuritySDK.getWifiP2PClientService()?.addWifiP2PClientListener(p2pListener)
            messageService.addMessageListener(messageListener)
            messageService.getFileOperater()?.addFileReceiveV2Listener(fileReceiveListener)
            messageService.getBtFileOperater()?.addFileReceiveV2Listener(fileReceiveListener)
            listenersRegistered = true
            onStatus("SDK 已就绪；请先连接 Glass3")
            true
        }.onFailure { error ->
            // A partially registered listener set must not make the next
            // initialization attempt register duplicates or crash the UI.
            listenersRegistered = false
            runCatching { PSecuritySDK.getClassicBlueToothClientService()?.removeClientListener(classicListener) }
            runCatching { PSecuritySDK.getWifiP2PClientService()?.removeWifiP2PClientListener(p2pListener) }
            runCatching { messageService.removeMessageListener(messageListener) }
            runCatching { messageService.getFileOperater()?.removeFileReceiveV2Listener(fileReceiveListener) }
            runCatching { messageService.getBtFileOperater()?.removeFileReceiveV2Listener(fileReceiveListener) }
            onStatus("Rokid 消息监听注册失败：${error.message.orEmpty()}；请重新初始化")
        }.getOrDefault(false)
    }

    private fun finishInitialization(onReady: (Boolean) -> Unit, success: Boolean) {
        initializationInFlight = false
        if (!released) onReady(success)
    }

    fun sendDispatch(payload: TaskDispatchPayload, taskId: String) {
        val envelope = ProtocolEnvelope(
            type = "task.dispatch",
            commandId = ProtocolCommandIds.dispatch(taskId),
            taskId = taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = ProtocolCodec.json(payload),
        )
        val message = ProtocolCodec.encode(envelope)
        sendControlText(message, rokidClientId, "task.dispatch") {
            onStatus("已发送 task.dispatch；等待眼镜确认")
        }
    }

    /**
     * Reconcile a link that was already connected before this Activity or SDK
     * listener was initialized. This lets the caller recover a durable task
     * after a process restart without requiring a second physical reconnect.
     */
    fun notifyLinkReadyIfConnected() {
        if (released) return
        val classicReady = runCatching {
            PSecuritySDK.getClassicBlueToothClientService()?.isMessageChanelConnect() == true
        }.getOrDefault(false)
        if (classicReady) {
            onLinkReady()
            return
        }
        val p2p = PSecuritySDK.getWifiP2PClientService() ?: return
        runCatching {
            p2p.isConnect { connected ->
                if (connected && !released) onLinkReady()
            }
        }.onFailure { error ->
            if (!released) onStatus("P2P 状态查询失败：${error.message.orEmpty()}")
        }
    }

    private fun sendPhotoAck(taskId: String, photo: PhotoUploadPayload, clientId: String, accepted: Boolean = true, reason: String? = null) {
        val envelope = ProtocolEnvelope(
            type = "photo.ack",
            commandId = ProtocolCommandIds.photoAck(photo.photoId, accepted),
            taskId = taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = ProtocolCodec.json(PhotoAckPayload(photo.photoId, accepted, reason)),
        )
        sendControlText(ProtocolCodec.encode(envelope), clientId, "photo.ack")
    }

    @Suppress("ApplySharedPref")
    private fun registerPending(tag: String, pending: PendingPhoto) {
        pendingPhotoIds[tag] = pending
        // Metadata must survive a process kill between the text callback and
        // the file callback; otherwise a valid photo can remain unacknowledged.
        pendingPreferences.edit().putString(tag, gson.toJson(pending)).commit()
    }

    private fun loadPending(tag: String): PendingPhoto? {
        val raw = pendingPreferences.getString(tag, null) ?: return null
        return runCatching { gson.fromJson(raw, PendingPhoto::class.java) }.getOrNull()?.also { pendingPhotoIds[tag] = it }
    }

    @Suppress("ApplySharedPref")
    private fun clearPending(tag: String): Boolean {
        pendingPhotoIds.remove(tag)
        orphanReceivedFiles.remove(tag)
        // Callers commit this index before sending the terminal ACK, so a
        // process death cannot leave a stale pending photo after the glasses
        // have already discarded their outbox item.
        return pendingPreferences.edit().remove(tag).remove(orphanKey(tag)).commit()
    }

    private fun orphanKey(tag: String): String = "orphan:$tag"

    private fun handleReceivedPhotoFile(filePath: String) {
        if (released) return
        cleanupOrphans()
        val source = File(filePath)
        val sourceName = source.name
        val photoId = source.nameWithoutExtension
        val tag = "inspection-photo:$photoId"
        val pending = pendingPhotoIds[tag]
            ?: loadPending(tag)
            ?: loadPendingForFile(sourceName)
        if (pending == null) {
            saveOrphan(filePath)
            onStatus("收到照片文件，等待元数据：${source.name}")
            return
        }
        val pendingTag = "inspection-photo:${pending.photoId}"
        runCatching {
            check(source.exists() && source.length() > 0L) { "照片文件为空" }
            processPhotoBytes(pending, source.readBytes(), rokidClientId, pendingTag)
        }.onFailure {
            if (!released) rejectPhoto(pending, rokidClientId, pendingTag, it.message ?: "照片校验或保存失败")
        }.also {
            // Files copied into the durable orphan directory are temporary
            // correlation buffers. Remove them after either acceptance or a
            // terminal negative ACK; SDK-managed source files remain owned by
            // the SDK and are never deleted here.
            deleteOwnedOrphan(source)
        }
    }

    private fun saveOrphan(filePath: String) {
        cleanupOrphans()
        val source = File(filePath)
        if (!source.exists() || source.length() == 0L) {
            onStatus("收到空照片文件，等待元数据失败：${source.name}")
            return
        }
        // Keep the original basename for PhotoTransferMatcher; isolate each
        // copy in a UUID directory so two SDK callbacks with the same name
        // cannot overwrite one another.
        val directory = File(orphanDirectory(), UUID.randomUUID().toString()).apply { mkdirs() }
        val safeName = source.name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
            .ifBlank { "photo.bin" }
        val durable = File(directory, safeName)
        runCatching {
            source.copyTo(durable, overwrite = false)
            val key = "inspection-photo-orphan:${durable.parentFile?.name}:${durable.name}"
            orphanReceivedFiles[key] = durable.absolutePath
            // The metadata callback can arrive after a process kill, so this
            // path must be committed before returning from the SDK callback.
            check(pendingPreferences.edit().putString(orphanKey(key), durable.absolutePath).commit()) {
                "照片临时文件索引保存失败"
            }
        }.onFailure {
            durable.delete()
            onStatus("照片文件暂存失败：${it.message.orEmpty()}")
        }
    }

    private fun orphanDirectory(): File = File(context.filesDir, "pending-photo-files")

    private fun deleteOwnedOrphan(file: File) {
        val root = orphanDirectory().absolutePath.trimEnd(File.separatorChar) + File.separator
        if (file.absolutePath.startsWith(root)) file.delete()
    }

    /** Bound file-first correlation buffers when metadata never arrives. */
    private fun cleanupOrphans(nowEpochMs: Long = System.currentTimeMillis()) {
        val indexed = pendingPreferences.all.entries.asSequence()
            .filter { (key, _) -> key.startsWith("orphan:") }
            .mapNotNull { (key, value) ->
                (value as? String)?.let { path -> Triple(key, path, File(path)) }
            }
            .toList()
        if (indexed.isEmpty()) return

        val ordered = indexed.sortedBy { (_, _, file) -> file.lastModified() }
        var totalBytes = ordered.sumOf { (_, _, file) -> file.takeIf(File::exists)?.length() ?: 0L }
        val remove = linkedMapOf<String, File>()
        ordered.forEach { (key, path, file) ->
            val stale = !file.exists() ||
                (file.lastModified() > 0L && nowEpochMs - file.lastModified() >= ORPHAN_MAX_AGE_MS)
            if (stale || (totalBytes > ORPHAN_MAX_BYTES && file.exists())) {
                remove[key] = file
                totalBytes = (totalBytes - file.length()).coerceAtLeast(0L)
                orphanReceivedFiles.entries.removeIf { it.value == path }
            }
        }
        if (remove.isEmpty()) return
        val editor = pendingPreferences.edit()
        remove.forEach { (key, file) ->
            editor.remove(key)
            if (file.exists()) file.delete()
        }
        editor.commit()
    }

    /** Find metadata persisted before a file callback, even if the SDK renamed the file. */
    private fun loadPendingForFile(fileName: String): PendingPhoto? {
        val inMemory = pendingPhotoIds.values.firstOrNull { PhotoTransferMatcher.matches(it.payload, fileName) }
        if (inMemory != null) return inMemory
        return pendingPreferences.all.entries.asSequence()
            .filter { (key, _) -> key.startsWith("inspection-photo:") }
            .mapNotNull { (_, raw) -> runCatching { gson.fromJson(raw as? String, PendingPhoto::class.java) }.getOrNull() }
            .firstOrNull { PhotoTransferMatcher.matches(it.payload, fileName) }
            ?.also { pendingPhotoIds["inspection-photo:${it.photoId}"] = it }
    }

    /** Take the first orphan whose filename or exact photo id matches metadata. */
    private fun takeOrphanFor(payload: PhotoUploadPayload): String? {
        cleanupOrphans()
        val candidates = buildList {
            addAll(orphanReceivedFiles.entries.map { it.key to it.value })
            addAll(
                pendingPreferences.all.entries.asSequence()
                    .filter { (key, _) -> key.startsWith("orphan:") }
                    .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                    .toList(),
            )
        }
        val match = candidates.firstOrNull { (_, path) ->
            PhotoTransferMatcher.matches(payload, File(path).name)
        } ?: return null
        val (key, path) = match
        orphanReceivedFiles.remove(key)
        val storageKey = if (key.startsWith("orphan:")) key else orphanKey(key)
        pendingPreferences.edit().remove(storageKey).commit()
        return path
    }

    private fun processPhotoBytes(pending: PendingPhoto, data: ByteArray, clientId: String, tag: String) {
        val lock = photoLocks.compute(pending.photoId) { _, current ->
            (current ?: PhotoLock()).also { it.users += 1 }
        } ?: return
        try {
            synchronized(lock.monitor) {
                processPhotoBytesLocked(pending, data, clientId, tag)
            }
        } finally {
            photoLocks.computeIfPresent(pending.photoId) { _, current ->
                current.users -= 1
                if (current.users == 0) null else current
            }
        }
    }

    /** Serialize duplicate P2P/Bluetooth callbacks for one durable photo id. */
    private fun processPhotoBytesLocked(pending: PendingPhoto, data: ByteArray, clientId: String, tag: String) {
        if (released) return
        val integrityErrors = validatePhotoBytes(pending.payload, data)
        check(integrityErrors.isEmpty()) { integrityErrors.joinToString("；") }
        val directory = File(context.filesDir, "photos").apply { mkdirs() }
        val extension = when (pending.payload.mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val target = File(directory, "${pending.photoId}.$extension")
        if (target.exists() && target.length() == data.size.toLong()) {
            val existingErrors = validatePhotoBytes(pending.payload, target.readBytes())
            if (existingErrors.isEmpty()) {
                if (released) return
                val accepted = onPhotoSaved(pending.taskId, target, pending.payload)
                if (released) return
                if (!accepted) deleteStoredPhoto(pending.photoId)
                if (!clearPending(tag)) {
                    onStatus("照片结果已处理，但待处理索引清理失败；等待重试")
                    return
                }
                sendPhotoAck(pending.taskId, pending.payload, clientId, accepted, if (accepted) null else "任务已切换或本地保存失败")
                onStatus(
                    if (accepted) "照片已存在，重复传输已确认：${target.name}"
                    else "照片未关联到当前任务：${pending.photoId}",
                )
                return
            }
            target.delete()
        }
        val temp = File(directory, "${pending.photoId}.tmp")
        temp.writeBytes(data)
        check(temp.renameTo(target)) { "photo rename failed" }
        if (released) return
        val accepted = onPhotoSaved(pending.taskId, target, pending.payload)
        if (released) return
        if (!accepted) deleteStoredPhoto(pending.photoId)
        if (!clearPending(tag)) {
            onStatus("照片结果已处理，但待处理索引清理失败；等待重试")
            return
        }
        sendPhotoAck(pending.taskId, pending.payload, clientId, accepted, if (accepted) null else "任务已切换或本地保存失败")
        onStatus(if (accepted) "照片已保存：${target.name}" else "照片未关联到当前任务：${pending.photoId}")
    }

    /**
     * Every photo transfer must reach a terminal protocol state. Without a
     * negative ACK, the glasses would retain and resend a permanently invalid
     * file after every SDK or Activity restart.
     */
    private fun rejectPhoto(pending: PendingPhoto, clientId: String, tag: String, reason: String) {
        if (released) return
        val normalized = reason.ifBlank { "照片校验或保存失败" }
        // A callback can fail after the bytes have been renamed into the
        // private photo directory (for example, Room persistence throws).
        // Negative ACKs are terminal, so remove only this photo's artifacts
        // and do not leave an unassociated file behind.
        deleteStoredPhoto(pending.photoId)
        if (!clearPending(tag)) {
            onStatus("照片拒绝结果未能落盘，等待重试")
            return
        }
        sendPhotoAck(pending.taskId, pending.payload, clientId, accepted = false, reason = normalized)
        onStatus("照片已拒绝：$normalized")
    }

    private fun deleteStoredPhoto(photoId: String) {
        if (photoId.isBlank()) return
        val directory = File(context.filesDir, "photos")
        directory.listFiles()?.filter { file ->
            file.nameWithoutExtension == photoId || file.name == "$photoId.tmp"
        }?.forEach { it.delete() }
    }

    private fun validatePhotoBytes(payload: PhotoUploadPayload, bytes: ByteArray): List<String> {
        val errors = PhotoIntegrity.validate(payload, bytes).toMutableList()
        if (errors.isNotEmpty()) return errors
        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bitmap == null) {
            errors += "照片无法解码"
        } else {
            if (!options.outMimeType.equals(payload.mimeType, ignoreCase = true)) {
                errors += "照片实际格式与声明不一致"
            }
            if (bitmap.width != payload.width || bitmap.height != payload.height) {
                errors += "照片尺寸与元数据不一致"
            }
            bitmap.recycle()
        }
        return errors
    }

    fun sendStepAck(
        taskId: String,
        payload: StepCompletePayload,
        accepted: Boolean = true,
        reason: String? = null,
        onSent: () -> Unit = {},
    ) {
        val envelope = ProtocolEnvelope(
            type = "step.ack",
            commandId = ProtocolCommandIds.stepAck(taskId, payload.expectedStepId, payload.revision, accepted),
            taskId = taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = ProtocolCodec.json(StepAckPayload(payload.expectedStepId, payload.revision, accepted, reason)),
        )
        sendControlText(ProtocolCodec.encode(envelope), rokidClientId, "step.ack", onSent)
    }

    fun sendTaskComplete(taskId: String) {
        val envelope = ProtocolEnvelope(
            type = "task.complete",
            commandId = ProtocolCommandIds.taskComplete(taskId),
            taskId = taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = ProtocolCodec.json(TaskCompletePayload(accepted = true)),
        )
        sendControlText(ProtocolCodec.encode(envelope), rokidClientId, "task.complete")
    }

    private fun sendControlText(message: String, clientId: String, label: String, onSent: () -> Unit = {}) {
        if (released) {
            return
        }
        val service = PSecuritySDK.getMessageService() ?: run {
            onStatus("$label 未发送：消息服务不可用")
            return
        }
        val classicReady = runCatching {
            PSecuritySDK.getClassicBlueToothClientService()?.isMessageChanelConnect() == true
        }.getOrDefault(false)
        if (classicReady) {
            runCatching {
                service.sendTextMessageByClassicBT(message, clientId)
            }.onSuccess {
                if (!released) onSent()
            }.onFailure { error ->
                if (!released) onStatus("$label 发送失败：${error.message.orEmpty()}")
            }
            return
        }
        val p2p = PSecuritySDK.getWifiP2PClientService() ?: run {
            onStatus("$label 未发送：蓝牙和 P2P 消息通道均未连接")
            return
        }
        runCatching {
            p2p.isConnect { connected ->
                if (released) return@isConnect
                if (!connected) {
                    onStatus("$label 未发送：蓝牙和 P2P 消息通道均未连接")
                    return@isConnect
                }
                runCatching {
                    service.sendTextMessageByP2P(message, clientId)
                }.onSuccess {
                    if (!released) onSent()
                }.onFailure { error ->
                    if (!released) onStatus("$label P2P 发送失败：${error.message.orEmpty()}")
                }
            }
        }.onFailure { error ->
            if (!released) onStatus("$label P2P 状态查询失败：${error.message.orEmpty()}")
        }
    }

    @SuppressLint("MissingPermission")
    fun scanAndConnect() {
        if (released) return
        val service = PSecuritySDK.getClassicBlueToothClientService()
        if (service == null) { onStatus("蓝牙服务不可用"); return }
        onStatus("正在扫描 Glass3…")
        // The listener receives every nearby device. Stop at the first Glass3 device;
        // production UI should show a selectable list instead.
        scanTarget = { device ->
            if (released) {
                false
            } else {
                val name = runCatching { device.name.orEmpty() }.getOrDefault("")
                if (name.contains("Glass3", ignoreCase = true) || name.contains("Rokid", ignoreCase = true)) {
                    scanTarget = null
                    service.stopScan()
                    onStatus("正在连接 $name…")
                    service.connectToServer(device) { success ->
                        if (!released) onStatus(if (success) "蓝牙已连接：$name" else "蓝牙连接失败：$name")
                    }
                    true
                } else false
            }
        }
        service.startScan(10_000L)
    }

    /**
     * Glass3 uses classic Bluetooth for control messages and a separate
     * Wi-Fi P2P session for large media transfers. The official sample first
     * checks the existing state and only then sends the pairing request.
     */
    fun connectP2p() {
        if (released) return
        if (p2pConnecting) return
        val service = PSecuritySDK.getWifiP2PClientService() ?: run {
            onStatus("P2P 服务不可用")
            return
        }
        p2pConnecting = true
        runCatching {
            service.isConnect { connected ->
                if (released) return@isConnect
                if (connected) {
                    p2pConnecting = false
                    onStatus("P2P 已连接")
                    onLinkReady()
                    return@isConnect
                }
                onStatus("正在建立 P2P…")
                service.sendConnectP2pRequest { success ->
                    if (released) return@sendConnectP2pRequest
                    p2pConnecting = false
                    onStatus(if (success) "P2P 已连接" else "P2P 连接失败")
                    if (success) onLinkReady()
                }
            }
        }.onFailure { error ->
            p2pConnecting = false
            if (!released) onStatus("P2P 状态查询失败：${error.message.orEmpty()}")
        }
    }

    fun disconnectP2p() {
        if (released) return
        p2pConnecting = false
        PSecuritySDK.getWifiP2PClientService()?.disconnect()
        onStatus("P2P 已断开")
    }

    fun release() {
        released = true
        scanTarget = null
        p2pConnecting = false
        initializationInFlight = false
        listenersRegistered = false
        PSecuritySDK.getClassicBlueToothClientService()?.stopScan()
        PSecuritySDK.getWifiP2PClientService()?.disconnect()
        PSecuritySDK.getClassicBlueToothClientService()?.removeClientListener(classicListener)
        PSecuritySDK.getWifiP2PClientService()?.removeWifiP2PClientListener(p2pListener)
        PSecuritySDK.getMessageService()?.let { messageService ->
            messageService.removeMessageListener(messageListener)
            messageService.getFileOperater()?.removeFileReceiveV2Listener(fileReceiveListener)
            messageService.getBtFileOperater()?.removeFileReceiveV2Listener(fileReceiveListener)
        }
        PSecuritySDK.getMobileEngineService()?.destroy()
    }
}
