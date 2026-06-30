package com.inmoair.xrcompanion.core.ime

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class XRInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "XRInputMethod"

        @Volatile
        var instance: XRInputMethodService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "XR input method created")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
        Log.i(TAG, "XR input method destroyed")
    }

    override fun onCreateInputView(): View {
        return View(this).apply {
            minimumHeight = 0
            visibility = View.GONE
        }
    }

    fun commitRemoteText(text: String): Boolean {
        val connection = currentInputConnection ?: return false
        val handled = connection.commitText(text, 1)
        Log.v(TAG, "commitText len=${text.length} handled=$handled")
        return handled
    }

    fun remoteBackspace(): Boolean {
        val connection = currentInputConnection ?: return false
        val deleted = connection.deleteSurroundingText(1, 0)
        if (deleted) {
            Log.v(TAG, "deleteSurroundingText handled=true")
            return true
        }
        val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        Log.v(TAG, "backspace key handled=${down || up}")
        return down || up
    }

    fun remoteEnter(): Boolean {
        val connection = currentInputConnection ?: return false
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            val handled = connection.performEditorAction(action)
            if (handled) {
                Log.v(TAG, "editorAction=$action handled=true")
                return true
            }
        }
        val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        Log.v(TAG, "enter key handled=${down || up}")
        return down || up
    }
}
