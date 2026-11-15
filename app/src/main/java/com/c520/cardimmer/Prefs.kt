package com.c520.cardimmer

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import java.util.Calendar

/** 設定值與「現在要不要調暗」的判斷。 */
object Prefs {
    const val ENABLED = "enabled"
    const val LEVEL = "level"         // 暗度百分比 0–80
    const val SCHEDULE = "schedule"   // 依時間自動
    const val START = "start"         // 開始時間（一天中的第幾分鐘）
    const val END = "end"

    fun of(c: Context): SharedPreferences = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun enabled(p: SharedPreferences) = p.getBoolean(ENABLED, false)
    fun level(p: SharedPreferences) = p.getInt(LEVEL, 60).coerceIn(0, 100)
    fun schedule(p: SharedPreferences) = p.getBoolean(SCHEDULE, false)
    fun start(p: SharedPreferences) = p.getInt(START, 18 * 60).coerceIn(0, 1439)
    fun end(p: SharedPreferences) = p.getInt(END, 6 * 60).coerceIn(0, 1439)

    /** Android 12 起，其他 App 的疊層超過這個不透明度就會擋住觸控（通常 0.8）。 */
    fun maxPercent(c: Context): Int =
        (c.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch * 100).toInt()

    fun nowMinutes(): Int = Calendar.getInstance().let { it[Calendar.HOUR_OF_DAY] * 60 + it[Calendar.MINUTE] }

    /** 開始＝結束視為整天；開始 > 結束代表跨午夜（例如 18:00–06:00）。 */
    fun inWindow(start: Int, end: Int, now: Int): Boolean = when {
        start == end -> true
        start < end -> now in start until end
        else -> now >= start || now < end
    }

    /** 排程只負責在開始／結束時間切換 enabled，所以要不要調暗就看 enabled。 */
    fun shouldDim(p: SharedPreferences): Boolean = enabled(p)

    fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
}
