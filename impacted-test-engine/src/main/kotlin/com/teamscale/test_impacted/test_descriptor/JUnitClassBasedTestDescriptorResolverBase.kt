package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import org.junit.platform.engine.TestDescriptor
import java.util.*

/** Test descriptor resolver for JUnit based [org.junit.platform.engine.TestEngine]s.  */
abstract class JUnitClassBasedTestDescriptorResolverBase : ITestDescriptorResolver {
	companion object {
		private val LOG = createLogger()
	}

	override fun getUniformPath(descriptor: TestDescriptor): Optional<String> =
		descriptor.getClassName().map { className ->
			val dotName = className.replace(".", "/")
			"$dotName/${descriptor.legacyReportingName.trim { it <= ' ' }}"
		}

	override fun getClusterId(descriptor: TestDescriptor): Optional<String> {
		val classSegmentName = descriptor.getClassName()

		if (!classSegmentName.isPresent) {
			LOG.severe {
				"Could not determine a class name for test descriptor '${descriptor.displayName}';" +
						" using its unique ID as the impact-analysis cluster ID." +
						" Tests in this descriptor may be over-selected for execution."
			}
			// Default to uniform path.
			return Optional.of(descriptor.uniqueId.toString())
		}

		return classSegmentName
	}

	/** Returns the test class containing the test.  */
	protected abstract fun TestDescriptor.getClassName(): Optional<String>
}
