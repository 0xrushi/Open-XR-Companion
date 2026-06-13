package com.inmoair.xrcompanion.core.command

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.inmoair.xrcompanion.shared.protocol.XRCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles "spacewalker" commands from the xr-companion client (phone).
 * Forwards them as local broadcasts to SpaceDesk on the same device (glasses).
 *
 * SpaceDesk registers its BroadcastReceiver with RECEIVER_EXPORTED so it can
 * receive from this process (different package, same device).
 */
@Singleton
class SpaceWalkerCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val TAG = "SpaceWalkerCmd"

    // Must match SpaceWalkerActions in SpaceDesk client.
    private object SW {
        const val PKG           = "com.rushi.spacedesk.client"
        const val ZOOM_IN       = "com.rushi.spacedesk.SPACEWALKER_ZOOM_IN"
        const val ZOOM_OUT      = "com.rushi.spacedesk.SPACEWALKER_ZOOM_OUT"
        const val SET_ROTATION  = "com.rushi.spacedesk.SPACEWALKER_ROTATE"
        const val ADD_SCREEN    = "com.rushi.spacedesk.SPACEWALKER_ADD_SCREEN"
        const val REMOVE_SCREEN = "com.rushi.spacedesk.SPACEWALKER_REMOVE_SCREEN"
        const val EXTRA_DEGREES = "degrees"
    }

    fun handle(cmd: XRCommand) {
        Log.d(TAG, "handle action=${cmd.action}")
        val action = when (cmd.action) {
            "zoom_in"       -> SW.ZOOM_IN
            "zoom_out"      -> SW.ZOOM_OUT
            "set_rotation"  -> SW.SET_ROTATION
            "add_screen"    -> SW.ADD_SCREEN
            "remove_screen" -> SW.REMOVE_SCREEN
            else -> {
                Log.w(TAG, "Unknown spacewalker action: ${cmd.action}")
                return
            }
        }

        val intent = Intent(action).apply {
            setPackage(SW.PKG)
            if (cmd.action == "set_rotation") {
                putExtra(SW.EXTRA_DEGREES, cmd.floatValue)
            }
        }

        Log.d(TAG, "Sending broadcast: $action")
        context.sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent")
    }
}
