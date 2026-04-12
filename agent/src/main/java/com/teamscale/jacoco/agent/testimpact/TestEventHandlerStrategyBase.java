package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.client.ClusteredTestDetails;
import com.teamscale.client.HttpUtils;
import com.teamscale.client.PrioritizableTestCluster;
import com.teamscale.client.TeamscaleClient;
import com.teamscale.client.TestWithClusterId;
import com.teamscale.jacoco.agent.JacocoRuntimeController;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig;
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException;
import com.teamscale.report.testwise.model.TestExecution;
import com.teamscale.report.testwise.model.TestInfo;
import org.slf4j.Logger;
import retrofit2.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Base class for strategies to handle test events. */
public abstract class TestEventHandlerStrategyBase {

	private final Logger logger = LoggingUtils.getLogger(this);

	/** Controls the JaCoCo runtime. */
	protected final JacocoRuntimeController controller;

	/** The timestamp at which the /test/start endpoint has been called last time. */
	private long startTimestamp = -1;

	/** The options the user has configured for the agent. */
	protected final AgentOptions agentOptions;

	/** May be null if the user did not configure Teamscale. */
	protected final TeamscaleClient teamscaleClient;


	protected TestEventHandlerStrategyBase(AgentOptions agentOptions, JacocoRuntimeController controller) {
		this.controller = controller;
		this.agentOptions = agentOptions;
		this.teamscaleClient = agentOptions.createTeamscaleClient(true);
	}

	/** Called when test test with the given name is about to start. */
	public void testStart(String test) {
		logger.debug("Test {} started", test);
		// Reset coverage so that we only record coverage that belongs to this particular test case.
		controller.reset();
		controller.setSessionId(test);
		startTimestamp = System.currentTimeMillis();
	}

	/**
	 * Called when the test with the given name finished.
	 *
	 * @param test          Uniform path of the test
	 * @param testExecution A test execution object holding the test result and error message. May be null if none is
	 *                      given in the request.
	 * @return The body of the response. <code>null</code> indicates "204 No content". Non-null results will be treated
	 * as a json response.
	 */
	public TestInfo testEnd(String test,
			TestExecution testExecution) throws JacocoRuntimeController.DumpException, CoverageGenerationException {
		if (testExecution != null) {
			testExecution.uniformPath = test;
			if (startTimestamp != -1) {
				long endTimestamp = System.currentTimeMillis();
				testExecution.setDurationMillis(endTimestamp - startTimestamp);
			}
		}
		logger.debug("Test {} ended with test execution {}", test, testExecution);
		return null;
	}

	/**
	 * Retrieves impacted tests from Teamscale, if a {@link #teamscaleClient} has been configured.
	 *
	 * @param availableTests          List of all available tests that could be run or null if the user does not want to
	 *                                provide one.
	 * @param includeNonImpactedTests If this is true, only performs prioritization, no selection.
	 * @param baseline                Optional baseline for the considered changes.
	 * @throws IOException                   if the request to Teamscale failed.
	 * @throws UnsupportedOperationException if the user did not properly configure the {@link #teamscaleClient}.
	 */
	public List<PrioritizableTestCluster> testRunStart(List<ClusteredTestDetails> availableTests,
			boolean includeNonImpactedTests,
			boolean includeAddedTests, boolean includeFailedAndSkipped,
			String baseline, String baselineRevision) throws IOException {
		int availableTestCount = 0;
		List<TestWithClusterId> availableTestsWithClusterId = null;
		if (availableTests != null) {
			availableTestCount = availableTests.size();
			availableTestsWithClusterId = availableTests.stream()
					.map(availableTest -> TestWithClusterId.Companion.fromClusteredTestDetails(availableTest, getPartition()))
					.collect(
							Collectors.toList());
		}
		logger.debug("Test run started with {} available tests. baseline = {}, includeNonImpactedTests = {}",
				availableTestCount, baseline, includeNonImpactedTests);
		validateConfiguration();

		Response<List<PrioritizableTestCluster>> response = teamscaleClient
				.getImpactedTests(availableTestsWithClusterId, baseline, baselineRevision,
						agentOptions.teamscaleServer.commit,
						agentOptions.teamscaleServer.revision,
						agentOptions.teamscaleServer.repository,
						Collections.singletonList(agentOptions.teamscaleServer.partition),
						includeNonImpactedTests, includeAddedTests, includeFailedAndSkipped);
		if (response.isSuccessful()) {
			List<PrioritizableTestCluster> prioritizableTestClusters = response.body();
			logger.debug("Teamscale suggested these tests: {}", prioritizableTestClusters);
			return prioritizableTestClusters;
		} else {
			String responseBody = HttpUtils.getErrorBodyStringSafe(response);
			throw new IOException(
					"Request to Teamscale to get impacted tests failed with HTTP status " + response.code() +
							" " + response.message() + ". Response body: " + responseBody);
		}
	}

	/**
	 * Returns the partition defined in the agent options. Asserts that the partition is defined.
	 */
	private String getPartition() {
		String partition = agentOptions.teamscaleServer.partition;
		if (partition == null) {
			throw new UnsupportedOperationException(
					"You must provide a partition via the agent's '" + TeamscaleConfig.TEAMSCALE_PARTITION_OPTION + "' option or using the /partition REST endpoint.");
		}
		return partition;
	}

	private void validateConfiguration() {
		if (teamscaleClient == null) {
			throw new UnsupportedOperationException("You did not configure a connection to Teamscale in the agent." +
					" Thus, you cannot use the agent to retrieve impacted tests via the testrun/start REST endpoint." +
					" Please use the 'teamscale-' agent parameters to configure a Teamscale connection.");
		}
		if (!agentOptions.teamscaleServer.hasCommitOrRevision()) {
			throw new UnsupportedOperationException(
					"You must provide a revision or commit via the agent's '" + TeamscaleConfig.TEAMSCALE_REVISION_OPTION + "', '" +
							TeamscaleConfig.TEAMSCALE_REVISION_MANIFEST_JAR_OPTION + "', '" + TeamscaleConfig.TEAMSCALE_COMMIT_OPTION +
							"', '" + TeamscaleConfig.TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION + "' or '" +
							AgentOptions.GIT_PROPERTIES_JAR_OPTION + "' option." +
							" Auto-detecting the git.properties does not work since we need the commit before any code" +
							" has been profiled in order to obtain the prioritized test cases from the TIA.");
		}
	}

	/**
	 * Signals that the test run has ended. Strategies that support this can upload a report via the
	 * {@link #teamscaleClient} here.
	 */
	public void testRunEnd(boolean partial) throws IOException, CoverageGenerationException {
		throw new UnsupportedOperationException("You configured the agent in a mode that does not support uploading " +
				"reports to Teamscale. Please configure 'tia-mode=teamscale-upload' or simply don't call" +
				"POST /testrun/end.");
	}
}
