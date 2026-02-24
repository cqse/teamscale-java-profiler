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

/**
 * A utility class providing some advanced string functionality.
 */
object StringUtils {

	/** Line feed ("\n"), platform independent.  */
	const val LINE_FEED: String = "\n"

	/** The empty string.  */
	private const val EMPTY_STRING: String = ""

	/**
	 * Checks if a string is empty (after trimming).
	 *
	 * @param text the string to check.
	 * @return `true` if string is empty or `null`,
	 * `false` otherwise.
	 */
	@JvmStatic
	fun isEmpty(text: String?): Boolean {
		if (text == null) {
			return true
		}
		return EMPTY_STRING == text.trim { it <= ' ' }
	}

	/**
	 * Returns the beginning of a String, cutting off the last part which is separated by the given character.
	 *
	 *
	 * E.g., removeLastPart("org.conqat.lib.commons.string.StringUtils", '.') gives "org.conqat.lib.commons.string".
	 *
	 * @param string    the String
	 * @param separator separation character
	 * @return the String without the last part, or the original string if the separation character is not found.
	 */
	fun removeLastPart(string: String, separator: Char): String {
		val idx = string.lastIndexOf(separator)
		if (idx == -1) {
			return string
		}

		return string.take(idx)
	}

	/**
	 * Remove prefix from a string.
	 *
	 * @param string the string
	 * @param prefix the prefix
	 * @return the string without the prefix or the original string if it does not start with the prefix.
	 */
	@JvmStatic
	fun stripPrefix(string: String, prefix: String): String {
		if (string.startsWith(prefix)) {
			return string.substring(prefix.length)
		}
		return string
	}

	/**
	 * Remove suffix from a string.
	 *
	 * @param string the string
	 * @param suffix the suffix
	 * @return the string without the suffix or the original string if it does not end with the suffix.
	 */
	@JvmStatic
	fun stripSuffix(string: String, suffix: String): String {
		return string.removeSuffix(suffix)
	}

	/**
	 * Calculates the Levenshtein distance between this CharSequence and another CharSequence.
	 * The Levenshtein distance is a measure of the number of single-character edits (insertions, deletions, or substitutions)
	 * required to change one string into the other.
	 *
	 * This implementation has a time complexity of O(n * m) and a space complexity of O(n), where n and m are the lengths
	 * of the two strings.
	 *
	 * For more information, see [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance).
	 *
	 * @receiver The string to compare.
	 * @param rhs The string to compare against.
	 * @return The Levenshtein distance between the two strings.
	 */
	@JvmStatic
	fun CharSequence.levenshteinDistance(rhs: CharSequence): Int {
		if (this == rhs) return 0
		if (isEmpty()) return rhs.length
		if (rhs.isEmpty()) return length

		val len0 = length + 1
		val len1 = rhs.length + 1

		var cost = IntArray(len0) { it }
		var newCost = IntArray(len0) { 0 }

		(1..<len1).forEach { i ->
			newCost[0] = i

			(1..<len0).forEach { j ->
				val match = if (this[j - 1] == rhs[i - 1]) 0 else 1
				val costReplace = cost[j - 1] + match
				val costInsert = cost[j] + 1
				val costDelete = newCost[j - 1] + 1

				newCost[j] = minOf(costInsert, costDelete, costReplace)
			}

			val swap = cost
			cost = newCost
			newCost = swap
		}

		return cost[len0 - 1]
	}

	/**
	 * Split string in lines. For the empty string and `null` an empty
	 * list is returned.
	 */
	@JvmStatic
	fun splitLinesAsList(content: String?) = content?.lines() ?: emptyList()

	/**
	 * Test if a string ends with one of the provided suffixes. Returns
	 * `false` if the list of prefixes is empty. This should only be used
	 * for short lists of suffixes.
	 */
	@JvmStatic
	fun endsWithOneOf(string: String, vararg suffixes: String) = suffixes.any { string.endsWith(it) }

	/**
	 * Removes double quotes from beginning and end (if present) and returns the new
	 * string.
	 */
	@JvmStatic
	fun removeDoubleQuotes(string: String) = string.removeSuffix("\"").removePrefix("\"")

	/**
	 * Converts an empty string to null. If the input string is not empty, it returns the string unmodified.
	 *
	 * @param string the string to be converted. Must not be null.
	 * @return `null` if the input string is empty after trimming; the original string otherwise.
	 */
	@JvmStatic
	fun emptyToNull(string: String) = if (isEmpty(string)) null else string

	/**
	 * Converts a nullable string to a non-null, empty string.
	 * If the input string is null, it returns an empty string. Otherwise, it returns the original string.
	 *
	 * @param stringOrNull the nullable string which may be null
	 * @return a non-null string; either the original string or an empty string if the input was null
	 */
	@JvmStatic
	fun nullToEmpty(stringOrNull: String?) = stringOrNull ?: EMPTY_STRING
}
