package com.teamscale.test_impacted.engine.executor

import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.test_impacted.test_descriptor.ITestDescriptorResolver
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
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
		mockApi, resolver, executionListenerMock
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
}
