package com.inmoair.xrcompanion.core.command

import android.util.Log
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import org.java_websocket.WebSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandDispatcher @Inject constructor(
    private val touchHandler: TouchCommandHandler,
    private val keyboardHandler: KeyboardCommandHandler,
    private val systemHandler: SystemCommandHandler,
    private val appControlHandler: AppControlHandler,
    private val spaceWalkerHandler: SpaceWalkerCommandHandler,
) {
    private val TAG = "CommandDispatcher"

    suspend fun dispatch(cmd: XRCommand, socket: WebSocket) {
        Log.v(TAG, "dispatch type=${cmd.type} action=${cmd.action}")
        when (cmd.type) {
            "touch"       -> touchHandler.handle(cmd)
            "scroll"      -> touchHandler.handleScroll(cmd)
            "keyboard"    -> keyboardHandler.handle(cmd)
            "system"      -> systemHandler.handle(cmd, socket)
            "apps"        -> appControlHandler.handle(cmd, socket)
            "screenshot"  -> systemHandler.handleScreenshot(cmd, socket)
            "file"        -> systemHandler.handleFile(cmd, socket)
            "spacewalker" -> spaceWalkerHandler.handle(cmd)
            else          -> Log.w(TAG, "Unknown command type: ${cmd.type}")
        }
    }
}
