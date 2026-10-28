package com.providers.network

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlin.coroutines.cancellation.CancellationException

/** Keep successful media even if a later backup fails; failures stay in the native player flow. */
internal suspend fun withNativePlayback(
    callback: (ExtractorLink) -> Unit,
    extract: suspend ((ExtractorLink) -> Unit) -> Boolean,
): Boolean {
    var emitted = false
    var failure: Exception? = null
    try {
        extract { emitted = true; callback(it) }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        failure = error
    }
    if (emitted) return true
    val reason = failure?.message?.takeIf { it.isNotBlank() }
        ?: "หน้าเว็บไม่ส่งลิงก์วิดีโอที่ตัวอ่านรองรับ"
    throw ErrorLoadingException("ยังอ่านลิงก์วิดีโอไม่ได้ ($reason)")
}
