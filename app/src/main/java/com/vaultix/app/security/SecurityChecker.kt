package com.vaultix.app.security

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Security checks: root detection, emulator detection, debugger detection.
 * These are best-effort checks; sophisticated attackers may bypass them.
 */
@Singleton
class SecurityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Comprehensive security check.
     * Returns list of detected threats.
     */
    fun performSecurityChecks(): List<SecurityThreatInfo> {
        val threats = mutableListOf<SecurityThreatInfo>()

        if (isRooted()) threats.add(SecurityThreatInfo(SecurityThreat.ROOT_DETECTED, "Device is rooted, compromising Keystore integrity.", SecuritySeverity.HIGH))
        if (isHookingFrameworkDetected()) threats.add(SecurityThreatInfo(SecurityThreat.HOOKING_DETECTED, "Active hooking framework (Frida/Xposed) detected. Possible active tampering.", SecuritySeverity.CRITICAL))
        if (isDebuggerAttached()) threats.add(SecurityThreatInfo(SecurityThreat.DEBUGGER_ATTACHED, "A debugger is currently attached to the process.", SecuritySeverity.HIGH))
        if (isEmulator()) threats.add(SecurityThreatInfo(SecurityThreat.EMULATOR_DETECTED, "Running on an emulator is less secure than physical hardware.", SecuritySeverity.LOW))
        if (isDebuggableBuild()) threats.add(SecurityThreatInfo(SecurityThreat.DEBUGGABLE_BUILD, "This build of the app is debuggable.", SecuritySeverity.MEDIUM))
        if (areDeveloperOptionsEnabled()) threats.add(SecurityThreatInfo(SecurityThreat.DEVELOPER_OPTIONS_ENABLED, "Developer options are enabled.", SecuritySeverity.LOW))

        return threats
    }

    private fun isRooted(): Boolean {
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
            "/system/bin/.ext/.su", "/system/usr/we-need-root/su-backup",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk", "/system/app/Magisk.apk"
        )
        for (path in suPaths) {
            if (File(path).exists()) return true
        }

        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) return true

        if (File("/sbin/.magisk").exists() || File("/data/adb/magisk").exists()) return true

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val result = process.inputStream.bufferedReader().readLine()
            process.destroy()
            result != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Advanced Hooking Detection (Frida, Xposed)
     */
    private fun isHookingFrameworkDetected(): Boolean {
        // 1. Check for Frida default port
        try {
            val socket = java.net.Socket("127.0.0.1", 27042)
            socket.close()
            return true // Frida is running
        } catch (e: Exception) {
            // Port not open
        }

        // 2. Check loaded libraries for frida/xposed
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val maps = mapsFile.readText()
                if (maps.contains("frida") || maps.contains("xposed")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Check for Xposed classes
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            return true
        } catch (e: ClassNotFoundException) {
            // Not found
        }
        try {
            Class.forName("com.saurik.substrate.MS$2")
            return true
        } catch (e: ClassNotFoundException) {
            // Not found
        }

        return false
    }

    private fun isDebuggerAttached(): Boolean = android.os.Debug.isDebuggerConnected()

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.BOARD.lowercase().contains("nox")
                || Build.BRAND.startsWith("generic"))
    }

    private fun isDebuggableBuild(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun areDeveloperOptionsEnabled(): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }
}

data class SecurityThreatInfo(
    val type: SecurityThreat,
    val message: String,
    val severity: SecuritySeverity
)

enum class SecurityThreat {
    ROOT_DETECTED,
    HOOKING_DETECTED,
    DEBUGGER_ATTACHED,
    EMULATOR_DETECTED,
    DEBUGGABLE_BUILD,
    DEVELOPER_OPTIONS_ENABLED
}

enum class SecuritySeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}
