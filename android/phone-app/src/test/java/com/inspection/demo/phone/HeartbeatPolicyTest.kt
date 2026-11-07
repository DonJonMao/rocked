package com.inspection.demo.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatPolicyTest {
    @Test
    fun unknownHeartbeatIsStale() {
        assertTrue(HeartbeatPolicy.isStale(0L, 1L))
    }

    @Test
    fun heartbeatRemainsFreshUntilTheTimeoutBoundary() {
        assertFalse(HeartbeatPolicy.isStale(1_000L, 1_000L + HeartbeatPolicy.TIMEOUT_MS - 1L))
        assertTrue(HeartbeatPolicy.isStale(1_000L, 1_000L + HeartbeatPolicy.TIMEOUT_MS))
    }

    @Test
    fun clockMovingBackwardsDoesNotCreateAFalseTimeout() {
        assertFalse(HeartbeatPolicy.isStale(10_000L, 9_000L))
    }
}
