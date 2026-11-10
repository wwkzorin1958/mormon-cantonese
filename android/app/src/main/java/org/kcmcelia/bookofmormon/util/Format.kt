package org.kcmcelia.bookofmormon.util

import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

fun formatHours(seconds: Double): String =
    String.format(Locale.ROOT, "%.0f", seconds / 3600.0)

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format(Locale.ROOT, "%.0f MB", bytes / 1_000_000.0)
    else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
}

/** Estimated size once encoded at 48 kbps mono, which is what the downloads will cost. */
fun estimatedEncodedBytes(durationSec: Double): Long = (durationSec * 48_000 / 8).toLong()
