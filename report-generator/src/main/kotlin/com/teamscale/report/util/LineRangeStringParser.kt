package com.teamscale.report.util

/**
 * Parses a list of line numbers in the format like 1,3-5,8-10.
 *
 * We do the parsing in here manually as using StringTokenizer and [String.split] is
 * around 10-20x slower and produces a lot of pressure on the garbage collector.
 */
class LineRangeStringParser {
	/** The current (partial) line number.  */
	private var currentLine = 0

	/** The already parsed start line of a range while the end line is parsed.  */
	private var startLine = 0

	/**
	 * Whether the [.currentLine] refers to the start or end line number of a range.
	 */
	private var isParsingEndLine = false

	/**
	 * Parses a list of line numbers in the format like 1,3-5,8-10.
	 *
	 * @return A collection of integers, which are described by the given string
	 */
	fun parse(lineNumbersString: String): CompactLines {
		reset()
		val lineNumbers = CompactLines()
		for (characterIndex in lineNumbersString.indices) {
			val currentChar = lineNumbersString[characterIndex]
			if (Character.isDigit(currentChar)) {
				currentLine = currentLine * 10 + currentChar.digitToInt()
			} else if (currentChar == '-') {
				check(!isParsingEndLine) { "Line number pattern at character $characterIndex is invalid in: $lineNumbersString" }
				startLine = currentLine
				currentLine = 0
				isParsingEndLine = true
			} else if (currentChar == ',') {
				appendLines(lineNumbers)
				reset()
			} else {
				check(Character.isWhitespace(currentChar)) { "Unexpected character $currentChar in $lineNumbersString" }
			}
		}
		appendLines(lineNumbers)
		return lineNumbers
	}

	/** Parses the currently parsed line or line range to the lineNumbers.  */
	private fun appendLines(lineNumbers: CompactLines) {
		if (currentLine == 0) {
			return
		}
		if (isParsingEndLine) {
			lineNumbers.addRange(startLine, currentLine)
		} else {
			lineNumbers.add(currentLine)
		}
	}

	/** Resets all internal parsing state.  */
	private fun reset() {
		currentLine = 0
		startLine = 0
		isParsingEndLine = false
	}

	companion object {
		/**
		 * Creates a list of [LineRange]s from a sorted and distinct plain list of line numbers.
		 */
		fun compactifyToRanges(lines: CompactLines): List<LineRange> {
			if (lines.isEmpty) {
				return ArrayList()
			}

			val firstLine = lines.iterator().next()
			var currentRange = LineRange(firstLine, firstLine)

			val compactifiedRanges: MutableList<LineRange> = ArrayList()
			compactifiedRanges.add(currentRange)

			for (currentLine in lines) {
				if (currentRange.end == currentLine || currentRange.end == currentLine - 1) {
					currentRange.end = currentLine
				} else {
					currentRange = LineRange(currentLine, currentLine)
					compactifiedRanges.add(currentRange)
				}
			}
			return compactifiedRanges
		}
	}
}
