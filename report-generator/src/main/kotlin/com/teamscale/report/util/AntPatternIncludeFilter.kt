package com.teamscale.report.util

import com.teamscale.client.AntPatternUtils
import com.teamscale.client.FileSystemUtils
import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * Applies ANT include and exclude patterns to paths.
 */
class AntPatternIncludeFilter(
	locationIncludeFilters: List<String>,
	locationExcludeFilters: List<String>
) : Predicate<String> {
	/** The include filters. Empty means include everything.  */
	private val locationIncludeFilters: List<Pattern> =
		locationIncludeFilters.map { AntPatternUtils.convertPattern(it, false) }

	/** The exclude filters. Empty means exclude nothing.  */
	private val locationExcludeFilters: List<Pattern> =
		locationExcludeFilters.map { AntPatternUtils.convertPattern(it, false) }

	/** {@inheritDoc}  */
	override fun test(path: String) = !isFiltered(FileSystemUtils.normalizeSeparators(path))

	/**
	 * Returns `true` if the given class file location (normalized to forward slashes as path separators)
	 * should not be analyzed.
	 *
	 *
	 * Exclude filters overrule include filters.
	 */
	private fun isFiltered(location: String): Boolean {
		// first check includes
		val noneIncludes = locationIncludeFilters.none { it.matcher(location).matches() }
		if (locationIncludeFilters.isNotEmpty() && noneIncludes) {
			return true
		}
		// only if they match, check excludes
		return locationExcludeFilters.any { it.matcher(location).matches() }
	}
}
