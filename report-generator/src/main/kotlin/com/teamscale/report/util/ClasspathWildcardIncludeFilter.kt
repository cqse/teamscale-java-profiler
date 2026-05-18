package com.teamscale.report.util

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.StringUtils
import org.jacoco.core.runtime.WildcardMatcher
import org.jacoco.report.JavaNames
import java.util.*


/***
 * Tests given class file paths against call name patterns.
 * E.g. "/some/file/path/test.jar@my/package/Test.class" matches "my/package/ *" or "my/package/Test"
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
	 * Tests if the given file path (e.g. "/some/file/path/test.jar@my/package/Test.class" or "org/mypackage/MyClass"
	 */
	fun isIncluded(path: String): Boolean {
		val className = getClassName(path)
		// first check includes
		if (locationIncludeFilters != null && locationIncludeFilters?.matches(className) == false) {
			return false
		}
		// if they match, check excludes
		return locationExcludeFilters == null || locationExcludeFilters?.matches(className) == false
	}


	companion object {
		/**
		 * Returns the normalized class name of the given class file's path. I.e. turns something like
		 * "/opt/deploy/some.jar@com/teamscale/Class.class" into something like "com.teamscale.Class".
		 */
		fun getClassName(path: String): String {
			val parts = FileSystemUtils.normalizeSeparators(path)
				.split("@".toRegex()).dropLastWhile { it.isEmpty() }
				.toTypedArray()
			if (parts.isEmpty()) {
				return ""
			}

			var pathInsideJar = parts[parts.size - 1]
			if (path.lowercase(Locale.getDefault()).endsWith(".class")) {
				pathInsideJar = StringUtils.removeLastPart(pathInsideJar, '.')
			}
			return JavaNames().getQualifiedClassName(pathInsideJar)
		}
	}
}
