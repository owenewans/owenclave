package com.termux.terminal

import com.termux.terminal.WcWidth.width
import java.util.*

/**
 * A row in a terminal, composed of a fixed number of cells.
 *
 *
 * The text in the row is stored in a char[] array, [.mText], for quick access during rendering.
 */
class TerminalRow(
    /** The number of columns in this terminal row.  */
    private val mColumns: Int, style: Long
) {
    /** The text filling this terminal row.  */
    var mText: CharArray

    /** The number of java char:s used in [.mText].  */
    private var mSpaceUsed: Short = 0

    /** If this row has been line wrapped due to text output at the end of line.  */
    var mLineWrap = false

    /** The style bits of each cell in the row. See [TextStyle].  */
    val mStyle: LongArray

    /** If this row might contain chars with width != 1, used for deactivating fast path  */
    var mHasNonOneWidthOrSurrogateChars = false

    /** Construct a blank row (containing only whitespace, ' ') with a specified style.  */
    init {
        mText = CharArray((SPARE_CAPACITY_FACTOR * mColumns).toInt())
        mStyle = LongArray(mColumns)
        clear(style)
    }

    /** NOTE: The sourceX2 is exclusive.  */
    fun copyInterval(line: TerminalRow, sourceX1: Int, sourceX2: Int, destinationX: Int) {

        mHasNonOneWidthOrSurrogateChars = mHasNonOneWidthOrSurrogateChars or line.mHasNonOneWidthOrSurrogateChars

        val x1 = line.findStartOfColumn(sourceX1)
        val x2 = line.findStartOfColumn(sourceX2)
        var startingFromSecondHalfOfWideChar = sourceX1 > 0 && line.wideDisplayCharacterStartingAt(sourceX1 - 1)
        val sourceChars = if (this == line) line.mText.copyOf(line.mText.size) else line.mText
        var latestNonCombiningWidth = 0

        var srcX1 = sourceX1
        var dstX = destinationX
        var i = x1
        while (i < x2) {
            val sourceChar = sourceChars[i]
            var codePoint = if (Character.isHighSurrogate(sourceChar)) Character.toCodePoint(
                sourceChar, sourceChars[++i]
            ) else sourceChar.code

            if (startingFromSecondHalfOfWideChar) {
                // Just treat copying second half of wide char as copying whitespace.
                codePoint = ' '.code
                startingFromSecondHalfOfWideChar = false
            }

            val w = width(codePoint)
            if (w > 0) {
                dstX += latestNonCombiningWidth
                srcX1 += latestNonCombiningWidth
                latestNonCombiningWidth = w
            }
            setChar(dstX, codePoint, line.getStyle(srcX1))
            i++
        }
    }

    val spaceUsed: Int
        get() = mSpaceUsed.toInt()

    /** Note that the column may end of second half of wide character.  */
    fun findStartOfColumn(column: Int): Int {
        if (column == mColumns) return spaceUsed
        var currentColumn = 0
        var currentCharIndex = 0
        while (true) { // 0<2 1 < 2
            var newCharIndex = currentCharIndex
            val c = mText[newCharIndex++] // cci=1, cci=2
            val isHigh = Character.isHighSurrogate(c)
            val codePoint = if (isHigh) Character.toCodePoint(c, mText[newCharIndex++]) else c.code
            val wcwidth = width(codePoint) // 1, 2
            if (wcwidth > 0) {
                currentColumn += wcwidth
                if (currentColumn == column) {
                    while (newCharIndex < mSpaceUsed) {
                        // Skip combining chars.
                        if (Character.isHighSurrogate(mText[newCharIndex])) {
                            newCharIndex += if (width(
                                    Character.toCodePoint(
                                        mText[newCharIndex], mText[newCharIndex + 1]
                                    )
                                ) <= 0
                            ) {
                                2
                            } else {
                                break
                            }
                        } else if (width(mText[newCharIndex].code) <= 0) {
                            newCharIndex++
                        } else {
                            break
                        }
                    }
                    return newCharIndex
                } else if (currentColumn > column) {
                    // Wide column going past end.
                    return currentCharIndex
                }
            }
            currentCharIndex = newCharIndex
        }
    }

    private fun wideDisplayCharacterStartingAt(column: Int): Boolean {

        var currentCharIndex = 0
        var currentColumn = 0
        while (currentCharIndex < mSpaceUsed) {
            val c = mText[currentCharIndex++]
            val codePoint = if (Character.isHighSurrogate(c)) Character.toCodePoint(
                c, mText[currentCharIndex++]
            ) else c.code
            val wcwidth = width(codePoint)
            if (wcwidth > 0) {
                if (currentColumn == column && wcwidth == 2) return true
                currentColumn += wcwidth
                if (currentColumn > column) return false
            }
        }
        return false
    }

    fun clear(style: Long) {

        Arrays.fill(mText, ' ')
        Arrays.fill(mStyle, style)
        mSpaceUsed = mColumns.toShort()
        mHasNonOneWidthOrSurrogateChars = false
    }

    // https://github.com/steven676/Android-Terminal-Emulator/commit/9a47042620bec87617f0b4f5d50568535668fe26
    fun setChar(columnToSet: Int, codePoint: Int, style: Long) {

        var column = columnToSet

        require(column in mStyle.indices) { "TerminalRow.setChar(): columnToSet=$column, codePoint=$codePoint, style=$style" }

        mStyle[column] = style
        val newCodePointDisplayWidth = width(codePoint)

        // Fast path when we don't have any chars with width != 1
        if (!mHasNonOneWidthOrSurrogateChars) {
            if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT || newCodePointDisplayWidth != 1) {
                mHasNonOneWidthOrSurrogateChars = true
            } else {
                mText[column] = codePoint.toChar()
                return
            }
        }
        val newIsCombining = newCodePointDisplayWidth <= 0
        val wasExtraColForWideChar = column > 0 && wideDisplayCharacterStartingAt(column - 1)
        if (newIsCombining) {
            // When standing at second half of wide character and inserting combining:
            if (wasExtraColForWideChar) column--
        } else {
            // Check if we are overwriting the second half of a wide character starting at the previous column:
            if (wasExtraColForWideChar) setChar(column - 1, ' '.code, style)
            // Check if we are overwriting the first half of a wide character starting at the next column:
            val overwritingWideCharInNextColumn = newCodePointDisplayWidth == 2 && wideDisplayCharacterStartingAt(
                column + 1
            )
            if (overwritingWideCharInNextColumn) setChar(column + 1, ' '.code, style)
        }
        var text = mText
        val oldStartOfColumnIndex = findStartOfColumn(column)
        val oldCodePointDisplayWidth = width(text, oldStartOfColumnIndex)

        // Get the number of elements in the mText array this column uses now
        val oldCharactersUsedForColumn = if (column + oldCodePointDisplayWidth < mColumns) {
            findStartOfColumn(column + oldCodePointDisplayWidth) - oldStartOfColumnIndex
        } else {
            // Last character.
            mSpaceUsed - oldStartOfColumnIndex
        }

        // Find how many chars this column will need
        var newCharactersUsedForColumn = Character.charCount(codePoint)
        if (newIsCombining) {
            // Combining characters are added to the contents of the column instead of overwriting them, so that they
            // modify the existing contents.
            // FIXME: Put a limit of combining characters.
            // FIXME: Unassigned characters also get width=0.
            newCharactersUsedForColumn += oldCharactersUsedForColumn
        }
        val oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn
        val newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn
        val javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn
        if (javaCharDifference > 0) {
            // Shift the rest of the line right.
            val oldCharactersAfterColumn = mSpaceUsed - oldNextColumnIndex
            if (mSpaceUsed + javaCharDifference > text.size) {
                // We need to grow the array
                val newText = CharArray(text.size + mColumns)
                System.arraycopy(text, 0, newText, 0, oldStartOfColumnIndex + oldCharactersUsedForColumn)
                System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn)
                text = newText
                mText = text
            } else {
                System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn)
            }
        } else if (javaCharDifference < 0) {
            // Shift the rest of the line left.
            System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - oldNextColumnIndex)
        }
        mSpaceUsed = (mSpaceUsed + javaCharDifference).toShort()

        // Store char. A combining character is stored at the end of the existing contents so that it modifies them:
        Character.toChars(
            codePoint, text, oldStartOfColumnIndex + if (newIsCombining) oldCharactersUsedForColumn else 0
        )
        if (oldCodePointDisplayWidth == 2 && newCodePointDisplayWidth == 1) {
            // Replace second half of wide char with a space. Which mean that we actually add a ' ' java character.
            if (mSpaceUsed + 1 > text.size) {
                val newText = CharArray(text.size + mColumns)
                System.arraycopy(text, 0, newText, 0, newNextColumnIndex)
                System.arraycopy(
                    text, newNextColumnIndex, newText, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex
                )
                text = newText
                mText = text
            } else {
                System.arraycopy(
                    text, newNextColumnIndex, text, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex
                )
            }
            text[newNextColumnIndex] = ' '
            ++mSpaceUsed
        } else if (oldCodePointDisplayWidth == 1 && newCodePointDisplayWidth == 2) {// Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
            // check at the beginning of this method we know that we are not overwriting a wide char.

            // Shift the array leftwards.
            // Truncate the line to the second part of this wide char:
            require(column != mColumns - 1) { "Cannot put wide character in last column" }
            if (column == mColumns - 2) {
                // Truncate the line to the second part of this wide char:
                mSpaceUsed = newNextColumnIndex.toShort()
            } else {
                // Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
                // check at the beginning of this method we know that we are not overwriting a wide char.
                val newNextNextColumnIndex = newNextColumnIndex + if (Character.isHighSurrogate(mText[newNextColumnIndex])) 2 else 1
                val nextLen = newNextNextColumnIndex - newNextColumnIndex

                // Shift the array leftwards.
                System.arraycopy(
                    text, newNextNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - newNextNextColumnIndex
                )
                mSpaceUsed = (mSpaceUsed - nextLen).toShort()
            }
        }
    }

    val isBlank: Boolean
        get() {
            var charIndex = 0
            val charLen = spaceUsed
            while (charIndex < charLen) {
                if (mText[charIndex] != ' ') return false
                charIndex++
            }
            return true
        }

    fun getStyle(column: Int): Long {
        return mStyle[column]
    }

    companion object {
        private const val SPARE_CAPACITY_FACTOR = 1.5f
    }
}