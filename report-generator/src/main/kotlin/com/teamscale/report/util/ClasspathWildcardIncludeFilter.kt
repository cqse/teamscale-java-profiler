package com.teamscale.report.util

import org.jacoco.core.runtime.WildcardMatcher


/***
 * Tests class names against wildcard include and exclude patterns, the same way JaCoCo's agent does.
 * E.g. "com/example/Test" and "com.example.Test" match "com.example.*" or "com.example.Test".
 */
open class ClasspathWildcardIncludeFilter(
	locationIncludeFilters: String?,
	locationExcludeFilters: String?
) {
	/**
	 * Include patterns to apply during JaCoCo's traversal of class files. If null then everything is included.
	 */
	private var locationIncludeFilters: WildcardMatcher? = null

	/**
	 * Exclude patterns to apply during JaCoCo's traversal of class files. If null then nothing is excluded.
	 */
	private var locationExcludeFilters: WildcardMatcher? = null

	/**
	 * Constructor.
	 *
	 * @param locationIncludeFilters Colon separated list of wildcard include patterns for fully qualified class names
	 * or null for no includes. See [WildcardMatcher] for the pattern syntax.
	 * @param locationExcludeFilters Colon separated list of wildcard exclude patterns for fully qualified class names
	 * or null for no excludes.See [WildcardMatcher] for the pattern syntax.
	 */
	init {
		if (!locationIncludeFilters.isNullOrEmpty()) {
			this.locationIncludeFilters = WildcardMatcher(locationIncludeFilters)
		}
		if (!locationExcludeFilters.isNullOrEmpty()) {
			this.locationExcludeFilters = WildcardMatcher(locationExcludeFilters)
		}
	}

	/**
	 * Tests if the given class name (e.g. "com/example/Outer$Inner" or "com.example.Outer$Inner") is included. Like
	 * JaCoCo, only slashes are replaced by dots, so nested classes keep their "$".
	 */
	fun isIncluded(className: String): Boolean {
		val dottedClassName = className.replace('/', '.')
		if (locationIncludeFilters != null && locationIncludeFilters?.matches(dottedClassName) == false) {
			return false
		}
		return locationExcludeFilters == null || locationExcludeFilters?.matches(dottedClassName) == false
	}
}
