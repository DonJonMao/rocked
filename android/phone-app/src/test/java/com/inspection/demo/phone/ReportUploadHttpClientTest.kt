package com.inspection.demo.phone

import com.inspection.demo.protocol.InspectionSyncEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ReportUploadHttpClientTest {
    private lateinit var endpoint: LocalEndpoint

    @Before
    fun setUp() {
        endpoint = LocalEndpoint()
        endpoint.start()
    }

    @After
    fun tearDown() {
        endpoint.stop()
    }

    @Test
    fun postCarriesIdempotencyAuthAndReportBody() = runBlocking {
        endpoint.status = 201
        val entry = entry("report-1", "{\"taskId\":\"task-1\"}")

        val result = ReportUploadHttpClient.upload(endpoint.url(), "secret-token", entry, enforceHttps = false)

        assertTrue(result is ReportUploadResult.Success)
        assertEquals("POST", endpoint.method)
        assertEquals("application/json; charset=utf-8", endpoint.headers["content-type"])
        assertEquals("report-1", endpoint.headers["idempotency-key"])
        assertEquals("Bearer secret-token", endpoint.headers["authorization"])
        assertEquals(entry.reportJson, endpoint.body)
    }

    @Test
    fun transientAndPermanentStatusesMapToWorkerOutcomes() = runBlocking {
        val entry = entry("report-2", "{}")

        endpoint.status = 500
        val retry = ReportUploadHttpClient.upload(endpoint.url(), "", entry, enforceHttps = false)
        assertTrue(retry is ReportUploadResult.Retry)
        assertTrue((retry as ReportUploadResult.Retry).reason.contains("HTTP 500"))

        endpoint.status = 422
        val rejected = ReportUploadHttpClient.upload(endpoint.url(), "", entry, enforceHttps = false)
        assertTrue(rejected is ReportUploadResult.PermanentFailure)
        assertTrue((rejected as ReportUploadResult.PermanentFailure).reason.contains("HTTP 422"))

        endpoint.status = 302
        val redirect = ReportUploadHttpClient.upload(endpoint.url(), "", entry, enforceHttps = false)
        assertTrue(redirect is ReportUploadResult.PermanentFailure)
        assertEquals("报告上传不允许 HTTP 重定向", (redirect as ReportUploadResult.PermanentFailure).reason)
    }

    @Test
    fun productionModeRejectsLoopbackHttpBeforeOpeningConnection() = runBlocking {
        val result = ReportUploadHttpClient.upload(endpoint.url(), "", entry("report-3", "{}"))

        assertTrue(result is ReportUploadResult.PermanentFailure)
        assertTrue((result as ReportUploadResult.PermanentFailure).reason.contains("HTTPS"))
        assertEquals(null, endpoint.method)
    }

    private fun entry(reportId: String, json: String) = InspectionSyncEntry(
        reportId = reportId,
        taskId = reportId,
        reportJson = json,
        enqueuedAtEpochMs = 1L,
    )

    private class LocalEndpoint {
        private val server = ServerSocket(0, 1, InetSocketAddress("127.0.0.1", 0).address)
        @Volatile private var running = false
        private var thread: Thread? = null
        var status: Int = 200
        var method: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String = ""

        fun start() {
            running = true
            thread = Thread {
                while (running) {
                    runCatching { server.accept().use(::handle) }
                }
            }.apply { isDaemon = true; start() }
        }

        fun stop() {
            running = false
            server.close()
            thread?.join(1_000L)
        }

        fun url() = "http://127.0.0.1:${server.localPort}/reports"

        private fun handle(socket: Socket) {
            val input = socket.getInputStream()
            val headerBytes = readHeaders(input)
            val headerText = headerBytes.toString(StandardCharsets.ISO_8859_1)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            method = requestLine.substringBefore(' ')
            val parsedHeaders = linkedMapOf<String, String>()
            lines.drop(1).filter { it.isNotEmpty() }.forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) parsedHeaders[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
            }
            headers = parsedHeaders
            val length = parsedHeaders["content-length"]?.toIntOrNull() ?: 0
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < bytes.size) {
                val read = input.read(bytes, offset, bytes.size - offset)
                if (read <= 0) break
                offset += read
            }
            body = bytes.copyOf(offset).toString(StandardCharsets.UTF_8)
            val response = if (status in 300..399) "redirect" else "response"
            val responseBytes = response.toByteArray(StandardCharsets.UTF_8)
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 $status\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            output.write(responseBytes)
            output.flush()
        }

        private fun readHeaders(input: InputStream): ByteArray {
            val bytes = ByteArrayOutputStream()
            var matched = 0
            while (matched < 4) {
                val next = input.read()
                if (next < 0) break
                bytes.write(next)
                matched = when {
                    matched == 0 && next == '\r'.code -> 1
                    matched == 1 && next == '\n'.code -> 2
                    matched == 2 && next == '\r'.code -> 3
                    matched == 3 && next == '\n'.code -> 4
                    next == '\r'.code -> 1
                    else -> 0
                }
            }
            val data = bytes.toByteArray()
            return if (data.size >= 4) data.copyOf(data.size - 4) else data
        }
    }
}
