package com.teamscale.test_impacted.engine.executor

import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.test_impacted.test_descriptor.ClassTemplateRegistry
import com.teamscale.test_impacted.test_descriptor.ITestDescriptorResolver
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.CLASS_TEMPLATE_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.METHOD_SEGMENT_TYPE
import com.teamscale.test_impacted.test_descriptor.JUnitJupiterTestDescriptorResolver.Companion.TEST_FACTORY_SEGMENT_TYPE
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.engine.descriptor.TestFactoryTestDescriptor.DYNAMIC_CONTAINER_SEGMENT_TYPE
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.mockito.kotlin.*

/** Tests for [TestwiseCoverageCollectingExecutionListener].  */
internal class TestwiseCoverageCollectingExecutionListenerTest {
	private val mockApi = mock<TeamscaleAgentNotifier>()
	private val resolver = mock<ITestDescriptorResolver>()
	private val executionListenerMock = mock<EngineExecutionListener>()

	private val executionListener = TestwiseCoverageCollectingExecutionListener(
		mockApi, resolver, executionListenerMock, ClassTemplateRegistry()
	)

	private val rootId = UniqueId.forEngine("dummy")

	@Test
	fun testInteractionWithListenersAndCoverageApi() {
		val testClassId = rootId.append("TEST_CONTAINER", "MyClass")
		val impactedTestCaseId = testClassId.append("TEST_CASE", "impactedTestCase()")
		val regularSkippedTestCaseId = testClassId.append("TEST_CASE", "regularSkippedTestCase()")

		val impactedTestCase = SimpleTestDescriptor.testCase(impactedTestCaseId)
		val regularSkippedTestCase = SimpleTestDescriptor.testCase(regularSkippedTestCaseId)
		val testClass = SimpleTestDescriptor.testContainer(
			testClassId, impactedTestCase,
			regularSkippedTestCase
		)
		val testRoot = SimpleTestDescriptor.testContainer(rootId, testClass)

		whenever(resolver.getUniformPath(impactedTestCase))
			.thenReturn("MyClass/impactedTestCase()")
		whenever(resolver.getClusterId(impactedTestCase))
			.thenReturn("MyClass")
		whenever(resolver.getUniformPath(regularSkippedTestCase))
			.thenReturn("MyClass/regularSkippedTestCase()")
		whenever(resolver.getClusterId(regularSkippedTestCase))
			.thenReturn("MyClass")

		// Start engine and class.
		executionListener.executionStarted(testRoot)
		verify(executionListenerMock).executionStarted(testRoot)
		executionListener.executionStarted(testClass)
		verify(executionListenerMock).executionStarted(testClass)

		// Execution of an impacted test case.
		executionListener.executionStarted(impactedTestCase)
		verify(mockApi).startTest("MyClass/impactedTestCase()")
		verify(executionListenerMock).executionStarted(impactedTestCase)
		executionListener.executionFinished(impactedTestCase, TestExecutionResult.successful())
		verify(mockApi).endTest(eq("MyClass/impactedTestCase()"), any())
		verify(executionListenerMock).executionFinished(impactedTestCase, TestExecutionResult.successful())

		// Ignored or disabled impacted test case is skipped.
		executionListener.executionSkipped(regularSkippedTestCase, "Test is disabled.")
		verify(executionListenerMock).executionSkipped(regularSkippedTestCase, "Test is disabled.")

		// Finish class and engine.
		executionListener.executionFinished(testClass, TestExecutionResult.successful())
		verify(executionListenerMock).executionFinished(testClass, TestExecutionResult.successful())
		executionListener.executionFinished(testRoot, TestExecutionResult.successful())
		verify(executionListenerMock).executionFinished(testRoot, TestExecutionResult.successful())

		verifyNoMoreInteractions(mockApi)
		verifyNoMoreInteractions(executionListenerMock)

		val testExecutions = executionListener.testExecutions

		Assertions.assertThat(testExecutions).hasSize(2)
		Assertions.assertThat(testExecutions).anySatisfy { testExecution: TestExecution ->
			Assertions.assertThat(testExecution.uniformPath).isEqualTo("MyClass/impactedTestCase()")
		}
		Assertions.assertThat(testExecutions).anySatisfy { testExecution: TestExecution ->
			Assertions.assertThat(testExecution.uniformPath).isEqualTo("MyClass/regularSkippedTestCase()")
		}
	}

	@Test
	fun testSkipOfTestClass() {
		val testClassId = rootId.append("TEST_CONTAINER", "MyClass")
		val testCase1Id = testClassId.append("TEST_CASE", "testCase1()")
		val testCase2Id = testClassId.append("TEST_CASE", "testCase2()")

		val testCase1 = SimpleTestDescriptor.testCase(testCase1Id)
		val testCase2 = SimpleTestDescriptor.testCase(testCase2Id)
		val testClass = SimpleTestDescriptor.testContainer(testClassId, testCase1, testCase2)
		val testRoot = SimpleTestDescriptor.testContainer(rootId, testClass)

		whenever(resolver.getUniformPath(testCase1))
			.thenReturn("MyClass/testCase1()")
		whenever(resolver.getClusterId(testCase1))
			.thenReturn("MyClass")
		whenever(resolver.getUniformPath(testCase2))
			.thenReturn("MyClass/testCase2()")
		whenever(resolver.getClusterId(testCase2))
			.thenReturn("MyClass")

		// Start engine and class.
		executionListener.executionStarted(testRoot)
		verify(executionListenerMock).executionStarted(testRoot)

		executionListener.executionSkipped(testClass, "Test class is disabled.")
		verify(executionListenerMock).executionStarted(testClass)
		verify(executionListenerMock).executionSkipped(testCase1, "Test class is disabled.")
		verify(executionListenerMock).executionSkipped(testCase2, "Test class is disabled.")
		verify(executionListenerMock).executionFinished(testClass, TestExecutionResult.successful())

		executionListener.executionFinished(testRoot, TestExecutionResult.successful())
		verify(executionListenerMock).executionFinished(testRoot, TestExecutionResult.successful())

		verifyNoMoreInteractions(executionListenerMock)

		val testExecutions = executionListener.testExecutions

		Assertions.assertThat(testExecutions).hasSize(2)
		Assertions.assertThat(testExecutions)
			.allMatch { it.result == ETestExecutionResult.SKIPPED }
	}

	/**
	 * A `@ParameterizedClass` dynamically registers one invocation per parameter set, each containing all test methods
	 * of the class. Every method is one test, so the agent has to see one test per method and parameter set, all
	 * reported under the method's uniform path without the invocation index, so that the coverage of all parameter
	 * sets ends up on the same test.
	 */
	@Test
	fun testParameterizedClassIsReportedPerTestMethod() {
		val jupiterRootId = UniqueId.forEngine("junit-jupiter")
		val classTemplateId = jupiterRootId.append(CLASS_TEMPLATE_SEGMENT_TYPE, "example.ParameterizedTest")
		val classTemplate = SimpleTestDescriptor.testContainer(classTemplateId)
		val testRoot = SimpleTestDescriptor.testContainer(jupiterRootId, classTemplate)

		val listener = jupiterListener()
		simulateClassTemplateExecution(listener, testRoot, classTemplate, createInvocations(classTemplateId))

		// Both parameter sets report the same uniform path, so that their coverage lands on the same test.
		verify(mockApi, times(2)).startTest("example/ParameterizedTest/testA()")
		verify(mockApi, times(2)).startTest("example/ParameterizedTest/testB()")
		verify(mockApi, times(2)).endTest(eq("example/ParameterizedTest/testA()"), any())
		verify(mockApi, times(2)).endTest(eq("example/ParameterizedTest/testB()"), any())
		verifyNoMoreInteractions(mockApi)

		// Every test method of every parameter set is reported with the result that its parameter set produced.
		Assertions.assertThat(listener.testExecutions.map { it.uniformPath to it.result })
			.containsExactly(
				"example/ParameterizedTest/testA()" to ETestExecutionResult.FAILURE,
				"example/ParameterizedTest/testB()" to ETestExecutionResult.PASSED,
				"example/ParameterizedTest/testA()" to ETestExecutionResult.PASSED,
				"example/ParameterizedTest/testB()" to ETestExecutionResult.PASSED
			)
		Assertions.assertThat(listener.testExecutions.first().message).contains("expected")
	}

	/**
	 * The results that the two simulated parameter sets report for the test methods of the `@ParameterizedClass`, in
	 * the order in which the methods are executed.
	 */
	private val resultsPerParameterSet = listOf(
		listOf("testA()" to FAILED_RESULT, "testB()" to TestExecutionResult.successful()),
		listOf("testA()" to TestExecutionResult.successful(), "testB()" to TestExecutionResult.successful())
	)

	/** Creates one invocation per parameter set, each containing the test methods of [resultsPerParameterSet]. */
	private fun createInvocations(classTemplateId: UniqueId) =
		resultsPerParameterSet.mapIndexed { index, results ->
			val invocationIndex = index + 1
			val invocationId = classTemplateId.append(CLASS_TEMPLATE_INVOCATION_SEGMENT_TYPE, "#$invocationIndex")
			val methods = results.map { (methodName, result) ->
				// The jupiter engine appends the index of the enclosing invocation to the reporting name.
				SimpleTestDescriptor.testCase(
					invocationId.append(METHOD_SEGMENT_TYPE, methodName), "$methodName[$invocationIndex]"
				).result(result)
			}
			SimpleTestDescriptor.testContainer(invocationId, *methods.toTypedArray())
		}

	/** Simulates the execution of the given invocations of a `@ParameterizedClass`. */
	private fun simulateClassTemplateExecution(
		listener: TestwiseCoverageCollectingExecutionListener,
		testRoot: SimpleTestDescriptor,
		classTemplate: SimpleTestDescriptor,
		invocations: List<SimpleTestDescriptor>
	) {
		listener.executionStarted(testRoot)
		listener.executionStarted(classTemplate)
		invocations.forEach { invocation ->
			// The invocations and their test methods are only registered while the class template is executing.
			classTemplate.addChild(invocation)
			listener.dynamicTestRegistered(invocation)
			listener.executionStarted(invocation)
			simulateInvocationExecution(listener, invocation)
			listener.executionFinished(invocation, TestExecutionResult.successful())
		}
		listener.executionFinished(classTemplate, TestExecutionResult.successful())
		listener.executionFinished(testRoot, TestExecutionResult.successful())
	}

	/** Simulates the execution of all test methods of one invocation of a `@ParameterizedClass`. */
	private fun simulateInvocationExecution(
		listener: TestwiseCoverageCollectingExecutionListener,
		invocation: SimpleTestDescriptor
	) {
		invocation.children.filterIsInstance<SimpleTestDescriptor>().forEach { method ->
			listener.dynamicTestRegistered(method)
			listener.executionStarted(method)
			listener.executionFinished(method, method.executionResult)
		}
	}

	/**
	 * A `@TestFactory` is one test, however deeply its dynamic containers are nested below it. A failure of one of
	 * those containers therefore has to be reported for the factory method itself, and the containers that did not
	 * fail must not contribute any "null" noise to its message.
	 */
	@Test
	fun testFailureInNestedContainerIsReportedForTheTest() {
		val jupiterRootId = UniqueId.forEngine("junit-jupiter")
		val testClassId = jupiterRootId.append(CLASS_SEGMENT_TYPE, "example.FactoryTest")
		val testFactoryId = testClassId.append(TEST_FACTORY_SEGMENT_TYPE, "tests()")
		val outerContainerId = testFactoryId.append(DYNAMIC_CONTAINER_SEGMENT_TYPE, "#1")

		val innerContainer = SimpleTestDescriptor.testContainer(
			outerContainerId.append(DYNAMIC_CONTAINER_SEGMENT_TYPE, "#1")
		)
		val outerContainer = SimpleTestDescriptor.testContainer(outerContainerId, innerContainer)
		val testFactory = SimpleTestDescriptor.testContainer(testFactoryId, outerContainer)
		val testClass = SimpleTestDescriptor.testContainer(testClassId, testFactory)
		val testRoot = SimpleTestDescriptor.testContainer(jupiterRootId, testClass)

		val listener = jupiterListener()
		listOf(testRoot, testClass, testFactory, outerContainer, innerContainer)
			.forEach { listener.executionStarted(it) }
		listener.executionFinished(innerContainer, FAILED_RESULT)
		listOf(outerContainer, testFactory, testClass, testRoot)
			.forEach { listener.executionFinished(it, TestExecutionResult.successful()) }

		verify(mockApi).startTest("example/FactoryTest/tests()")
		verify(mockApi).endTest(eq("example/FactoryTest/tests()"), any())
		verifyNoMoreInteractions(mockApi)

		Assertions.assertThat(listener.testExecutions.map { it.uniformPath to it.result })
			.containsExactly("example/FactoryTest/tests()" to ETestExecutionResult.FAILURE)
		// Only the failed container contributes to the message, the successful ones have no stacktrace to report.
		Assertions.assertThat(listener.testExecutions.single().message)
			.contains("expected")
			.doesNotContain("null")
	}

	/** The tests of a skipped `@ParameterizedClass` are no longer in the test tree, but must still be reported. */
	@Test
	fun testSkipOfParameterizedClass() {
		val jupiterRootId = UniqueId.forEngine("junit-jupiter")
		val classTemplateId = jupiterRootId.append(CLASS_TEMPLATE_SEGMENT_TYPE, "example.ParameterizedTest")

		val recordedTests = listOf("testA()", "testB()").map {
			SimpleTestDescriptor.testCase(classTemplateId.append(METHOD_SEGMENT_TYPE, it))
		}
		val classTemplate = SimpleTestDescriptor.testContainer(classTemplateId, *recordedTests.toTypedArray())
		val registry = ClassTemplateRegistry().apply { record(classTemplate) }
		// The JUnit platform prunes the tests of the class template away before it is executed.
		recordedTests.forEach { classTemplate.removeChild(it) }

		val listener = jupiterListener(registry)

		listener.executionSkipped(classTemplate, "Test class is disabled.")

		verify(executionListenerMock).executionSkipped(classTemplate, "Test class is disabled.")
		verifyNoMoreInteractions(executionListenerMock)
		verifyNoMoreInteractions(mockApi)

		Assertions.assertThat(listener.testExecutions)
			.extracting<String> { it.uniformPath }
			.containsExactly("example/ParameterizedTest/testA()", "example/ParameterizedTest/testB()")
		Assertions.assertThat(listener.testExecutions)
			.allMatch { it.result == ETestExecutionResult.SKIPPED }
	}

	/** Creates a listener that resolves the uniform paths of the jupiter engine's test descriptors. */
	private fun jupiterListener(classTemplateRegistry: ClassTemplateRegistry = ClassTemplateRegistry()) =
		TestwiseCoverageCollectingExecutionListener(
			mockApi, JUnitJupiterTestDescriptorResolver(), executionListenerMock, classTemplateRegistry
		)

	companion object {
		/** The result reported for the executions that are meant to fail in a simulation. */
		private val FAILED_RESULT = TestExecutionResult.failed(AssertionError("expected"))
	}
}
