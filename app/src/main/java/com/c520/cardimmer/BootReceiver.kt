package com.c520.cardimmer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 開機或 App 更新後，自動把調暗服務叫起來（恢復上次設定）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (ok && Settings.canDrawOverlays(context)) DimService.refresh(context)
    }
}
