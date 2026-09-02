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
		val isNonParameterizedTest = isTest && parent.orElse(null)?.isTestTemplateOrTestFactory() != true
		return isNonParameterizedTest || isTestTemplateOrTestFactory
	}

	/**
	 * Returns true if a [TestDescriptor] represents a `@ParameterizedClass`.
	 *
	 * An example of a [org.junit.platform.engine.UniqueId] of such a [TestDescriptor] is:
	 *
	 *
	 * `[engine:junit-jupiter]/[class:com.example.project.JUnit5Test]/[nested-class-template:WithValueSource]`
	 */
	fun TestDescriptor.isClassTemplate(): Boolean {
		val lastSegmentType = uniqueId.segments.lastOrNull()?.type ?: return false
		return JUnitJupiterTestDescriptorResolver.CLASS_TEMPLATE_SEGMENT_TYPE == lastSegmentType
				|| JUnitJupiterTestDescriptorResolver.NESTED_CLASS_TEMPLATE_SEGMENT_TYPE == lastSegmentType
	}

	/**
	 * Returns true if a [TestDescriptor] is one of the invocations of a `@ParameterizedClass` or is contained in one.
	 * These are only registered dynamically during test execution.
	 */
	fun TestDescriptor.isInsideClassTemplate() =
		uniqueId.segments.any {
			it.type == JUnitJupiterTestDescriptorResolver.CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE
		}

	/**
	 * Returns true if a [TestDescriptor] represents a test template or a test factory.
	 *
	 * An example of a [org.junit.platform.engine.UniqueId] of the [TestDescriptor] is:
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

	/**
	 * Creates a stream of the test representatives contained by the [TestDescriptor], each together with the
	 * [UniqueId] that has to be selected in order to execute it.
	 *
	 * Both are the same for an ordinary test. The tests of a `@ParameterizedClass` are taken from the
	 * [ClassTemplateRegistry], because the JUnit platform pruned them from the test tree, and all of them are selected
	 * via the class template itself, since JUnit can only execute a `@ParameterizedClass` as a whole.
	 */
	private fun TestDescriptor.streamTestRepresentatives(
		classTemplateRegistry: ClassTemplateRegistry,
		selectionId: UniqueId?
	): Stream<Pair<TestDescriptor, UniqueId>> {
		if (isClassTemplate()) {
			return classTemplateRegistry.testsOf(this).stream().flatMap {
				it.streamTestRepresentatives(classTemplateRegistry, selectionId ?: uniqueId)
			}
		}
		if (isRepresentative()) {
			return Stream.of(this to (selectionId ?: uniqueId))
		}
		return children.stream().flatMap {
			it.streamTestRepresentatives(classTemplateRegistry, selectionId)
		}
	}

	/**
	 * Returns the [org.junit.platform.engine.UniqueId.Segment.getValue] matching the type or [Optional.empty] if no matching segment can
	 * be found.
	 */
	fun TestDescriptor.getUniqueIdSegment(type: String): Optional<String> =
		Optional.ofNullable(uniqueId.segments.firstOrNull { it.type == type }?.value)

	/** Returns [com.teamscale.client.TestDetails.sourcePath] for a [TestDescriptor].  */
	private fun TestDescriptor.source(): String? {
		val source = source.orElse(null) ?: return null
		return when (source) {
			is MethodSource -> source.className.replace('.', '/')
			is ClassSource -> source.className.replace('.', '/')
			else -> null
		}
	}

	/**
	 * Returns the [AvailableTests] contained within the root [TestDescriptor], taking the tests of the
	 * `@ParameterizedClass`es from the given [ClassTemplateRegistry] because the JUnit platform pruned them from the
	 * test tree.
	 */
	fun getAvailableTests(
		rootTestDescriptor: TestDescriptor,
		classTemplateRegistry: ClassTemplateRegistry
	): AvailableTests {
		val availableTests = AvailableTests()

		rootTestDescriptor.streamTestRepresentatives(classTemplateRegistry, null)
			.forEach { (testDescriptor, selectionId) ->
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

				if (uniformPath == null) {
					LOG.severe {
						"Could not determine a uniform path for test descriptor '${testDescriptor.displayName}'." +
								" This test will be skipped during impact analysis."
					}
					return@forEach
				}

				if (clusterId == null) {
					LOG.severe {
						"Could not determine an impact-analysis cluster ID for test descriptor '${testDescriptor.displayName}'." +
								" This test will be skipped during impact analysis."
					}
					return@forEach
				}

				val testDetails = ClusteredTestDetails(
					uniformPath,
					testDescriptor.source(),
					null,
					clusterId
				)
				availableTests.add(selectionId, testDetails)
			}


		return availableTests
	}
}
