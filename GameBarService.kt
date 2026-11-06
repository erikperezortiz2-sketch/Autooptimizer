package com.autooptimizer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class GameBarService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var strip: View? = null
    private var panel: View? = null
    private var stats: TextView? = null
    private var msg: TextView? = null
    private var dndBtn: Button? = null
    private var game = ""
    private var dnd = false
    private var inGame = true

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun acc(): Int {
        val p = getSharedPreferences("ao", 0)
        val a = p.getInt("acc", 0)
        if (a != 0) return a
        return intArrayOf(0xFF00F0FF.toInt(), 0xFFFF2D55.toInt(), 0xFF39FF14.toInt())[p.getInt("theme", 0).coerceIn(0, 2)]
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 13f
        setTextColor(0xFF0B0F1A.toInt()); setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(acc()) }
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

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(0xF2151B2E.toInt()); cornerRadius = dp(16).toFloat(); setStroke(dp(1), acc())
            }
            isClickable = true
        }
        fun add(v: View) = card.addView(v, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6)) })

        add(label("⚡ MODO JUEGO", 16f, acc(), true))
        stats = label("", 14f, 0xFFFFFFFF.toInt()); add(stats!!)
        msg = label("", 12f, 0xFFAAAAAA.toInt()); add(msg!!)

        add(btn("Limpiar RAM") {
            msg?.text = "Optimizando…"
            Thread {
                val r = try { Opt.full(this, game.ifEmpty { null }) } catch (e: Throwable) { "Error: ${e.message}" }
                h.post { msg?.text = r }
            }.start()
        })

        dndBtn = btn(if (dnd) "No molestar: ACTIVADO" else "No molestar: apagado") {
            if (!Sz.ready()) { msg?.text = "Necesita Shizuku (pestaña Pro)" }
            else {
                dnd = !dnd
                val on = dnd
                Thread { Sz.sh("settings put global zen_mode ${if (on) 1 else 0}") }.start()
                dndBtn?.text = if (on) "No molestar: ACTIVADO" else "No molestar: apagado"
            }
        }
        add(dndBtn!!)

        add(label("Brillo", 12f, 0xFFAAAAAA.toInt()))
        val row = LinearLayout(this)
        for (pc in intArrayOf(30, 60, 100)) {
            row.addView(btn("$pc%") {
                if (!Sz.ready()) { msg?.text = "Necesita Shizuku (pestaña Pro)" }
                else {
                    val v = 255 * pc / 100
                    Thread { Sz.sh("settings put system screen_brightness_mode 0; settings put system screen_brightness $v") }.start()
                    msg?.text = "Brillo $pc%"
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        add(row)

        add(btn("Cerrar rayita") { stopSelf() })

        val scroll = ScrollView(this).apply { addView(card) }
        val catcher = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { closePanel() }
        }
        catcher.addView(scroll, FrameLayout.LayoutParams(dp(250), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply {
            setMargins(0, dp(8), dp(8), dp(8))
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
        panel = null; stats = null; msg = null; dndBtn = null
        strip?.visibility = if (inGame) View.VISIBLE else View.GONE
    }

    private val upd = object : Runnable {
        override fun run() {
            val (a, _) = Opt.ram(this@GameBarService)
            stats?.text = "RAM libre: ${a / 1048576} MB\n" +
                "Temperatura: ${Opt.temp(this@GameBarService)} °C\n" +
                "Batería: ${Opt.batt(this@GameBarService)}%"
            h.postDelayed(this, 1000)
        }
    }

    // Con Shizuku: muestra la rayita solo cuando el juego está abierto.
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
