package com.autooptimizer

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*

// Fondo con esquinas cortadas (arriba a la derecha y abajo a la izquierda), estilo rayita
class Chamfer(private val fill: Int, private val edge: Int, private val cut: Float, private val sw: Float) : Drawable() {
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val ep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = edge; style = Paint.Style.STROKE; strokeWidth = sw
    }
    private val path = Path()

    override fun draw(c: Canvas) {
        val r = bounds
        val l = r.left + sw / 2f
        val t = r.top + sw / 2f
        val rt = r.right - sw / 2f
        val b = r.bottom - sw / 2f
        path.reset()
        path.moveTo(l, t); path.lineTo(rt - cut, t); path.lineTo(rt, t + cut)
        path.lineTo(rt, b); path.lineTo(l + cut, b); path.lineTo(l, b - cut); path.close()
        c.drawPath(path, fp)
        c.drawPath(path, ep)
    }

    override fun setAlpha(alpha: Int) { fp.alpha = alpha; ep.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { fp.colorFilter = cf; ep.colorFilter = cf }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Botón circular con brillo, anillos giratorios, pulsación y onda al tocar
class PulseButton(c: Context, private val accent: Int, private val label: String, private val onTap: () -> Unit) : View(c) {
    private val d = resources.displayMetrics.density
    private var scale = 1f
    private var ripple = -1f
    private var spin = 0f
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f * d; color = accent; alpha = 120
        maskFilter = BlurMaskFilter(10f * d, BlurMaskFilter.Blur.NORMAL)
    }
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * d; color = accent; alpha = 150
    }
    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f * d; color = accent; strokeCap = Paint.Cap.ROUND
    }
    private val rippleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f * d; color = accent
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B0F1A.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
    }
    private val spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 8000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { spin = it.animatedValue as Float; invalidate() }
    }

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    private fun dimC(c: Int) = Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (190f * d).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        spinAnim.start()
    }

    override fun onDetachedFromWindow() {
        spinAnim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 24f * d
        cv.save()
        cv.scale(scale, scale, cx, cy)

        cv.drawCircle(cx, cy, r + 4f * d, glowP)
        fillP.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.3f,
            intArrayOf(0xCCFFFFFF.toInt(), accent, dimC(accent)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, fillP)

        cv.drawCircle(cx, cy, r + 10f * d, ringP)
        cv.save()
        cv.rotate(spin, cx, cy)
        val box = RectF(cx - r - 16f * d, cy - r - 16f * d, cx + r + 16f * d, cy + r + 16f * d)
        cv.drawArc(box, 0f, 70f, false, arcP)
        cv.drawArc(box, 120f, 70f, false, arcP)
        cv.drawArc(box, 240f, 70f, false, arcP)
        cv.restore()

        textP.textSize = 22f * d
        cv.drawText("⚡", cx, cy + 2f * d, textP)
        textP.textSize = 12f * d
        cv.drawText(label, cx, cy + 22f * d, textP)

        if (ripple >= 0f) {
            rippleP.alpha = (255 * (1f - ripple)).toInt()
            cv.drawCircle(cx, cy, r + 10f * d + ripple * 40f * d, rippleP)
        }
        cv.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> animScale(0.9f, 90, LinearInterpolator())
            MotionEvent.ACTION_UP -> {
                animScale(1f, 260, OvershootInterpolator(2f))
                startRipple()
                onTap()
            }
            MotionEvent.ACTION_CANCEL -> animScale(1f, 160, LinearInterpolator())
        }
        return true
    }

    private fun animScale(to: Float, ms: Long, ip: TimeInterpolator) {
        ValueAnimator.ofFloat(scale, to).apply {
            duration = ms; interpolator = ip
            addUpdateListener { scale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun startRipple() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            addUpdateListener { ripple = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { ripple = -1f; invalidate() }
            })
            start()
        }
    }
}

class MainActivity : Activity() {
    private val p by lazy { getSharedPreferences("ao", 0) }
    private val h = Handler(Looper.getMainLooper())
    private lateinit var body: LinearLayout
    private val W = 0xFFFFFFFF.toInt()
    private val G = 0xFFAAAAAA.toInt()
    private val themes = arrayOf(
        intArrayOf(0xFF0B0F1A.toInt(), 0xFF151B2E.toInt(), 0xFF00F0FF.toInt()),
        intArrayOf(0xFF140808.toInt(), 0xFF241010.toInt(), 0xFFFF2D55.toInt()),
        intArrayOf(0xFF06120A.toInt(), 0xFF0E2214.toInt(), 0xFF39FF14.toInt()))
    private val names = arrayOf("Neón azul", "Rojo gamer", "Verde matrix")
    private val accents = intArrayOf(0xFF00F0FF.toInt(), 0xFFFF2D55.toInt(), 0xFF39FF14.toInt(),
        0xFFFFD60A.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF9F0A.toInt())
    private var bgKey = ""
    private var bgBmp: Bitmap? = null

    private fun th() = themes[p.getInt("theme", 0)]
    private fun bgC() = th()[0]
    private fun cardC() = th()[1]
    private fun acc() = if (p.getInt("acc", 0) != 0) p.getInt("acc", 0) else th()[2]
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun chamfer(fill: Int, edge: Int, cut: Int) = Chamfer(fill, edge, dp(cut).toFloat(), dp(1).toFloat())

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        render(p.getInt("tab", 0))
    }

    private fun decodeBg(uri: Uri): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > 1080) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
    }

    private fun bgDrawable(): Drawable? {
        val key = p.getString("bg", null) ?: return null
        if (key != bgKey || bgBmp == null) {
            bgBmp = try { decodeBg(Uri.parse(key)) } catch (e: Throwable) { null }
            bgKey = key
        }
        val bmp = bgBmp ?: return null
        return LayerDrawable(arrayOf<Drawable>(BitmapDrawable(resources, bmp), ColorDrawable(0x99000000.toInt())))
    }

    private fun pickBg() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(i, 42)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 42 && res == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Throwable) { }
            p.edit().putString("bg", uri.toString()).apply()
            bgKey = ""; bgBmp = null
            render(4)
        }
    }

    private fun tv(t: String, sz: Float = 14f, c: Int = W, b: Boolean = false) = TextView(this).apply {
        text = t; textSize = sz; setTextColor(c); if (b) setTypeface(null, Typeface.BOLD)
    }
    private fun head(t: String) = tv("■ $t", 14f, acc(), true).apply { letterSpacing = 0.08f }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(bgC()); setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.04f
        background = chamfer(acc(), acc(), 8)
        setOnClickListener { f() }
    }
    private fun sw(t: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = t; setTextColor(W); isChecked = on; setPadding(0, dp(6), 0, dp(6)); setOnCheckedChangeListener { _, v -> f(v) }
    }

    // Cada elemento dentro de la tarjeta se separa 8 dp del anterior
    private fun add(vararg v: View) {
        val hasBg = p.getString("bg", null) != null
        val fill = if (hasBg) (cardC() and 0x00FFFFFF) or 0xD8000000.toInt() else cardC()
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = chamfer(fill, acc(), 12)
            v.forEachIndexed { i, view ->
                addView(view, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
        }
        body.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) })
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun async(f: () -> String) = Thread {
        val r = try { f() } catch (e: Throwable) { "Error: ${e.message}" }
        runOnUiThread { toast(r) }
    }.start()
    private fun need(f: () -> String) = async { if (Sz.ready()) f() else "Primero activa Shizuku (mira el tutorial)" }

    private fun render(tab: Int) {
        h.removeCallbacksAndMessages(null)
        p.edit().putInt("tab", tab).apply()
        window.statusBarColor = bgC(); window.navigationBarColor = bgC()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(38), dp(14), dp(10))
        }
        root.background = bgDrawable() ?: ColorDrawable(bgC())
        root.addView(tv("⚡ AUTO OPTIMIZER", 20f, acc(), true).apply {
            letterSpacing = 0.12f; setShadowLayer(dp(8).toFloat(), 0f, 0f, acc())
        })
        root.addView(View(this).apply { setBackgroundColor(acc()) },
            LinearLayout.LayoutParams(-1, dp(2)).apply { setMargins(0, dp(6), 0, dp(10)) })
        val tabs = LinearLayout(this)
        listOf("Inicio", "Juegos", "Info", "Pro", "Estilo").forEachIndexed { i, t ->
            val sel = i == tab
            tabs.addView(tv(t, 12f, if (sel) bgC() else acc(), true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(7))
                background = chamfer(if (sel) acc() else cardC(), acc(), 6)
                setOnClickListener { render(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), dp(12)) })
        }
        root.addView(tabs)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        when (tab) { 0 -> home(); 1 -> games(); 2 -> info(); 3 -> pro(); else -> style() }
    }

    private fun home() {
        val stats = tv("", 13f)
        add(head("Estado en vivo"), stats)
        val tick = object : Runnable {
            override fun run() {
                val (a, t) = Opt.ram(this@MainActivity); val (fa, ft) = Opt.storage()
                stats.text = "RAM libre: ${a / 1048576} MB de ${t / 1048576} MB\n" +
                    "Almacenamiento: ${(ft - fa) * 100 / ft}% usado\n" +
                    "Temperatura: ${Opt.temp(this@MainActivity)} °C\n" +
                    "Batería: ${Opt.batt(this@MainActivity)}%\n" +
                    "Pantalla: ${windowManager.defaultDisplay.refreshRate.toInt()} Hz"
                h.postDelayed(this, 2000)
            }
        }
        tick.run()
        add(PulseButton(this, acc(), "OPTIMIZAR") { async { Opt.full(this) } })
        add(sw("Modo automático (cada 10 s)", p.getBoolean("auto", false)) { on ->
            p.edit().putBoolean("auto", on).apply()
            val i = Intent(this, OptService::class.java)
            if (on) startForegroundService(i) else stopService(i)
        }, tv("Cierra apps en segundo plano y limpia caché. No cierra WhatsApp, Instagram, etc., para no perder mensajes.", 12f, G))
        add(sw("Ahorro de batería", p.getBoolean("save", false)) { on ->
            p.edit().putBoolean("save", on).apply()
            if (Sz.ready()) async { Sz.sh("settings put global low_power ${if (on) 1 else 0}"); "Ahorro de batería ${if (on) "activado" else "desactivado"}" }
            else startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }, tv("Con Shizuku se activa directo; sin Shizuku te abre el ajuste del sistema.", 12f, G))
        add(head("Luz RGB en notificaciones"),
            tv("Borde muy fino y suave al llegar mensajes de WhatsApp, Instagram, Facebook, YouTube, Telegram, TikTok…", 12f, G),
            sw("Activar luz RGB", p.getBoolean("rgb", false)) { p.edit().putBoolean("rgb", it).apply() },
            btn("1) Permiso: acceso a notificaciones") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            btn("2) Permiso: mostrar sobre otras apps") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            },
            btn("Probar luz") { Opt.flash(this) })
    }

    private fun games() {
        val pm = packageManager
        add(head("Mis juegos"), btn("＋ Agregar juego") { pick() },
            tv("Mantén pulsado un juego para quitarlo.", 12f, G))
        add(sw("Rayita de juego (panel en el borde)", p.getBoolean("bar", true)) { p.edit().putBoolean("bar", it).apply() },
            tv("Al tocar 'Optimizar y jugar' aparece una rayita en el borde del juego. Tócala para abrir el panel.", 12f, G))
        val set = p.getStringSet("games", emptySet())!!
        for (pk in set) {
            val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pk, 0)).toString() } catch (e: Exception) { continue }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(ImageView(this).apply { setImageDrawable(pm.getApplicationIcon(pk)) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            row.addView(tv(name, 14f, W, true).apply { setPadding(dp(10), 0, dp(6), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(btn("Optimizar y jugar") { play(pk) }.apply { textSize = 12f })
            row.setOnLongClickListener {
                p.edit().putStringSet("games", HashSet(set).apply { remove(pk) }).apply(); render(1); true
            }
            add(row)
        }
        protectedCard()
    }

    // Lista de apps protegidas: las que marcaste con "Proteger app" se pueden quitar aquí.
    private fun protectedCard() {
        val pm = packageManager
        val keep = (p.getStringSet("keep", emptySet()) ?: emptySet()).toList().sorted()
        val views = mutableListOf<View>(
            head("Apps protegidas"),
            tv("Estas apps no se cierran al limpiar. Las redes sociales siempre están protegidas y no se pueden quitar.", 12f, G)
        )
        if (keep.isEmpty()) {
            views.add(tv("No tienes apps protegidas extra. Entra a un juego, abre la rayita y toca Proteger app.", 12f, G))
        } else {
            for (pk in keep) {
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pk, 0)).toString() } catch (e: Exception) { pk }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(tv(name, 13f, W, true), LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(btn("Quitar") { unprotect(pk) }.apply { textSize = 12f },
                    LinearLayout.LayoutParams(dp(100), -2))
                views.add(row)
            }
        }
        add(*views.toTypedArray())
    }

    private fun unprotect(pk: String) {
        val set = HashSet(p.getStringSet("keep", emptySet()) ?: emptySet())
        set.remove(pk)
        p.edit().putStringSet("keep", set).apply()
        toast("Ya no está protegida")
        render(1)
    }

    private fun pick() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        AlertDialog.Builder(this).setTitle("Elige un juego")
            .setItems(apps.map { it.second }.toTypedArray()) { _, i ->
                p.edit().putStringSet("games", HashSet(p.getStringSet("games", emptySet())!!).apply { add(apps[i].first) }).apply()
                render(1)
            }.show()
    }
    private fun startGameBar(pk: String) {
        if (!p.getBoolean("bar", true)) return
        if (!Settings.canDrawOverlays(this)) {
            toast("Para la rayita activa 'mostrar sobre otras apps' en la pestaña Inicio")
            return
        }
        startForegroundService(Intent(this, GameBarService::class.java).putExtra("game", pk))
    }
    private fun play(pk: String) {
        toast("Optimizando…")
        Thread {
            val r = try { Opt.full(this, pk) } catch (e: Throwable) { "Error: ${e.message}" }
            runOnUiThread {
                toast(r)
                startGameBar(pk)
                packageManager.getLaunchIntentForPackage(pk)?.let { startActivity(it) }
            }
        }.start()
    }

    private fun info() {
        val (a, t) = Opt.ram(this); val (fa, ft) = Opt.storage(); val dm = resources.displayMetrics
        add(head("Tu dispositivo"), tv(
            "Modelo: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "RAM total: ${"%.1f".format(t / 1073741824.0)} GB\n" +
            "RAM libre: ${a / 1048576} MB\n" +
            "Procesador: ${Opt.cpu()}\n" +
            "Núcleos: ${Runtime.getRuntime().availableProcessors()}\n" +
            "GPU: ${Opt.gpu()}\n" +
            "Almacenamiento: ${"%.1f".format(ft / 1.0e9)} GB (libre ${"%.1f".format(fa / 1.0e9)} GB)\n" +
            "Pantalla: ${dm.widthPixels}x${dm.heightPixels} @ ${windowManager.defaultDisplay.refreshRate.toInt()} Hz\n" +
            "Temperatura: ${Opt.temp(this)} °C", 14f))
        add(head("Créditos"),
            tv("App creada por ZORO HACK", 14f, W, true),
            tv("Erik Pérez Ortiz", 14f, W),
            tv("GitHub: erikperezortiz2-sketch", 12f, G),
            tv("© 2026 Erik Pérez Ortiz (ZORO HACK). Todos los derechos reservados. Puedes usar la app, pero no copiarla ni venderla como tuya.", 11f, G))
    }

    private fun pro() {
        val st = if (Sz.ready()) "✅ Shizuku conectado y con permiso" else if (Sz.running()) "⚠️ Shizuku activo, falta el permiso" else "❌ Shizuku no está activo"
        add(head("Modo Pro (Shizuku, gratis)"), tv(st, 14f))
        add(head("Tutorial paso a paso"), tv(
            "1) Instala Shizuku (gratis, Play Store o GitHub).\n" +
            "2) Ajustes > Acerca del teléfono: toca 7 veces 'Número de compilación'.\n" +
            "3) Opciones de desarrollador: activa 'Depuración inalámbrica' (con WiFi).\n" +
            "4) Abre Shizuku > 'Emparejar'. En Depuración inalámbrica toca 'Vincular con código' y escribe el código en la notificación de Shizuku.\n" +
            "5) En Shizuku toca 'Iniciar'.\n" +
            "6) Vuelve aquí y toca 'Conceder permiso'.\n\n" +
            "Android 10 o menor: necesita PC con ADB (Shizuku lo explica). Al reiniciar el móvil repite el paso 5.", 13f),
            btn("Abrir Shizuku") {
                val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                startActivity(i ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            },
            btn("Opciones de desarrollador") { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
            btn("Conceder permiso") { Sz.ask(); h.postDelayed({ render(3) }, 4000) })
        val row = LinearLayout(this)
        for (pc in intArrayOf(90, 80, 70, 60)) {
            row.addView(btn("$pc%") { need { Opt.res(pc) } },
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        add(head("Resolución de pantalla (más FPS)"),
            tv("Menos resolución = menos carga para la GPU. Si algo se ve mal, toca Restaurar.", 12f, G), row,
            btn("Restaurar resolución") { need { Opt.res(100) } })
        add(head("Más herramientas"),
            btn("Limpiar caché de TODAS las apps") { need { Sz.sh("pm trim-caches 999G"); "Caché global limpiada" } },
            btn("Animaciones rápidas (0.5x)") {
                need { Sz.sh("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"); "Animaciones 0.5x" }
            },
            btn("Animaciones normales (1x)") {
                need { Sz.sh("settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1"); "Animaciones 1x" }
            })
    }

    private fun style() {
        add(head("Fondo de la app"),
            tv("Elige una foto de tu galería como fondo. Las tarjetas se ven semitransparentes encima.", 12f, G),
            btn("Elegir foto de la galería") { pickBg() },
            btn("Quitar fondo") { p.edit().remove("bg").apply(); bgKey = ""; bgBmp = null; render(4) })
        add(head("Interfaz (3 estilos)"))
        names.forEachIndexed { i, n ->
            add(btn((if (p.getInt("theme", 0) == i) "✔ " else "") + n) { p.edit().putInt("theme", i).putInt("acc", 0).apply(); render(4) })
        }
        val row = LinearLayout(this)
        accents.forEach { c ->
            row.addView(View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                setOnClickListener { p.edit().putInt("acc", c).apply(); render(4) }
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        add(head("Color de acento"), row)
    }
}
