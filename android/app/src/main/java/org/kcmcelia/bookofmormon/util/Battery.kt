package org.kcmcelia.bookofmormon.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import org.kcmcelia.bookofmormon.R

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
        .onFailure { openAppSettings(context) }
}

fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** The guide that matches this phone, since every Chinese OEM hides autostart somewhere else. */
fun manufacturerGuideRes(): Int {
    val maker = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return when {
        maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") ->
            R.string.battery_guide_xiaomi

        maker.contains("huawei") || maker.contains("honor") ->
            R.string.battery_guide_huawei

        maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus") ->
            R.string.battery_guide_oppo

        maker.contains("vivo") || maker.contains("iqoo") ->
            R.string.battery_guide_vivo

        maker.contains("samsung") -> R.string.battery_guide_samsung
        else -> R.string.battery_guide_generic
    }
}
