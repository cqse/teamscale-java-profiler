package com.teamscale.jacoco.agent.logging

import java.nio.file.Path

/** Defines a property that contains the path to which log files should be written.  */
class DebugLogDirectoryPropertyDefiner : LogDirectoryPropertyDefiner() {
	override fun getPropertyValue() =
		filePath?.resolve("logs")?.toAbsolutePath()?.toString() ?: super.getPropertyValue()

	companion object {
		/** File path for debug logging.  */ /* package */
		var filePath: Path? = null
	}
}