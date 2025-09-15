package com.teamscale.client

/**
 * Utility object for system-related operations and constants.
 */
object SystemUtils {
	/**
	 * Boolean constant that indicates whether the operating system is Windows.
	 */
	val IS_OS_WINDOWS: Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)
}
