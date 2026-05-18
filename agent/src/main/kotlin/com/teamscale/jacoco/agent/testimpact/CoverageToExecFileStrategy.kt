package com.teamscale.jacoco.agent.testimpact

import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.JacocoRuntimeController.DumpException
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestInfo
import java.io.IOException

/**
 * Strategy for appending coverage into one exec file with one session per test. Execution data will be stored in a json
 * file side-by-side with the exec file. Test executions are also appended into a single file.
 */
class CoverageToExecFileStrategy(
	controller: JacocoRuntimeController, agentOptions: AgentOptions,
	/** Helper for writing test executions to disk.  */
	private val testExecutionWriter: TestExecutionWriter?
) : TestEventHandlerStrategyBase(agentOptions, controller) {
	private val logger = getLogger(this)

	@Throws(DumpException::class, CoverageGenerationException::class)
	override fun testEnd(
		test: String,
		testExecution: TestExecution?
	): TestInfo? {
		logger.debug("Test {} ended with execution {}. Writing exec file and test execution", test, testExecution)
		super.testEnd(test, testExecution)
		controller.dump()
		// Ensures that the coverage collected between the last test and the JVM shutdown
		// is not considered a test with the same name as the last test
		controller.resetSessionId()
		if (testExecution != null) {
			try {
				testExecutionWriter?.append(testExecution)
				logger.debug("Successfully wrote test execution for {}", test)
			} catch (e: IOException) {
				logger.error("Failed to store test execution: {}", e.message, e)
			}
		}
		return null
	}
}
