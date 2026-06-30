package com.inmoair.xrcompanion.core.command

import android.util.Log
import com.inmoair.xrcompanion.core.accessibility.XRAccessibilityService
import com.inmoair.xrcompanion.core.ime.XRInputMethodService
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
        Log.v(TAG, "keyboard action=${cmd.action} valueLen=${cmd.value.length}")
        when (cmd.action) {
            "text"       -> if (cmd.value.isNotEmpty()) {
                if (XRInputMethodService.instance?.commitRemoteText(cmd.value) != true) {
                    svc.injectText(cmd.value)
                }
            }
            "backspace"  -> {
                if (XRInputMethodService.instance?.remoteBackspace() != true) {
                    svc.injectBackspace()
                }
            }
            "enter"      -> {
                if (XRInputMethodService.instance?.remoteEnter() != true) {
                    svc.injectEnter()
                }
            }
            "paste"      -> if (cmd.value.isNotEmpty()) {
                if (XRInputMethodService.instance?.commitRemoteText(cmd.value) != true) {
                    svc.injectText(cmd.value)
                }
            }
            else         -> Log.w(TAG, "Unknown keyboard action: ${cmd.action}")
        }
    }
}
