package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.FileSystemUtils.writeFileUTF8
import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import java.io.IOException

/**
 * Strategy for appending coverage into one json test-wise coverage file with one session per test.
 */
class CoverageToDiskStrategy(
	controller: JacocoRuntimeController, agentOptions: AgentOptions,
	reportGenerator: JaCoCoTestwiseReportGenerator
) : CoverageToJsonStrategyBase(controller, agentOptions, reportGenerator) {
	@Throws(IOException::class)
	override fun handleTestwiseCoverageJsonReady(json: String) {
		val reportFile = agentOptions.createNewFileInPartitionOutputDirectory("testwise-coverage", "json")
		writeFileUTF8(reportFile, json)
	}
}
