package com.inspection.demo.protocol

import java.net.URL

/** Pure validation/classification rules shared by the uploader and tests. */
object ReportSyncPolicy {
    enum class Disposition { SUCCESS, RETRY, REJECT }

    fun endpointError(rawEndpoint: String): String? {
        val endpoint = rawEndpoint.trim()
        if (endpoint.isBlank()) return "报告上传地址为空"
        val url = runCatching { URL(endpoint) }.getOrNull()
            ?: return "报告上传地址无效"
        if (!url.protocol.equals("https", ignoreCase = true)) return "报告上传仅允许 HTTPS"
        if (url.host.isBlank()) return "报告上传地址缺少主机名"
        if (!url.userInfo.isNullOrBlank()) return "报告上传地址不允许携带用户信息"
        return null
    }

    fun classifyHttpStatus(status: Int): Disposition = when {
        status in 200..299 -> Disposition.SUCCESS
        status in 300..399 -> Disposition.REJECT
        status == 408 || status == 429 || status >= 500 -> Disposition.RETRY
        else -> Disposition.REJECT
    }
}
