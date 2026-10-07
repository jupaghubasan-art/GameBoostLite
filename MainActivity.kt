package com.gameboost.lite

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var factor = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF121212.toInt())
            addView(col)
        })

        fun label(t: String, size: Float = 14f) {
            col.addView(TextView(this).apply {
                text = t; setTextColor(Color.WHITE); textSize = size
                setPadding(0, dp(12), 0, dp(4))
            })
        }
        fun button(t: String, onClick: () -> Unit) {
            col.addView(Button(this).apply {
                text = t; isAllCaps = false; setTextColor(Color.BLACK)
                background = roundBg(0xFFFFC107.toInt(), 12)
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        label("GameBoost Lite", 24f)
        label("Floating FPS counter + edge game panel. Light boost lang (RAM clear), hindi nito tataasan ang max FPS ng phone.")

        label("Game Panel", 18f)
        button("1. Allow overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("2. Start Game Panel") { startPanel() }
        button("Stop Game Panel") { stopService(Intent(this, OverlayService::class.java)) }
        button("Open Mobile Legends") {
            val i = packageManager.getLaunchIntentForPackage("com.mobile.legends")
            if (i != null) startActivity(i) else toast("MLBB not found")
        }

        label("Touch / Display", 18f)
        button("Display & refresh rate settings") { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
        button("Developer options (touch tweaks)") {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (e: Exception) { toast("Enable Developer options muna sa About phone") }
        }

        label("Voice Changer (gamitin ang earphones!)", 18f)
        val pitchLabel = TextView(this).apply { setTextColor(Color.LTGRAY); text = "Pitch: 1.00x (normal)" }
        col.addView(pitchLabel)
        col.addView(SeekBar(this).apply {
            max = 100; progress = 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    factor = 0.6f + p / 100f
                    pitchLabel.text = "Pitch: ${"%.2f".format(factor)}x"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        button("Start voice changer") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
                toast("Allow mic, tapos pindutin ulit ang Start")
            } else {
                startForegroundService(Intent(this, VoiceService::class.java).putExtra("factor", factor))
            }
        }
        button("Stop voice changer") { stopService(Intent(this, VoiceService::class.java)) }
        label("Note: gagana lang ang voice changer sa loob ng app na ito. Hindi ito mapapasok sa voice chat ng MLBB.")
    }

    private fun startPanel() {
        if (!Settings.canDrawOverlays(this)) { toast("Allow overlay permission muna (step 1)"); return }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, OverlayService::class.java))
        toast("Panel started. Buksan na ang MLBB; hanapin ang dilaw na handle sa kanang gilid")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
