package com.teamscale.test_impacted.test_descriptor

import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId

/**
 * Test descriptor resolver for JUnit Platform Suite test (c.f.
 * https://junit.org/junit5/docs/current/user-guide/#junit-platform-suite-engine)
 */
class JUnitPlatformSuiteDescriptorResolver : ITestDescriptorResolver {
	override fun getUniformPath(descriptor: TestDescriptor) =
		descriptor.extractUniformPathOrClusterId("uniform path") {
			it.getUniformPath(descriptor)
		}

	override fun getClusterId(descriptor: TestDescriptor) =
		descriptor.extractUniformPathOrClusterId("cluster id") {
			it.getClusterId(descriptor)
		}

	override val engineId: String
		get() = "junit-platform-suite"

	companion object {
		private val LOG = createLogger()

		/** Type of the unique id segment of a test descriptor representing a test suite  */
		private const val SUITE_SEGMENT_TYPE: String = "suite"

		private fun TestDescriptor.extractUniformPathOrClusterId(
			nameOfValueToExtractForLogs: String,
			uniformPathOrClusterIdExtractor: (ITestDescriptorResolver) -> String?
		): String? {
			val segments = uniqueId.segments
			if (verifySegments(segments)) {
				LOG.severe {
					"Assuming structure [engine:junit-platform-suite]/[suite:mySuite]/[engine:anotherEngine] for junit-platform-suite tests. Using $uniqueId as $nameOfValueToExtractForLogs as fallback."
				}
				return uniqueId.toString()
			}

			val suite = segments[1].value.replace('.', '/')
			val secondaryEngineSegments = segments.subList(2, segments.size)

			val descriptorResolver = TestDescriptorResolverRegistry.getTestDescriptorResolver(
				secondaryEngineSegments.first().value
			)
			if (descriptorResolver == null) {
				LOG.severe {
					"Cannot find a secondary engine nested under the junit-platform-suite engine (assuming structure [engine:junit-platform-suite]/[suite:mySuite]/[engine:anotherEngine]). Using $uniqueId as $nameOfValueToExtractForLogs as fallback."
				}
				return uniqueId.toString()
			}

			val idOrUniformPath = uniformPathOrClusterIdExtractor(descriptorResolver)
			if (idOrUniformPath == null) {
				LOG.severe {
					"Secondary test descriptor resolver for engine ${secondaryEngineSegments.first().value} was not able to resolve the $nameOfValueToExtractForLogs. Using $uniqueId as fallback."
				}
				return uniqueId.toString()
			}

			return "$suite/$idOrUniformPath"
		}

		private fun verifySegments(segments: List<UniqueId.Segment>) =
			segments.size < 3
					|| (segments[0].type != ITestDescriptorResolver.ENGINE_SEGMENT_TYPE)
					|| (segments[1].type != SUITE_SEGMENT_TYPE)
					|| (segments[2].type != ITestDescriptorResolver.ENGINE_SEGMENT_TYPE)
	}
}
