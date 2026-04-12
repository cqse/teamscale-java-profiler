package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.HttpUtils.getErrorBodyStringSafe
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.client.TeamscaleClient
import com.teamscale.client.TestWithClusterId
import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.JacocoRuntimeController.DumpException
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestInfo
import retrofit2.Response
import java.io.IOException
import java.util.stream.Collectors

/** Base class for strategies to handle test events.  */
abstract class TestEventHandlerStrategyBase protected constructor(
	/** The options the user has configured for the agent.  */
	@JvmField protected val agentOptions: AgentOptions,
	/** Controls the JaCoCo runtime.  */
	@JvmField protected val controller: JacocoRuntimeController
) {
	private val logger = getLogger(this)

	/** The timestamp at which the /test/start endpoint has been called last time.  */
	private var startTimestamp: Long = -1

	/** May be null if the user did not configure Teamscale.  */
	@JvmField
	protected val teamscaleClient = agentOptions.createTeamscaleClient(true)

	/** Called when test test with the given name is about to start.  */
	open fun testStart(test: String) {
		logger.debug("Test {} started", test)
		// Reset coverage so that we only record coverage that belongs to this particular test case.
		controller.reset()
		controller.sessionId = test
		startTimestamp = System.currentTimeMillis()
	}

	/**
	 * Called when the test with the given name finished.
	 * 
	 * @param test          Uniform path of the test
	 * @param testExecution A test execution object holding the test result and error message. May be null if none is
	 * given in the request.
	 * @return The body of the response. `null` indicates "204 No content". Non-null results will be treated
	 * as a json response.
	 */
	@Throws(DumpException::class, CoverageGenerationException::class)
	open fun testEnd(
		test: String,
		testExecution: TestExecution?
	): TestInfo? {
		if (testExecution != null) {
			testExecution.uniformPath = test
			if (startTimestamp != -1L) {
				val endTimestamp = System.currentTimeMillis()
				testExecution.durationMillis = endTimestamp - startTimestamp
			}
		}
		logger.debug("Test {} ended with test execution {}", test, testExecution)
		return null
	}

	/**
	 * Retrieves impacted tests from Teamscale, if a [.teamscaleClient] has been configured.
	 * 
	 * @param availableTests          List of all available tests that could be run or null if the user does not want to
	 * provide one.
	 * @param includeNonImpactedTests If this is true, only performs prioritization, no selection.
	 * @param baseline                Optional baseline for the considered changes.
	 * @throws IOException                   if the request to Teamscale failed.
	 * @throws UnsupportedOperationException if the user did not properly configure the [.teamscaleClient].
	 */
	@Throws(IOException::class)
	open fun testRunStart(
		availableTests: List<ClusteredTestDetails>?,
		includeNonImpactedTests: Boolean,
		includeAddedTests: Boolean, includeFailedAndSkipped: Boolean,
		baseline: String?, baselineRevision: String?
	): List<PrioritizableTestCluster>? {
		var availableTestCount = 0
		var availableTestsWithClusterId: List<TestWithClusterId>? = null
		if (availableTests != null) {
			availableTestCount = availableTests.size
			availableTestsWithClusterId = availableTests.map { availableTest ->
				TestWithClusterId.fromClusteredTestDetails(
					availableTest,
					partition
				)
			}
		}
		logger.debug(
			"Test run started with {} available tests. baseline = {}, includeNonImpactedTests = {}",
			availableTestCount, baseline, includeNonImpactedTests
		)
		validateConfiguration()

		val response = teamscaleClient!!.getImpactedTests(
			availableTestsWithClusterId, baseline, baselineRevision,
			agentOptions.teamscaleServer.commit,
			agentOptions.teamscaleServer.revision,
			agentOptions.teamscaleServer.repository,
			mutableListOf(agentOptions.teamscaleServer.partition!!),
			includeNonImpactedTests, includeAddedTests, includeFailedAndSkipped
		)
		if (response.isSuccessful) {
			val prioritizableTestClusters = response.body()
			logger.debug("Teamscale suggested these tests: {}", prioritizableTestClusters)
			return prioritizableTestClusters
		} else {
			val responseBody = getErrorBodyStringSafe(response)
			throw IOException(
				"Request to Teamscale to get impacted tests failed with HTTP status ${response.code()} ${response.message()}. Response body: $responseBody"
			)
		}
	}

	/**
	 * Returns the partition defined in the agent options. Asserts that the partition is defined.
	 */
	private val partition: String
		get() = agentOptions.teamscaleServer.partition ?: throw UnsupportedOperationException(
			"You must provide a partition via the agent's '${TeamscaleConfig.TEAMSCALE_PARTITION_OPTION}' option or using the /partition REST endpoint."
		)

	private fun validateConfiguration() {
		if (teamscaleClient == null) {
			throw UnsupportedOperationException(
				"You did not configure a connection to Teamscale in the agent." +
						" Thus, you cannot use the agent to retrieve impacted tests via the testrun/start REST endpoint." +
						" Please use the 'teamscale-' agent parameters to configure a Teamscale connection."
			)
		}
		if (!agentOptions.teamscaleServer.hasCommitOrRevision()) {
			throw UnsupportedOperationException(
				"You must provide a revision or commit via the agent's '" + TeamscaleConfig.TEAMSCALE_REVISION_OPTION + "', '" +
						TeamscaleConfig.TEAMSCALE_REVISION_MANIFEST_JAR_OPTION + "', '" + TeamscaleConfig.TEAMSCALE_COMMIT_OPTION +
						"', '" + TeamscaleConfig.TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION + "' or '" +
						AgentOptions.GIT_PROPERTIES_JAR_OPTION + "' option." +
						" Auto-detecting the git.properties does not work since we need the commit before any code" +
						" has been profiled in order to obtain the prioritized test cases from the TIA."
			)
		}
	}

	/**
	 * Signals that the test run has ended. Strategies that support this can upload a report via the
	 * [.teamscaleClient] here.
	 */
	@Throws(IOException::class, CoverageGenerationException::class)
	open fun testRunEnd(partial: Boolean) {
		throw UnsupportedOperationException(
			"You configured the agent in a mode that does not support uploading " +
					"reports to Teamscale. Please configure 'tia-mode=teamscale-upload' or simply don't call" +
					"POST /testrun/end."
		)
	}
}
