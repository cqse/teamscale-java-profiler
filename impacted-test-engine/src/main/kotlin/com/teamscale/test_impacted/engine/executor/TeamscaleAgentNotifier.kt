package com.teamscale.test_impacted.engine.executor

import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.test_impacted.commons.LoggerUtils.createLogger
import com.teamscale.tia.client.ITestwiseCoverageAgentApi
import com.teamscale.tia.client.UrlUtils.encodeUrl
import java.io.IOException
import java.util.logging.Level

/**
 * A notifier class responsible for communicating with the Teamscale JaCoCo agent in test-wise coverage mode.
 * It sends signals for test start, test end, and test run completion events to the specified APIs.
 *
 * @param testwiseCoverageAgentApis A list of API services used to signal test actions to the Teamscale agent.
 * @param partial Indicates whether only a subset of tests is executed (`true`) or all tests are executed (`false`).
 */
open class TeamscaleAgentNotifier(
	private val testwiseCoverageAgentApis: List<ITestwiseCoverageAgentApi>,
	private val partial: Boolean
) {
	companion object {
		private val LOG = createLogger()
	}

	/** Reports the start of a test to the Teamscale JaCoCo agent.  */
	open fun startTest(testUniformPath: String) {
		LOG.fine { "Starting test notification for: $testUniformPath" }
		try {
			testwiseCoverageAgentApis.forEach { apiService ->
				apiService.testStarted(testUniformPath.encodeUrl()).execute()
			}
			LOG.fine { "Successfully notified test start for: $testUniformPath" }
		} catch (e: IOException) {
			LOG.log(
				Level.SEVERE, e
			) { "Error while calling service api." }
		}
	}

	/** Reports the end of a test to the Teamscale JaCoCo agent.  */
	open fun endTest(testUniformPath: String, testExecution: TestExecution?) {
		LOG.fine { "Ending test notification for: $testUniformPath (execution: ${testExecution != null})" }
		try {
			testwiseCoverageAgentApis.forEach { apiService ->
				val url = testUniformPath.encodeUrl()
				if (testExecution == null) {
					apiService.testFinished(url).execute()
				} else {
					apiService.testFinished(url, testExecution).execute()
				}
			}
			LOG.fine { "Successfully notified test end for: $testUniformPath" }
		} catch (e: IOException) {
			LOG.log(
				Level.SEVERE, e
			) { "Error contacting test wise coverage agent." }
		}
	}

	/** Reports the end of the test run to the Teamscale JaCoCo agent.  */
	open fun testRunEnded() {
		LOG.fine { "Notifying test run ended (partial: $partial)" }
		try {
			testwiseCoverageAgentApis.forEach { apiService ->
				apiService.testRunFinished(partial).execute()
			}
			LOG.fine { "Successfully notified test run ended" }
		} catch (e: IOException) {
			LOG.log(
				Level.SEVERE, e
			) { "Error contacting test wise coverage agent." }
		}
	}
}
