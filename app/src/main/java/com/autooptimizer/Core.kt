package com.autooptimizer

import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.*
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.*
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import rikka.shizuku.Shizuku

object Sz {
    fun running() = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    fun ready() = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0 } catch (e: Throwable) { false }
    fun ask() = try { Shizuku.requestPermission(1) } catch (e: Throwable) { }
    fun sh(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        val pr = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as java.lang.Process
        val out = pr.inputStream.bufferedReader().readText()
        pr.waitFor(); out
    } catch (e: Throwable) { "ERR ${e.message}" }
}

object Opt {
    val social = setOf("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android", "com.facebook.katana",
        "com.facebook.orca", "com.google.android.youtube", "org.telegram.messenger",
        "com.zhiliaoapp.musically", "com.twitter.android", "com.snapchat.android", "com.discord")

    fun ram(c: Context): Pair<Long, Long> {
        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return Pair(mi.availMem, mi.totalMem)
    }
    fun storage(): Pair<Long, Long> { val s = StatFs(Environment.getDataDirectory().path); return Pair(s.availableBytes, s.totalBytes) }
    fun temp(c: Context): Float =
        (c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("temperature", 0) ?: 0) / 10f
    fun batt(c: Context): Int =
        c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("level", 0) ?: 0
    fun cpu(): String {
        val m = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
        return if (m.isNotEmpty() && m != "unknown") "${Build.SOC_MANUFACTURER} $m" else Build.HARDWARE
    }
    fun gpu(): String = try {
        val d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); EGL14.eglInitialize(d, v, 0, v, 1)
        val cfg = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(d, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE), 0, cfg, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val sf = EGL14.eglCreatePbufferSurface(d, cfg[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(d, sf, sf, ctx)
        val r = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Desconocida"
        EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(d, sf); EGL14.eglDestroyContext(d, ctx); EGL14.eglTerminate(d)
        r
    } catch (e: Throwable) { "Desconocida" }

    fun killBg(c: Context, keep: String? = null): Int {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = c.packageManager
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        var n = 0
        for (a in pm.getInstalledApplications(0)) {
            val pk = a.packageName
            if (pk == c.packageName || pk == keep || pk == home || pk in social) continue
            if ((a.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            am.killBackgroundProcesses(pk); n++
        }
        return n
    }
    fun clearOwn(c: Context) {
        c.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        c.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }
    fun full(c: Context, game: String? = null): String {
        val k = killBg(c, game); clearOwn(c)
        var extra = ""
        if (Sz.ready()) {
            Sz.sh("pm trim-caches 999G")
            if (game != null) Sz.sh("cmd game mode performance $game")
            extra = " + caché global"
        }
        val (a, _) = ram(c)
        return "Listo: $k apps cerradas$extra. RAM libre: ${a / 1048576} MB"
    }
    fun res(pct: Int): String {
        if (pct >= 100) { Sz.sh("wm size reset; wm density reset"); return "Resolución restaurada" }
        val out = Sz.sh("wm size; wm density")
        val s = Regex("Physical size: (\\d+)x(\\d+)").find(out) ?: return "No pude leer la resolución"
        val d = Regex("Physical density: (\\d+)").find(out)?.groupValues?.get(1)?.toInt() ?: 420
        val w = s.groupValues[1].toInt() * pct / 100 / 2 * 2
        val h = s.groupValues[2].toInt() * pct / 100 / 2 * 2
        Sz.sh("wm size ${w}x$h; wm density ${d * pct / 100}")
        return "Resolución ${w}x$h ($pct%)"
    }
    fun notify(c: Context, id: Int, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ao", "Avisos", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(id, Notification.Builder(c, "ao").setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title).setContentText(text).build())
    }
    fun flash(c: Context) {
        if (!Settings.canDrawOverlays(c)) return
        val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = RgbBorder(c)
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        wm.addView(v, lp)
        Handler(Looper.getMainLooper()).postDelayed({ try { wm.removeView(v) } catch (e: Exception) { } }, 2500)
    }
}

class RgbBorder(c: Context) : View(c) {
    private val sw = resources.displayMetrics.density * 2.5f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; alpha = 110 }
    private var sweep: SweepGradient? = null
    private val m = Matrix()
    private var ang = 0f
    private val anim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { ang = it.animatedValue as Float; invalidate() }
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sweep = SweepGradient(w / 2f, h / 2f, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED), null)
        paint.shader = sweep
    }
    override fun onDraw(cv: Canvas) {
        m.setRotate(ang, width / 2f, height / 2f); sweep?.setLocalMatrix(m)
        cv.drawRoundRect(RectF(sw / 2, sw / 2, width - sw / 2, height - sw / 2), 70f, 70f, paint)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
}

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!getSharedPreferences("ao", 0).getBoolean("rgb", false)) return
        if (sbn.packageName !in Opt.social) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        Handler(Looper.getMainLooper()).post { Opt.flash(applicationContext) }
    }
}

class OptService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var last = 0L
    private val tick = object : Runnable {
        override fun run() {
            Thread {
                Opt.killBg(this@OptService); Opt.clearOwn(this@OptService)
                if (Sz.ready()) Sz.sh("pm trim-caches 999G")
                alerts()
            }.start()
            h.postDelayed(this, 10_000)
        }
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("fg", "Servicio", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "fg").setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Auto Optimizer activo").setContentText("Optimizando cada 10 s").build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        h.removeCallbacks(tick); h.post(tick)
        return START_STICKY
    }
    override fun onDestroy() { h.removeCallbacks(tick) }
    private fun alerts() {
        val now = System.currentTimeMillis(); if (now - last < 300_000) return
        val t = Opt.temp(this); val (fa, ft) = Opt.storage()
        if (t >= 42f) { Opt.notify(this, 2, "🔥 Móvil muy caliente", "$t °C: cierra apps pesadas o deja de cargarlo"); last = now }
        else if ((ft - fa) * 100 / ft >= 90) { Opt.notify(this, 3, "💾 Almacenamiento casi lleno", "Libera espacio para que el móvil rinda mejor"); last = now }
    }
}
