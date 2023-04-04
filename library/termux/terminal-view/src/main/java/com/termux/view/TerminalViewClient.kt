package com.termux.view

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession

/**
 * The interface for communication between [TerminalView] and its client. It allows for getting
 * various  configuration options from the client and for sending back data to the client like logs,
 * key events, both hardware and IME (which makes it different from that available with
 * [android.view.View.setOnKeyListener], etc. It must be set for the
 * [TerminalView] through [TerminalView.setTerminalViewClient].
 */
interface TerminalViewClient {
    /**
     * Callback function on scale events according to [android.view.ScaleGestureDetector.getScaleFactor].
     */
    fun onScale(scale: Float): Float

    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    fun onSingleTapUp(e: MotionEvent?)
    val shouldBackButtonBeMappedToEscape: Boolean
    val shouldEnforceCharBasedInput: Boolean
    val shouldUseCtrlSpaceWorkaround: Boolean
    val isTerminalViewSelected: Boolean
    fun disableInput(): Boolean
    fun copyModeChanged(copyMode: Boolean)
    fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean
    fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean
    fun onLongPress(event: MotionEvent?): Boolean
    fun readControlKey(): Boolean
    fun readAltKey(): Boolean
    fun readShiftKey(): Boolean
    fun readFnKey(): Boolean
    fun onScroll(offset: Int)
    fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean
    fun onEmulatorSet()
    fun logError(tag: String?, message: String?)
    fun logWarn(tag: String?, message: String?)
    fun logInfo(tag: String?, message: String?)
    fun logDebug(tag: String?, message: String?)
    fun logVerbose(tag: String?, message: String?)
    fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?)
    fun logStackTrace(tag: String?, e: Exception?)
}