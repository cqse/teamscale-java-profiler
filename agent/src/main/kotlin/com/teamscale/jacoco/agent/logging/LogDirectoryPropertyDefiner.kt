package com.teamscale.jacoco.agent.logging

import ch.qos.logback.core.PropertyDefinerBase
import com.teamscale.jacoco.agent.util.AgentUtils

/** Defines a property that contains the default path to which log files should be written.  */
open class LogDirectoryPropertyDefiner : PropertyDefinerBase() {
	override fun getPropertyValue() =
		AgentUtils.mainTempDirectory.resolve("logs").toAbsolutePath().toString()
}