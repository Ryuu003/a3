package com.focuslock

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)

    private val systemAllowlist = setOf(
        // Nuestra app
        "com.focuslock",
        // Sistema Android
        "android", "com.android.systemui", "com.android.settings",
        "com.android.packageinstaller", "com.android.permissioncontroller",
        "com.android.keyguard",
        // Launchers
        "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home", "com.sec.android.app.launcher",
        "com.huawei.android.launcher", "com.oppo.launcher", "com.vivo.launcher",
        // Teléfono y llamadas
        "com.android.phone", "com.android.server.telecom", "com.android.incallui",
        "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.emergency",
        // Google servicios base — estos corren en fondo y no son apps reales
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.gms.ui",
        "com.google.process.gapps",
        "com.google.android.packageinstaller",
        "com.google.android.gms.persistent",
        // MIUI servicios
        "com.miui.securitycenter", "com.miui.securityadd",
        "com.miui.daemon", "com.xiaomi.xmsf", "com.miui.guardprovider",
        // Teclados
        "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
        "com.miui.ime", "com.sohu.inputmethod.sogou.xiaomi"
    )

    var isActive: Boolean
        get() = prefs.getBoolean("active", false)
        set(v) = prefs.edit().putBoolean("active", v).apply()

    var mode: String
        get() = prefs.getString("mode", "blacklist") ?: "blacklist"
        set(v) = prefs.edit().putString("mode", v).apply()

    var isNuclear: Boolean
        get() = prefs.getBoolean("nuclear", false)
        set(v) = prefs.edit().putBoolean("nuclear", v).apply()

    var sessionEndTime: Long
        get() = prefs.getLong("session_end", 0L)
        set(v) = prefs.edit().putLong("session_end", v).apply()

    var sessionDurationMin: Int
        get() = prefs.getInt("session_dur", 0)
        set(v) = prefs.edit().putInt("session_dur", v).apply()

    val selectedApps: Set<String>
        get() = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()

    fun setSelectedApps(v: Set<String>) = prefs.edit().putStringSet("selected_apps", v).apply()
    fun addApp(pkg: String) { setSelectedApps(selectedApps + pkg) }
    fun removeApp(pkg: String) { setSelectedApps(selectedApps - pkg) }

    fun isTimerExpired(): Boolean {
        val end = sessionEndTime
        if (end == 0L) return false
        return System.currentTimeMillis() >= end
    }

    fun isBlocked(packageName: String): Boolean {
        if (!isActive) return false
        if (isTimerExpired()) { isActive = false; return false }
        if (packageName in systemAllowlist) return false
        if (packageName.contains("launcher", ignoreCase = true)) return false
        if (packageName.startsWith("com.google.android.gms")) return false
        if (isNuclear) return true
        val selected = selectedApps
        return when (mode) {
            "whitelist" -> packageName !in selected
            "blacklist" -> packageName in selected
            else -> false
        }
    }
}
