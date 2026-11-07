package com.inspection.demo.glass

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.inspection.demo.protocol.ProtocolCodec
import com.inspection.demo.protocol.ProtocolCommandIds
import com.inspection.demo.protocol.PhotoUploadPayload
import com.inspection.demo.protocol.PhotoIntegrity
import com.inspection.demo.protocol.ProtocolEnvelope
import com.rokid.security.glass3.open.sdk.GlassSdk
import com.rokid.security.glass3.open.sdk.camera.CameraMediaOptions
import com.rokid.security.glass3.sdk.base.data.media.CameraShareConfigV2
import com.rokid.security.glass3.sdk.base.data.media.CameraZoomConfig
import com.rokid.security.glass3.sdk.base.data.media.CameraZoomState
import com.rokid.security.system.server.message.callback.IResultCallback
import com.rokid.security.system.server.message.file.listener.FileReceiveListener
import com.rokid.security.system.server.media.ICameraPhotoCallback
import com.rokid.security.system.server.wifip2p.callback.IResultCallback as P2pResultCallback
import java.io.File
import java.util.UUID

class GlassPhotoCapture(private val context: Context) {
    var onStatus: (String) -> Unit = {}
    var onPhotoSent: (String, String) -> Unit = { _, _ -> }
    private val gson = Gson()
    private val outbox = context.getSharedPreferences("glass_photo_outbox", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var retryScheduled = false
    @Volatile private var released = false

    private data class OutboxEntry(
        val taskId: String,
        val stepId: String,
        val photoId: String,
        val path: String,
        val payload: PhotoUploadPayload,
    )

    /** Durable identity needed to apply a photo.ack after an app restart. */
    data class PendingPhoto(
        val taskId: String,
        val stepId: String,
        val photoId: String,
    )

    fun captureAndSend(taskId: String, stepId: String) {
        if (released) return
        val photoId = "photo-${UUID.randomUUID()}"
        val directory = File(context.filesDir, "photos").apply { mkdirs() }
        val output = File(directory, "$photoId.jpg")
        val config = CameraShareConfigV2(
            previewWidth = 1280,
            previewHeight = 720,
            previewTargetFps = 15,
            zoom = CameraZoomConfig.explicit(1f),
        )
        onStatus("正在拍照…")
        runCatching {
            CameraMediaOptions.takePhoto(config, false, output.absolutePath, 90, object : ICameraPhotoCallback.Stub() {
                override fun onPhoto(path: String?, width: Int, height: Int, state: CameraZoomState?) {
                    if (released) return
                    val file = path?.let(::File) ?: output
                    if (!file.exists() || file.length() == 0L) { onStatus("拍照失败：文件为空"); return }
                    val bytes = file.readBytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val actualWidth = bounds.outWidth.takeIf { it > 0 } ?: width
                    val actualHeight = bounds.outHeight.takeIf { it > 0 } ?: height
                    val actualMime = bounds.outMimeType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                    if (actualWidth <= 0 || actualHeight <= 0) {
                        onStatus("拍照失败：无法读取照片尺寸")
                        return
                    }
                    val payload = PhotoUploadPayload(
                        photoId = photoId,
                        stepId = stepId,
                        fileName = file.name,
                        mimeType = actualMime,
                        width = actualWidth,
                        height = actualHeight,
                        sha256 = PhotoIntegrity.sha256(bytes),
                        sizeBytes = bytes.size.toLong(),
                    )
                    val entry = OutboxEntry(taskId, stepId, photoId, file.absolutePath, payload)
                    if (save(entry)) {
                        send(entry, bytes)
                    } else {
                        onStatus("照片本地队列保存失败，请重试")
                    }
                }

                override fun onError(code: Int, reason: String?) {
                    if (!released) onStatus("拍照失败：$code ${reason.orEmpty()}")
                }
            })
        }.onFailure { error -> if (!released) onStatus("拍照调用失败：${error.message}") }
    }

    private fun send(entry: OutboxEntry, bytes: ByteArray) {
        if (released) return
        val service = GlassSdk.getGlassMessageService() ?: run {
            onStatus("消息服务不可用，照片将稍后重试")
            scheduleRetry(entry.taskId)
            return
        }
        val metadata = ProtocolEnvelope(
            type = "photo.upload",
            commandId = ProtocolCommandIds.photoUpload(entry.photoId),
            taskId = entry.taskId,
            sentAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()),
            payload = ProtocolCodec.json(entry.payload),
        )
        val phoneClientId = "SecurityPhone"
        val tag = "inspection-photo:${entry.photoId}"
        val encodedMetadata = ProtocolCodec.encode(metadata)
        runCatching {
            sendMetadata(service, encodedMetadata, phoneClientId, entry.taskId) {
                if (released) return@sendMetadata
                // Register the photo before sending the bytes. The phone may
                // validate and acknowledge a very small image before the SDK's
                // stream callback returns on the glasses side.
                onPhotoSent(entry.photoId, entry.stepId)
                sendUsingBestFileChannel(service, entry, bytes, tag, phoneClientId)
            }
        }.onFailure { error ->
            if (released) return@onFailure
            Log.w("GlassPhotoCapture", "photo relay failed", error)
            onStatus("照片发送失败，稍后重试：${error.message}")
            scheduleRetry(entry.taskId)
        }
    }

    private fun sendMetadata(
        service: com.rokid.security.system.server.message.IMessageServer,
        message: String,
        phoneClientId: String,
        taskId: String,
        onSent: () -> Unit,
    ) {
        if (released) return
        val classicReady = runCatching {
            GlassSdk.getClassicBluetoothService()?.isConnect() == true
        }.getOrDefault(false)
        if (classicReady) {
            service.sendTextMessageByClassicBTWithClient(message, phoneClientId)
            if (released) return
            onSent()
            return
        }
        val p2pService = GlassSdk.getP2PGoService()
            ?: error("蓝牙和 P2P 消息通道均未连接")
        p2pService.isConnect(object : P2pResultCallback.Stub() {
            override fun onResult(connected: Boolean) {
                if (released) return
                if (!connected) {
                    onStatus("照片元数据未发送：蓝牙和 P2P 消息通道均未连接")
                    scheduleRetry(taskId)
                    return
                }
                runCatching {
                    if (released) return@runCatching
                    service.sendTextMessageByP2PWithClient(message, phoneClientId)
                }.onSuccess {
                    if (released) return@onSuccess
                    onSent()
                }.onFailure { error ->
                    if (released) return@onFailure
                    onStatus("照片元数据 P2P 发送失败：${error.message.orEmpty()}")
                    scheduleRetry(taskId)
                }
            }
        })
    }

    private fun sendUsingBestFileChannel(
        service: com.rokid.security.system.server.message.IMessageServer,
        entry: OutboxEntry,
        bytes: ByteArray,
        tag: String,
        phoneClientId: String,
    ) {
        if (released) return
        val sendStreamFallback: () -> Unit = {
            if (!released) {
                runCatching {
                    service.sendStreamData(tag, bytes, phoneClientId, object : IResultCallback.Stub() {
                        override fun onSuccess(result: Boolean) {
                            if (released) return
                            if (result) {
                                onStatus("照片已通过字节流发送，等待手机校验：${entry.photoId}")
                            } else {
                                // The SDK can invoke onSuccess(false) for a
                                // completed callback whose transfer was rejected.
                                // Keep the durable outbox item and retry instead
                                // of silently waiting forever for an ACK that will
                                // never arrive.
                                onStatus("照片字节流传输未确认，稍后重试：${entry.photoId}")
                            }
                            scheduleRetry(entry.taskId)
                        }
                        override fun onFailed(code: Int, message: String?) {
                            if (released) return
                            onStatus("照片发送失败：$code ${message.orEmpty()}")
                            scheduleRetry(entry.taskId)
                        }
                    })
                }.onFailure { error ->
                    if (released) return@onFailure
                    onStatus("照片字节流调用失败，稍后重试：${error.message.orEmpty()}")
                    scheduleRetry(entry.taskId)
                }
            }
        }
        val sendFile = { operator: com.rokid.security.system.server.message.file.IGlassFileOperate?, channel: String ->
            if (operator == null) {
                sendStreamFallback()
            } else {
                val transferListener = object : FileReceiveListener.Stub() {
                    override fun onStart() { if (!released) onStatus("正在通过${channel}发送照片：${entry.photoId}") }
                    override fun onProgressChanged(progress: Float) = Unit
                    override fun onComplete(filePath: String) {
                        if (released) return
                        onStatus("照片已通过${channel}发送，等待手机校验：${entry.photoId}")
                        scheduleRetry(entry.taskId)
                    }
                    override fun onFail() {
                        if (released) return
                        onStatus("照片${channel}传输失败：${entry.photoId}")
                        scheduleRetry(entry.taskId)
                    }
                    override fun onCancel() {
                        if (released) return
                        onStatus("照片${channel}传输被取消：${entry.photoId}")
                        scheduleRetry(entry.taskId)
                    }
                }
                val resultCallback = object : IResultCallback.Stub() {
                    override fun onSuccess(result: Boolean) {
                        if (released) return
                        if (!result) {
                            onStatus("照片${channel}传输未确认：${entry.photoId}")
                            scheduleRetry(entry.taskId)
                        } else {
                            scheduleRetry(entry.taskId)
                        }
                    }
                    override fun onFailed(code: Int, message: String?) {
                        if (released) return
                        onStatus("照片${channel}传输失败：$code ${message.orEmpty()}")
                        scheduleRetry(entry.taskId)
                    }
                }
                runCatching { operator.sendFile("", entry.path, transferListener, resultCallback) }
                    .onFailure {
                        if (released) return@onFailure
                        onStatus("照片${channel}调用失败，回退字节流：${it.message.orEmpty()}")
                        sendStreamFallback()
                    }
            }
        }
        val p2pService = GlassSdk.getP2PGoService()
        if (p2pService == null) {
            sendFile(service.getGlassBtFileOperater(), "蓝牙文件")
            return
        }
        runCatching {
            p2pService.isConnect(object : P2pResultCallback.Stub() {
                override fun onResult(connected: Boolean) {
                    if (released) return
                    if (connected) sendFile(service.getGlassFileOperater(), "P2P 文件")
                    else sendFile(service.getGlassBtFileOperater(), "蓝牙文件")
                }
            })
        }.onFailure {
            if (released) return@onFailure
            onStatus("P2P 状态查询失败，回退蓝牙文件：${it.message.orEmpty()}")
            sendFile(service.getGlassBtFileOperater(), "蓝牙文件")
        }
    }

    /** Return durable photo-to-step identities before the transport retries. */
    fun pendingPhotos(taskId: String): List<PendingPhoto> =
        outbox.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .mapNotNull { key ->
                runCatching { gson.fromJson(outbox.getString(key, null), OutboxEntry::class.java) }.getOrNull()
            }
            .filter { it.taskId == taskId }
            .map { PendingPhoto(it.taskId, it.stepId, it.photoId) }

    /** Retry files that survived an Activity/SDK restart. */
    fun retryPending(taskId: String) {
        if (released) return
        if (taskId.isBlank()) return
        retryScheduled = false
        outbox.all.keys.filter { it.startsWith(KEY_PREFIX) }.forEach { key ->
            val entry = runCatching { gson.fromJson(outbox.getString(key, null), OutboxEntry::class.java) }.getOrNull()
            if (entry == null) {
                outbox.edit().remove(key).apply()
                return@forEach
            }
            if (entry.taskId != taskId) return@forEach
            val file = File(entry.path)
            if (!file.exists() || file.length() == 0L) {
                outbox.edit().remove(key).apply()
                onStatus("待发送照片已不存在：${entry.photoId}")
                return@forEach
            }
            send(entry, file.readBytes())
        }
    }

    fun markAcknowledged(photoId: String): Boolean {
        // The transport replay ledger is persisted immediately after the
        // callback returns. Commit the outbox deletion first so a process kill
        // cannot leave a retryable file behind while its ACK is already
        // considered replayed.
        return outbox.edit().remove(KEY_PREFIX + photoId).commit()
    }

    /** Retry transport failures while leaving terminal negative ACKs removed. */
    private fun scheduleRetry(taskId: String) {
        if (released) return
        if (retryScheduled) return
        retryScheduled = true
        main.postDelayed({ retryPending(taskId) }, RETRY_DELAY_MS)
    }

    fun release() {
        released = true
        main.removeCallbacksAndMessages(null)
        retryScheduled = false
    }

    private fun save(entry: OutboxEntry): Boolean {
        // The file must be durable before metadata/bytes leave the glasses.
        return outbox.edit().putString(KEY_PREFIX + entry.photoId, gson.toJson(entry)).commit()
    }

    private companion object {
        const val KEY_PREFIX = "photo:"
        const val RETRY_DELAY_MS = 10_000L
    }
}
