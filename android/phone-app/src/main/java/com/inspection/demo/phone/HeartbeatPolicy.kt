package com.inspection.demo.phone

/** Pure liveness rule shared by the phone UI and its unit tests. */
object HeartbeatPolicy {
    const val TIMEOUT_MS = 90_000L

    fun isStale(lastHeartbeatAtEpochMs: Long, nowEpochMs: Long): Boolean =
        lastHeartbeatAtEpochMs <= 0L || nowEpochMs - lastHeartbeatAtEpochMs >= TIMEOUT_MS
}
