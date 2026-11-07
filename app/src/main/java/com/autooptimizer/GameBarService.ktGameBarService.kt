package com.autooptimizer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class FutureFrame(c: Context, private val col: Int) : FrameLayout(c) {
    private val d = resources.displayMetrics.density
    private val topH = 30f * d
    private val sideW = 156f * d
    private val cut = 14f * d
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA000000.toInt()
        maskFilter = BlurMaskFilter(10f * d, BlurMaskFilter.Blur.NORMAL)
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f * d; color = col; alpha = 110
        maskFilter = BlurMaskFilter(5f * d, BlurMaskFilter.Blur.NORMAL)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f * d; color = col
    }
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.8f * d; color = col; alpha = 120
    }
    private val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f * d; color = col; alpha = 26
    }
    private val stripeL = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripeR = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun panel(left: Boolean, w: Float, h: Float): Path {
        val p = Path()
        val x0 = if (left) 0f else w - sideW
        val x1 = if (left) sideW else w
        val top = topH
        val bot = h - 8f * d
        if (left) {
            p.moveTo(x0, top); p.lineTo(x1 - cut, top); p.lineTo(x1, top + cut)
            p.lineTo(x1, bot - cut); p.lineTo(x1 - cut, bot); p.lineTo(x0, bot)
        } else {
            p.moveTo(x1, top); p.lineTo(x0 + cut, top); p.lineTo(x0, top + cut)
            p.lineTo(x0, bot - cut); p.lineTo(x0 + cut, bot); p.lineTo(x1, bot)
        }
        p.close()
        return p
    }

    private fun edge(left: Boolean, w: Float, h: Float): Path {
        val p = Path()
        val x0 = if (left) 0f else w - sideW
        val x1 = if (left) sideW else w
        val top = topH
        val bot = h - 8f * d
        if (left) {
            p.moveTo(x1 - cut, top); p.lineTo(x1, top + cut); p.lineTo(x1, bot - cut); p.lineTo(x1 - cut, bot)
        } else {
            p.moveTo(x0 + cut, top); p.lineTo(x0, top + cut); p.lineTo(x0, bot - cut); p.lineTo(x0 + cut, bot)
        }
        return p
    }

    private fun slant(x: Float, y0: Float, y1: Float, dir: Float): Path {
        val p = Path()
        val s = 24f * d * dir
        p.moveTo(x, y0); p.lineTo(x + s, y0); p.lineTo(x + s * 0.75f, y1); p.lineTo(x - 6f * d * dir, y1)
        p.close()
        return p
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bot = h - 8f * d

        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, 0f, w, 0f,
            intArrayOf(0xF2070D1A.toInt(), 0xF2132A4D.toInt(), 0xF2070D1A.toInt()), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, topH, fill)
        cv.drawLine(0f, topH, w, topH, glow)
        cv.drawLine(0f, topH, w, topH, line)
        cv.drawLine(0f, topH + 3f * d, w, topH + 3f * d, thin)

        for (left in listOf(true, false)) {
            val x0 = if (left) 0f else w - sideW
            val x1 = if (left) sideW else w
            val p = panel(left, w, h)

            cv.save()
            cv.translate(0f, 4f * d)
            cv.drawPath(p, shadow)
            cv.restore()

            fill.shader = LinearGradient(0f, topH, 0f, bot,
                intArrayOf(0xF81A2D50.toInt(), 0xF8080F1E.toInt()), null, Shader.TileMode.CLAMP)
            cv.drawPath(p, fill)

            cv.save()
            cv.clipPath(p)
            var y = topH - (x1 - x0)
            while (y < bot) {
                cv.drawLine(x0, y, x1, y + (x1 - x0), hatch)
                y += 9f * d
            }
            cv.restore()

            val e = edge(left, w, h)
            cv.drawPath(e, glow)
            cv.drawPath(e, line)

            val inX = if (left) x1 - 5f * d else x0 + 5f * d
            cv.drawLine(inX, topH + cut + 6f * d, inX, bot - cut - 6f * d, thin)

            var ty = topH + 16f * d
            while (ty < bot - 8f * d) {
                if (left) cv.drawLine(x0, ty, x0 + 6f * d, ty, thin)
                else cv.drawLine(x1 - 6f * d, ty, x1, ty, thin)
                ty += 14f * d
            }
        }

        for (i in 0 until 5) {
            val y0 = topH + (70f + i * 26f) * d
            val y1 = y0 + 8f * d
            val lx = sideW + 6f * d
            val rx = w - sideW - 6f * d
            stripeL.shader = LinearGradient(lx, 0f, lx + 26f * d, 0f,
                intArrayOf(col, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            stripeL.alpha = 255 - i * 40
            stripeR.shader = LinearGradient(rx, 0f, rx - 26f * d, 0f,
                intArrayOf(col, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            stripeR.alpha = 255 - i * 40
            cv.drawPath(slant(lx, y0, y1, 1f), stripeL)
            cv.drawPath(slant(rx, y0, y1, -1f), stripeR)
        }
        super.onDraw(cv)
    }
}

class SegBar(c: Context, private val col: Int) : View(c) {
    private val d = resources.displayMetrics.density
    private var frac = 0f
    private val onP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
    private val offP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332A4A70 }

    fun set(f: Float) { frac = f.coerceIn(0f, 1f); invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (7f * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val n = 12
        val gap = 2f * d
        val sw = (width - gap * (n - 1)) / n
        val lit = Math.round(frac * n)
        for (i in 0 until n) {
            val x = i * (sw + gap)
            cv.drawRect(x, 0f, x + sw, height.toFloat(), if (i < lit) onP else offP)
        }
    }
}

class Gauge(c: Context, private val col: Int) : View(c) {
    private val d = resources.displayMetrics.density
    private var frac = 0f
    private var txtBig = ""
    private var txtSmall = ""
    private val box = RectF(12f * d, 12f * d, 68f * d, 68f * d)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f * d; color = 0x552A4A70
    }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f * d; color = col; alpha = 90
        maskFilter = BlurMaskFilter(5f * d, BlurMaskFilter.Blur.NORMAL)
    }
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3.5f * d; color = col; strokeCap = Paint.Cap.ROUND
    }
    private val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = 150 }
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 15f * d; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f * d, 0f, 0f, col)
    }
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8C7DA.toInt(); textSize = 9f * d; textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    fun set(f: Float, b: String, s: String) {
        frac = f.coerceIn(0f, 1f); txtBig = b; txtSmall = s; invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((80f * d).toInt(), (92f * d).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val cx = 40f * d
        val cy = 40f * d
        val r = 28f * d
        for (i in 0 until 24) {
            val ang = Math.toRadians(i * 15.0 - 90.0)
            val major = i % 6 == 0
            val r1 = r + 5f * d
            val r2 = r + (if (major) 11f else 8f) * d
            cv.drawLine(
                cx + (r1 * Math.cos(ang)).toFloat(), cy + (r1 * Math.sin(ang)).toFloat(),
                cx + (r2 * Math.cos(ang)).toFloat(), cy + (r2 * Math.sin(ang)).toFloat(), tickP)
        }
        cv.drawArc(box, 0f, 360f, false, track)
        if (frac > 0f) {
            cv.drawArc(box, -90f, 360f * frac, false, glowP)
            cv.drawArc(box, -90f, 360f * frac, false, arcP)
        }
        cv.drawText(txtBig, cx, cy + 5f * d, bigP)
        cv.drawText(txtSmall, cx, 86f * d, smallP)
    }
}

class GameBarService : Service() {
    private val nombre = "Gamer"

    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var strip: View? = null
    private var panel: View? = null
    private var stat: TextView? = null
    private var msg: TextView? = null
    private var g1: Gauge? = null
    private var g2: Gauge? = null
    private var ramBar: SegBar? = null
    private var tempBar: SegBar? = null
    private var perfBtn: Button? = null
    private var silBtn: Button? = null
    private var briBtn: Button? = null
    private var pantBtn: Button? = null
    private var rapBtn: Button? = null
    private var ahoBtn: Button? = null
    private var rgbBtn: Button? = null
    private var autoBtn: Button? = null
    private var game = ""
    private var inGame = true
    private var dnd = false
    private var fast = false
    private var perfOn = false
    private var origBri = -1
    private var lastClean = 0L
    private var briIdx = -1
    private var resIdx = 0
    private val levels = intArrayOf(30, 60, 100)
    private val resLevels = intArrayOf(100, 90, 80, 70)
    @Volatile private var busy = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun acc(): Int {
        val p = getSharedPreferences("ao", 0)
        val a = p.getInt("acc", 0)
        if (a != 0) return a
        return intArrayOf(0xFF00F0FF.toInt(), 0xFFFF2D55.toInt(), 0xFF39FF14.toInt())[p.getInt("theme", 0).coerceIn(0, 2)]
    }

    private fun dim(c: Int) = Color.rgb(Color.red(c) * 5 / 10, Color.green(c) * 5 / 10, Color.blue(c) * 5 / 10)

    private fun style(b: Button?, on: Boolean) {
        val bb = b ?: return
        val a = acc()
        bb.setTextColor(if (on) 0xFF05101E.toInt() else a)
        bb.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            if (on) intArrayOf(a, dim(a)) else intArrayOf(0xFF1A2C4E.toInt(), 0xFF0A1322.toInt())).apply {
            cornerRadius = dp(5).toFloat()
            setStroke(dp(1), a)
        }
    }

    private fun btn(t: String, on: Boolean, f: () -> Unit): Button {
        val b = Button(this)
        b.text = t; b.isAllCaps = false; b.textSize = 11f
        b.minHeight = 0; b.minimumHeight = 0
        b.setPadding(dp(2), 0, dp(2), 0)
        b.setTypeface(null, Typeface.BOLD)
        style(b, on)
        b.setOnClickListener { f() }
        return b
    }

    private fun slot(parent: LinearLayout, b: Button) {
        parent.addView(b, LinearLayout.LayoutParams(-1, dp(36)).apply { bottomMargin = dp(6) })
    }

    private fun label(t: String, sz: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; setTextColor(c); letterSpacing = 0.1f
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        game = i?.getStringExtra("game") ?: game
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gb", "Rayita de juego", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "gb").setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Rayita de juego activa")
            .setContentText("Toca la rayita del borde para abrir el panel").build()
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

    private fun needShizuku(): Boolean {
        if (Sz.ready()) return false
        msg?.text = "Necesita Shizuku (pestaña Pro)"
        return true
    }

    // Apps que nunca se cierran: las redes sociales y las que tú guardes en "keep".
    private fun keepSet(): Set<String> {
        val p = getSharedPreferences("ao", 0)
        return Opt.social + (p.getStringSet("keep", emptySet()) ?: emptySet())
    }

    // Cierra apps de fondo, respetando la lista protegida. Devuelve cuántas cerró.
    private fun cleanBackground(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = packageManager
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val keep = keepSet()
        var n = 0
        for (a in pm.getInstalledApplications(0)) {
            val pk = a.packageName
            if (pk == packageName || pk == game || pk == home || pk in keep) continue
            if ((a.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            am.killBackgroundProcesses(pk); n++
        }
        lastClean = System.currentTimeMillis()
        return n
    }

    // Activa o desactiva el modo rendimiento: 80% pantalla, brillo 60%, No molestar, limpieza.
    private fun togglePerf() {
        if (needShizuku()) return
        if (perfOn) {
            restoreAll()
            msg?.text = "Rendimiento desactivado"
            return
        }
        perfOn = true
        setPerfBtn()
        msg?.text = "Aplicando rendimiento…"
        Thread {
            val orig = Sz.sh("settings get system screen_brightness").trim().toIntOrNull()
            if (origBri < 0) origBri = orig ?: 255
            val n = cleanBackground()
            Opt.clearOwn(this)
            Sz.sh("pm trim-caches 999G")
            Opt.res(80)
            Sz.sh("settings put system screen_brightness_mode 0; settings put system screen_brightness 153; settings put global zen_mode 1")
            dnd = true
            resIdx = 2
            briIdx = 1
            h.post {
                style(silBtn, true); silBtn?.text = "Silencio ON"
                style(pantBtn, true); pantBtn?.text = "Pantalla 80%"
                style(briBtn, true); briBtn?.text = "Brillo 60%"
                msg?.text = "Rendimiento: 80% · brillo 60% · $n apps cerradas"
            }
        }.start()
    }

    // Mientras juegas: si la RAM baja mucho limpia de nuevo, y si hace calor baja a 70%.
    private fun maintain() {
        val (a, t) = Opt.ram(this)
        val tmp = Opt.temp(this)
        val now = System.currentTimeMillis()
        if (a.toFloat() / t < 0.20f && now - lastClean > 180_000L) {
            Thread {
                val n = cleanBackground()
                h.post { msg?.text = "RAM baja: cerré $n apps de fondo" }
            }.start()
        }
        if (tmp >= 42f && resIdx != 3 && Sz.ready()) {
            resIdx = 3
            Thread {
                Opt.res(70)
                h.post {
                    msg?.text = "Calor: bajé la pantalla a 70%"
                    pantBtn?.text = "Pantalla 70%"
                }
            }.start()
        }
    }

    private fun setPerfBtn() {
        perfBtn?.text = if (perfOn) "Rendimiento ON" else "Rendimiento"
        style(perfBtn, perfOn)
    }

    // Devuelve a lo normal lo que la rayita cambió.
    private fun restoreAll() {
        val needRes = resIdx != 0
        val needFast = fast
        val needDnd = dnd
        val needBri = briIdx != -1 || origBri >= 0
        if (!needRes && !needFast && !needDnd && !needBri) return
        if (!Sz.ready()) return
        val bri = if (origBri >= 0) origBri else 255
        resIdx = 0; fast = false; dnd = false; briIdx = -1; origBri = -1; perfOn = false
        Thread {
            val cmds = StringBuilder()
            if (needFast) cmds.append("settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1; ")
            if (needDnd) cmds.append("settings put global zen_mode 0; ")
            if (needBri) cmds.append("settings put system screen_brightness $bri; ")
            if (cmds.isNotEmpty()) Sz.sh(cmds.toString())
            if (needRes) Opt.res(100)
            h.post {
                silBtn?.text = "Silencio"; style(silBtn, false)
                pantBtn?.text = "Pantalla 100%"; style(pantBtn, false)
                briBtn?.text = "Brillo"; style(briBtn, false)
                rapBtn?.text = "Rápido"; style(rapBtn, false)
                setPerfBtn()
                msg?.text = "Restaurado al salir del juego"
            }
        }.start()
    }

    private fun doClean() {
        msg?.text = "Optimizando…"
        Thread {
            val r = try { Opt.full(this, game.ifEmpty { null }) } catch (e: Throwable) { "Error: ${e.message}" }
            h.post { msg?.text = r }
        }.start()
    }

    private fun toggleDnd() {
        if (needShizuku()) return
        dnd = !dnd
        val on = dnd
        Thread { Sz.sh("settings put global zen_mode ${if (on) 1 else 0}") }.start()
        silBtn?.text = if (on) "Silencio ON" else "Silencio"
        style(silBtn, on)
        msg?.text = if (on) "No molestar activado" else "No molestar apagado"
    }

    private fun cycleBrightness() {
        if (needShizuku()) return
        briIdx = (briIdx + 1) % levels.size
        val pc = levels[briIdx]
        val v = 255 * pc / 100
        Thread { Sz.sh("settings put system screen_brightness_mode 0; settings put system screen_brightness $v") }.start()
        briBtn?.text = "Brillo $pc%"
        style(briBtn, true)
        msg?.text = "Brillo $pc%"
    }

    private fun cycleRes() {
        if (needShizuku()) return
        resIdx = (resIdx + 1) % resLevels.size
        val pc = resLevels[resIdx]
        Thread {
            val r = Opt.res(pc)
            h.post { msg?.text = r }
        }.start()
        pantBtn?.text = "Pantalla $pc%"
        style(pantBtn, pc < 100)
    }

    private fun toggleFast() {
        if (needShizuku()) return
        fast = !fast
        val s = if (fast) "0.5" else "1"
        Thread {
            Sz.sh("settings put global window_animation_scale $s; settings put global tr
