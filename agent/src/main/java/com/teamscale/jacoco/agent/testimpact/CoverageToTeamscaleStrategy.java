package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.client.EReportFormat;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.jacoco.agent.JacocoRuntimeController;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Strategy that records test-wise coverage and uploads the resulting report to Teamscale. Also handles the
 * {@link #testRunStart(List, boolean, boolean, boolean, String, String)} event by retrieving tests to run from
 * Teamscale.
 */
public class CoverageToTeamscaleStrategy extends CoverageToJsonStrategyBase {

	public CoverageToTeamscaleStrategy(JacocoRuntimeController controller, AgentOptions agentOptions,
			JaCoCoTestwiseReportGenerator reportGenerator) {
		super(controller, agentOptions, reportGenerator);
	}

	@Override
	protected void handleTestwiseCoverageJsonReady(String json) throws IOException {
		try {
			teamscaleClient
					.uploadReport(EReportFormat.TESTWISE_COVERAGE, json,
							agentOptions.getTeamscaleServerOptions().commit,
							agentOptions.getTeamscaleServerOptions().revision,
							agentOptions.getTeamscaleServerOptions().repository,
							agentOptions.getTeamscaleServerOptions().partition,
							agentOptions.getTeamscaleServerOptions().getMessage());
		} catch (IOException e) {
			File reportFile = agentOptions.createNewFileInOutputDirectory("testwise-coverage", "json");
			FileSystemUtils.writeFileUTF8(reportFile, json);
			String errorMessage = "Failed to upload coverage to Teamscale! Report is stored in " + reportFile + "!";
			logger.error(errorMessage, e);
			throw new IOException(errorMessage, e);
		}
	}
}
