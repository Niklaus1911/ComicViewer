package com.comicviewer

const val TAP_THROTTLE_MS = 350L

fun shouldAcceptThrottledAction(
    nowMs: Long,
    lastAcceptedAtMs: Long,
    throttleMs: Long = TAP_THROTTLE_MS,
): Boolean = nowMs - lastAcceptedAtMs >= throttleMs
