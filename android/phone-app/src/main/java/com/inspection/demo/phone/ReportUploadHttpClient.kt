package com.inspection.demo.phone

import com.inspection.demo.protocol.InspectionSyncEntry
import com.inspection.demo.protocol.ReportSyncPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Result of one report HTTP attempt, kept independent from WorkManager. */
internal sealed interface ReportUploadResult {
    data object Success : ReportUploadResult
    data class Retry(val reason: String) : ReportUploadResult
    data class PermanentFailure(val reason: String) : ReportUploadResult
}

/**
 * Performs the concrete POST used by the WorkManager worker.
 *
 * Endpoint validation remains enabled in production. The opt-out exists only
 * for the device-free loopback test, which uses a plain local HTTP server to
 * verify request headers, body and status handling without a certificate.
 */
internal object ReportUploadHttpClient {
    suspend fun upload(
        endpoint: String,
        token: String,
        entry: InspectionSyncEntry,
        enforceHttps: Boolean = true,
    ): ReportUploadResult = withContext(Dispatchers.IO) {
        if (enforceHttps) {
            ReportSyncPolicy.endpointError(endpoint)?.let {
                return@withContext ReportUploadResult.PermanentFailure(it)
            }
        }
        val parsedUrl = runCatching { URL(endpoint) }.getOrElse { error ->
            return@withContext ReportUploadResult.PermanentFailure(error.message ?: "报告上传地址无效")
        }
        runCatching {
            val connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Idempotency-Key", entry.reportId)
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                connection.outputStream.use { output ->
                    output.write(entry.reportJson.toByteArray(StandardCharsets.UTF_8))
                }
                val status = connection.responseCode
                val reason = (if (status >= 400) connection.errorStream else connection.inputStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?.take(500)
                    .orEmpty()
                when (ReportSyncPolicy.classifyHttpStatus(status)) {
                    ReportSyncPolicy.Disposition.SUCCESS -> ReportUploadResult.Success
                    ReportSyncPolicy.Disposition.RETRY -> ReportUploadResult.Retry("HTTP $status $reason")
                    ReportSyncPolicy.Disposition.REJECT -> {
                        val message = if (status in 300..399) {
                            "报告上传不允许 HTTP 重定向"
                        } else {
                            "HTTP $status $reason"
                        }
                        ReportUploadResult.PermanentFailure(message)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error -> ReportUploadResult.Retry(error.message ?: "网络请求失败") }
    }
}
