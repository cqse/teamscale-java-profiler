package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.jacoco.agent.JacocoRuntimeController;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator;

import java.io.File;
import java.io.IOException;

/**
 * Strategy for appending coverage into one json test-wise coverage file with one session per test.
 */
public class CoverageToDiskStrategy extends CoverageToJsonStrategyBase {

	public CoverageToDiskStrategy(JacocoRuntimeController controller, AgentOptions agentOptions,
			JaCoCoTestwiseReportGenerator reportGenerator) {
		super(controller, agentOptions, reportGenerator);
	}

	@Override
	protected void handleTestwiseCoverageJsonReady(String json) throws IOException {
		File reportFile = agentOptions.createNewFileInPartitionOutputDirectory("testwise-coverage", "json");
		FileSystemUtils.writeFileUTF8(reportFile, json);
	}
}
