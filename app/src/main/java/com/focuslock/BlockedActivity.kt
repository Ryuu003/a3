package com.focuslock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.focuslock.databinding.ActivityBlockedBinding

class BlockedActivity : Activity() {

    companion object {
        const val EXTRA_PKG = "blocked_pkg"
        const val EXTRA_LABEL = "blocked_label"
    }

    private lateinit var b: ActivityBlockedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBlockedBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Esta app"
        b.blockedAppName.text = label
        b.btnGoHome.setOnClickListener { goHome() }
        b.btnOpenFocusLock.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }
    }

    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }
}
