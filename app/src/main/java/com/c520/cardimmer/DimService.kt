package com.c520.cardimmer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager

/**
 * 常駐服務：在所有畫面上蓋一層可觸控穿透的黑色疊層，並在通知列提供開關。
 * 開了「依時間自動」時每 30 秒檢查一次是否在時段內。
 */
class DimService : Service() {

    companion object {
        private const val CHANNEL = "dim"
        private const val NOTIF_ID = 1
        private const val ACTION_TOGGLE = "com.c520.cardimmer.TOGGLE"

        fun refresh(c: Context) {
            try {
                c.startForegroundService(Intent(c, DimService::class.java))
            } catch (e: Exception) {
                // 背景啟動被系統拒絕時不讓 App 當掉；下次打開 App 會再啟動
                Log.w("DimService", "start failed", e)
            }
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastNotifText: String? = null
    // 上次檢查時是否在排程時段內；null＝還沒同步過（服務剛啟動、或剛打開排程）
    private var lastInWindow: Boolean? = null
    private val tick = object : Runnable {
        override fun run() {
            apply()
            // 對準下一個整分（多留 0.5 秒），時間一到就切換，不會晚半分鐘
            if (Prefs.schedule(Prefs.of(this@DimService))) {
                handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 500)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, L.t("調暗開關", "Dimmer switch"), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = Prefs.of(this)
        if (intent?.action == ACTION_TOGGLE) p.edit().putBoolean(Prefs.ENABLED, !Prefs.enabled(p)).apply()
        lastNotifText = null
        // 車機是 Android 13：specialUse 類型是 14 才有，13 以下用不帶類型的版本
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        handler.removeCallbacks(tick)
        tick.run()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeOverlay()
        super.onDestroy()
    }

    private fun apply() {
        val p = Prefs.of(this)
        if (Prefs.schedule(p)) {
            // 像手機夜燈：進入時段自動打開、離開時段自動關閉；中途手動切換會維持到下一個時間點
            val inWin = Prefs.inWindow(Prefs.start(p), Prefs.end(p), Prefs.nowMinutes())
            if (inWin != lastInWindow) p.edit().putBoolean(Prefs.ENABLED, inWin).apply()
            lastInWindow = inWin
        } else {
            lastInWindow = null
        }
        if (Settings.canDrawOverlays(this) && Prefs.shouldDim(p)) {
            showOverlay(minOf(Prefs.level(p), Prefs.maxPercent(this)) / 100f)
        } else {
            removeOverlay()
        }
        val prev = lastNotifText
        val n = buildNotification()
        if (lastNotifText != prev) {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        }
    }

    private fun showOverlay(alpha: Float) {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.alpha = alpha
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "CarDimmerOverlay"
        }
        try {
            val v = overlay
            if (v == null) {
                overlay = View(this).apply {
                    setBackgroundColor(getColor(R.color.overlay))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.also { wm.addView(it, lp) }
            } else {
                wm.updateViewLayout(v, lp)
            }
        } catch (e: Exception) {
            // 權限剛被收回等情況
            Log.w("DimService", "overlay failed", e)
            overlay = null
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }

    private fun buildNotification(): Notification {
        val p = Prefs.of(this)
        val enabled = Prefs.enabled(p)
        val text = when {
            !Settings.canDrawOverlays(this) -> L.t("尚未授權，點這裡開啟設定", "Permission needed. Tap to open settings")
            !enabled && Prefs.schedule(p) -> L.t("已關閉，${Prefs.hhmm(Prefs.start(p))} 自動開啟", "Off. Turns on at ${Prefs.hhmm(Prefs.start(p))}")
            !enabled -> L.t("已關閉", "Off")
            Prefs.schedule(p) -> L.t("調暗中 ${minOf(Prefs.level(p), Prefs.maxPercent(this))}%（${Prefs.hhmm(Prefs.end(p))} 自動關閉）", "Dimming ${minOf(Prefs.level(p), Prefs.maxPercent(this))}% (turns off at ${Prefs.hhmm(Prefs.end(p))})")
            else -> L.t("調暗中 ${minOf(Prefs.level(p), Prefs.maxPercent(this))}%", "Dimming ${minOf(Prefs.level(p), Prefs.maxPercent(this))}%")
        }
        lastNotifText = text
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getForegroundService(
            this, 1, Intent(this, DimService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_moon),
                    if (enabled) L.t("關閉", "Turn off") else L.t("開啟", "Turn on"), toggle,
                ).build()
            )
            .build()
    }
}
