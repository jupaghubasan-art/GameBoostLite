package com.gameboost.lite

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var panel: View? = null
    private var fpsView: TextView? = null
    private var statsText: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val startTime = SystemClock.elapsedRealtime()
    private var frames = 0
    private var last = 0L
    private var running = true

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            frames++
            if (last == 0L) last = frameTimeNanos
            val dt = frameTimeNanos - last
            if (dt >= 1_000_000_000L) {
                fpsView?.text = "${(frames * 1e9 / dt).roundToInt()} FPS"
                frames = 0
                last = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val statsRunner = object : Runnable {
        override fun run() {
            val st = statsText ?: return
            val am = getSystemService(ActivityManager::class.java)
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val usedPct = 100 - (mi.availMem * 100 / mi.totalMem)
            val bat = getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val mins = (SystemClock.elapsedRealtime() - startTime) / 60000
            st.text = "RAM $usedPct%   Battery $bat%\nSession: $mins min"
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, fgNotification("Game panel active"))
        if (handle == null) setup()
        return START_STICKY
    }

    private fun params(w: Int, h: Int, gravity: Int, flags: Int, x: Int = 0, y: Int = 0) =
        WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity; this.x = x; this.y = y }

    private fun setup() {
        wm = getSystemService(WindowManager::class.java)
        val nf = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        fpsView = TextView(this).apply {
            text = "-- FPS"; setTextColor(0xFF00E676.toInt()); textSize = 13f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = roundBg(0x99000000.toInt(), 8)
        }
        wm.addView(
            fpsView,
            params(wrap, wrap, Gravity.TOP or Gravity.START, nf or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, dp(8), dp(8))
        )
        Choreographer.getInstance().postFrameCallback(frameCb)

        handle = View(this).apply {
            background = roundBg(0xAAFFC107.toInt(), 6)
            setOnClickListener { showPanel() }
        }
        wm.addView(handle, params(dp(12), dp(100), Gravity.END or Gravity.CENTER_VERTICAL, nf))
    }

    private fun tile(t: String, action: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
        background = roundBg(0xFF3A3A3A.toInt(), 12)
        setOnClickListener { action() }
    }

    private fun buildPanel(): LinearLayout {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundBg(0xEE1E1E1E.toInt(), 16)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
                false
            }
        }
        p.addView(TextView(this).apply {
            text = "Performance panel"; setTextColor(0xFFFFC107.toInt()); textSize = 15f
        })
        statsText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f
            setPadding(0, dp(6), 0, dp(6))
        }
        p.addView(statsText)

        val tiles: List<Pair<String, () -> Unit>> = listOf(
            "Clear RAM" to { clearRam() },
            "Open MLBB" to { openMlbb() },
            "Do Not Disturb" to { toggleDnd() },
            "Display / Hz" to { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
            "Show/Hide FPS" to {
                fpsView?.let { it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            },
            "Close panel" to { stopSelf() }
        )
        tiles.chunked(2).forEach { row ->
            val r = LinearLayout(this)
            row.forEach { (t, a) ->
                r.addView(tile(t, a), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            p.addView(r)
        }
        return p
    }

    private fun showPanel() {
        if (panel != null) return
        val p = buildPanel()
        panel = p
        wm.addView(
            p,
            params(
                dp(280), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                dp(8), 0
            )
        )
        handle?.visibility = View.GONE
        handler.post(statsRunner)
    }

    private fun hidePanel() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        statsText = null
        handler.removeCallbacks(statsRunner)
        handle?.visibility = View.VISIBLE
    }

    private fun go(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: Exception) { toast("Hindi mabuksan") }
    }

    private fun openMlbb() {
        val i = packageManager.getLaunchIntentForPackage("com.mobile.legends")
        if (i != null) go(i) else toast("MLBB not found")
    }

    private fun clearRam() {
        val am = getSystemService(ActivityManager::class.java)
        var n = 0
        packageManager.getInstalledApplications(0).forEach { a ->
            if (a.packageName != packageName &&
                a.packageName != "com.mobile.legends" &&
                (a.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            ) {
                am.killBackgroundProcesses(a.packageName)
                n++
            }
        }
        toast("Cleared $n background apps")
    }

    private fun toggleDnd() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            toast("Allow GameBoost Lite sa DND access, tapos ulitin")
            go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        val off = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (off) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        toast(if (off) "Do Not Disturb ON" else "Do Not Disturb OFF")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
        handler.removeCallbacksAndMessages(null)
        hidePanel()
        try { handle?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { fpsView?.let { wm.removeView(it) } } catch (_: Exception) {}
        handle = null
        super.onDestroy()
    }
}
