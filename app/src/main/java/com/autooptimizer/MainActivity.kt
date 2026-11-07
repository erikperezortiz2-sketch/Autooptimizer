package com.autooptimizer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.view.View
import android.widget.*

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
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(bgC()); setTypeface(null, Typeface.BOLD)
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(acc()) }
        setOnClickListener { f() }
    }
    private fun sw(t: String, on: Boolean, f: (Boolean) -> Unit) = Switch(this).apply {
        text = t; setTextColor(W); isChecked = on; setPadding(0, dp(6), 0, dp(6)); setOnCheckedChangeListener { _, v -> f(v) }
    }
    private fun add(vararg v: View) {
        val hasBg = p.getString("bg", null) != null
        val cardBg = if (hasBg) (cardC() and 0x00FFFFFF) or 0xD8000000.toInt() else cardC()
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(cardBg); cornerRadius = dp(16).toFloat(); setStroke(dp(1), acc()) }
            v.forEach { addView(it) }
        }
        body.addView(c, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
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
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(36), dp(12), dp(8))
        }
        root.background = bgDrawable() ?: ColorDrawable(bgC())
        root.addView(tv("⚡ AUTO OPTIMIZER", 22f, acc(), true))
        val tabs = LinearLayout(this)
        listOf("Inicio", "Juegos", "Info", "Pro", "Estilo").forEachIndexed { i, t ->
            tabs.addView(tv(t, 13f, if (i == tab) bgC() else acc(), true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(if (i == tab) acc() else cardC()) }
                setOnClickListener { render(i) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(8), dp(2), dp(8)) })
        }
        root.addView(tabs)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        when (tab) { 0 -> home(); 1 -> games(); 2 -> info(); 3 -> pro(); else -> style() }
    }

    private fun home() {
        val stats = tv("", 15f)
        add(tv("Estado en vivo", 16f, acc(), true), stats)
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
        add(btn("⚡ OPTIMIZAR AHORA") { async { Opt.full(this) } })
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
        add(tv("Luz RGB en notificaciones", 16f, acc(), true),
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
        add(tv("Mis juegos", 16f, acc(), true), btn("＋ Agregar juego") { pick() },
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
        add(tv("Tu dispositivo", 16f, acc(), true), tv(
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
        add(tv("Créditos", 16f, acc(), true),
            tv("App creada por ZORO HACK", 14f, W, true),
            tv("Erik Pérez Ortiz", 14f, W),
            tv("GitHub: erikperezortiz2-sketch", 12f, G),
            tv("© 2026 Erik Pérez Ortiz (ZORO HACK). Todos los derechos reservados. Puedes usar la app, pero no copiarla ni venderla como tuya.", 11f, G))
    }

    private fun pro() {
        val st = if (Sz.ready()) "✅ Shizuku conectado y con permiso" else if (Sz.running()) "⚠️ Shizuku activo, falta el permiso" else "❌ Shizuku no está activo"
        add(tv("Modo Pro (Shizuku, gratis)", 16f, acc(), true), tv(st, 14f))
        add(tv("Tutorial paso a paso", 15f, acc(), true), tv(
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
        for (pc in intArrayOf(90, 80, 70, 60)) row.addView(btn("$pc%") { need { Opt.res(pc) } }, LinearLayout.LayoutParams(0, -2, 1f))
        add(tv("Resolución de pantalla (más FPS)", 16f, acc(), true),
            tv("Menos resolución = menos carga para la GPU. Si algo se ve mal, toca Restaurar.", 12f, G), row,
            btn("Restaurar resolución") { need { Opt.res(100) } })
        add(tv("Más herramientas", 16f, acc(), true),
            btn("Limpiar caché de TODAS las apps") { need { Sz.sh("pm trim-caches 999G"); "Caché global limpiada" } },
            btn("Animaciones rápidas (0.5x)") {
                need { Sz.sh("settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"); "Animaciones 0.5x" }
            },
            btn("Animaciones normales (1x)") {
                need { Sz.sh("settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1"); "Animaciones 1x" }
            })
    }

    private fun style() {
        add(tv("Fondo de la app", 16f, acc(), true),
            tv("Elige una foto de tu galería como fondo. Las tarjetas se ven semitransparentes encima.", 12f, G),
            btn("Elegir foto de la galería") { pickBg() },
            btn("Quitar fondo") { p.edit().remove("bg").apply(); bgKey = ""; bgBmp = null; render(4) })
        add(tv("Interfaz (3 estilos)", 16f, acc(), true))
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
        add(tv("Color de acento", 16f, acc(), true), row)
    }
}
