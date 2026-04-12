package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.EReportFormat
import com.teamscale.client.FileSystemUtils.writeFileUTF8
import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import java.io.IOException

/**
 * Strategy that records test-wise coverage and uploads the resulting report to Teamscale. Also handles the
 * [.testRunStart] event by retrieving tests to run from
 * Teamscale.
 */
class CoverageToTeamscaleStrategy(
	controller: JacocoRuntimeController, agentOptions: AgentOptions,
	reportGenerator: JaCoCoTestwiseReportGenerator
) : CoverageToJsonStrategyBase(controller, agentOptions, reportGenerator) {
	@Throws(IOException::class)
	override fun handleTestwiseCoverageJsonReady(json: String) {
		try {
			teamscaleClient?.uploadReport(
					EReportFormat.TESTWISE_COVERAGE, json,
					agentOptions.teamscaleServer.commit,
					agentOptions.teamscaleServer.revision,
					agentOptions.teamscaleServer.repository,
				agentOptions.teamscaleServer.partition!!,
				agentOptions.teamscaleServer.message!!
			)
		} catch (e: IOException) {
			val reportFile = agentOptions.createNewFileInOutputDirectory("testwise-coverage", "json")
			writeFileUTF8(reportFile, json)
			val errorMessage = "Failed to upload coverage to Teamscale! Report is stored in $reportFile!"
			logger.error(errorMessage, e)
			throw IOException(errorMessage, e)
		}
	}
}
