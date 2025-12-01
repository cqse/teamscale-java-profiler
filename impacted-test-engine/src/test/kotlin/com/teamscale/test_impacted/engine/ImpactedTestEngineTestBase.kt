package com.teamscale.test_impacted.engine

import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.test_impacted.engine.executor.ImpactedTestsProvider
import com.teamscale.test_impacted.engine.executor.ImpactedTestsSorter
import com.teamscale.test_impacted.engine.executor.TeamscaleAgentNotifier
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.platform.engine.*
import org.mockito.kotlin.*

/** Base class for testing specific scenarios in the impacted test engine.  */
abstract class ImpactedTestEngineTestBase {
	private val testEngineRegistry = mock<TestEngineRegistry>()
	private val testDataWriter = mock<TestDataWriter>()
	private val impactedTestsProvider = mock<ImpactedTestsProvider>()
	private val discoveryRequest = mock<EngineDiscoveryRequest>()
	private val executionRequest = mock<ExecutionRequest>()
	private val configurationParameters = mock<ConfigurationParameters>()
	private val executionListener = mock<EngineExecutionListener>()
	private val teamscaleAgentNotifier = mock<TeamscaleAgentNotifier>()

	@Test
	fun testEngineExecution() {
		whenever(impactedTestsProvider.getImpactedTestsFromTeamscale(any()))
			.thenReturn(impactedTests)
		whenever(impactedTestsProvider.partition)
			.thenReturn("partition")

		val testEngine = createInternalImpactedTestEngine(engines)

		val engineDescriptor = testEngine
			.discover(discoveryRequest, UniqueId.forEngine(ImpactedTestEngine.ENGINE_ID))
		Assertions.assertThat(engineDescriptor.uniqueId)
			.isEqualTo(UniqueId.forEngine(ImpactedTestEngine.ENGINE_ID))

		whenever(executionRequest.engineExecutionListener)
			.thenReturn(executionListener)
		whenever(executionRequest.rootTestDescriptor)
			.thenReturn(engineDescriptor)
		whenever(executionRequest.configurationParameters)
			.thenReturn(configurationParameters)

		testEngine.execute(executionRequest)

		verifyCallbacks(executionListener)

		// Ensure test data is written.
		verify(testDataWriter).dumpTestDetails(any())
		verify(testDataWriter).dumpTestExecutions(any())

		verifyNoMoreInteractions(executionListener)
		verifyNoMoreInteractions(testDataWriter)
	}

	/** Returns the available engines that should be assumed by the impacted test engine.  */
	abstract val engines: List<TestEngine>

	/** Returns the result that Teamscale should return when asked for impacted tests.  */
	abstract val impactedTests: List<PrioritizableTestCluster>

	/** Verifies that the interactions with the executionListener are the ones we would expect.  */
	abstract fun verifyCallbacks(executionListener: EngineExecutionListener)

	private fun createInternalImpactedTestEngine(engines: List<TestEngine>): InternalImpactedTestEngine {
		engines.forEach { engine ->
			whenever(testEngineRegistry.getTestEngine(eq(engine.id)))
				.thenReturn(engine)
		}
		whenever(testEngineRegistry.iterator()).thenReturn(engines.iterator())

		return InternalImpactedTestEngine(
			ImpactedTestEngineConfiguration(
				testDataWriter, testEngineRegistry,
				ImpactedTestsSorter(impactedTestsProvider),
				teamscaleAgentNotifier
			)
		)
	}
}
