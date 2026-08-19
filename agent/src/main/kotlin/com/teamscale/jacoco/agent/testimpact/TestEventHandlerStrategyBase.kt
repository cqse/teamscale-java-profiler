package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.HttpUtils.getErrorBodyStringSafe
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.client.TestWithClusterId
import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.JacocoRuntimeController.DumpException
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestInfo
import java.io.IOException

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

	/** Uniform path of the test that is currently running, or null if no test is in progress.  */
	private var runningTest: String? = null

	/** Uniform path of the test that was ended last, or null if no test has ended yet.  */
	private var lastEndedTest: String? = null

	/**
	 * The duration we derived for [lastEndedTest] from the time between its /test/start and /test/end requests, or null
	 * if we could not derive one because we did not receive a matching /test/start request.
	 */
	private var lastEndedTestDurationSeconds: Double? = null

	/** May be null if the user did not configure Teamscale.  */
	@JvmField
	protected val teamscaleClient = agentOptions.createTeamscaleClient(true)

	/** Called when test test with the given name is about to start.  */
	open fun testStart(test: String) {
		endRunningTestAsInconclusive("a new test ('$test') was started")
		logger.debug("Test {} started", test)
		// Reset coverage so that we only record coverage that belongs to this particular test case.
		controller.reset()
		controller.sessionId = test
		runningTest = test
		startTimestamp = System.currentTimeMillis()
	}

	/**
	 * Called when the test with the given name finished.
	 * 
	 * @param test          Uniform path of the test
	 * @param testExecution A test execution object holding the test result and error message.
	 * @return The body of the response. `null` indicates "204 No content". Non-null results will be treated
	 * as a json response.
	 */
	@Throws(DumpException::class, CoverageGenerationException::class)
	open fun testEnd(
		test: String,
		testExecution: TestExecution
	): TestInfo? {
		testExecution.uniformPath = test
		val derivedDurationSeconds = deriveDurationSeconds(test, testExecution)
		derivedDurationSeconds?.let {
			testExecution.durationSeconds = it
		}
		lastEndedTest = test
		lastEndedTestDurationSeconds = derivedDurationSeconds
		runningTest = null
		startTimestamp = -1
		logger.debug("Test {} ended with test execution {}", test, testExecution)
		return null
	}

	/**
	 * Ends the test that is currently running, if any, as [ETestExecutionResult.INCONCLUSIVE]. We do not know its
	 * actual result, since the profiler never received a valid /test/end request for it.
	 *
	 * @param cause Description of what ended the test, e.g. the start of another test. Used in the log message and
	 * recorded as the test's message in the report.
	 */
	@Throws(DumpException::class, CoverageGenerationException::class)
	protected fun endRunningTestAsInconclusive(cause: String) {
		val test = runningTest ?: return
		logger.warn(
			"No valid /test/end request was received for test '{}' before {}." +
					" Automatically ending it as '{}', using the time until now as its duration.",
			test, cause, ETestExecutionResult.INCONCLUSIVE
		)
		val testInfo = testEnd(
			test, TestExecution(
				test, 0L, ETestExecutionResult.INCONCLUSIVE,
				"The test did not end properly: $cause before this test ended."
			)
		)
		if (testInfo != null) {
			logger.warn(
				"The coverage recorded for test '{}' is lost. The profiler is configured to return each test's" +
						" coverage in the response to its /test/end request (tia-mode=http), but it never received a" +
						" valid /test/end request for this test.", test
			)
		}
	}

	/**
	 * Derives the duration of the given test from the time between its /test/start and its /test/end request. Returns
	 * null if we cannot derive it, in which case the duration given by the caller (if any) is used as-is.
	 */
	private fun deriveDurationSeconds(test: String, testExecution: TestExecution): Double? {
		if (runningTest == test) return (System.currentTimeMillis() - startTimestamp) / 1000.0

		if (test == lastEndedTest) {
			// The test was already ended once, e.g. because the caller sent a second /test/end request for it. Reuse
			// the duration derived back then instead of claiming that no /test/start request was received, which
			// would be wrong and unactionable.
			return lastEndedTestDurationSeconds
		}

		if (!testExecution.hasExplicitDuration) logMissingDuration(test)
		return null
	}

	/**
	 * Logs that we can neither derive the duration of the given test ourselves, because we did not receive a matching
	 * /test/start request, nor did the caller provide the duration in the /test/end request.
	 */
	private fun logMissingDuration(test: String) {
		logger.warn(
			"No /test/start request was received for test '{}', so the profiler could not derive its" +
					" duration and none was provided in the /test/end request either." +
					" The duration reported for this test will be inaccurate." +
					" Please send a /test/start request before ending a test or provide a 'duration'" +
					" (in seconds) in the body of the /test/end request.",
			test
		)
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
