package com.termux.terminal

import android.util.Base64
import com.termux.terminal.KeyHandler.getCodeFromTermcap
import com.termux.terminal.Logger.logError
import com.termux.terminal.Logger.logStackTraceWithMessage
import com.termux.terminal.Logger.logWarn
import com.termux.terminal.TextStyle.decodeEffect
import com.termux.terminal.TextStyle.encode
import com.termux.terminal.WcWidth.width
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 *
 *
 * References:
 *
 *  * http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 *  * http://en.wikipedia.org/wiki/ANSI_escape_code
 *  * http://man.he.net/man4/console_codes
 *  * http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c
 *  * http://www.columbia.edu/~kermit/k95manual/iso2022.html
 *  * http://www.vt100.net/docs/vt510-rm/chapter4
 *  * http://en.wikipedia.org/wiki/ISO/IEC_2022 - for 7-bit and 8-bit GL GR explanation
 *  * http://bjh21.me.uk/all-escapes/all-escapes.txt - extensive!
 *  * http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref.
 * html - document for konsole - accessible!
 *
 */
class TerminalEmulator(
    /** The terminal session this emulator is bound to.  */
    private val mSession: TerminalOutput, columns: Int, rows: Int, transcriptRows: Int, client: TerminalSessionClient?
) {
    private var mTitle: String? = null
    private val mTitleStack = Stack<String?>()

    /** If processing first character of first parameter of [.ESC_CSI].  */
    private var mIsCSIStart = false

    /** The last character processed of a parameter of [.ESC_CSI].  */
    private var mLastCSIArg: Int? = null

    /** The cursor position. Between (0,0) and (mRows-1, mColumns-1).  */
    private var mCursorRow = 0
    private var mCursorCol = 0

    /** The number of character rows and columns in the terminal screen.  */
    var mRows: Int
    var mColumns: Int
    /** Get the terminal cursor style. It will be one of [.TERMINAL_CURSOR_STYLES_LIST]  */
    /** The terminal cursor styles.  */
    var cursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE
        private set

    /** The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal.  */
    private val mMainBuffer: TerminalBuffer

    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate screen buffer is active, you cannot scroll back to view saved lines).
     *
     *
     * See http://www.xfree86.org/current/ctlseqs.html#The%20Alternate%20Screen%20Buffer
     */
    val mAltBuffer: TerminalBuffer

    /** The current screen buffer, pointing at either [.mMainBuffer] or [.mAltBuffer].  */
    var screen: TerminalBuffer
        private set
    var mClient: TerminalSessionClient?

    /** Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1.  */
    private var mArgIndex = 0

    /** Holds the arguments of the current escape sequence.  */
    private val mArgs = IntArray(MAX_ESCAPE_PARAMETERS)

    /** Holds OSC and device control arguments, which can be strings.  */
    private val mOSCOrDeviceControlArgs = StringBuilder()

    /**
     * True if the current escape sequence should continue, false if the current escape sequence should be terminated.
     * Used when parsing a single character.
     */
    private var mContinueSequence = false

    /** The current state of the escape sequence state machine. One of the ESC_* constants.  */
    private var mEscapeState = 0
    private val mSavedStateMain = SavedScreenState()
    private val mSavedStateAlt = SavedScreenState()

    /** http://www.vt100.net/docs/vt102-ug/table5-15.html  */
    private var mUseLineDrawingG0 = false
    private var mUseLineDrawingG1 = false
    private var mUseLineDrawingUsesG0 = true

    /**
     * @see TerminalEmulator.mapDecSetBitToInternalBit
     */
    private var mCurrentDecSetFlags = 0
    private var mSavedDecSetFlags = 0

    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private var mInsertMode = false

    /** An array of tab stops. mTabStop[i] is true if there is a tab stop set for column i.  */
    private var mTabStop: BooleanArray

    /**
     * Top margin of screen for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
     * (Defines the first row after the scrolling region). Left/right margin in [0, mColumns].
     */
    private var mTopMargin = 0
    private var mBottomMargin = 0
    private var mLeftMargin = 0
    private var mRightMargin = 0

    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (mColumns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private var mAboutToAutoWrap = false

    /**
     * If the cursor blinking is enabled. It requires cursor itself to be enabled, which is controlled
     * byt whether [.DECSET_BIT_CURSOR_ENABLED] bit is set or not.
     */
    private var mCursorBlinkingEnabled = false

    /**
     * If currently cursor should be in a visible state or not if [.mCursorBlinkingEnabled]
     * is `true`.
     */
    private var mCursorBlinkState = false

    /**
     * Current foreground and background colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     *
     * @see TextStyle
     */
    var mForeColor = 0
    var mBackColor = 0

    /** Current [TextStyle] effect.  */
    private var mEffect = 0

    /**
     * The number of scrolled lines since last calling [.clearScrollCounter]. Used for moving selection up along
     * with the scrolling text.
     */
    var scrollCounter = 0
        private set

    /** If automatic scrolling of terminal is disabled  */
    var isAutoScrollDisabled = false
        private set
    private var mUtf8ToFollow: Byte = 0
    private var mUtf8Index: Byte = 0
    private val mUtf8InputBuffer = ByteArray(4)
    private var mLastEmittedCodePoint = -1
    val mColors = TerminalColors()
    private fun isDecsetInternalBitSet(bit: Int): Boolean {
        return mCurrentDecSetFlags and bit != 0
    }

    private fun setDecsetinternalBit(internalBit: Int, set: Boolean) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false)
            } else if (internalBit == DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false)
            }
        }
        mCurrentDecSetFlags = if (set) {
            mCurrentDecSetFlags or internalBit
        } else {
            mCurrentDecSetFlags and internalBit.inv()
        }
    }

    init {
        mMainBuffer = TerminalBuffer(columns, getTerminalTranscriptRows(transcriptRows), rows)
        screen = mMainBuffer
        mAltBuffer = TerminalBuffer(columns, rows, rows)
        mClient = client
        mRows = rows
        mColumns = columns
        mTabStop = BooleanArray(mColumns)
        reset()
    }

    fun updateTerminalSessionClient(client: TerminalSessionClient?) {
        mClient = client
        setCursorStyle()
        setCursorBlinkState(true)
    }

    val isAlternateBufferActive: Boolean
        get() = screen == mAltBuffer

    private fun getTerminalTranscriptRows(transcriptRows: Int): Int {
        return if (transcriptRows < TERMINAL_TRANSCRIPT_ROWS_MIN || transcriptRows > TERMINAL_TRANSCRIPT_ROWS_MAX) DEFAULT_TERMINAL_TRANSCRIPT_ROWS else transcriptRows
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    fun sendMouseEvent(mouseButton: Int, column: Int, row: Int, pressed: Boolean) {

        var col = if (column < 1) 1 else column
        if (col > mColumns) col = mColumns

        var row1 = if (row < 1) 1 else row
        if (row1 > mRows) row1 = mRows
        if (mouseButton == MOUSE_LEFT_BUTTON_MOVED && !isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) {
            // Do not send tracking.
        } else if (isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
            mSession.write(String.format("\u001b[<%d;%d;%d" + if (pressed) 'M' else 'm', mouseButton, col, row1))
        } else {
            val button = if (pressed) mouseButton else 3 // 3 for release of all buttons.
            // Clip to screen, and clip to the limits of 8-bit data.
            val outOfBounds = col > 255 - 32 || row1 > 255 - 32
            if (!outOfBounds) {
                val data = byteArrayOf(
                    '\u001b'.code.toByte(),
                    '['.code.toByte(),
                    'M'.code.toByte(),
                    (32 + button).toByte(),
                    (32 + col).toByte(),
                    (32 + row1).toByte()
                )
                mSession.write(data, 0, data.size)
            }
        }
    }

    fun resize(columns: Int, rows: Int) {
        if (mRows == rows && mColumns == columns) {
            return
        } else require(columns >= 2 && rows >= 2) { "rows=$rows, columns=$columns" }
        if (mRows != rows) {
            mRows = rows
            mTopMargin = 0
            mBottomMargin = mRows
        }
        if (mColumns != columns) {
            val oldColumns = mColumns
            mColumns = columns
            val oldTabStop = mTabStop
            mTabStop = BooleanArray(mColumns)
            setDefaultTabStops()
            val toTransfer = oldColumns.coerceAtMost(columns)
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer)
            mLeftMargin = 0
            mRightMargin = mColumns
        }
        resizeScreen()
    }

    private fun resizeScreen() {
        val cursor = intArrayOf(mCursorCol, mCursorRow)
        val newTotalRows = if (screen == mAltBuffer) mRows else mMainBuffer.mTotalRows
        screen.resize(mColumns, mRows, newTotalRows, cursor, style, isAlternateBufferActive)
        mCursorCol = cursor[0]
        mCursorRow = cursor[1]
    }

    var cursorRow: Int
        get() = mCursorRow
        private set(row) {
            mCursorRow = row
            mAboutToAutoWrap = false
        }
    var cursorCol: Int
        get() = mCursorCol
        private set(col) {
            mCursorCol = col
            mAboutToAutoWrap = false
        }

    /** Set the terminal cursor style.  */
    fun setCursorStyle() {
        var cursorStyle: Int? = null
        if (mClient != null) cursorStyle = mClient!!.terminalCursorStyle
        if (cursorStyle == null || !listOf(*TERMINAL_CURSOR_STYLES_LIST).contains(cursorStyle)) this.cursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE else this.cursorStyle = cursorStyle
    }

    val isReverseVideo: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO)
    val isCursorEnabled: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED)

    fun shouldCursorBeVisible(): Boolean {
        return if (!isCursorEnabled) false else !mCursorBlinkingEnabled || mCursorBlinkState
    }

    fun setCursorBlinkingEnabled(cursorBlinkingEnabled: Boolean) {
        mCursorBlinkingEnabled = cursorBlinkingEnabled
    }

    fun setCursorBlinkState(cursorBlinkState: Boolean) {
        mCursorBlinkState = cursorBlinkState
    }

    val isKeypadApplicationMode: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)
    val isCursorKeysApplicationMode: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS)

    /** If mouse events are being sent as escape codes to the terminal.  */
    val isMouseTrackingActive: Boolean
        get() = isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(
            DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
        )

    private fun setDefaultTabStops() {
        for (i in 0 until mColumns) mTabStop[i] = i and 7 == 0 && i != 0
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    fun append(buffer: ByteArray, length: Int) {
        for (i in 0 until length) processByte(buffer[i])
    }

    private fun processByte(byteToProcess: Byte) {
        if (mUtf8ToFollow > 0) {
            if (byteToProcess.toInt() and 192 == 128) {
                // 10xxxxxx, a continuation byte.
                mUtf8InputBuffer[mUtf8Index++.toInt()] = byteToProcess
                --mUtf8ToFollow
                if (mUtf8ToFollow.toInt() == 0) {
                    val firstByteMask = (if (mUtf8Index.toInt() == 2) 31 else if (mUtf8Index.toInt() == 3) 15 else 7).toByte()
                    var codePoint = mUtf8InputBuffer[0].toInt() and firstByteMask.toInt()
                    for (i in 1 until mUtf8Index) codePoint = codePoint shl 6 or (mUtf8InputBuffer[i].toInt() and 63)
                    if ((codePoint <= 127 && mUtf8Index > 1 || codePoint < 2047 && mUtf8Index > 2 || codePoint < 65535) && mUtf8Index > 3) {
                        // Overlong encoding.
                        codePoint = UNICODE_REPLACEMENT_CHAR
                    }
                    mUtf8ToFollow = 0
                    mUtf8Index = 0
                    if (codePoint in 0x80..0x9F) {
                        // Sequence decoded to a C1 control character which we ignore. They are
                        // not used nowadays and increases the risk of messing up the terminal state
                        // on binary input. XTerm does not allow them in utf-8:
                        // "It is not possible to use a C1 control obtained from decoding the
                        // UTF-8 text" - http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
                    } else {
                        when (Character.getType(codePoint).toByte()) {
                            Character.UNASSIGNED, Character.SURROGATE -> codePoint = UNICODE_REPLACEMENT_CHAR
                        }
                        processCodePoint(codePoint)
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                mUtf8ToFollow = 0
                mUtf8Index = 0
                emitCodePoint(UNICODE_REPLACEMENT_CHAR)
                // The Unicode Standard Version 6.2 – Core Specification
                // (http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf):
                // "If the converter encounters an ill-formed UTF-8 code unit sequence which starts with a valid first
                // byte, but which does not continue with valid successor bytes (see Table 3-7), it must not consume the
                // successor bytes as part of the ill-formed subsequence
                // whenever those successor bytes themselves constitute part of a well-formed UTF-8 code unit
                // subsequence."
                processByte(byteToProcess)
            }
        } else {
            mUtf8ToFollow = if (byteToProcess.toInt() and 128 == 0) { // The leading bit is not set so it is a 7-bit ASCII character.
                processCodePoint(byteToProcess.toInt())
                return
            } else if (byteToProcess.toInt() and 224 == 192) { // 110xxxxx, a two-byte sequence.
                1
            } else if (byteToProcess.toInt() and 240 == 224) { // 1110xxxx, a three-byte sequence.
                2
            } else if (byteToProcess.toInt() and 248 == 240) { // 11110xxx, a four-byte sequence.
                3
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                processCodePoint(UNICODE_REPLACEMENT_CHAR)
                return
            }
            mUtf8InputBuffer[mUtf8Index++.toInt()] = byteToProcess
        }
    }

    fun processCodePoint(b: Int) {
        when (b) {
            0 -> {}
            7 -> if (mEscapeState == ESC_OSC) doOsc(b) else mSession.onBell()
            8 -> if (mLeftMargin == mCursorCol) {
                // Jump to previous line if it was auto-wrapped.
                val previousRow = mCursorRow - 1
                if (previousRow >= 0 && screen.getLineWrap(previousRow)) {
                    screen.clearLineWrap(previousRow)
                    setCursorRowCol(previousRow, mRightMargin - 1)
                }
            } else {
                cursorCol = mCursorCol - 1
            }
            9 ->                 // XXX: Should perhaps use color if writing to new cells. Try with
                //       printf "\033[41m\tXX\033[0m\n"
                // The OSX Terminal.app colors the spaces from the tab red, but xterm does not.
                // Note that Terminal.app only colors on new cells, in e.g.
                //       printf "\033[41m\t\r\033[42m\tXX\033[0m\n"
                // the first cells are created with a red background, but when tabbing over
                // them again with a green background they are not overwritten.
                mCursorCol = nextTabStop(1)
            10, 11, 12 -> doLinefeed()
            13 -> cursorCol = mLeftMargin
            14 -> mUseLineDrawingUsesG0 = false
            15 -> mUseLineDrawingUsesG0 = true
            24, 26 -> if (mEscapeState != ESC_NONE) {
                // FIXME: What is this??
                mEscapeState = ESC_NONE
                emitCodePoint(127)
            }
            27 ->                 // Starts an escape sequence unless we're parsing a string
                if (mEscapeState == ESC_P) {
                    // XXX: Ignore escape when reading device control sequence, since it may be part of string terminator.
                    return
                } else if (mEscapeState != ESC_OSC) {
                    startEscapeSequence()
                } else {
                    doOsc(b)
                }
            else -> {
                mContinueSequence = false
                when (mEscapeState) {
                    ESC_NONE -> if (b >= 32) emitCodePoint(b)
                    ESC -> doEsc(b)
                    ESC_POUND -> doEscPound(b)
                    ESC_SELECT_LEFT_PAREN -> mUseLineDrawingG0 = b == '0'.code
                    ESC_SELECT_RIGHT_PAREN -> mUseLineDrawingG1 = b == '0'.code
                    ESC_CSI -> doCsi(b)
                    ESC_CSI_EXCLAMATION -> if (b == 'p'.code) { // Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                        reset()
                    } else {
                        unknownSequence(b)
                    }
                    ESC_CSI_QUESTIONMARK -> doCsiQuestionMark(b)
                    ESC_CSI_BIGGERTHAN -> doCsiBiggerThan(b)
                    ESC_CSI_DOLLAR -> {
                        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
                        val effectiveTopMargin = if (originMode) mTopMargin else 0
                        val effectiveBottomMargin = if (originMode) mBottomMargin else mRows
                        val effectiveLeftMargin = if (originMode) mLeftMargin else 0
                        val effectiveRightMargin = if (originMode) mRightMargin else mColumns
                        when (b) {
                            'v'.code -> {
                                // Copy rectangular area (DECCRA - http://vt100.net/docs/vt510-rm/DECCRA):
                                // "If Pbs is greater than Pts, or Pls is greater than Prs, the terminal ignores DECCRA.
                                // The coordinates of the rectangular area are affected by the setting of origin mode (DECOM).
                                // DECCRA is not affected by the page margins.
                                // The copied text takes on the line attributes of the destination area.
                                // If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, then the value
                                // is treated as the width or height of that page.
                                // If the destination area is partially off the page, then DECCRA clips the off-page data.
                                // DECCRA does not change the active cursor position."
                                val topSource = (getArg(0, 1, true) - 1 + effectiveTopMargin).coerceAtMost(mRows)
                                val leftSource = (getArg(1, 1, true) - 1 + effectiveLeftMargin).coerceAtMost(mColumns)
                                // Inclusive, so do not subtract one:
                                val bottomSource = (getArg(
                                    2, mRows, true
                                ) + effectiveTopMargin).coerceAtLeast(topSource).coerceAtMost(mRows)
                                val rightSource = (getArg(3, mColumns, true) + effectiveLeftMargin).coerceAtLeast(
                                    leftSource
                                ).coerceAtMost(mColumns)
                                // int sourcePage = getArg(4, 1, true);
                                val destinationTop = (getArg(5, 1, true) - 1 + effectiveTopMargin).coerceAtMost(mRows)
                                val destinationLeft = (getArg(6, 1, true) - 1 + effectiveLeftMargin).coerceAtMost(
                                    mColumns
                                )
                                // int destinationPage = getArg(7, 1, true);
                                val heightToCopy = (mRows - destinationTop).coerceAtMost(bottomSource - topSource)
                                val widthToCopy = (mColumns - destinationLeft).coerceAtMost(rightSource - leftSource)
                                screen.blockCopy(
                                    leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destinationTop
                                )
                            }
                            '{'.code, 'x'.code, 'z'.code -> {
                                // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                val erase = b != 'x'.code
                                val selective = b == '{'.code
                                // Only DECSERA keeps visual attributes, DECERA does not:
                                val keepVisualAttributes = erase && selective
                                var argIndex = 0
                                val fillChar: Int = if (erase) ' '.code else getArg(argIndex++, -1, true)
                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if (fillChar in 32..126 || fillChar in 160..255) {
                                    // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    val top = (getArg(argIndex++, 1, true) + effectiveTopMargin).coerceAtMost(
                                        effectiveBottomMargin + 1
                                    )
                                    val left = (getArg(argIndex++, 1, true) + effectiveLeftMargin).coerceAtMost(
                                        effectiveRightMargin + 1
                                    )
                                    val bottom = (getArg(argIndex++, mRows, true) + effectiveTopMargin).coerceAtMost(
                                        effectiveBottomMargin
                                    )
                                    val right = (getArg(argIndex, mColumns, true) + effectiveLeftMargin).coerceAtMost(
                                        effectiveRightMargin
                                    )
                                    val style = style
                                    var row = top - 1
                                    while (row < bottom) {
                                        var col = left - 1
                                        while (col < right) {
                                            if (!selective || decodeEffect(
                                                    screen.getStyleAt(row, col)
                                                ) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED == 0
                                            ) screen.setChar(
                                                col,
                                                row,
                                                fillChar,
                                                if (keepVisualAttributes) screen.getStyleAt(row, col) else style
                                            )
                                            col++
                                        }
                                        row++
                                    }
                                }
                            }
                            'r'.code, 't'.code -> {
                                // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                val reverse = b == 't'.code
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                val top = (getArg(
                                    0,
                                    1,
                                    true
                                ) - 1).coerceAtMost(effectiveBottomMargin) + effectiveTopMargin
                                val left = (getArg(
                                    1,
                                    1,
                                    true
                                ) - 1).coerceAtMost(effectiveRightMargin) + effectiveLeftMargin
                                val bottom = (getArg(
                                    2,
                                    mRows,
                                    true
                                ) + 1).coerceAtMost(effectiveBottomMargin - 1) + effectiveTopMargin
                                val right = (getArg(
                                    3,
                                    mColumns,
                                    true
                                ) + 1).coerceAtMost(effectiveRightMargin - 1) + effectiveLeftMargin
                                if (mArgIndex >= 4) {
                                    if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                                    var i = 4
                                    while (i <= mArgIndex) {
                                        var bits = 0
                                        var setOrClear = true // True if setting, false if clearing.
                                        when (getArg(i, 0, false)) {
                                            0 -> {
                                                bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE or TextStyle.CHARACTER_ATTRIBUTE_BLINK or TextStyle.CHARACTER_ATTRIBUTE_INVERSE)
                                                if (!reverse) setOrClear = false
                                            }
                                            1 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD
                                            4 -> bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                                            5 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK
                                            7 -> bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                                            22 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD
                                                setOrClear = false
                                            }
                                            24 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                                                setOrClear = false
                                            }
                                            25 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK
                                                setOrClear = false
                                            }
                                            27 -> {
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                                                setOrClear = false
                                            }
                                        }
                                        if (reverse && !setOrClear) {
                                            // Reverse attributes in rectangular area ignores non-(1,4,5,7) bits.
                                        } else {
                                            screen.setOrClearEffect(
                                                bits, setOrClear, reverse, isDecsetInternalBitSet(
                                                    DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE
                                                ), effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right
                                            )
                                        }
                                        i++
                                    }
                                } else {
                                    // Do nothing.
                                }
                            }
                            else -> unknownSequence(b)
                        }
                    }
                    ESC_CSI_DOUBLE_QUOTE -> if (b == 'q'.code) {
                        // http://www.vt100.net/docs/vt510-rm/DECSCA
                        when (getArg0(0)) {
                            0, 2 -> {
                                // DECSED and DECSEL can erase characters.
                                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED.inv()
                            }
                            1 -> {
                                // DECSED and DECSEL cannot erase characters.
                                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_PROTECTED
                            }
                            else -> {
                                unknownSequence(b)
                            }
                        }
                    } else {
                        unknownSequence(b)
                    }
                    ESC_CSI_SINGLE_QUOTE -> when (b) {
                        '}'.code -> { // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                            val columnsAfterCursor = mRightMargin - mCursorCol
                            val columnsToInsert = getArg0(1).coerceAtMost(columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToInsert
                            screen.blockCopy(mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0)
                            blockClear(mCursorCol, 0, columnsToInsert, mRows)
                        }
                        '~'.code -> { // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                            val columnsAfterCursor = mRightMargin - mCursorCol
                            val columnsToDelete = getArg0(1).coerceAtMost(columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToDelete
                            screen.blockCopy(mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0)
                        }
                        else -> {
                            unknownSequence(b)
                        }
                    }
                    ESC_PERCENT -> {}
                    ESC_OSC -> doOsc(b)
                    ESC_OSC_ESC -> doOscEsc(b)
                    ESC_P -> doDeviceControl(b)
                    ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> if (b == 'p'.code) {
                        // Request DEC private mode (DECRQM).
                        val mode = getArg0(0)
                        val value = if (mode == 47 || mode == 1047 || mode == 1049) {
                            // This state is carried by mScreen pointer.
                            if (screen == mAltBuffer) 1 else 2
                        } else {
                            val internalBit = mapDecSetBitToInternalBit(mode)
                            if (internalBit != -1) {
                                if (isDecsetInternalBitSet(internalBit)) 1 else 2 // 1=set, 2=reset.
                            } else {
                                logError(mClient, LOG_TAG, "Got DECRQM for unrecognized private DEC mode=$mode")
                                0 // 0=not recognized, 3=permanently set, 4=permanently reset
                            }
                        }
                        mSession.write(String.format(Locale.US, "\u001b[?%d;%d\$y", mode, value))
                    } else {
                        unknownSequence(b)
                    }
                    ESC_CSI_ARGS_SPACE -> {
                        val arg = getArg0(0)
                        when (b) {
                            'q'.code -> when (arg) {
                                0, 1, 2 -> cursorStyle = TERMINAL_CURSOR_STYLE_BLOCK
                                3, 4 -> cursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE
                                5, 6 -> cursorStyle = TERMINAL_CURSOR_STYLE_BAR
                            }
                            't'.code, 'u'.code -> {}
                            else -> unknownSequence(b)
                        }
                    }
                    ESC_CSI_ARGS_ASTERIX -> {
                        val attributeChangeExtent = getArg0(0)
                        if (b == 'x'.code && attributeChangeExtent >= 0 && attributeChangeExtent <= 2) {
                            // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            setDecsetinternalBit(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, attributeChangeExtent == 2)
                        } else {
                            unknownSequence(b)
                        }
                    }
                    else -> unknownSequence(b)
                }
                if (!mContinueSequence) mEscapeState = ESC_NONE
            }
        }
    }

    /** When in [.ESC_P] ("device control") sequence.  */
    private fun doDeviceControl(b: Int) {
        when (b) {
            '\\'.code -> {
                val dcs = mOSCOrDeviceControlArgs.toString()
                // DCS $ q P t ST. Request Status String (DECRQSS)
                if (dcs.startsWith("\$q")) {
                    if (dcs == "\$q\"p") {
                        // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                        val csiString = "64;1\"p"
                        mSession.write("\u001bP1\$r$csiString\u001b\\")
                    } else {
                        finishSequenceAndLogError("Unrecognized DECRQSS string: '$dcs'")
                    }
                } else if (dcs.startsWith("+q")) {
                    // Request Termcap/Terminfo String. The string following the "q" is a list of names encoded in
                    // hexadecimal (2 digits per character) separated by ; which correspond to termcap or terminfo key
                    // names.
                    // Two special features are also recognized, which are not key names: Co for termcap colors (or colors
                    // for terminfo colors), and TN for termcap name (or name for terminfo name).
                    // xterm responds with DCS 1 + r P t ST for valid requests, adding to P t an = , and the value of the
                    // corresponding string that xterm would send, or DCS 0 + r P t ST for invalid requests. The strings are
                    // encoded in hexadecimal (2 digits per character).
                    // Example:
                    // :kr=\EOC: ks=\E[?1h\E=: ku=\EOA: le=^H:mb=\E[5m:md=\E[1m:\
                    // where
                    // kd=down-arrow key
                    // kl=left-arrow key
                    // kr=right-arrow key
                    // ku=up-arrow key
                    // #2=key_shome, "shifted home"
                    // #4=key_sleft, "shift arrow left"
                    // %i=key_sright, "shift arrow right"
                    // *7=key_send, "shifted end"
                    // k1=F1 function key

                    // Example: Request for ku is "ESC P + q 6 b 7 5 ESC \", where 6b7d=ku in hexadecimal.
                    // Xterm response in normal cursor mode:
                    // "<27> P 1 + r 6 b 7 5 = 1 B 5 B 4 1" where 0x1B 0x5B 0x41 = 27 91 65 = ESC [ A
                    // Xterm response in application cursor mode:
                    // "<27> P 1 + r 6 b 7 5 = 1 B 5 B 4 1" where 0x1B 0x4F 0x41 = 27 91 65 = ESC 0 A

                    // #4 is "shift arrow left":
                    // *** Device Control (DCS) for '#4'- 'ESC P + q 23 34 ESC \'
                    // Response: <27> P 1 + r 2 3 3 4 = 1 B 5 B 3 1 3 B 3 2 4 4 <27> \
                    // where 0x1B 0x5B 0x31 0x3B 0x32 0x44 = ESC [ 1 ; 2 D
                    // which we find in: TermKeyListener.java: KEY_MAP.put(KEYMOD_SHIFT | KEYCODE_DPAD_LEFT, "\033[1;2D");

                    // See http://h30097.www3.hp.com/docs/base_doc/DOCUMENTATION/V40G_HTML/MAN/MAN4/0178____.HTM for what to
                    // respond, as well as http://www.freebsd.org/cgi/man.cgi?query=termcap&sektion=5#CAPABILITIES for
                    // the meaning of e.g. "ku", "kd", "kr", "kl"
                    for (part in dcs.substring(2).split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        if (part.length % 2 == 0) {
                            val transBuffer = StringBuilder()
                            var c: Char
                            var i = 0
                            while (i < part.length) {
                                try {
                                    c = Char(
                                        java.lang.Long.decode("0x" + part[i] + "" + part[i + 1]).toLong().toUShort()
                                    )
                                } catch (e: NumberFormatException) {
                                    logStackTraceWithMessage(
                                        mClient, LOG_TAG, "Invalid device termcap/terminfo encoded name \"$part\"", e
                                    )
                                    i += 2
                                    continue
                                }
                                transBuffer.append(c)
                                i += 2
                            }
                            val trans = transBuffer.toString()
                            val responseValue: String? = when (trans) {
                                "Co", "colors" -> "256" // Number of colors.
                                "TN", "name" -> "xterm"
                                else -> getCodeFromTermcap(
                                    trans,
                                    isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS),
                                    isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)
                                )
                            }
                            if (responseValue == null) {
                                when (trans) {
                                    "%1", "&8" -> {}
                                    else -> logWarn(mClient, LOG_TAG, "Unhandled termcap/terminfo name: '$trans'")
                                }
                                // Respond with invalid request:
                                mSession.write("\u001bP0+r$part\u001b\\")
                            } else {
                                val hexEncoded = StringBuilder()
                                var j = 0
                                while (j < responseValue.length) {
                                    hexEncoded.append(String.format("%02X", responseValue[j].code))
                                    j++
                                }
                                mSession.write("\u001bP1+r$part=$hexEncoded\u001b\\")
                            }
                        } else {
                            logError(mClient, LOG_TAG, "Invalid device termcap/terminfo name of odd length: $part")
                        }
                    }
                } else {
                    if (LOG_ESCAPE_SEQUENCES) logError(mClient, LOG_TAG, "Unrecognized device control string: $dcs")
                }
                finishSequence()
            }
            else -> if (mOSCOrDeviceControlArgs.length > MAX_OSC_STRING_LENGTH) {
                // Too long.
                mOSCOrDeviceControlArgs.setLength(0)
                finishSequence()
            } else {
                mOSCOrDeviceControlArgs.appendCodePoint(b)
                continueSequence(mEscapeState)
            }
        }
    }

    private fun nextTabStop(numTabs: Int): Int {
        var tabs = numTabs
        for (i in mCursorCol + 1 until mColumns) if (mTabStop[i] && --tabs == 0) return i.coerceAtMost(mRightMargin)
        return mRightMargin - 1
    }

    /** Process byte while in the [.ESC_CSI_QUESTIONMARK] escape state.  */
    private fun doCsiQuestionMark(b: Int) {
        when (b) {
            'J'.code, 'K'.code -> {
                mAboutToAutoWrap = false
                val fillChar = ' '.code
                var startCol = -1
                var startRow = -1
                var endCol = -1
                var endRow = -1
                val justRow = b == 'K'.code
                when (getArg0(0)) {
                    0 -> {
                        startCol = mCursorCol
                        startRow = mCursorRow
                        endCol = mColumns
                        endRow = if (justRow) mCursorRow + 1 else mRows
                    }
                    1 -> {
                        startCol = 0
                        startRow = if (justRow) mCursorRow else 0
                        endCol = mCursorCol + 1
                        endRow = mCursorRow + 1
                    }
                    2 -> {
                        startCol = 0
                        startRow = if (justRow) mCursorRow else 0
                        endCol = mColumns
                        endRow = if (justRow) mCursorRow + 1 else mRows
                    }
                    else -> unknownSequence(b)
                }
                val style = style
                var row = startRow
                while (row < endRow) {
                    var col = startCol
                    while (col < endCol) {
                        if (decodeEffect(
                                screen.getStyleAt(
                                    row, col
                                )
                            ) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED == 0
                        ) screen.setChar(col, row, fillChar, style)
                        col++
                    }
                    row++
                }
            }
            'h'.code, 'l'.code -> {
                if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                var i = 0
                while (i <= mArgIndex) {
                    doDecSetOrReset(b == 'h'.code, mArgs[i])
                    i++
                }
            }
            'n'.code -> when (getArg0(-1)) {
                6 ->                         // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                    mSession.write(String.format(Locale.US, "\u001b[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1))
                else -> {
                    finishSequence()
                    return
                }
            }
            'r'.code, 's'.code -> {
                if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
                var i = 0
                while (i <= mArgIndex) {
                    val externalBit = mArgs[i]
                    val internalBit = mapDecSetBitToInternalBit(externalBit)
                    if (internalBit == -1) {
                        logWarn(mClient, LOG_TAG, "Ignoring request to save/recall decset bit=$externalBit")
                    } else {
                        if (b == 's'.code) {
                            mSavedDecSetFlags = mSavedDecSetFlags or internalBit
                        } else {
                            doDecSetOrReset(mSavedDecSetFlags and internalBit != 0, externalBit)
                        }
                    }
                    i++
                }
            }
            '$'.code -> {
                continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR)
                return
            }
            else -> parseArg(b)
        }
    }

    fun doDecSetOrReset(setting: Boolean, externalBit: Int) {
        val internalBit = mapDecSetBitToInternalBit(externalBit)
        if (internalBit != -1) {
            setDecsetinternalBit(internalBit, setting)
        }
        when (externalBit) {
            1 -> {}
            3 -> {
                run {
                    mTopMargin = 0
                    mLeftMargin = 0
                }
                mBottomMargin = mRows
                mRightMargin = mColumns
                // "DECCOLM resets vertical split screen mode (DECLRMM) to unavailable":
                setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false)
                // "Erases all data in page memory":
                blockClear(0, 0, mColumns, mRows)
                setCursorRowCol(0, 0)
            }
            4 -> {}
            5 -> {}
            6 -> if (setting) setCursorPosition(0, 0)
            7, 8, 9, 12, 25 -> if (mClient != null) mClient!!.onTerminalCursorStateChange(setting)
            40, 45, 66 -> {}
            69 -> if (!setting) {
                mLeftMargin = 0
                mRightMargin = mColumns
            }
            1000, 1001, 1002, 1003, 1004, 1005, 1006, 1015, 1034 -> {}
            1048 -> if (setting) saveCursor() else restoreCursor()
            47, 1047, 1049 -> {

                // Set: Save cursor as in DECSC and use Alternate Screen Buffer, clearing it first.
                // Reset: Use Normal Screen Buffer and restore cursor as in DECRC.
                val newScreen = if (setting) mAltBuffer else mMainBuffer
                if (newScreen != screen) {
                    val resized = !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows)
                    if (setting) saveCursor()
                    screen = newScreen
                    if (!setting) {
                        val col = mSavedStateMain.mSavedCursorCol
                        val row = mSavedStateMain.mSavedCursorRow
                        restoreCursor()
                        if (resized) {
                            // Restore cursor position _not_ clipped to current screen (let resizeScreen() handle that):
                            mCursorCol = col
                            mCursorRow = row
                        }
                    }
                    // Check if buffer size needs to be updated:
                    if (resized) resizeScreen()
                    // Clear new screen if alt buffer:
                    if (newScreen == mAltBuffer) newScreen.blockSet(0, 0, mColumns, mRows, ' '.code, style)
                }
            }
            2004 -> {}
            else -> unknownParameter(externalBit)
        }
    }

    private fun doCsiBiggerThan(b: Int) {
        when (b) {
            'c'.code ->                 // Originally this was used for the terminal to respond with "identification code, firmware version level,
                // and hardware options" (http://vt100.net/docs/vt510-rm/DA2), with the first "41" meaning the VT420
                // terminal type. This is not used anymore, but the second version level field has been changed by xterm
                // to mean it's release number ("patch numbers" listed at http://invisible-island.net/xterm/xterm.log.html),
                // and some applications use it as a feature check:
                // * tmux used to have a "xterm won't reach version 500 for a while so set that as the upper limit" check,
                // and then check "xterm_version > 270" if rectangular area operations such as DECCRA could be used.
                // * vim checks xterm version number >140 for "Request termcap/terminfo string" functionality >276 for SGR
                // mouse report.
                // The third number is a keyboard identifier not used nowadays.
                mSession.write("\u001b[>41;320;0c")
            'm'.code ->                 // https://bugs.launchpad.net/gnome-terminal/+bug/96676/comments/25
                // Depending on the first number parameter, this can set one of the xterm resources
                // modifyKeyboard, modifyCursorKeys, modifyFunctionKeys and modifyOtherKeys.
                // http://invisible-island.net/xterm/manpage/xterm.html#RESOURCES

                // * modifyKeyboard (parameter=1):
                // Normally xterm makes a special case regarding modifiers (shift, control, etc.) to handle special keyboard
                // layouts (legacy and vt220). This is done to provide compatible keyboards for DEC VT220 and related
                // terminals that implement user-defined keys (UDK).
                // The bits of the resource value selectively enable modification of the given category when these keyboards
                // are selected. The default is "0":
                // (0) The legacy/vt220 keyboards interpret only the Control-modifier when constructing numbered
                // function-keys. Other special keys are not modified.
                // (1) allows modification of the numeric keypad
                // (2) allows modification of the editing keypad
                // (4) allows modification of function-keys, overrides use of Shift-modifier for UDK.
                // (8) allows modification of other special keys

                // * modifyCursorKeys (parameter=2):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a cursor-key. The default is "2".
                // - Set it to -1 to disable it.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.

                // * modifyFunctionKeys (parameter=3):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a (numbered) function-
                // key. The default is "2". The resource values are similar to modifyCursorKeys:
                // Set it to -1 to permit the user to use shift- and control-modifiers to construct function-key strings
                // using the normal encoding scheme.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.
                // If modifyFunctionKeys is zero, xterm uses Control- and Shift-modifiers to allow the user to construct
                // numbered function-keys beyond the set provided by the keyboard:
                // (Control) adds the value given by the ctrlFKeys resource.
                // (Shift) adds twice the value given by the ctrlFKeys resource.
                // (Control/Shift) adds three times the value given by the ctrlFKeys resource.
                //
                // As a special case, legacy (when oldFunctionKeys is true) or vt220 (when sunKeyboard is true)
                // keyboards interpret only the Control-modifier when constructing numbered function-keys.
                // This is done to provide compatible keyboards for DEC VT220 and related terminals that
                // implement user-defined keys (UDK).

                // * modifyOtherKeys (parameter=4):
                // Like modifyCursorKeys, tells xterm to construct an escape sequence for other keys (such as "2") when
                // modified by Control-, Alt- or Meta-modifiers. This feature does not apply to function keys and
                // well-defined keys such as ESC or the control keys. The default is "0".
                // (0) disables this feature.
                // (1) enables this feature for keys except for those with well-known behavior, e.g., Tab, Backarrow and
                // some special control character cases, e.g., Control-Space to make a NUL.
                // (2) enables this feature for keys including the exceptions listed.
                logError(mClient, LOG_TAG, "(ignored) CSI > MODIFY RESOURCE: " + getArg0(-1) + " to " + getArg1(-1))
            else -> parseArg(b)
        }
    }

    private fun startEscapeSequence() {
        mEscapeState = ESC
        mArgIndex = 0
        Arrays.fill(mArgs, -1)
    }

    private fun doLinefeed() {
        val belowScrollingRegion = mCursorRow >= mBottomMargin
        var newCursorRow = mCursorRow + 1
        if (belowScrollingRegion) {
            // Move down (but not scroll) as long as we are above the last row.
            if (mCursorRow != mRows - 1) {
                cursorRow = newCursorRow
            }
        } else {
            if (newCursorRow == mBottomMargin) {
                scrollDownOneLine()
                newCursorRow = mBottomMargin - 1
            }
            cursorRow = newCursorRow
        }
    }

    private fun continueSequence(state: Int) {
        mEscapeState = state
        mContinueSequence = true
    }

    private fun doEscPound(b: Int) {
        when (b) {
            '8'.code -> screen.blockSet(0, 0, mColumns, mRows, 'E'.code, style)
            else -> unknownSequence(b)
        }
    }

    /** Encountering a character in the [.ESC] state.  */
    private fun doEsc(b: Int) {
        when (b) {
            '#'.code -> continueSequence(ESC_POUND)
            '('.code -> continueSequence(ESC_SELECT_LEFT_PAREN)
            ')'.code -> continueSequence(ESC_SELECT_RIGHT_PAREN)
            '6'.code -> if (mCursorCol > mLeftMargin) {
                mCursorCol--
            } else {
                val rows = mBottomMargin - mTopMargin
                screen.blockCopy(
                    mLeftMargin, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin + 1, mTopMargin
                )
                screen.blockSet(mLeftMargin, mTopMargin, 1, rows, ' '.code, encode(mForeColor, mBackColor, 0))
            }
            '7'.code -> saveCursor()
            '8'.code -> restoreCursor()
            '9'.code -> if (mCursorCol < mRightMargin - 1) {
                mCursorCol++
            } else {
                val rows = mBottomMargin - mTopMargin
                screen.blockCopy(
                    mLeftMargin + 1, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin, mTopMargin
                )
                screen.blockSet(mRightMargin - 1, mTopMargin, 1, rows, ' '.code, encode(mForeColor, mBackColor, 0))
            }
            'c'.code -> {
                reset()
                mMainBuffer.clearTranscript()
                blockClear(0, 0, mColumns, mRows)
                setCursorPosition(0, 0)
            }
            'D'.code -> doLinefeed()
            'E'.code -> {
                cursorCol = if (isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)) mLeftMargin else 0
                doLinefeed()
            }
            'F'.code -> setCursorRowCol(0, mBottomMargin - 1)
            'H'.code -> mTabStop[mCursorCol] = true
            'M'.code ->                 // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (mCursorRow <= mTopMargin) {
                    screen.blockCopy(0, mTopMargin, mColumns, mBottomMargin - (mTopMargin + 1), 0, mTopMargin + 1)
                    blockClear(0, mTopMargin, mColumns)
                } else {
                    mCursorRow--
                }
            'N'.code, '0'.code -> {}
            'P'.code -> {
                mOSCOrDeviceControlArgs.setLength(0)
                continueSequence(ESC_P)
            }
            '['.code -> {
                continueSequence(ESC_CSI)
                mIsCSIStart = true
                mLastCSIArg = null
            }
            '='.code -> setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true)
            ']'.code -> {
                mOSCOrDeviceControlArgs.setLength(0)
                continueSequence(ESC_OSC)
            }
            '>'.code -> setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false)
            else -> unknownSequence(b)
        }
    }

    /** DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC . See [.restoreCursor].  */
    private fun saveCursor() {
        val state = if (screen == mMainBuffer) mSavedStateMain else mSavedStateAlt
        state.mSavedCursorRow = mCursorRow
        state.mSavedCursorCol = mCursorCol
        state.mSavedEffect = mEffect
        state.mSavedForeColor = mForeColor
        state.mSavedBackColor = mBackColor
        state.mSavedDecFlags = mCurrentDecSetFlags
        state.mUseLineDrawingG0 = mUseLineDrawingG0
        state.mUseLineDrawingG1 = mUseLineDrawingG1
        state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0
    }

    /** DECRS restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC. See [.saveCursor].  */
    private fun restoreCursor() {
        val state = if (screen == mMainBuffer) mSavedStateMain else mSavedStateAlt
        setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol)
        mEffect = state.mSavedEffect
        mForeColor = state.mSavedForeColor
        mBackColor = state.mSavedBackColor
        val mask = DECSET_BIT_AUTOWRAP or DECSET_BIT_ORIGIN_MODE
        mCurrentDecSetFlags = mCurrentDecSetFlags and mask.inv() or (state.mSavedDecFlags and mask)
        mUseLineDrawingG0 = state.mUseLineDrawingG0
        mUseLineDrawingG1 = state.mUseLineDrawingG1
        mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0
    }

    /** Following a CSI - Control Sequence Introducer, "\033[". [.ESC_CSI].  */
    private fun doCsi(b: Int) {
        when (b) {
            '!'.code -> continueSequence(ESC_CSI_EXCLAMATION)
            '"'.code -> continueSequence(ESC_CSI_DOUBLE_QUOTE)
            '\''.code -> continueSequence(ESC_CSI_SINGLE_QUOTE)
            '$'.code -> continueSequence(ESC_CSI_DOLLAR)
            '*'.code -> continueSequence(ESC_CSI_ARGS_ASTERIX)
            '@'.code -> {

                // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                mAboutToAutoWrap = false
                val columnsAfterCursor = mColumns - mCursorCol
                val spacesToInsert = getArg0(1).coerceAtMost(columnsAfterCursor)
                val charsToMove = columnsAfterCursor - spacesToInsert
                screen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow)
                blockClear(mCursorCol, mCursorRow, spacesToInsert)
            }
            'A'.code -> cursorRow = 0.coerceAtLeast(mCursorRow - getArg0(1))
            'B'.code -> cursorRow = (mRows - 1).coerceAtMost(mCursorRow + getArg0(1))
            'C'.code, 'a'.code -> cursorCol = (mRightMargin - 1).coerceAtMost(mCursorCol + getArg0(1))
            'D'.code -> cursorCol = mLeftMargin.coerceAtLeast(mCursorCol - getArg0(1))
            'E'.code -> setCursorPosition(0, mCursorRow + getArg0(1))
            'F'.code -> setCursorPosition(0, mCursorRow - getArg0(1))
            'G'.code -> cursorCol = 1.coerceAtLeast(getArg0(1)).coerceAtMost(mColumns) - 1
            'H'.code, 'f'.code -> setCursorPosition(getArg1(1) - 1, getArg0(1) - 1)
            'I'.code -> cursorCol = nextTabStop(getArg0(1))
            'J'.code -> {
                when (getArg0(0)) {
                    0 -> {
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                        blockClear(0, mCursorRow + 1, mColumns, mRows - (mCursorRow + 1))
                    }
                    1 -> {
                        blockClear(0, 0, mColumns, mCursorRow)
                        blockClear(0, mCursorRow, mCursorCol + 1)
                    }
                    2 ->                         // move..
                        blockClear(0, 0, mColumns, mRows)
                    3 -> mMainBuffer.clearTranscript()
                    else -> {
                        unknownSequence(b)
                        return
                    }
                }
                mAboutToAutoWrap = false
            }
            'K'.code -> {
                when (getArg0(0)) {
                    0 -> blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                    1 -> blockClear(0, mCursorRow, mCursorCol + 1)
                    2 -> blockClear(0, mCursorRow, mColumns)
                    else -> {
                        unknownSequence(b)
                        return
                    }
                }
                mAboutToAutoWrap = false
            }
            'L'.code -> {
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToInsert = getArg0(1).coerceAtMost(linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToInsert
                screen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert)
                blockClear(0, mCursorRow, mColumns, linesToInsert)
            }
            'M'.code -> {
                mAboutToAutoWrap = false
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToDelete = getArg0(1).coerceAtMost(linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToDelete
                screen.blockCopy(0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow)
                blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete)
            }
            'P'.code -> {

                // http://www.vt100.net/docs/vt510-rm/DCH: "If ${N} is greater than the number of characters between the
                // cursor and the right margin, then DCH only deletes the remaining characters.
                // As characters are deleted, the remaining characters between the cursor and right margin move to the left.
                // Character attributes move with the characters. The terminal adds blank spaces with no visual character
                // attributes at the right margin. DCH has no effect outside the scrolling margins."
                mAboutToAutoWrap = false
                val cellsAfterCursor = mColumns - mCursorCol
                val cellsToDelete = getArg0(1).coerceAtMost(cellsAfterCursor)
                val cellsToMove = cellsAfterCursor - cellsToDelete
                screen.blockCopy(mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow)
                blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete)
            }
            'S'.code -> {
                // "${CSI}${N}S" - scroll up ${N} lines (default = 1) (SU).
                val linesToScroll = getArg0(1)
                var i = 0
                while (i < linesToScroll) {
                    scrollDownOneLine()
                    i++
                }
            }
            'T'.code -> if (mArgIndex == 0) {
                // "${CSI}${N}T" - Scroll down N lines (default = 1) (SD).
                // http://vt100.net/docs/vt510-rm/SD: "N is the number of lines to move the user window up in page
                // memory. N new lines appear at the top of the display. N old lines disappear at the bottom of the
                // display. You cannot pan past the top margin of the current page".
                val linesToScrollArg = getArg0(1)
                val linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin
                val linesToScroll = linesBetweenTopAndBottomMargins.coerceAtMost(linesToScrollArg)
                screen.blockCopy(
                    0,
                    mTopMargin,
                    mColumns,
                    linesBetweenTopAndBottomMargins - linesToScroll,
                    0,
                    mTopMargin + linesToScroll
                )
                blockClear(0, mTopMargin, mColumns, linesToScroll)
            } else {
                // "${CSI}${func};${startx};${starty};${firstrow};${lastrow}T" - initiate highlight mouse tracking.
                unimplementedSequence(b)
            }
            'X'.code -> {
                mAboutToAutoWrap = false
                screen.blockSet(
                    mCursorCol, mCursorRow, getArg0(1).coerceAtMost(mColumns - mCursorCol), 1, ' '.code, style
                )
            }
            'Z'.code -> {
                var numberOfTabs = getArg0(1)
                var newCol = mLeftMargin
                var i = mCursorCol - 1
                while (i >= 0) {
                    if (mTabStop[i]) {
                        if (--numberOfTabs == 0) {
                            newCol = i.coerceAtLeast(mLeftMargin)
                            break
                        }
                    }
                    i--
                }
                mCursorCol = newCol
            }
            '?'.code -> continueSequence(ESC_CSI_QUESTIONMARK)
            '>'.code -> continueSequence(ESC_CSI_BIGGERTHAN)
            '`'.code -> setCursorColRespectingOriginMode(getArg0(1) - 1)
            'b'.code -> {
                if (mLastEmittedCodePoint == -1) return
                val numRepeat = getArg0(1)
                var i = 0
                while (i < numRepeat) {
                    emitCodePoint(mLastEmittedCodePoint)
                    i++
                }
            }
            'c'.code ->                 // The important part that may still be used by some (tmux stores this value but does not currently use it)
                // is the first response parameter identifying the terminal service class, where we send 64 for "vt420".
                // This is followed by a list of attributes which is probably unused by applications. Send like xterm.
                if (getArg0(0) == 0) mSession.write("\u001b[?64;1;2;6;9;15;18;21;22c")
            'd'.code -> cursorRow = 1.coerceAtLeast(getArg0(1)).coerceAtMost(mRows) - 1
            'e'.code -> setCursorPosition(mCursorCol, mCursorRow + getArg0(1))
            'g'.code -> when (getArg0(0)) {
                0 -> mTabStop[mCursorCol] = false
                3 -> {
                    var i = 0
                    while (i < mColumns) {
                        mTabStop[i] = false
                        i++
                    }
                }
                else -> {}
            }
            'h'.code -> doSetMode(true)
            'l'.code -> doSetMode(false)
            'm'.code -> selectGraphicRendition()
            'n'.code -> when (getArg0(0)) {
                5 -> {
                    // Answer is ESC [ 0 n (Terminal OK).
                    val dsr = byteArrayOf(27.toByte(), '['.code.toByte(), '0'.code.toByte(), 'n'.code.toByte())
                    mSession.write(dsr, 0, dsr.size)
                }
                6 ->                         // Answer is ESC [ y ; x R, where x,y is
                    // the cursor location.
                    mSession.write(String.format(Locale.US, "\u001b[%d;%dR", mCursorRow + 1, mCursorCol + 1))
                else -> {}
            }
            'r'.code -> {

                // https://vt100.net/docs/vt510-rm/DECSTBM.html
                // The top margin defaults to 1, the bottom margin defaults to mRows.
                // The escape sequence numbers top 1..23, but we number top 0..22.
                // The escape sequence numbers bottom 2..24, and so do we (because we use a zero based numbering
                // scheme, but we store the first line below the bottom-most scrolling line.
                // As a result, we adjust the top line by -1, but we leave the bottom line alone.
                // Also require that top + 2 <= bottom.
                mTopMargin = 0.coerceAtLeast((getArg0(1) - 1).coerceAtMost(mRows - 2))
                mBottomMargin = (mTopMargin + 2).coerceAtLeast(getArg1(mRows).coerceAtMost(mRows))

                // DECSTBM moves the cursor to column 1, line 1 of the page respecting origin mode.
                setCursorPosition(0, 0)
            }
            's'.code -> if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                mLeftMargin = (getArg0(1) - 1).coerceAtMost(mColumns - 2)
                mRightMargin = (mLeftMargin + 1).coerceAtLeast(getArg1(mColumns).coerceAtMost(mColumns))
                // DECSLRM moves the cursor to column 1, line 1 of the page.
                setCursorPosition(0, 0)
            } else {
                // Save cursor (ANSI.SYS), available only when DECLRMM is disabled.
                saveCursor()
            }
            't'.code -> when (getArg0(0)) {
                11 -> mSession.write("\u001b[1t")
                13 -> mSession.write("\u001b[3;0;0t")
                14 ->                         // We just report characters time 12 here.
                    mSession.write(String.format(Locale.US, "\u001b[4;%d;%dt", mRows * 12, mColumns * 12))
                18 -> mSession.write(String.format(Locale.US, "\u001b[8;%d;%dt", mRows, mColumns))
                19 ->                         // We report the same size as the view, since it's the view really isn't resizable from the shell.
                    mSession.write(String.format(Locale.US, "\u001b[9;%d;%dt", mRows, mColumns))
                20 -> mSession.write("\u001b]LIconLabel\u001b\\")
                21 -> mSession.write("\u001b]l\u001b\\")
                22 -> {
                    // 22;0 -> Save xterm icon and window title on stack.
                    // 22;1 -> Save xterm icon title on stack.
                    // 22;2 -> Save xterm window title on stack.
                    mTitleStack.push(mTitle)
                    if (mTitleStack.size > 20) {
                        // Limit size
                        mTitleStack.removeAt(0)
                    }
                }
                23 -> if (!mTitleStack.isEmpty()) title = mTitleStack.pop()
                else -> {}
            }
            'u'.code -> restoreCursor()
            ' '.code -> continueSequence(ESC_CSI_ARGS_SPACE)
            else -> parseArg(b)
        }
    }

    /** Select Graphic Rendition (SGR) - see http://en.wikipedia.org/wiki/ANSI_escape_code#graphics.  */
    private fun selectGraphicRendition() {
        if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
        var i = 0
        while (i <= mArgIndex) {
            var code = mArgs[i]
            if (code < 0) {
                code = if (mArgIndex > 0) {
                    i++
                    continue
                } else {
                    0
                }
            }
            if (code == 0) { // reset
                mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
                mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
                mEffect = 0
            } else if (code == 1) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_BOLD
            } else if (code == 2) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_DIM
            } else if (code == 3) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_ITALIC
            } else if (code == 4) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
            } else if (code == 5) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_BLINK
            } else if (code == 7) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_INVERSE
            } else if (code == 8) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
            } else if (code == 9) {
                mEffect = mEffect or TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH
            } else if (code == 10) {
                // Exit alt charset (TERM=linux) - ignore.
            } else if (code == 11) {
                // Enter alt charset (TERM=linux) - ignore.
            } else if (code == 22) { // Normal color or intensity, neither bright, bold nor faint.
                mEffect = mEffect and (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_DIM).inv()
            } else if (code == 23) { // not italic, but rarely used as such; clears standout with TERM=screen
                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC.inv()
            } else if (code == 24) { // underline: none
                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE.inv()
            } else if (code == 25) { // blink: none
                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_BLINK.inv()
            } else if (code == 27) { // image: positive
                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE.inv()
            } else if (code == 28) {
                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE.inv()
            } else if (code == 29) {
                mEffect = mEffect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH.inv()
            } else if (code in 30..37) {
                mForeColor = code - 30
            } else if (code == 38 || code == 48) {
                // Extended set foreground(38)/background (48) color.
                // This is followed by either "2;$R;$G;$B" to set a 24-bit color or
                // "5;$INDEX" to set an indexed color.
                if (i + 2 > mArgIndex) {
                    i++
                    continue
                }
                val firstArg = mArgs[i + 1]
                if (firstArg == 2) {
                    if (i + 4 > mArgIndex) {
                        logWarn(mClient, LOG_TAG, "Too few CSI$code;2 RGB arguments")
                    } else {
                        val red = mArgs[i + 2]
                        val green = mArgs[i + 3]
                        val blue = mArgs[i + 4]
                        if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
                            finishSequenceAndLogError("Invalid RGB: $red,$green,$blue")
                        } else {
                            val argbColor = -0x1000000 or (red shl 16) or (green shl 8) or blue
                            if (code == 38) {
                                mForeColor = argbColor
                            } else {
                                mBackColor = argbColor
                            }
                        }
                        i += 4 // "2;P_r;P_g;P_r"
                    }
                } else if (firstArg == 5) {
                    val color = mArgs[i + 2]
                    i += 2 // "5;P_s"
                    if (color >= 0 && color < TextStyle.NUM_INDEXED_COLORS) {
                        if (code == 38) {
                            mForeColor = color
                        } else {
                            mBackColor = color
                        }
                    } else {
                        if (LOG_ESCAPE_SEQUENCES) logWarn(mClient, LOG_TAG, "Invalid color index: $color")
                    }
                } else {
                    finishSequenceAndLogError("Invalid ISO-8613-3 SGR first argument: $firstArg")
                }
            } else if (code == 39) { // Set default foreground color.
                mForeColor = TextStyle.COLOR_INDEX_FOREGROUND
            } else if (code in 40..47) { // Set background color.
                mBackColor = code - 40
            } else if (code == 49) { // Set default background color.
                mBackColor = TextStyle.COLOR_INDEX_BACKGROUND
            } else if (code in 90..97) { // Bright foreground colors (aixterm codes).
                mForeColor = code - 90 + 8
            } else if (code in 100..107) { // Bright background color (aixterm codes).
                mBackColor = code - 100 + 8
            } else {
                if (LOG_ESCAPE_SEQUENCES) logWarn(mClient, LOG_TAG, String.format("SGR unknown code %d", code))
            }
            i++
        }
    }

    private fun doOsc(b: Int) {
        when (b) {
            7 -> doOscSetTextParameters("\u0007")
            27 -> continueSequence(ESC_OSC_ESC)
            else -> collectOSCArgs(b)
        }
    }

    private fun doOscEsc(b: Int) {
        when (b) {
            '\\'.code -> doOscSetTextParameters("\u001b\\")
            else -> {
                // The ESC character was not followed by a \, so insert the ESC and
                // the current character in arg buffer.
                collectOSCArgs(27)
                collectOSCArgs(b)
                continueSequence(ESC_OSC)
            }
        }
    }

    /** An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.  */
    private fun doOscSetTextParameters(bellOrStringTerminator: String) {
        var value = -1
        var textParameter = ""
        // Extract initial $value from initial "$value;..." string.
        for (mOSCArgTokenizerIndex in mOSCOrDeviceControlArgs.indices) {
            val b = mOSCOrDeviceControlArgs[mOSCArgTokenizerIndex]
            if (b == ';') {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1)
                break
            } else if (b in '0'..'9') {
                value = (if (value < 0) 0 else value * 10) + (b.code - '0'.code)
            } else {
                unknownSequence(b.code)
                return
            }
        }
        when (value) {
            0, 1, 2 -> title = textParameter
            4 -> {
                // P s = 4 ; c ; spec → Change Color Number c to the color specified by spec. This can be a name or RGB
                // specification as per XParseColor. Any number of c name pairs may be given. The color numbers correspond
                // to the ANSI colors 0-7, their bright versions 8-15, and if supported, the remainder of the 88-color or
                // 256-color table.
                // If a "?" is given rather than a name or RGB specification, xterm replies with a control sequence of the
                // same form which can be used to set the corresponding color. Because more than one pair of color number
                // and specification can be given in one control sequence, xterm can make more than one reply.
                var colorIndex = -1
                var parsingPairStart = -1
                var i = 0
                while (true) {
                    val endOfInput = i == textParameter.length
                    val b = if (endOfInput) ';' else textParameter[i]
                    if (b == ';') {
                        if (parsingPairStart < 0) {
                            parsingPairStart = i + 1
                        } else {
                            if (colorIndex < 0 || colorIndex > 255) {
                                unknownSequence(b.code)
                                return
                            } else {
                                mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i))
                                mSession.onColorsChanged()
                                colorIndex = -1
                                parsingPairStart = -1
                            }
                        }
                    } else if (parsingPairStart >= 0) {
                        // We have passed a color index and are now going through color spec.
                    } else if (b in '0'..'9') {
                        colorIndex = (if (colorIndex < 0) 0 else colorIndex * 10) + (b.code - '0'.code)
                    } else {
                        unknownSequence(b.code)
                        return
                    }
                    if (endOfInput) break
                    i++
                }
            }
            10, 11, 12 -> {
                var specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10)
                var lastSemiIndex = 0
                var charIndex = 0
                while (true) {
                    val endOfInput = charIndex == textParameter.length
                    if (endOfInput || textParameter[charIndex] == ';') {
                        try {
                            val colorSpec = textParameter.substring(lastSemiIndex, charIndex)
                            if ("?" == colorSpec) {
                                // Report current color in the same format xterm and gnome-terminal does.
                                val rgb = mColors.mCurrentColors[specialIndex]
                                val r = 65535 * (rgb and 0x00FF0000 shr 16) / 255
                                val g = 65535 * (rgb and 0x0000FF00 shr 8) / 255
                                val b = 65535 * (rgb and 0x000000FF) / 255
                                mSession.write(
                                    "\u001b]$value;rgb:" + String.format(
                                        Locale.US, "%04x", r
                                    ) + "/" + String.format(
                                        Locale.US, "%04x", g
                                    ) + "/" + String.format(Locale.US, "%04x", b) + bellOrStringTerminator
                                )
                            } else {
                                mColors.tryParseColor(specialIndex, colorSpec)
                                mSession.onColorsChanged()
                            }
                            specialIndex++
                            if (endOfInput || specialIndex > TextStyle.COLOR_INDEX_CURSOR || ++charIndex >= textParameter.length) break
                            lastSemiIndex = charIndex
                        } catch (e: NumberFormatException) {
                            // Ignore.
                        }
                    }
                    charIndex++
                }
            }
            52 -> {
                val startIndex = textParameter.indexOf(";") + 1
                try {
                    val clipboardText = String(
                        Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8
                    )
                    mSession.onCopyTextToClipboard(clipboardText)
                } catch (e: Exception) {
                    logError(mClient, LOG_TAG, "OSC Manipulate selection, invalid string '$textParameter")
                }
            }
            104 ->                 // "104;$c" → Reset Color Number $c. It is reset to the color specified by the corresponding X
                // resource. Any number of c parameters may be given. These parameters correspond to the ANSI colors 0-7,
                // their bright versions 8-15, and if supported, the remainder of the 88-color or 256-color table. If no
                // parameters are given, the entire table will be reset.
                if (textParameter.isEmpty()) {
                    mColors.reset()
                    mSession.onColorsChanged()
                } else {
                    var lastIndex = 0
                    var charIndex = 0
                    while (true) {
                        val endOfInput = charIndex == textParameter.length
                        if (endOfInput || textParameter[charIndex] == ';') {
                            try {
                                val colorToReset = textParameter.substring(lastIndex, charIndex).toInt()
                                mColors.reset(colorToReset)
                                mSession.onColorsChanged()
                                if (endOfInput) break
                                charIndex++
                                lastIndex = charIndex
                            } catch (e: NumberFormatException) {
                                // Ignore.
                            }
                        }
                        charIndex++
                    }
                }
            110, 111, 112 -> {
                mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110))
                mSession.onColorsChanged()
            }
            119 -> {}
            else -> unknownParameter(value)
        }
        finishSequence()
    }

    private fun blockClear(sx: Int, sy: Int, w: Int, h: Int = 1) {
        screen.blockSet(sx, sy, w, h, ' '.code, style)
    }

    private val style: Long
        get() = encode(mForeColor, mBackColor, mEffect)

    /** "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.  */
    private fun doSetMode(newValue: Boolean) {
        when (val modeBit = getArg0(0)) {
            4 -> mInsertMode = newValue
            20 -> unknownParameter(modeBit)
            34 -> {}
            else -> unknownParameter(modeBit)
        }
    }

    /**
     * NOTE: The parameters of this function respect the [.DECSET_BIT_ORIGIN_MODE]. Use
     * [.setCursorRowCol] for absolute pos.
     */
    private fun setCursorPosition(x: Int, y: Int) {
        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
        val effectiveTopMargin = if (originMode) mTopMargin else 0
        val effectiveBottomMargin = if (originMode) mBottomMargin else mRows
        val effectiveLeftMargin = if (originMode) mLeftMargin else 0
        val effectiveRightMargin = if (originMode) mRightMargin else mColumns
        val newRow = effectiveTopMargin.coerceAtLeast((effectiveTopMargin + y).coerceAtMost(effectiveBottomMargin - 1))
        val newCol = effectiveLeftMargin.coerceAtLeast((effectiveLeftMargin + x).coerceAtMost(effectiveRightMargin - 1))
        setCursorRowCol(newRow, newCol)
    }

    private fun scrollDownOneLine() {
        scrollCounter++
        if (mLeftMargin != 0 || mRightMargin != mColumns) {
            // Horizontal margin: Do not put anything into scroll history, just non-margin part of screen up.
            screen.blockCopy(
                mLeftMargin,
                mTopMargin + 1,
                mRightMargin - mLeftMargin,
                mBottomMargin - mTopMargin - 1,
                mLeftMargin,
                mTopMargin
            )
            // .. and blank bottom row between margins:
            screen.blockSet(mLeftMargin, mBottomMargin - 1, mRightMargin - mLeftMargin, 1, ' '.code, mEffect.toLong())
        } else {
            screen.scrollDownOneLine(mTopMargin, mBottomMargin, style)
        }
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     * Parameter characters modify the action or interpretation of the sequence. You can use up to
     * 16 parameters per sequence. You must use the ; character to separate parameters.
     * All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     *
     * https://vt100.net/docs/vt510-rm/chapter4.html#S4.3.3
     */
    private fun parseArg(inputByte: Int) {
        var bytes = intArrayOf(inputByte)
        // Only doing this for ESC_CSI and not for other ESC_CSI_* since they seem to be using their
        // own defaults with getArg*() calls, but there may be missed cases
        if (mEscapeState == ESC_CSI) {
            if ((mIsCSIStart && inputByte == ';'.code) || (!mIsCSIStart && mLastCSIArg != null && mLastCSIArg == ';'.code && inputByte == ';'.code)) {  // If sequence contains sequential ; characters, like \033[;;m
                bytes = intArrayOf('0'.code, ';'.code) // Assume 0 was passed
            }
        }
        mIsCSIStart = false
        for (b in bytes) {
            if (b >= '0'.code && b <= '9'.code) {
                if (mArgIndex < mArgs.size) {
                    val oldValue = mArgs[mArgIndex]
                    val thisDigit = b - '0'.code
                    var value: Int
                    value = if (oldValue >= 0) {
                        oldValue * 10 + thisDigit
                    } else {
                        thisDigit
                    }
                    if (value > 9999) value = 9999
                    mArgs[mArgIndex] = value
                }
                continueSequence(mEscapeState)
            } else if (b == ';'.code) {
                if (mArgIndex < mArgs.size) {
                    mArgIndex++
                }
                continueSequence(mEscapeState)
            } else {
                unknownSequence(b)
            }
            mLastCSIArg = b
        }
    }

    private fun getArg0(defaultValue: Int): Int {
        return getArg(0, defaultValue, true)
    }

    private fun getArg1(defaultValue: Int): Int {
        return getArg(1, defaultValue, true)
    }

    private fun getArg(index: Int, defaultValue: Int, treatZeroAsDefault: Boolean): Int {
        var result = mArgs[index]
        if (result < 0 || result == 0 && treatZeroAsDefault) {
            result = defaultValue
        }
        return result
    }

    private fun collectOSCArgs(b: Int) {
        if (mOSCOrDeviceControlArgs.length < MAX_OSC_STRING_LENGTH) {
            mOSCOrDeviceControlArgs.appendCodePoint(b)
            continueSequence(mEscapeState)
        } else {
            unknownSequence(b)
        }
    }

    private fun unimplementedSequence(b: Int) {
        logError("Unimplemented sequence char '" + b.toChar() + "' (U+" + String.format("%04x", b) + ")")
        finishSequence()
    }

    private fun unknownSequence(b: Int) {
        logError("Unknown sequence char '" + b.toChar() + "' (numeric value=" + b + ")")
        finishSequence()
    }

    private fun unknownParameter(parameter: Int) {
        logError("Unknown parameter: $parameter")
        finishSequence()
    }

    private fun logError(errorType: String) {
        if (LOG_ESCAPE_SEQUENCES) {
            val buf = StringBuilder()
            buf.append(errorType)
            buf.append(", escapeState=")
            buf.append(mEscapeState)
            var firstArg = true
            if (mArgIndex >= mArgs.size) mArgIndex = mArgs.size - 1
            for (i in 0..mArgIndex) {
                val value = mArgs[i]
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false
                        buf.append(", args={")
                    } else {
                        buf.append(',')
                    }
                    buf.append(value)
                }
            }
            if (!firstArg) buf.append('}')
            finishSequenceAndLogError(buf.toString())
        }
    }

    private fun finishSequenceAndLogError(error: String) {
        if (LOG_ESCAPE_SEQUENCES) logWarn(mClient, LOG_TAG, error)
        finishSequence()
    }

    private fun finishSequence() {
        mEscapeState = ESC_NONE
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private fun emitCodePoint(codePoint: Int) {
        mLastEmittedCodePoint = codePoint
        var cp = codePoint
        if (if (mUseLineDrawingUsesG0) mUseLineDrawingG0 else mUseLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            when (cp) {
                '_'.code -> cp = ' '.code // Blank.
                '`'.code -> cp = '◆'.code // Diamond.
                '0'.code -> cp = '█'.code // Solid block;
                'a'.code -> cp = '▒'.code // Checker board.
                'b'.code -> cp = '␉'.code // Horizontal tab.
                'c'.code -> cp = '␌'.code // Form feed.
                'd'.code -> cp = '\r'.code // Carriage return.
                'e'.code -> cp = '␊'.code // Linefeed.
                'f'.code -> cp = '°'.code // Degree.
                'g'.code -> cp = '±'.code // Plus-minus.
                'h'.code -> cp = '\n'.code // Newline.
                'i'.code -> cp = '␋'.code // Vertical tab.
                'j'.code -> cp = '┘'.code // Lower right corner.
                'k'.code -> cp = '┐'.code // Upper right corner.
                'l'.code -> cp = '┌'.code // Upper left corner.
                'm'.code -> cp = '└'.code // Left left corner.
                'n'.code -> cp = '┼'.code // Crossing lines.
                'o'.code -> cp = '⎺'.code // Horizontal line - scan 1.
                'p'.code -> cp = '⎻'.code // Horizontal line - scan 3.
                'q'.code -> cp = '─'.code // Horizontal line - scan 5.
                'r'.code -> cp = '⎼'.code // Horizontal line - scan 7.
                's'.code -> cp = '⎽'.code // Horizontal line - scan 9.
                't'.code -> cp = '├'.code // T facing rightwards.
                'u'.code -> cp = '┤'.code // T facing leftwards.
                'v'.code -> cp = '┴'.code // T facing upwards.
                'w'.code -> cp = '┬'.code // T facing downwards.
                'x'.code -> cp = '│'.code // Vertical line.
                'y'.code -> cp = '≤'.code // Less than or equal to.
                'z'.code -> cp = '≥'.code // Greater than or equal to.
                '{'.code -> cp = 'π'.code // Pi.
                '|'.code -> cp = '≠'.code // Not equal to.
                '}'.code -> cp = '£'.code // UK pound.
                '~'.code -> cp = '·'.code // Centered dot.
            }
        }
        val autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP)
        val displayWidth = width(cp)
        val cursorInLastColumn = mCursorCol == mRightMargin - 1
        if (autoWrap) {
            if (cursorInLastColumn && (mAboutToAutoWrap && displayWidth == 1 || displayWidth == 2)) {
                screen.setLineWrap(mCursorRow)
                mCursorCol = mLeftMargin
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++
                } else {
                    scrollDownOneLine()
                }
            }
        } else if (cursorInLastColumn && displayWidth == 2) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return
        }
        if (mInsertMode && displayWidth > 0) {
            // Move character to right one space.
            val destCol = mCursorCol + displayWidth
            if (destCol < mRightMargin) screen.blockCopy(
                mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow
            )
        }
        val offsetDueToCombiningChar = if (displayWidth <= 0 && mCursorCol > 0 && !mAboutToAutoWrap) 1 else 0
        var column = mCursorCol - offsetDueToCombiningChar

        // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported
        // The offsetDueToCombiningChar would never be 1 if mCursorCol was 0 to get column/index=-1,
        // so was mCursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow, possibly causing others bugs too.
        if (column < 0) column = 0
        screen.setChar(column, mCursorRow, cp, style)
        if (autoWrap && displayWidth > 0) mAboutToAutoWrap = mCursorCol == mRightMargin - displayWidth
        mCursorCol = (mCursorCol + displayWidth).coerceAtMost(mRightMargin - 1)
    }

    /** Set the cursor mode, but limit it to margins if [.DECSET_BIT_ORIGIN_MODE] is enabled.  */
    private fun setCursorColRespectingOriginMode(col: Int) {
        setCursorPosition(col, mCursorRow)
    }

    /** TODO: Better name, distinguished from [.setCursorPosition] by not regarding origin mode.  */
    private fun setCursorRowCol(row: Int, col: Int) {
        mCursorRow = 0.coerceAtLeast(row.coerceAtMost(mRows - 1))
        mCursorCol = 0.coerceAtLeast(col.coerceAtMost(mColumns - 1))
        mAboutToAutoWrap = false
    }

    fun clearScrollCounter() {
        scrollCounter = 0
    }

    fun toggleAutoScrollDisabled() {
        isAutoScrollDisabled = !isAutoScrollDisabled
    }

    /** Reset terminal state so user can interact with it regardless of present state.  */
    fun reset() {
        setCursorStyle()
        mArgIndex = 0
        mContinueSequence = false
        mEscapeState = ESC_NONE
        mInsertMode = false
        mLeftMargin = 0
        mTopMargin = 0
        mBottomMargin = mRows
        mRightMargin = mColumns
        mAboutToAutoWrap = false
        mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND
        mSavedStateMain.mSavedForeColor = mSavedStateAlt.mSavedForeColor
        mForeColor = mSavedStateMain.mSavedForeColor
        mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND
        mSavedStateMain.mSavedBackColor = mSavedStateAlt.mSavedBackColor
        mBackColor = mSavedStateMain.mSavedBackColor
        setDefaultTabStops()
        mUseLineDrawingG1 = false
        mUseLineDrawingG0 = false
        mUseLineDrawingUsesG0 = true
        mSavedStateMain.mSavedDecFlags = 0
        mSavedStateMain.mSavedEffect = 0
        mSavedStateMain.mSavedCursorCol = 0
        mSavedStateMain.mSavedCursorRow = 0
        mSavedStateAlt.mSavedDecFlags = 0
        mSavedStateAlt.mSavedEffect = 0
        mSavedStateAlt.mSavedCursorCol = 0
        mSavedStateAlt.mSavedCursorRow = 0
        mCurrentDecSetFlags = 0
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true)
        setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true)
        mSavedStateAlt.mSavedDecFlags = mCurrentDecSetFlags
        mSavedStateMain.mSavedDecFlags = mSavedStateAlt.mSavedDecFlags
        mSavedDecSetFlags = mSavedStateMain.mSavedDecFlags

        // XXX: Should we set terminal driver back to IUTF8 with termios?
        mUtf8ToFollow = 0
        mUtf8Index = 0
        mColors.reset()
        mSession.onColorsChanged()
    }

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return screen.getSelectedText(x1, y1, x2, y2)
    }
    /** Get the terminal session's title (null if not set).  */
    /** Change the terminal session's title.  */
    var title: String?
        get() = mTitle
        private set(newTitle) {
            val oldTitle = mTitle
            mTitle = newTitle
            if (oldTitle != newTitle) {
                mSession.titleChanged(oldTitle, newTitle)
            }
        }

    /** If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".  */
    fun paste(text: String) {
        // First: Always remove escape key and C1 control characters [0x80,0x9F]:
        var newText = text.replace("(\u001B|[\u0080-\u009F])".toRegex(), "")
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        newText = newText.replace("\r?\n".toRegex(), "\r")

        // Then: Implement bracketed paste mode if enabled:
        val bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE)
        if (bracketed) mSession.write("\u001b[200~")
        mSession.write(newText)
        if (bracketed) mSession.write("\u001b[201~")
    }

    /** http://www.vt100.net/docs/vt510-rm/DECSC  */
    internal class SavedScreenState {
        /** Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences.  */
        var mSavedCursorRow = 0
        var mSavedCursorCol = 0
        var mSavedEffect = 0
        var mSavedForeColor = 0
        var mSavedBackColor = 0
        var mSavedDecFlags = 0
        var mUseLineDrawingG0 = false
        var mUseLineDrawingG1 = false
        var mUseLineDrawingUsesG0 = true
    }

    override fun toString(): String {
        return ("TerminalEmulator[size=" + screen.mColumns + "x" + screen.mScreenRows + ", margins={" + mTopMargin + "," + mRightMargin + "," + mBottomMargin + "," + mLeftMargin + "}]")
    }

    companion object {
        /** Log unknown or unimplemented escape sequences received from the shell process.  */
        private const val LOG_ESCAPE_SEQUENCES = false
        const val MOUSE_LEFT_BUTTON = 0

        /** Mouse moving while having left mouse button pressed.  */
        const val MOUSE_LEFT_BUTTON_MOVED = 32
        const val MOUSE_WHEELUP_BUTTON = 64
        const val MOUSE_WHEELDOWN_BUTTON = 65

        /** Used for invalid data - http://en.wikipedia.org/wiki/Replacement_character#Replacement_character  */
        const val UNICODE_REPLACEMENT_CHAR = 0xFFFD

        /** Escape processing: Not currently in an escape sequence.  */
        private const val ESC_NONE = 0

        /** Escape processing: Have seen an ESC character - proceed to [.doEsc]  */
        private const val ESC = 1

        /** Escape processing: Have seen ESC POUND  */
        private const val ESC_POUND = 2

        /** Escape processing: Have seen ESC and a character-set-select ( char  */
        private const val ESC_SELECT_LEFT_PAREN = 3

        /** Escape processing: Have seen ESC and a character-set-select ) char  */
        private const val ESC_SELECT_RIGHT_PAREN = 4

        /** Escape processing: "ESC [" or CSI (Control Sequence Introducer).  */
        private const val ESC_CSI = 6

        /** Escape processing: ESC [ ?  */
        private const val ESC_CSI_QUESTIONMARK = 7

        /** Escape processing: ESC [ $  */
        private const val ESC_CSI_DOLLAR = 8

        /** Escape processing: ESC %  */
        private const val ESC_PERCENT = 9

        /** Escape processing: ESC ] (AKA OSC - Operating System Controls)  */
        private const val ESC_OSC = 10

        /** Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC  */
        private const val ESC_OSC_ESC = 11

        /** Escape processing: ESC [ >  */
        private const val ESC_CSI_BIGGERTHAN = 12

        /** Escape procession: "ESC P" or Device Control String (DCS)  */
        private const val ESC_P = 13

        /** Escape processing: CSI >  */
        private const val ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14

        /** Escape processing: CSI $ARGS ' '  */
        private const val ESC_CSI_ARGS_SPACE = 15

        /** Escape processing: CSI $ARGS '*'  */
        private const val ESC_CSI_ARGS_ASTERIX = 16

        /** Escape processing: CSI "  */
        private const val ESC_CSI_DOUBLE_QUOTE = 17

        /** Escape processing: CSI '  */
        private const val ESC_CSI_SINGLE_QUOTE = 18

        /** Escape processing: CSI !  */
        private const val ESC_CSI_EXCLAMATION = 19

        /** The number of parameter arguments. This name comes from the ANSI standard for terminal escape codes.  */
        private const val MAX_ESCAPE_PARAMETERS = 16

        /** Needs to be large enough to contain reasonable OSC 52 pastes.  */
        private const val MAX_OSC_STRING_LENGTH = 8192

        /** DECSET 1 - application cursor keys.  */
        private const val DECSET_BIT_APPLICATION_CURSOR_KEYS = 1
        private const val DECSET_BIT_REVERSE_VIDEO = 1 shl 1

        /**
         * http://www.vt100.net/docs/vt510-rm/DECOM: "When DECOM is set, the home cursor position is at the upper-left
         * corner of the screen, within the margins. The starting point for line numbers depends on the current top margin
         * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
         * upper-left corner of the screen. The starting point for line numbers is independent of the margins. The cursor
         * can move outside of the margins."
         */
        private const val DECSET_BIT_ORIGIN_MODE = 1 shl 2

        /**
         * http://www.vt100.net/docs/vt510-rm/DECAWM: "If the DECAWM function is set, then graphic characters received when
         * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
         * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
         * characters received when the cursor is at the right border of the page replace characters already on the page."
         */
        private const val DECSET_BIT_AUTOWRAP = 1 shl 3

        /** DECSET 25 - if the cursor should be enabled, [.isCursorEnabled].  */
        private const val DECSET_BIT_CURSOR_ENABLED = 1 shl 4
        private const val DECSET_BIT_APPLICATION_KEYPAD = 1 shl 5

        /** DECSET 1000 - if to report mouse press&release events.  */
        private const val DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 shl 6

        /** DECSET 1002 - like 1000, but report moving mouse while pressed.  */
        private const val DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 shl 7

        /** DECSET 1004 - NOT implemented.  */
        private const val DECSET_BIT_SEND_FOCUS_EVENTS = 1 shl 8

        /** DECSET 1006 - SGR-like mouse protocol (the modern sane choice).  */
        private const val DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 shl 9

        /** DECSET 2004 - see [.paste]  */
        private const val DECSET_BIT_BRACKETED_PASTE_MODE = 1 shl 10

        /** Toggled with DECLRMM - http://www.vt100.net/docs/vt510-rm/DECLRMM  */
        private const val DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 shl 11

        /** Not really DECSET bit... - http://www.vt100.net/docs/vt510-rm/DECSACE  */
        private const val DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 shl 12

        /** The number of terminal transcript rows that can be scrolled back to.  */
        const val TERMINAL_TRANSCRIPT_ROWS_MIN = 100
        const val TERMINAL_TRANSCRIPT_ROWS_MAX = 50000
        const val DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000

        /* The supported terminal cursor styles. */
        const val TERMINAL_CURSOR_STYLE_BLOCK = 0
        const val TERMINAL_CURSOR_STYLE_UNDERLINE = 1
        const val TERMINAL_CURSOR_STYLE_BAR = 2
        const val DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK
        val TERMINAL_CURSOR_STYLES_LIST = arrayOf(
            TERMINAL_CURSOR_STYLE_BLOCK, TERMINAL_CURSOR_STYLE_UNDERLINE, TERMINAL_CURSOR_STYLE_BAR
        )
        private const val LOG_TAG = "TerminalEmulator"
        fun mapDecSetBitToInternalBit(decsetBit: Int): Int {
            return when (decsetBit) {
                1 -> DECSET_BIT_APPLICATION_CURSOR_KEYS
                5 -> DECSET_BIT_REVERSE_VIDEO
                6 -> DECSET_BIT_ORIGIN_MODE
                7 -> DECSET_BIT_AUTOWRAP
                25 -> DECSET_BIT_CURSOR_ENABLED
                66 -> DECSET_BIT_APPLICATION_KEYPAD
                69 -> DECSET_BIT_LEFTRIGHT_MARGIN_MODE
                1000 -> DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE
                1002 -> DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
                1004 -> DECSET_BIT_SEND_FOCUS_EVENTS
                1006 -> DECSET_BIT_MOUSE_PROTOCOL_SGR
                2004 -> DECSET_BIT_BRACKETED_PASTE_MODE
                else -> -1
            }
        }
    }
}