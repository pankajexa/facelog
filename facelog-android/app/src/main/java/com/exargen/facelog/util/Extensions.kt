package com.exargen.facelog.util

import android.graphics.Bitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Safe bitmap copy that handles null config (HARDWARE bitmaps) and copy failures.
 */
@Suppress("USELESS_ELVIS")
fun Bitmap.safeCopy(mutable: Boolean = false): Bitmap? {
    return try {
        // config can be null for HARDWARE bitmaps despite Kotlin's non-null annotation
        val cfg = config ?: Bitmap.Config.ARGB_8888
        copy(cfg, mutable)
    } catch (_: Exception) {
        null
    }
}

fun Long.toISTString(): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return sdf.format(Date(this))
}

fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return sdf.format(Date(this))
}
