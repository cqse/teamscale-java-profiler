/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.jacoco.agent.AgentBase;
import com.teamscale.jacoco.agent.GenericExceptionMapper;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * A wrapper around the JaCoCo Java agent that starts a HTTP server and listens for test events.
 */
public class TestwiseCoverageAgent extends AgentBase {

	/**
	 * The test event strategy handler.
	 */
	protected final TestEventHandlerStrategyBase testEventHandler;

	/** Creates a {@link TestwiseCoverageAgent} based on the given options. */
	public static TestwiseCoverageAgent create(AgentOptions agentOptions) throws IOException {
		Logger logger = LoggingUtils.getLogger(JaCoCoTestwiseReportGenerator.class);
		JaCoCoTestwiseReportGenerator reportGenerator = new JaCoCoTestwiseReportGenerator(
				agentOptions.classDirectoriesOrZips, agentOptions.getLocationIncludeFilter(),
				agentOptions.duplicateClassFileBehavior, LoggingUtils.wrap(logger));
		return new TestwiseCoverageAgent(agentOptions,
				new TestExecutionWriter(agentOptions.createNewFileInOutputDirectory("test-execution", "json")),
				reportGenerator);
	}


	public TestwiseCoverageAgent(AgentOptions options, TestExecutionWriter testExecutionWriter,
			JaCoCoTestwiseReportGenerator reportGenerator) throws IllegalStateException {
		super(options);
		switch (options.testwiseCoverageMode) {
			case TEAMSCALE_UPLOAD:
				testEventHandler = new CoverageToTeamscaleStrategy(controller, options, reportGenerator);
				break;
			case DISK:
				testEventHandler = new CoverageToDiskStrategy(controller, options, reportGenerator);
				break;
			case HTTP:
				testEventHandler = new CoverageViaHttpStrategy(controller, options, reportGenerator);
				break;
			default:
				testEventHandler = new CoverageToExecFileStrategy(controller, options, testExecutionWriter);
				break;
		}
		// Set to empty to not end up with a default session in case no tests are executed,
		// which in turn causes a warning because we didn't write a corresponding test detail
		controller.setSessionId("");
	}

	@Override
	protected ResourceConfig initResourceConfig() {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE.toString());
		TestwiseCoverageResource.setAgent(this);
		return resourceConfig.register(TestwiseCoverageResource.class).register(GenericExceptionMapper.class);
	}

	@Override
	public void dumpReport() {
		// Dumping via the API is not supported in testwise mode. Ending the test run dumps automatically
	}
}
