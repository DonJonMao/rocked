package com.inspection.demo.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSyncPolicyTest {
    @Test
    fun endpointRequiresHttpsHostAndNoUserInfo() {
        assertNull(ReportSyncPolicy.endpointError("https://inspection.example.com/api/reports"))
        assertTrue(ReportSyncPolicy.endpointError("http://inspection.example.com/api")!!.contains("HTTPS"))
        assertTrue(ReportSyncPolicy.endpointError("https:///missing-host")!!.contains("主机名"))
        assertTrue(ReportSyncPolicy.endpointError("https://user:secret@inspection.example.com/api")!!.contains("用户信息"))
        assertTrue(ReportSyncPolicy.endpointError(" ")!!.contains("为空"))
    }

    @Test
    fun statusClassificationMatchesQueueSemantics() {
        assertEquals(ReportSyncPolicy.Disposition.SUCCESS, ReportSyncPolicy.classifyHttpStatus(200))
        assertEquals(ReportSyncPolicy.Disposition.SUCCESS, ReportSyncPolicy.classifyHttpStatus(299))
        assertEquals(ReportSyncPolicy.Disposition.REJECT, ReportSyncPolicy.classifyHttpStatus(302))
        assertEquals(ReportSyncPolicy.Disposition.RETRY, ReportSyncPolicy.classifyHttpStatus(408))
        assertEquals(ReportSyncPolicy.Disposition.RETRY, ReportSyncPolicy.classifyHttpStatus(429))
        assertEquals(ReportSyncPolicy.Disposition.RETRY, ReportSyncPolicy.classifyHttpStatus(503))
        assertEquals(ReportSyncPolicy.Disposition.REJECT, ReportSyncPolicy.classifyHttpStatus(400))
    }
}
