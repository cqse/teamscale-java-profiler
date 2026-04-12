package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.jacoco.agent.JacocoRuntimeController.DumpException
import com.teamscale.jacoco.agent.ResourceBase
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestInfo
import java.io.IOException
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * The resource of the Jersey + Jetty http server holding all the endpoints specific for the
 * [TestwiseCoverageAgent].
 */
@Path("/")
class TestwiseCoverageResource : ResourceBase() {
	@get:Path("/test")
	@get:GET
	val test: String?
		/** Returns the session ID of the current test.  */
		get() = testwiseCoverageAgent?.controller?.sessionId

	/** Handles the start of a new test case by setting the session ID.  */
	@POST
	@Path("/test/start/{$TEST_ID_PARAMETER}")
	fun handleTestStart(@PathParam(TEST_ID_PARAMETER) testId: String?): Response? {
		if (testId.isNullOrEmpty()) handleBadRequest("Test name is missing!")

		logger.debug("Start test {}", testId)

		testwiseCoverageAgent?.testEventHandler?.testStart(testId!!)
		return Response.noContent().build()
	}

	/** Handles the end of a test case by resetting the session ID.  */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/test/end/{$TEST_ID_PARAMETER}")
	@Throws(DumpException::class, CoverageGenerationException::class)
	fun handleTestEnd(
		@PathParam(TEST_ID_PARAMETER) testId: String?,
		testExecution: TestExecution?
	): TestInfo? {
		if (testId.isNullOrEmpty()) handleBadRequest("Test name is missing!")

		logger.debug("End test {}", testId)

		return testwiseCoverageAgent?.testEventHandler?.testEnd(testId!!, testExecution)
	}

	/** Handles the start of a new testrun.  */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/testrun/start")
	@Throws(IOException::class)
	fun handleTestRunStart(
		@QueryParam("include-non-impacted") includeNonImpactedTests: Boolean,
		@QueryParam("include-added-tests") includeAddedTests: Boolean,
		@QueryParam("include-failed-and-skipped") includeFailedAndSkipped: Boolean,
		@QueryParam("baseline") baseline: String?,
		@QueryParam("baseline-revision") baselineRevision: String?,
		availableTests: List<ClusteredTestDetails>?
	) = testwiseCoverageAgent?.testEventHandler?.testRunStart(
		availableTests,
		includeNonImpactedTests, includeAddedTests,
		includeFailedAndSkipped, baseline, baselineRevision
	)

	/** Handles the end of a new testrun.  */
	@POST
	@Path("/testrun/end")
	@Throws(IOException::class, CoverageGenerationException::class)
	fun handleTestRunEnd(
		@DefaultValue("false") @QueryParam("partial") partial: Boolean
	): Response? {
		testwiseCoverageAgent?.testEventHandler?.testRunEnd(partial)
		return Response.noContent().build()
	}

	companion object {
		/** Path parameter placeholder used in the HTTP requests.  */
		private const val TEST_ID_PARAMETER = "testId"

		private var testwiseCoverageAgent: TestwiseCoverageAgent? = null

		/**
		 * Static setter to inject the [TestwiseCoverageAgent] to the resource.
		 */
		@JvmStatic
		fun setAgent(agent: TestwiseCoverageAgent) {
			testwiseCoverageAgent = agent
			agentBase = agent
		}
	}
}
