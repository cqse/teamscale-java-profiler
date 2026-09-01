package com.teamscale.test_impacted.test_descriptor

import com.teamscale.client.PrioritizableTest
import com.teamscale.test_impacted.engine.executor.SimpleTestDescriptor
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.METHOD_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.NESTED_CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.NESTED_CLASS_TEMPLATE_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.TestDescriptorUtils.getAvailableTests
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.engine.UniqueId

/** Tests for [TestDescriptorUtils]. */
internal class TestDescriptorUtilsTest {

	private val engineId = UniqueId.forEngine("junit-jupiter")

	private val outerClassId = engineId.append(CLASS_SEGMENT_TYPE, "example.OuterTest")
	private val plainNestedClassId = outerClassId.append(NESTED_CLASS_SEGMENT_TYPE, "PlainNested")
	private val nestedClassTemplateId = outerClassId.append(NESTED_CLASS_TEMPLATE_SEGMENT_TYPE, "NestedParameterized")
	private val classTemplateId = engineId.append(CLASS_TEMPLATE_SEGMENT_TYPE, "example.ParameterizedTest")

	/** The test tree as it looks during discovery, i.e. before the JUnit platform prunes it. */
	private val testRoot = SimpleTestDescriptor.testContainer(
		engineId,
		SimpleTestDescriptor.testContainer(
			outerClassId,
			SimpleTestDescriptor.testCase(outerClassId.append(METHOD_SEGMENT_TYPE, "testOuter()")),
			SimpleTestDescriptor.testContainer(
				plainNestedClassId,
				SimpleTestDescriptor.testCase(plainNestedClassId.append(METHOD_SEGMENT_TYPE, "testPlain()"))
			),
			SimpleTestDescriptor.testContainer(
				nestedClassTemplateId,
				SimpleTestDescriptor.testCase(nestedClassTemplateId.append(METHOD_SEGMENT_TYPE, "testOne()")),
				SimpleTestDescriptor.testCase(nestedClassTemplateId.append(METHOD_SEGMENT_TYPE, "testTwo()"))
			)
		),
		SimpleTestDescriptor.testContainer(
			classTemplateId,
			SimpleTestDescriptor.testCase(classTemplateId.append(METHOD_SEGMENT_TYPE, "testA()"))
		)
	)

	/** The tests that the engine recorded while the test tree was still complete. */
	private val registry = ClassTemplateRegistry().apply { record(testRoot) }

	/**
	 * Simulates the pruning that the JUnit platform applies to a `@ParameterizedClass` after discovery. Must not be
	 * named `prune`, because [org.junit.platform.engine.TestDescriptor.prune] would shadow it.
	 */
	private fun simulatePlatformPruning() {
		listOf(nestedClassTemplateId, classTemplateId).forEach { classTemplateId ->
			testRoot.findByUniqueId(classTemplateId).get().let { classTemplate ->
				classTemplate.children.toList().forEach { classTemplate.removeChild(it) }
			}
		}
	}

	/**
	 * The tests of a `@ParameterizedClass` are pruned from the test tree after discovery, so they have to be taken
	 * from the [ClassTemplateRegistry] which recorded them while they were still there.
	 */
	@Test
	fun testParameterizedClassTestsAreAvailableTests() {
		simulatePlatformPruning()

		assertThat(getAvailableTests(testRoot, registry).testList)
			.extracting<String> { it.uniformPath }
			.containsExactlyInAnyOrder(
				"example/OuterTest/testOuter()",
				"example/OuterTest\$PlainNested/testPlain()",
				"example/OuterTest\$NestedParameterized/testOne()",
				"example/OuterTest\$NestedParameterized/testTwo()",
				"example/ParameterizedTest/testA()"
			)
	}

	/** Without the recorded tests, everything below a pruned `@ParameterizedClass` is lost. */
	@Test
	fun testPrunedParameterizedClassTestsAreLostWithoutRecording() {
		simulatePlatformPruning()

		assertThat(getAvailableTests(testRoot, ClassTemplateRegistry()).testList)
			.extracting<String> { it.uniformPath }
			.containsExactlyInAnyOrder(
				"example/OuterTest/testOuter()",
				"example/OuterTest\$PlainNested/testPlain()"
			)
	}

	/** The cluster ID of the tests of a `@ParameterizedClass` is their class, just like for any other test. */
	@Test
	fun testParameterizedClassClusterId() {
		simulatePlatformPruning()

		assertThat(getAvailableTests(testRoot, registry).testList)
			.filteredOn { it.uniformPath.startsWith("example/ParameterizedTest") }
			.extracting<String> { it.clusterId }
			.containsExactly("example.ParameterizedTest")
	}

	/**
	 * JUnit can only execute a `@ParameterizedClass` as a whole, so each of its tests has to be selected via the class
	 * template. Otherwise the impacted tests returned by Teamscale cannot be found in the pruned test tree and the
	 * engine falls back to executing all tests.
	 */
	@Test
	fun testParameterizedClassTestsAreSelectedViaTheirClass() {
		simulatePlatformPruning()

		val availableTests = getAvailableTests(testRoot, registry)

		listOf("example/ParameterizedTest/testA()", "example/OuterTest\$NestedParameterized/testOne()")
			.forEach { uniformPath ->
				val uniqueId = availableTests.convertToUniqueId(PrioritizableTest(uniformPath))
				assertThat(uniqueId).isPresent()
				assertThat(testRoot.findByUniqueId(uniqueId.get())).isPresent()
			}
	}

	/** As long as nothing was pruned, the tests of a `@ParameterizedClass` are found in the test tree itself. */
	@Test
	fun testParameterizedClassTestsAreFoundWithoutRecording() {
		assertThat(getAvailableTests(testRoot, ClassTemplateRegistry()).testList)
			.extracting<String> { it.uniformPath }
			.contains("example/ParameterizedTest/testA()")
	}
}
