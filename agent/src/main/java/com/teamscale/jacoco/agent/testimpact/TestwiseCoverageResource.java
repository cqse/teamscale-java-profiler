package com.teamscale.jacoco.agent.testimpact;

import com.teamscale.client.ClusteredTestDetails;
import com.teamscale.client.PrioritizableTestCluster;
import com.teamscale.jacoco.agent.JacocoRuntimeController;
import com.teamscale.jacoco.agent.ResourceBase;
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException;
import com.teamscale.report.testwise.model.TestExecution;
import com.teamscale.report.testwise.model.TestInfo;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * The resource of the Jersey + Jetty http server holding all the endpoints specific for the
 * {@link TestwiseCoverageAgent}.
 */
@Path("/")
public class TestwiseCoverageResource extends ResourceBase {

	/** Path parameter placeholder used in the HTTP requests. */
	private static final String TEST_ID_PARAMETER = "testId";

	private static TestwiseCoverageAgent testwiseCoverageAgent;

	/**
	 * Static setter to inject the {@link TestwiseCoverageAgent} to the resource.
	 */
	public static void setAgent(TestwiseCoverageAgent agent) {
		TestwiseCoverageResource.testwiseCoverageAgent = agent;
		ResourceBase.agentBase = agent;
	}

	/** Returns the session ID of the current test. */
	@GET
	@Path("/test")
	public String getTest() {
		return testwiseCoverageAgent.controller.getSessionId();
	}


	/** Handles the start of a new test case by setting the session ID. */
	@POST
	@Path("/test/start/{" + TEST_ID_PARAMETER + "}")
	public Response handleTestStart(@PathParam(TEST_ID_PARAMETER) String testId) {
		if (testId == null || testId.isEmpty()) {
			handleBadRequest("Test name is missing!");
		}

		logger.debug("Start test {}", testId);

		testwiseCoverageAgent.testEventHandler.testStart(testId);
		return Response.noContent().build();
	}

	/** Handles the end of a test case by resetting the session ID. */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/test/end/{" + TEST_ID_PARAMETER + "}")
	public TestInfo handleTestEnd(@PathParam(TEST_ID_PARAMETER) String testId,
			TestExecution testExecution) throws JacocoRuntimeController.DumpException, CoverageGenerationException {
		if (testId == null || testId.isEmpty()) {
			handleBadRequest("Test name is missing!");
		}

		logger.debug("End test {}", testId);

		return testwiseCoverageAgent.testEventHandler.testEnd(testId,
				testExecution);
	}

	/** Handles the start of a new testrun. */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/testrun/start")
	public List<PrioritizableTestCluster> handleTestRunStart(
			@QueryParam("include-non-impacted") boolean includeNonImpactedTests,
			@QueryParam("include-added-tests") boolean includeAddedTests,
			@QueryParam("include-failed-and-skipped") boolean includeFailedAndSkipped,
			@QueryParam("baseline") String baseline,
			@QueryParam("baseline-revision") String baselineRevision,
			List<ClusteredTestDetails> availableTests) throws IOException {

		return testwiseCoverageAgent.testEventHandler.testRunStart(availableTests,
				includeNonImpactedTests, includeAddedTests,
				includeFailedAndSkipped, baseline, baselineRevision);
	}

	/** Handles the end of a new testrun. */
	@POST
	@Path("/testrun/end")
	public Response handleTestRunEnd(
			@DefaultValue("false") @QueryParam("partial") boolean partial) throws IOException, CoverageGenerationException {
		testwiseCoverageAgent.testEventHandler.testRunEnd(partial);
		return Response.noContent().build();
	}
}
