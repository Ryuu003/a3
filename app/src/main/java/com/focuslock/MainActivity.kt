package com.focuslock

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.focuslock.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var adapter: AppAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Prevents the toggle listener from firing when we set it programmatically
    private var isChangingToggle = false

    private var holdHandler: Handler? = null
    private var holdRunnable: Runnable? = null
    private var holdProgress = 0
    private val HOLD_MS = 30_000L
    private val TICK_MS = 100L

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() { updateTimerDisplay(); refreshHandler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = PrefsManager(this)
        holdHandler = Handler(Looper.getMainLooper())

        setupToggles()
        setupModeSwitch()
        setupTimer()
        setupNuclear()
        setupHoldToDeactivate()
        setupRecycler()
        setupSearch()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        syncToggleState()   // Always sync toggle to real state on resume
        updateTimerDisplay()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    /** Sync the toggle switch to actual prefs state without triggering the listener */
    private fun syncToggleState() {
        isChangingToggle = true
        b.masterToggle.isChecked = prefs.isActive
        isChangingToggle = false
        updateActiveUI()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java)
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updateServiceStatus() {
        val accessibility = isAccessibilityEnabled()
        val usage = hasUsageStatsPermission()
        when {
            accessibility && usage -> {
                b.serviceStatus.text = "● Servicio completo activo"
                b.serviceStatus.setTextColor(getColor(R.color.green))
                b.btnEnableService.visibility = View.GONE
                b.btnEnableUsage.visibility = View.GONE
            }
            !accessibility && !usage -> {
                b.serviceStatus.text = "● Activa los dos permisos abajo"
                b.serviceStatus.setTextColor(getColor(R.color.red))
                b.btnEnableService.visibility = View.VISIBLE
                b.btnEnableUsage.visibility = View.VISIBLE
            }
            !accessibility -> {
                b.serviceStatus.text = "● Falta activar Accesibilidad"
                b.serviceStatus.setTextColor(getColor(R.color.orange))
                b.btnEnableService.visibility = View.VISIBLE
                b.btnEnableUsage.visibility = View.GONE
            }
            else -> {
                b.serviceStatus.text = "● Falta activar Permiso de Uso"
                b.serviceStatus.setTextColor(getColor(R.color.orange))
                b.btnEnableService.visibility = View.GONE
                b.btnEnableUsage.visibility = View.VISIBLE
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/${AppBlockerService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(service, ignoreCase = true) }
    }

    private fun setupToggles() {
        b.masterToggle.setOnCheckedChangeListener { _, checked ->
            if (isChangingToggle) return@setOnCheckedChangeListener  // ignore programmatic changes

            if (checked) {
                // ACTIVATE
                if (!isAccessibilityEnabled() && !hasUsageStatsPermission()) {
                    isChangingToggle = true
                    b.masterToggle.isChecked = false
                    isChangingToggle = false
                    AlertDialog.Builder(this)
                        .setTitle("Permisos requeridos")
                        .setMessage("Activa al menos uno de los dos permisos primero.")
                        .setPositiveButton("OK", null).show()
                    return@setOnCheckedChangeListener
                }
                val dur = prefs.sessionDurationMin
                if (dur > 0) prefs.sessionEndTime = System.currentTimeMillis() + dur * 60_000L
                prefs.isActive = true
                ForegroundBlockerService.start(this)
                updateActiveUI()

            } else {
                // User tried to turn OFF — block and show warning
                isChangingToggle = true
                b.masterToggle.isChecked = true
                isChangingToggle = false
                AlertDialog.Builder(this)
                    .setTitle("Para desactivar")
                    .setMessage("Mantén presionado el botón rojo por 30 segundos.")
                    .setPositiveButton("ENTENDIDO", null).show()
            }
        }

        b.btnEnableService.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Servicio de Accesibilidad")
                .setMessage("1. Toca OK\n2. Busca 'FocusLock'\n3. Actívalo\n4. Regresa")
                .setPositiveButton("IR") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("CANCELAR", null).show()
        }

        b.btnEnableUsage.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Permiso de Uso de Apps")
                .setMessage("Necesario para Xiaomi/Redmi.\n\n1. Toca OK\n2. Busca 'FocusLock'\n3. Activa 'Permitir seguimiento de uso'\n4. Regresa")
                .setPositiveButton("IR") { _, _ -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .setNegativeButton("CANCELAR", null).show()
        }
    }

    private fun setupModeSwitch() {
        val isWhitelist = prefs.mode == "whitelist"
        b.modeGroup.check(if (isWhitelist) R.id.btnWhitelist else R.id.btnBlacklist)
        updateModeLabel(prefs.mode)
        b.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val m = if (checkedId == R.id.btnWhitelist) "whitelist" else "blacklist"
            prefs.mode = m; updateModeLabel(m)
        }
    }

    private fun updateModeLabel(mode: String) {
        b.modeDescription.text = if (mode == "whitelist")
            "LISTA BLANCA — solo las apps marcadas están permitidas"
        else "LISTA NEGRA — las apps marcadas quedan bloqueadas"
    }

    private fun setupTimer() {
        listOf(b.btnTimer25 to 25, b.btnTimer45 to 45, b.btnTimer90 to 90).forEach { (btn, min) ->
            btn.setOnClickListener {
                prefs.sessionDurationMin = if (prefs.sessionDurationMin == min) 0 else min
                b.customTimerInput.setText(""); updateTimerButtons()
            }
        }
        b.customTimerInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.sessionDurationMin = s?.toString()?.toIntOrNull() ?: 0; updateTimerButtons()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        b.btnNoTimer.setOnClickListener {
            prefs.sessionDurationMin = 0; prefs.sessionEndTime = 0L
            b.customTimerInput.setText(""); updateTimerButtons(); updateTimerDisplay()
        }
        updateTimerButtons()
    }

    private fun updateTimerButtons() {
        val d = prefs.sessionDurationMin
        b.btnTimer25.isSelected = d == 25
        b.btnTimer45.isSelected = d == 45
        b.btnTimer90.isSelected = d == 90
    }

    private fun updateTimerDisplay() {
        val end = prefs.sessionEndTime
        if (!prefs.isActive || end == 0L) {
            val d = prefs.sessionDurationMin
            b.timerDisplay.text = if (d > 0) "Sesión de $d min al activar" else "Sin límite de tiempo"
            b.timerDisplay.setTextColor(getColor(R.color.text_secondary))
            return
        }
        val rem = end - System.currentTimeMillis()
        if (rem <= 0) {
            // Timer expired — clean reset
            b.timerDisplay.text = "✓ Sesión completada"
            b.timerDisplay.setTextColor(getColor(R.color.green))
            resetSession()
            return
        }
        val h = rem / 3_600_000
        val m = (rem % 3_600_000) / 60_000
        val s = (rem % 60_000) / 1_000
        b.timerDisplay.text = if (h > 0) "${h}h ${m}m restantes" else "${m}m ${s}s restantes"
        b.timerDisplay.setTextColor(getColor(R.color.orange))
    }

    private fun setupNuclear() {
        b.nuclearToggle.isChecked = prefs.isNuclear
        b.nuclearToggle.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AlertDialog.Builder(this)
                    .setTitle("⚠ Modo Nuclear")
                    .setMessage("Bloqueará TODAS las apps. Solo puedes desactivarlo manteniendo el botón 30 segundos. ¿Continuar?")
                    .setPositiveButton("ACTIVAR") { _, _ -> prefs.isNuclear = true; updateModeVisibility() }
                    .setNegativeButton("CANCELAR") { _, _ -> b.nuclearToggle.isChecked = false }
                    .show()
            } else { prefs.isNuclear = false; updateModeVisibility() }
        }
        updateModeVisibility()
    }

    private fun updateModeVisibility() {
        val n = prefs.isNuclear
        b.modeGroup.visibility = if (n) View.GONE else View.VISIBLE
        b.modeDescription.text = if (n) "⚠ MODO NUCLEAR — todas las apps bloqueadas"
        else if (prefs.mode == "whitelist") "LISTA BLANCA — solo las apps marcadas están permitidas"
        else "LISTA NEGRA — las apps marcadas quedan bloqueadas"
        b.appListSection.visibility = if (n) View.GONE else View.VISIBLE
    }

    private fun setupHoldToDeactivate() {
        b.holdDeactivateBtn.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { if (prefs.isActive) startHold(); true }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> { cancelHold(); true }
                else -> false
            }
        }
    }

    private fun startHold() {
        holdProgress = 0
        b.holdProgressBar.progress = 0
        b.holdProgressBar.visibility = View.VISIBLE
        holdRunnable = object : Runnable {
            override fun run() {
                holdProgress += TICK_MS.toInt()
                b.holdProgressBar.progress = (holdProgress * 100 / HOLD_MS).toInt()
                val left = ((HOLD_MS - holdProgress) / 1000) + 1
                b.holdDeactivateBtn.text = "Mantén presionado... ${left}s"
                if (holdProgress >= HOLD_MS) deactivateNow()
                else holdHandler?.postDelayed(this, TICK_MS)
            }
        }
        holdHandler?.postDelayed(holdRunnable!!, TICK_MS)
    }

    private fun cancelHold() {
        holdHandler?.removeCallbacks(holdRunnable ?: return)
        b.holdProgressBar.visibility = View.GONE
        b.holdProgressBar.progress = 0
        b.holdDeactivateBtn.text = "MANTENER PARA DESACTIVAR"
    }

    /** Full clean reset after manual deactivation */
    private fun deactivateNow() {
        cancelHold()
        resetSession()
    }

    /** Reset all session state cleanly */
    private fun resetSession() {
        prefs.isActive = false
        prefs.sessionEndTime = 0L
        ForegroundBlockerService.stop(this)
        isChangingToggle = true
        b.masterToggle.isChecked = false
        isChangingToggle = false
        b.holdDeactivateBtn.visibility = View.GONE
        b.holdProgressBar.visibility = View.GONE
        b.holdDeactivateBtn.text = "MANTENER PARA DESACTIVAR"
        b.statusLabel.text = "● FOCUSLOCK INACTIVO"
        b.statusLabel.setTextColor(getColor(R.color.text_secondary))
    }

    private fun updateActiveUI() {
        val a = prefs.isActive
        b.statusLabel.text = if (a) "● FOCUSLOCK ACTIVO" else "● FOCUSLOCK INACTIVO"
        b.statusLabel.setTextColor(getColor(if (a) R.color.orange else R.color.text_secondary))
        b.holdDeactivateBtn.visibility = if (a) View.VISIBLE else View.GONE
        b.holdProgressBar.visibility = View.GONE
    }

    private fun setupRecycler() { b.recycler.layoutManager = LinearLayoutManager(this) }

    private fun setupSearch() {
        b.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (::adapter.isInitialized) adapter.setFilter(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadApps() {
        b.loadingIndicator.visibility = View.VISIBLE
        b.recycler.visibility = View.GONE
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager; val sel = prefs.selectedApps
                val skip = setOf("android", "com.android.systemui", "com.focuslock")
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName !in skip }
                    .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it.packageName), it.packageName in sel) }
                    .sortedWith(compareByDescending<AppItem> { it.isSelected }.thenBy { it.label.lowercase() })
            }
            b.loadingIndicator.visibility = View.GONE
            b.recycler.visibility = View.VISIBLE
            b.appCountLabel.text = "${apps.size} APPS"
            adapter = AppAdapter(apps) { item, checked ->
                if (checked) prefs.addApp(item.packageName) else prefs.removeApp(item.packageName)
                val count = apps.count { it.isSelected }
                b.selectedCountLabel.text = if (prefs.mode == "whitelist") "$count permitidas" else "$count bloqueadas"
            }
            b.recycler.adapter = adapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        holdHandler?.removeCallbacksAndMessages(null)
    }
}
