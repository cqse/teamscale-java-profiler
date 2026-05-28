package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import org.junit.platform.engine.TestDescriptor

/** Test descriptor resolver for JUnit based [org.junit.platform.engine.TestEngine]s.  */
abstract class JUnitClassBasedTestDescriptorResolverBase : ITestDescriptorResolver {
	companion object {
		private val LOG = createLogger()
	}

	override fun getUniformPath(descriptor: TestDescriptor): String? =
		descriptor.getClassName()?.let { className ->
			val dotName = className.replace(".", "/")
			"$dotName/${descriptor.legacyReportingName.trim { it <= ' ' }}"
		}

	override fun getClusterId(descriptor: TestDescriptor): String? {
		val classSegmentName = descriptor.getClassName()

		if (classSegmentName == null) {
			LOG.severe {
				"Falling back to unique ID as cluster id because class segment name could not be determined for test descriptor: $descriptor"
			}
			return descriptor.uniqueId.toString()
		}

		return classSegmentName
	}

	/** Returns the test class containing the test.  */
	protected abstract fun TestDescriptor.getClassName(): String?
}
