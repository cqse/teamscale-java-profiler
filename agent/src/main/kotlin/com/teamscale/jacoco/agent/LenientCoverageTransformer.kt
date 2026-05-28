package com.teamscale.jacoco.agent

import org.jacoco.agent.rt.internal_29a6edd.CoverageTransformer
import org.jacoco.agent.rt.internal_29a6edd.IExceptionLogger
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.AgentOptions
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.IRuntime
import org.slf4j.Logger
import java.lang.instrument.IllegalClassFormatException
import java.security.ProtectionDomain

/**
 * A class file transformer which delegates to the JaCoCo [org.jacoco.agent.rt.internal_29a6edd.CoverageTransformer] to do the actual instrumentation,
 * but treats instrumentation errors e.g. due to unsupported class file versions more lenient by only logging them, but
 * not bailing out completely. Those unsupported classes will not be instrumented and will therefore not be contained in
 * the collected coverage report.
 */
class LenientCoverageTransformer(
	runtime: IRuntime?,
	options: AgentOptions,
	private val logger: Logger
) : CoverageTransformer(
	runtime,
	options,
	// The coverage transformer only uses the logger to print an error when the instrumentation fails.
	// We want to show our more specific error message instead, so we only log this for debugging at trace.
	IExceptionLogger { logger.trace(it.message, it) }
) {
	private val useFastPathExclusion = options.includes.isNullOrBlank()

	override fun transform(
		loader: ClassLoader?,
		classname: String,
		classBeingRedefined: Class<*>?,
		protectionDomain: ProtectionDomain?,
		classfileBuffer: ByteArray
	): ByteArray? {
		if (useFastPathExclusion && isDefaultExcluded(classname)) {
			return null
		}
		try {
			return super.transform(loader, classname, classBeingRedefined, protectionDomain, classfileBuffer)
		} catch (e: IllegalClassFormatException) {
			logger.error(
				"Failed to instrument $classname. File will be skipped from instrumentation. " +
						"No coverage will be collected for it. Exclude the file from the instrumentation or try " +
						"updating the Teamscale Java Profiler if the file should actually be instrumented. (Cause: ${getRootCauseMessage(e)})"
			)
			return null
		}
	}

	companion object {
		/**
		 * Derived from [com.teamscale.jacoco.agent.options.AgentOptions.DEFAULT_EXCLUDES].
		 */
		private val DEFAULT_EXCLUDED_PREFIXES = setOf(
			"java/", "javax/", "jakarta/", "sun/", "junit/", "shadow/",
			"com/sun/", "com/fasterxml/",
			"org/eclipse/", "org/junit/", "org/apache/", "org/slf4j/",
			"org/gradle/", "org/jboss/", "org/wildfly/", "org/springframework/",
			"org/aspectj/", "org/h2/", "org/hibernate/", "org/assertj/",
			"org/mockito/", "org/thymeleaf/"
		)

		private fun isDefaultExcluded(classname: String): Boolean =
			DEFAULT_EXCLUDED_PREFIXES.any { classname.startsWith(it) }

		private fun getRootCauseMessage(e: Throwable): String? =
			e.cause?.let { getRootCauseMessage(it) } ?: e.message
	}
}
