package com.autooptimizer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class AngledPanel(c: Context, private val left: Boolean, col: Int) : LinearLayout(c) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE0B1426.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col; style = Paint.Style.STROKE; strokeWidth = d * 2f
    }
    private val path = Path()
    private val edgePath = Path()

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cut = d * 14f
        path.reset(); edgePath.reset()
        if (left) {
            path.moveTo(0f, 0f); path.lineTo(w - cut, 0f); path.lineTo(w, cut)
            path.lineTo(w, h - cut); path.lineTo(w - cut, h); path.lineTo(0f, h); path.close()
            edgePath.moveTo(w - cut, 0f); edgePath.lineTo(w, cut)
            edgePath.lineTo(w, h - cut); edgePath.lineTo(w - cut, h)
        } else {
            path.moveTo(w, 0f); path.lineTo(cut, 0f); path.lineTo(0f, cut)
            path.lineTo(0f, h - cut); path.lineTo(cut, h); path.lineTo(w, h); path.close()
            edgePath.moveTo(cut, 0f); edgePath.lineTo(0f, cut)
            edgePath.lineTo(0f, h - cut); edgePath.lineTo(cut, h)
        }
        cv.drawPath(path, fill)
        cv.drawPath(edgePath, edge)
        super.onDraw(cv)
    }
}

class Gauge(c: Context, private val col: Int) : View(c) {
    private val d = resources.displayMetrics.density
    private var frac = 0f
    private var center = ""
    private var label = ""
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5 * d; color = 0xFF1B2B4A.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5 * d; color = col; strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14 * d; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 11 * d; textAlign = Paint.Align.CENTER
    }
    private val box = RectF(8 * d, 8 * d, 64 * d, 64 * d)

    fun set(f: Float, c: String, l: String) {
        frac = f.coerceIn(0f, 1f); center = c; label = l; invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((72 * d).toInt(), (88 * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        cv.drawArc(box, 0f, 360f, false, track)
        if (frac > 0f) cv.drawArc(box, -90f, 360f * frac, false, arc)
        cv.drawText(center, 36 * d, 41 * d, big)
        cv.drawText(label, 36 * d, 82 * d, small)
    }
}

class MeterRow(c: Context, private val col: Int) : View(c) {
    private val d = resources.displayMetrics.density
    private var label = ""
    private var value = ""
    private var frac = 0f
    private val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11 * d }
    private val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 11 * d; textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD
    }
    private val tr = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B2B4A.toInt() }
    private val fl = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }

    fun set(l: String, v: String, f: Float) {
        label = l; value = v; frac = f.coerceIn(0f, 1f); invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (34 * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val wd = width.toFloat()
        cv.drawText(label, 0f, 12 * d, lp)
        cv.drawText(value, wd, 12 * d, vp)
        cv.drawRoundRect(0f, 19 * d, wd, 24 * d, 3 * d, 3 * d, tr)
        if (frac > 0f) cv.drawRoundRect(0f, 19 * d, wd * frac, 24 * d, 3 * d, 3 * d, fl)
    }
}

class GameBarService : Service() {
    private val nombre = "Gamer"

    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var strip: View? = null
    private var panel: View? = null
    private var msg: TextView? = null
    private var dndBtn: Button? = null
    private var briBtn: Button? = null
    private var g1: Gauge? = null
    private var g2: Gauge? = null
    private var m1: MeterRow? = null
    private var m2: MeterRow? = null
    private var m3: MeterRow? = null
    private var m4: MeterRow? = null
    private var game = ""
    private var dnd = false
    private var inGame = true
    private var briIdx = -1
    private val levels = intArrayOf(30, 60, 100)
    @Volatile private var busy = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun acc(): Int {
        val p = getSharedPreferences("ao", 0)
        val a = p.getInt("acc", 0)
        if (a != 0) return a
        return intArrayOf(0xFF00F0FF.toInt(), 0xFFFF2D55.toInt(), 0xFF39FF14.toInt())[p.getInt("theme", 0).coerceIn(0, 2)]
    }

    private fun btn(t: String, solid: Boolean = true, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 12f
        minHeight = 0; minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0)
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (solid) 0xFF0B1426.toInt() else acc())
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            if (solid) setColor(acc()) else { setColor(0xFF1B2B4A.toInt()); setStroke(dp(1), acc()) }
        }
        setOnClickListener { f() }
    }

    private fun label(t: String, sz: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; setTextColor(c); if (bold) setTypeface(null, Typeface.BOLD)
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        game = i?.getStringExtra("game") ?: game
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gb", "Rayita de juego", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "gb").setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Rayita de juego activa")
            .setContentText("Toca la rayita del borde para optimizar").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, n)
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showStrip()
        h.removeCallbacks(poll); h.post(poll)
        return START_NOT_STICKY
    }

    private fun showStrip() {
        val w = wm ?: return
        if (strip != null) return
        val v = FrameLayout(this)
        val line = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(acc()) }
            alpha = 0.65f
        }
        v.addView(line, FrameLayout.LayoutParams(dp(4), dp(64), Gravity.END or Gravity.CENTER_VERTICAL))
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) openPanel()
            true
        }
        val lp = WindowManager.LayoutParams(dp(30), dp(110), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        w.addView(v, lp)
        strip = v
    }

    private fun openPanel() {
        val w = wm ?: return
        if (panel != null) return
        strip?.visibility = View.GONE
        val a = acc()

        val left = AngledPanel(this, true, a).apply {
            setPadding(dp(12), dp(12), dp(24), dp(12)); isClickable = true
        }
        left.addView(label("Atajos", 12f, a, true),
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6)) })
        fun item(b: Button) = left.addView(b,
            LinearLayout.LayoutParams(-1, dp(38)).apply { setMargins(0, 0, 0, dp(6)) })

        item(btn("Limpiar RAM") {
            msg?.text = "Optimizando…"
            Thread {
                val r = try { Opt.full(this, game.ifEmpty { null }) } catch (e: Throwable) { "Error: ${e.message}" }
                h.post { msg?.text = r }
            }.start()
        })

        val dBtn = btn(if (dnd) "No molestar: ON" else "No molestar") {
            if (!Sz.ready()) { msg?.text = "Necesita Shizuku (pestaña Pro)" }
            else {
                dnd = !dnd
                val on = dnd
                Thread { Sz.sh("settings put global zen_mode ${if (on) 1 else 0}") }.start()
                dndBtn?.text = if (on) "No molestar: ON" else "No molestar"
            }
        }
        dndBtn = dBtn
        item(dBtn)

        val bBtn = btn("Brillo") {
            if (!Sz.ready()) { msg?.text = "Necesita Shizuku (pestaña Pro)" }
            else {
                briIdx = (briIdx + 1) % 3
                val pc = levels[briIdx]
                val v = 255 * pc / 100
                Thread { Sz.sh("settings put system screen_brightness_mode 0; settings put system screen_brightness $v") }.start()
                briBtn?.text = "Brillo $pc%"
            }
        }
        briBtn = bBtn
        item(bBtn)

        item(btn("Cerrar rayita", false) { stopSelf() })

        val right = AngledPanel(this, false, a).apply {
            setPadding(dp(24), dp(12), dp(12), dp(12)); isClickable = true
        }
        right.addView(label("Rendimiento", 12f, a, true),
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6)) })
        val r1 = MeterRow(this, a); val r2 = MeterRow(this, a)
        val r3 = MeterRow(this, a); val r4 = MeterRow(this, a)
        m1 = r1; m2 = r2; m3 = r3; m4 = r4
        for (m in listOf(r1, r2, r3, r4)) {
            right.addView(m, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(4)) })
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        }
        center.addView(label(nombre, 18f, a, true).apply { gravity = Gravity.CENTER })
        center.addView(label("Modo juego", 12f, 0xFFAAAAAA.toInt()).apply { gravity = Gravity.CENTER })
        val gr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL
        }
        val ga = Gauge(this, a); val gb = Gauge(this, a)
        g1 = ga; g2 = gb
        gr.addView(ga, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(6), dp(8), dp(6), 0) })
        gr.addView(gb, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(6), dp(8), dp(6), 0) })
        center.addView(gr)
        val mt = label("Toca el centro para cerrar", 12f, 0xFFDDDDDD.toInt()).apply { gravity = Gravity.CENTER }
        msg = mt
        center.addView(mt, LinearLayout.LayoutParams(dp(220), -2).apply { setMargins(0, dp(8), 0, 0) })

        val catcher = FrameLayout(this).apply {
            setBackgroundColor(0x77000000)
            setOnClickListener { closePanel() }
        }
        catcher.addView(left, FrameLayout.LayoutParams(dp(140), -2, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            setMargins(dp(24), dp(8), 0, dp(8))
        })
        catcher.addView(right, FrameLayout.LayoutParams(dp(140), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            setMargins(0, dp(8), dp(24), dp(8))
        })
        catcher.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(0, dp(20), 0, 0)
        })

        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        w.addView(catcher, lp)
        panel = catcher
        h.removeCallbacks(upd); h.post(upd)
    }

    private fun closePanel() {
        h.removeCallbacks(upd)
        panel?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        panel = null; msg = null; dndBtn = null; briBtn = null
        g1 = null; g2 = null; m1 = null; m2 = null; m3 = null; m4 = null
        strip?.visibility = if (inGame) View.VISIBLE else View.GONE
    }

    private fun readCpu(): Pair<Float, Float>? {
        if (!Sz.ready()) return null
        val out = Sz.sh("cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq; echo M; cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq")
        if (out.startsWith("ERR")) return null
        val parts = out.split("M")
        if (parts.size < 2) return null
        val cur = parts[0].split("\n").mapNotNull { it.trim().toLongOrNull() }.maxOrNull() ?: return null
        val mx = parts[1].split("\n").mapNotNull { it.trim().toLongOrNull() }.maxOrNull() ?: return null
        if (mx <= 0L) return null
        return Pair(cur / 1_000_000f, cur.toFloat() / mx)
    }

    private val upd = object : Runnable {
        override fun run() {
            val c = this@GameBarService
            val (a, t) = Opt.ram(c)
            val tmp = Opt.temp(c)
            val bt = Opt.batt(c)
            val freeF = a.toFloat() / t
            if (!busy) {
                busy = true
                Thread {
                    val cpu = try { readCpu() } catch (e: Throwable) { null }
                    h.post {
                        busy = false
                        if (panel == null) return@post
                        if (cpu != null) {
                            val gh = "%.1f".format(cpu.first)
                            g1?.set(cpu.second, gh, "CPU")
                            m1?.set("CPU", "$gh GHz", cpu.second)
                        } else {
                            val used = 1f - freeF
                            val pct = (used * 100).toInt()
                            g1?.set(used, "$pct%", "RAM usada")
                            m1?.set("RAM usada", "$pct%", used)
                        }
                        g2?.set(tmp / 60f, "${tmp.toInt()}°", "Temp")
                        m2?.set("RAM libre", "${a / 1048576} MB", freeF)
                        m3?.set("Temp", "$tmp °C", tmp / 60f)
                        m4?.set("Batería", "$bt%", bt / 100f)
                    }
                }.start()
            }
            h.postDelayed(this, 1000)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (Sz.ready() && game.isNotEmpty()) {
                Thread {
                    val o = Sz.sh("dumpsys activity activities | grep -i resumed")
                    val visible = o.isBlank() || o.startsWith("ERR") || o.contains(game)
                    h.post {
                        inGame = visible
                        if (panel == null) strip?.visibility = if (visible) View.VISIBLE else View.GONE
                    }
                }.start()
            }
            h.postDelayed(this, 3000)
        }
    }

    override fun onDestroy() {
        h.removeCallbacks(poll); h.removeCallbacks(upd)
        panel?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        strip?.let { try { wm?.removeView(it) } catch (e: Exception) { } }
        panel = null; strip = null
        super.onDestroy()
    }
}
