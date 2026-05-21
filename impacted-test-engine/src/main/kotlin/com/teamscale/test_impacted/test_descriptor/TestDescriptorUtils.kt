package com.teamscale.test_impacted.test_descriptor

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.test_impacted.commons.IndentingWriter
import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import com.teamscale.test_impacted.engine.executor.AvailableTests
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import java.util.*
import java.util.stream.Stream

/** Class containing utility methods for [TestDescriptor]s.  */
object TestDescriptorUtils {
	private val LOG = createLogger()

	/** Returns the test descriptor as a formatted string with indented children.  */
	fun getTestDescriptorAsString(testDescriptor: TestDescriptor): String {
		val writer = IndentingWriter()
		writer.printTestDescriptor(testDescriptor)
		return writer.toString()
	}

	private fun IndentingWriter.printTestDescriptor(testDescriptor: TestDescriptor) {
		writeLine(testDescriptor.uniqueId.toString())
		indent {
			testDescriptor.children.forEach { child ->
				printTestDescriptor(child)
			}
		}
	}

	/**
	 * Returns true if the [TestDescriptor] is an actual representative of a test. A representative of a test is
	 * either a regular test that was not dynamically generated or a test container that dynamically registers multiple
	 * test cases.
	 */
	fun TestDescriptor.isRepresentative(): Boolean {
		val isTestTemplateOrTestFactory = isTestTemplateOrTestFactory()
		val isNonParameterizedTest = isTest && !parent.get().isTestTemplateOrTestFactory()
		return isNonParameterizedTest || isTestTemplateOrTestFactory
	}

	/**
	 * Returns true if a [TestDescriptor] represents a test template or a test factory.
	 *
	 *
	 * An example of a [UniqueId] of the [TestDescriptor] is:
	 *
	 *
	 * `[engine:junit-jupiter]/[class:com.example.project.JUnit5Test]/[test-template:withValueSource(java.lang.String)]`
	 */
	private fun TestDescriptor.isTestTemplateOrTestFactory(): Boolean {
		val segments = uniqueId.segments

		if (segments.isEmpty()) {
			return false
		}

		val lastSegmentType = segments[segments.size - 1].type
		return JUnitJupiterTestDescriptorResolver.TEST_TEMPLATE_SEGMENT_TYPE == lastSegmentType
				|| JUnitJupiterTestDescriptorResolver.TEST_FACTORY_SEGMENT_TYPE == lastSegmentType
	}

	/** Creates a stream of the test representatives contained by the [TestDescriptor].  */
	private fun TestDescriptor.streamTestRepresentatives(): Stream<TestDescriptor> {
		if (isRepresentative()) {
			return Stream.of(this)
		}
		return children.stream().flatMap {
			it.streamTestRepresentatives()
		}
	}

	/**
	 * Returns the [org.junit.platform.engine.UniqueId.Segment.getValue] matching the type or [Optional.empty] if no matching segment can
	 * be found.
	 */
	fun TestDescriptor.getUniqueIdSegment(type: String): Optional<String> =
		uniqueId.segments.stream()
			.filter { it.type == type }
			.findFirst().map { it.value }

	/** Returns [com.teamscale.client.TestDetails.sourcePath] for a [TestDescriptor].  */
	private fun TestDescriptor.source(): String? {
		val source = source.orElse(null) ?: return null
		return when (source) {
			is MethodSource -> source.className.replace('.', '/')
			is ClassSource -> source.className.replace('.', '/')
			else -> null
		}
	}

	/** Returns the [AvailableTests] contained within the root [TestDescriptor].  */
	fun getAvailableTests(
		rootTestDescriptor: TestDescriptor
	): AvailableTests {
		val availableTests = AvailableTests()

		rootTestDescriptor.streamTestRepresentatives()
			.forEach { testDescriptor ->
				val engineId = testDescriptor.uniqueId.engineId
				if (!engineId.isPresent) {
					LOG.severe {
						"Could not determine the JUnit engine for test descriptor '${testDescriptor.displayName}'." +
								" This test will not be considered for impact analysis."
					}
					return@forEach
				}

				val testDescriptorResolver = TestDescriptorResolverRegistry.getTestDescriptorResolver(engineId.get())
				val clusterId = testDescriptorResolver!!.getClusterId(testDescriptor)
				val uniformPath = testDescriptorResolver.getUniformPath(testDescriptor)

				if (!uniformPath.isPresent) {
					LOG.severe {
						"Could not determine a uniform path for test descriptor '${testDescriptor.displayName}'." +
								" This test will be skipped during impact analysis."
					}
					return@forEach
				}

				if (!clusterId.isPresent) {
					LOG.severe {
						"Could not determine an impact-analysis cluster ID for test descriptor '${testDescriptor.displayName}'." +
								" This test will be skipped during impact analysis."
					}
					return@forEach
				}

				val testDetails = ClusteredTestDetails(
					uniformPath.get(),
					testDescriptor.source(),
					null,
					clusterId.get()
				)
				availableTests.add(testDescriptor.uniqueId, testDetails)
			}


		return availableTests
	}
}
