package com.teamscale.test_impacted.engine

import com.teamscale.client.PrioritizableTest
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.test_impacted.engine.executor.DummyEngine
import com.teamscale.test_impacted.engine.executor.SimpleTestDescriptor
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.METHOD_SEGMENT_TYPE
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.mockito.kotlin.verify

/**
 * Test setup for a JUnit Jupiter `@ParameterizedClass`. Its tests are visible during discovery, then pruned from the
 * test tree by the JUnit platform, and finally registered again during execution, once per parameter set. Each of them
 * is one test, but they can only be selected via the class template, since JUnit executes the class as a whole.
 *
 * The setup contains a second, non-impacted test class, so that the test fails if the engine cannot map the impacted
 * test back to a local one and therefore falls back to executing everything.
 */
internal class ImpactedTestEngineWithParameterizedClassTest : ImpactedTestEngineTestBase() {
	private val engineRootId = UniqueId.forEngine("junit-jupiter")

	private val classTemplateId = engineRootId.append(CLASS_TEMPLATE_SEGMENT_TYPE, "example.ParameterizedTest")

	/** The test as it is visible while the engine discovers the tests. */
	private val discoveredTestCase =
		SimpleTestDescriptor.testCase(classTemplateId.append(METHOD_SEGMENT_TYPE, "testMethod()"))

	private val classTemplate = SimpleTestDescriptor.testContainer(classTemplateId, discoveredTestCase)

	/** A test that is not impacted and must therefore not be executed. */
	private val nonImpactedClassId = engineRootId.append(CLASS_SEGMENT_TYPE, "example.OtherTest")
	private val nonImpactedTestCase =
		SimpleTestDescriptor.testCase(nonImpactedClassId.append(METHOD_SEGMENT_TYPE, "otherTest()"))
	private val nonImpactedClass = SimpleTestDescriptor.testContainer(nonImpactedClassId, nonImpactedTestCase)

	private val testRoot = SimpleTestDescriptor.testContainer(engineRootId, classTemplate, nonImpactedClass)

	/** One invocation per parameter set, each repeating all tests of the class. */
	private val invocations = (1..2).map { invocationIndex ->
		val invocationId = classTemplateId.append(CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE, "#$invocationIndex")
		SimpleTestDescriptor.dynamicTestContainer(
			invocationId,
			SimpleTestDescriptor.testCase(
				invocationId.append(METHOD_SEGMENT_TYPE, "testMethod()"), "testMethod()[$invocationIndex]"
			)
		)
	}

	override fun afterDiscovery() {
		classTemplate.removeChild(discoveredTestCase)
		classTemplate.dynamicTests.addAll(invocations)
	}

	override val engines = listOf(DummyEngine(testRoot))

	override val impactedTests =
		listOf(
			PrioritizableTestCluster(
				"example.ParameterizedTest",
				listOf(PrioritizableTest("example/ParameterizedTest/testMethod()"))
			)
		)

	override fun verifyCallbacks(executionListener: EngineExecutionListener) {
		verify(executionListener).executionStarted(testRoot)
		verify(executionListener).executionStarted(classTemplate)

		invocations.forEach { invocation ->
			verify(executionListener).dynamicTestRegistered(invocation)
			verify(executionListener).executionStarted(invocation)
			invocation.children.forEach { testCase ->
				verify(executionListener).dynamicTestRegistered(testCase)
				verify(executionListener).executionStarted(testCase)
				verify(executionListener).executionFinished(testCase, TestExecutionResult.successful())
			}
			verify(executionListener).executionFinished(invocation, TestExecutionResult.successful())
		}

		verify(executionListener).executionFinished(classTemplate, TestExecutionResult.successful())
		verify(executionListener).executionFinished(testRoot, TestExecutionResult.successful())
	}
}
