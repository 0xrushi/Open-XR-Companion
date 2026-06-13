package com.inmoair.xrcompanion.core.command

import android.util.Log
import com.inmoair.xrcompanion.core.accessibility.XRAccessibilityService
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardCommandHandler @Inject constructor() {
    private val TAG = "KeyboardHandler"

    fun handle(cmd: XRCommand) {
        val svc = XRAccessibilityService.instance ?: run {
            Log.w(TAG, "Accessibility service not running")
            return
        }
        when (cmd.action) {
            "text"       -> if (cmd.value.isNotEmpty()) svc.injectText(cmd.value)
            "backspace"  -> svc.injectBackspace()
            "enter"      -> svc.injectEnter()
            "paste"      -> svc.injectText(cmd.value)
            else         -> Log.w(TAG, "Unknown keyboard action: ${cmd.action}")
        }
    }
}
