package com.teamscale.jacoco.agent.testimpact

import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.JacocoRuntimeController.DumpException
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestInfo
import com.teamscale.report.testwise.model.builder.TestInfoBuilder

/**
 * Strategy which directly converts the collected coverage into a JSON object in place and returns the result to the
 * caller as response to the http request. If a test execution is given it is merged into the representation and
 * returned together with the coverage.
 */
class CoverageViaHttpStrategy(
	controller: JacocoRuntimeController, agentOptions: AgentOptions,
	private val reportGenerator: JaCoCoTestwiseReportGenerator
) : TestEventHandlerStrategyBase(agentOptions, controller) {
	private val logger = getLogger(this)

	@Throws(DumpException::class, CoverageGenerationException::class)
	override fun testEnd(test: String, testExecution: TestExecution?): TestInfo {
		super.testEnd(test, testExecution)

		val builder = TestInfoBuilder(test)
		val dump = controller.dumpAndReset()
		reportGenerator.updateClassDirCache()
		reportGenerator.convert(dump)?.let { builder.setCoverage(it) }
		if (testExecution != null) {
			builder.setExecution(testExecution)
		}
		val testInfo = builder.build()
		logger.debug("Generated test info {}", testInfo)
		return testInfo
	}
}
