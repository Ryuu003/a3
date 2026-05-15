package com.focuslock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class ForegroundBlockerService : Service() {

    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg = ""
    private var lastBlockedPkg = ""
    private var lastBlockTime = 0L

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                if (prefs.isActive) checkForegroundApp()
            } catch (e: Exception) {}
            handler.postDelayed(this, 400)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        return START_STICKY
    }

    private fun checkForegroundApp() {
        val pkg = getActualForegroundApp() ?: return

        // Ignorar si es la misma app que ya está en primer plano (sin cambio)
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        // No bloquear nuestra propia app ni sistema
        if (pkg == packageName) return

        // Evitar spam del mismo bloqueo
        val now = System.currentTimeMillis()
        if (pkg == lastBlockedPkg && now - lastBlockTime < 2000) return

        if (prefs.isBlocked(pkg)) {
            lastBlockedPkg = pkg
            lastBlockTime = now

            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }

            startActivity(Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(BlockedActivity.EXTRA_PKG, pkg)
                putExtra(BlockedActivity.EXTRA_LABEL, label)
            })
        }
    }

    /**
     * Usa UsageEvents.MOVE_TO_FOREGROUND — detecta SOLO apps que el usuario
     * realmente abrió, ignorando servicios de Google y procesos en segundo plano.
     */
    private fun getActualForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5000, now)
        val event = UsageEvents.Event()
        var lastForeground: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // Solo contar eventos donde el usuario MOVIÓ la app al frente
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForeground = event.packageName
            }
        }
        return lastForeground
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restart = Intent(applicationContext, ForegroundBlockerService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, 1, restart,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.ELAPSED_REALTIME, 1000, pi)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        startService(Intent(this, ForegroundBlockerService::class.java))
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusLock activo")
            .setContentText("Protegiendo tu enfoque")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "FocusLock", NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "focuslock_channel"
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, ForegroundBlockerService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, ForegroundBlockerService::class.java))
    }
}
