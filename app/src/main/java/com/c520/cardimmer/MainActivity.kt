package com.c520.cardimmer

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** 唯一的設定畫面：左邊開關＋暗度，右邊依時間自動。介面用程式碼組，不另外加套件。 */
class MainActivity : Activity() {

    private val p by lazy { Prefs.of(this) }
    private var binding = false

    private lateinit var permCard: View
    private lateinit var permOk: TextView
    private lateinit var dimSwitch: Switch
    private lateinit var levelText: TextView
    private lateinit var seek: SeekBar
    private lateinit var maxNote: TextView
    private lateinit var schedSwitch: Switch
    private lateinit var timeBox: LinearLayout
    private lateinit var startBtn: Button
    private lateinit var endBtn: Button
    private lateinit var schedNote: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // 排程或通知列改了設定時，畫面即時跟著更新（要保留強參考，否則會被回收）
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { render() } }

    override fun onResume() {
        super.onResume()
        p.registerOnSharedPreferenceChangeListener(prefListener)
        render()
        if (Settings.canDrawOverlays(this)) DimService.refresh(this)
    }

    override fun onPause() {
        p.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        if (Settings.canDrawOverlays(this)) DimService.refresh(this)
        render()
    }

    private fun maxLevel() = (Prefs.maxPercent(this) / 5 * 5).coerceIn(5, 100)

    private fun render() {
        binding = true
        val granted = Settings.canDrawOverlays(this)
        permCard.visibility = if (granted) View.GONE else View.VISIBLE
        permOk.visibility = if (granted) View.VISIBLE else View.GONE

        dimSwitch.isChecked = Prefs.enabled(p)
        val max = maxLevel()
        val lv = minOf(Prefs.level(p), max)
        seek.max = max / 5
        seek.progress = lv / 5
        seek.stateDescription = "$lv%"
        levelText.text = "$lv%"
        maxNote.text = L.t("最高 $max%（Android 觸控限制）。車機系統亮度先調到最低，效果最好。", "Max $max% (Android touch limit). Works best with the head unit's own brightness at minimum.")

        val sched = Prefs.schedule(p)
        val s = Prefs.hhmm(Prefs.start(p))
        val e = Prefs.hhmm(Prefs.end(p))
        schedSwitch.isChecked = sched
        startBtn.text = s
        startBtn.contentDescription = L.t("開始時間 $s", "Start time $s")
        endBtn.text = e
        endBtn.contentDescription = L.t("結束時間 $e", "End time $e")
        timeBox.alpha = if (sched) 1f else 0.4f
        schedNote.text = if (sched) L.t("每天 $s 自動開啟、$e 自動關閉，中途仍可手動開關。", "Turns on at $s and off at $e every day. You can still switch it by hand.") else L.t("關閉時只用上面的開關手動控制。", "When off, only the switch above controls dimming.")
        binding = false
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, L.t("找不到授權頁，請到系統設定 > 應用程式 > 車機調暗，開啟「顯示在其他應用程式上層」", "Can't open the permission page. Go to Settings > Apps > Car Dimmer and allow \"Display over other apps\"."), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun pickTime(key: String, cur: Int) {
        TimePickerDialog(this, R.style.TimeDialog, { _, h, m -> save { putInt(key, h * 60 + m) } }, cur / 60, cur % 60, true).show()
    }

    // ---------- 介面元件（顏色一律引用 colors.xml 的設計 token） ----------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun col(id: Int) = getColor(id)

    private fun box(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(col(fill)); setStroke(dp(1), col(stroke)); cornerRadius = dp(radius).toFloat()
    }

    private fun pressable(d: Drawable) = RippleDrawable(ColorStateList.valueOf(col(R.color.hairline_strong)), d, null)

    private fun text(s: String, sp: Float, color: Int = R.color.ink, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(col(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = box(R.color.surface_1, R.color.hairline, 12)
        setPadding(dp(24), dp(20), dp(24), dp(20))
    }

    private fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    /** 大尺寸開關（88×44dp），車上好點。軌道與圓鈕用自訂形狀，顏色由下面的 tint 決定。 */
    private fun bigSwitch(label: String, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(col(R.color.ink))
        minHeight = dp(80)
        trackDrawable = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(22).toFloat(); setSize(dp(88), dp(44))
        }
        thumbDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(5), Color.TRANSPARENT)   // 透明外框讓圓鈕內縮在軌道裡
            setSize(dp(44), dp(44))
        }
        switchMinWidth = dp(88)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        trackTintList = ColorStateList(states, intArrayOf(col(R.color.primary), col(R.color.ink_tertiary)))
        thumbTintList = ColorStateList.valueOf(col(R.color.ink))
        setOnCheckedChangeListener { _, on -> if (!binding) onChange(on) }
    }

    private fun timeButton(onClick: () -> Unit) = Button(this).apply {
        isAllCaps = false
        stateListAnimator = null
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(col(R.color.ink))   // 明確指定，避免沿用主題色而看不到數字
        background = pressable(box(R.color.surface_2, R.color.hairline_strong, 8))
        minWidth = dp(140)
        minHeight = dp(64)
        setPadding(dp(20), 0, dp(20), 0)
        setOnClickListener { onClick() }
    }

    private fun timeRow(label: String, btn: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(label, 20f, R.color.ink_subtle), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(btn)
        setPadding(0, dp(6), 0, dp(6))
    }

    /** 拉桿：灰色底條＋灰色刻度，已選部分用主色。 */
    private fun buildSeek() = SeekBar(this).apply {
        fun bar(color: Int) = GradientDrawable().apply { setColor(col(color)); cornerRadius = dp(7).toFloat() }
        progressDrawable = LayerDrawable(
            arrayOf(bar(R.color.ink_tertiary), ClipDrawable(bar(R.color.primary), Gravity.START, ClipDrawable.HORIZONTAL))
        ).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
            for (i in 0..1) { setLayerHeight(i, dp(14)); setLayerGravity(i, Gravity.CENTER_VERTICAL or Gravity.FILL_HORIZONTAL) }
        }
        tickMark = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(R.color.ink_subtle))
            setSize(dp(8), dp(8))
        }
        // 大圓鈕（40dp），白框讓它在主色底條上也看得清楚
        thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(col(R.color.primary_hover))
            setStroke(dp(3), col(R.color.ink))
            setSize(dp(40), dp(40))
        }
        thumbOffset = dp(20)
        minHeight = dp(72)
        contentDescription = L.t("暗度", "Dim level")
        setPadding(dp(24), 0, dp(24), 0)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                if (!binding && fromUser) save { putInt(Prefs.LEVEL, v * 5) }
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }

        // 標題列
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = text(getString(R.string.app_name), 28f, bold = true).apply {
            val moon = getDrawable(R.drawable.ic_moon)!!.mutate().apply {
                setTint(col(R.color.primary_hover)); setBounds(0, 0, dp(28), dp(28))
            }
            setCompoundDrawablesRelative(moon, null, null, null)
            compoundDrawablePadding = dp(12)
        }
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        permOk = text(L.t("權限已授予", "Permission granted"), 16f, R.color.success)
        header.addView(permOk)
        root.addView(header)
        root.addView(gap(16))

        // 未授權時的提示
        permCard = card().apply {
            addView(text(L.t("需要「顯示在其他應用程式上層」權限才能調暗畫面。", "Dimming needs the \"Display over other apps\" permission."), 20f))
            addView(gap(12))
            addView(Button(this@MainActivity).apply {
                text = L.t("前往授權", "Allow")
                isAllCaps = false
                stateListAnimator = null
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(col(R.color.ink))
                background = pressable(GradientDrawable().apply {
                    setColor(col(R.color.primary)); cornerRadius = dp(8).toFloat()
                })
                minHeight = dp(64)
                setPadding(dp(28), 0, dp(28), 0)
                setOnClickListener { openOverlaySettings() }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        root.addView(permCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // 左卡：開關＋暗度
        dimSwitch = bigSwitch(L.t("調暗", "Dim")) { on ->
            if (on && !Settings.canDrawOverlays(this)) openOverlaySettings()
            save { putBoolean(Prefs.ENABLED, on) }
        }
        levelText = text("", 34f, bold = true)
        seek = buildSeek()
        maxNote = text("", 15f, R.color.ink_subtle)
        val left = card().apply {
            addView(dimSwitch, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(gap(16))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text(L.t("暗度", "Dim level"), 20f, R.color.ink_subtle), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(levelText)
            })
            addView(seek, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(gap(12))
            addView(maxNote)
        }

        // 右卡：依時間自動
        schedSwitch = bigSwitch(L.t("依時間自動", "Schedule")) { on -> save { putBoolean(Prefs.SCHEDULE, on) } }
        startBtn = timeButton { pickTime(Prefs.START, Prefs.start(p)) }
        endBtn = timeButton { pickTime(Prefs.END, Prefs.end(p)) }
        timeBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(timeRow(L.t("開始", "Start"), startBtn))
            addView(timeRow(L.t("結束", "End"), endBtn))
        }
        schedNote = text("", 15f, R.color.ink_subtle)
        val right = card().apply {
            addView(schedSwitch, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(gap(8))
            addView(timeBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(gap(12))
            addView(schedNote)
        }

        // 寬螢幕（車機橫向）左右並排，窄螢幕上下排
        val wide = resources.configuration.screenWidthDp >= 720
        val cards = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        if (wide) {
            cards.addView(left, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginEnd = dp(16) })
            cards.addView(right, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        } else {
            cards.addView(left, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })
            cards.addView(right, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(cards, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // 關於：聯繫作者、更多軟體（只開寄信的 App 和瀏覽器；車機上沒有的話會顯示信箱和網址）
        root.addView(gap(24))
        root.addView(text(L.t("關於", "About"), 22f, bold = true))
        root.addView(text("${getString(R.string.app_name)} ${About.version(this)}", 18f, R.color.ink_subtle))
        root.addView(gap(8))
        root.addView(text(About.CONTACT_TEXT, 18f, R.color.ink_subtle))
        root.addView(text(About.MORE_TEXT, 18f, R.color.ink_subtle))
        root.addView(gap(12))
        root.addView(LinearLayout(this).apply {
            addView(timeButton { About.mail(this@MainActivity, getString(R.string.app_name)) }.apply { text = L.t("寄信給作者", "Email the author"); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) })
            addView(
                timeButton { About.more(this@MainActivity) }.apply { text = L.t("更多軟體", "More apps"); setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f) },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(16) },
            )
        })

        return ScrollView(this).apply {
            isFillViewport = true
            // Android 15 起強制滿版，內容要自己讓開狀態列與導覽列
            fitsSystemWindows = true
            addView(root)
        }
    }
}
