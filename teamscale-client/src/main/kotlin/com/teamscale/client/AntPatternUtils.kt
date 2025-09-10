/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright 2005-2011 The ConQAT Project                                   |
|                                                                          |
| Licensed under the Apache License, Version 2.0 (the "License");          |
| you may not use this file except in compliance with the License.         |
| You may obtain a copy of the License at                                  |
|                                                                          |
|    http://www.apache.org/licenses/LICENSE-2.0                            |
|                                                                          |
| Unless required by applicable law or agreed to in writing, software      |
| distributed under the License is distributed on an "AS IS" BASIS,        |
| WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. |
| See the License for the specific language governing permissions and      |
| limitations under the License.                                           |
+-------------------------------------------------------------------------*/
package com.teamscale.client

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Utility methods for dealing with Ant pattern as defined at http://ant.apache.org/manual/dirtasks.html#patterns
 *
 *
 * We implement a special version where a trailing '.' can be used to only match files without file extension (i.e. file
 * names without dot).
 */
object AntPatternUtils {
	/** Converts an ANT pattern to a regex pattern.  */
	@Throws(PatternSyntaxException::class)
	@JvmStatic
	fun convertPattern(antPattern: String, caseSensitive: Boolean): Pattern {
		var normalized = normalizePattern(antPattern)

		// ant specialty: trailing /** is optional
		// for example **/e*/** will also match foo/entry
		var addTrailAll = false
		if (normalized.endsWith("/**")) {
			addTrailAll = true
			normalized = StringUtils.stripSuffix(normalized, "/**")
		}

		val patternBuilder = StringBuilder()
		convertPlainPattern(normalized, patternBuilder)

		if (addTrailAll) {
			// the tail pattern is optional (i.e. we do not require the '/'),
			// but the "**" is only in effect if the '/' occurs
			patternBuilder.append("(/.*)?")
		}

		return compileRegex(patternBuilder.toString(), normalized, caseSensitive)
	}

	/** Compiles the given regex.  */
	private fun compileRegex(regex: String, antPattern: String, caseSensitive: Boolean): Pattern {
		try {
			return Pattern.compile(regex, determineRegexFlags(caseSensitive))
		} catch (e: PatternSyntaxException) {
			// make pattern syntax exception more understandable
			throw PatternSyntaxException(
				"Error compiling ANT pattern '" + antPattern + "' to regular expression. " + e.description,
				e.pattern, e.index
			)
		}
	}

	/** Returns the flags to be used for the regular expression.  */
	private fun determineRegexFlags(caseSensitive: Boolean): Int {
		// Use DOTALL flag, as on Unix the file names can contain line breaks
		var flags = Pattern.DOTALL
		if (!caseSensitive) {
			flags = flags or Pattern.CASE_INSENSITIVE
		}
		return flags
	}

	/**
	 * Normalizes the given pattern by ensuring forward slashes and mapping trailing slash to '/ **'.
	 */
	private fun normalizePattern(antPattern: String): String {
		var normalized = FileSystemUtils.normalizeSeparators(antPattern)

		// ant pattern syntax: if a pattern ends with /, then ** is
		// appended
		if (normalized.endsWith("/")) {
			normalized += "**"
		}
		return normalized
	}

	/**
	 * Converts a plain ANT pattern to a regular expression, by replacing special characters, such as '?', '*', and
	 * '**'. The created pattern is appended to the given [StringBuilder]. The pattern must be plain, i.e. all ANT
	 * specialties, such as trailing double stars have to be dealt with beforehand.
	 */
	private fun convertPlainPattern(antPattern: String, patternBuilder: StringBuilder) {
		var i = 0
		while (i < antPattern.length) {
			val c = antPattern[i]
			if (c == '?') {
				patternBuilder.append("[^/]")
			} else if (c != '*') {
				patternBuilder.append(Pattern.quote(c.toString()))
			} else {
				i = convertStarSequence(antPattern, patternBuilder, i)
			}
			++i
		}
	}

	/**
	 * Converts a sequence of the ant pattern starting with a star at the given index. Appends the pattern fragment the
	 * the builder and returns the index to continue scanning from.
	 */
	private fun convertStarSequence(antPattern: String, patternBuilder: StringBuilder, index: Int): Int {
		val doubleStar = isCharAt(antPattern, index + 1, '*')
		if (doubleStar) {
			// if the double star is followed by a slash, the entire
			// group becomes optional, as we want "**/foo" to also
			// match a top-level "foo"
			val doubleStarSlash = isCharAt(antPattern, index + 2, '/')
			if (doubleStarSlash) {
				patternBuilder.append("(.*/)?")
				return index + 2
			}

			val doubleStarDot = isCharAtBeforeSlashOrEnd(antPattern, index + 2, '.')
			if (doubleStarDot) {
				patternBuilder.append("(.*/)?[^/.]*[.]?")
				return index + 2
			}

			patternBuilder.append(".*")
			return index + 1
		}

		val starDot = isCharAtBeforeSlashOrEnd(antPattern, index + 1, '.')
		if (starDot) {
			patternBuilder.append("[^/.]*[.]?")
			return index + 1
		}

		patternBuilder.append("[^/]*")
		return index
	}

	/**
	 * Returns whether the given position exists in the string and equals the given character, and the given character
	 * is either at the end or right before a slash.
	 */
	private fun isCharAtBeforeSlashOrEnd(s: String, position: Int, character: Char) =
		isCharAt(s, position, character) && (position + 1 == s.length || isCharAt(s, position + 1, '/'))

	/**
	 * Returns whether the given position exists in the string and equals the given character.
	 */
	private fun isCharAt(s: String, position: Int, character: Char) =
		position < s.length && s[position] == character
}
