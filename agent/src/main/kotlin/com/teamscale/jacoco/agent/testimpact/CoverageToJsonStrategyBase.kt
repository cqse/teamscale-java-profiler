package com.teamscale.jacoco.agent.testimpact

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.client.JsonUtils.serializeToJson
import com.teamscale.client.PrioritizableTestCluster
import com.teamscale.jacoco.agent.JacocoRuntimeController
import com.teamscale.jacoco.agent.JacocoRuntimeController.DumpException
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.options.AgentOptions
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.jacoco.cache.CoverageGenerationException
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestInfo
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder
import com.teamscale.report.testwise.model.builder.TestwiseCoverageReportBuilder
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.util.stream.Collectors

/**
 * Base for strategies that produce testwise coverage information in JSON and store or send this data further.
 */
abstract class CoverageToJsonStrategyBase(
	controller: JacocoRuntimeController,
	agentOptions: AgentOptions,
	private val reportGenerator: JaCoCoTestwiseReportGenerator
) : TestEventHandlerStrategyBase(agentOptions, controller) {

	@JvmField
	protected val logger: Logger = getLogger(this)

	/**
	 * The path to the exec file into which the coverage of the current test run is appended to. Will be null if there
	 * is no file for the current test run yet.
	 */
	private var testExecFile: File? = null
	private val testExecutions = mutableListOf<TestExecution>()
	private var availableTests = mutableListOf<ClusteredTestDetails>()

	@Throws(IOException::class)
	override fun testRunStart(
		availableTests: List<ClusteredTestDetails>?,
		includeNonImpactedTests: Boolean,
		includeAddedTests: Boolean, includeFailedAndSkipped: Boolean,
		baseline: String?, baselineRevision: String?
	): List<PrioritizableTestCluster>? {
		if (availableTests != null) {
			this.availableTests = ArrayList(availableTests)
		}
		return super.testRunStart(
			this.availableTests, includeNonImpactedTests, includeAddedTests,
			includeFailedAndSkipped, baseline, baselineRevision
		)
	}

	override fun testStart(test: String) {
		super.testStart(test)
		if (availableTests.none { it.uniformPath == test }) {
			// ensure that we can at least generate a report for the tests that were actually run,
			// even if the caller did not provide a list of tests up-front in testRunStart
			availableTests.add(ClusteredTestDetails(test, test, null, null))
		}
	}

	@Throws(DumpException::class, CoverageGenerationException::class)
	override fun testEnd(
		test: String,
		testExecution: TestExecution?
	): TestInfo? {
		super.testEnd(test, testExecution)
		if (testExecution != null) {
			testExecutions.add(testExecution)
		}

		try {
			if (testExecFile == null) {
				testExecFile = agentOptions.createNewFileInOutputDirectory("coverage", "exec")
				testExecFile?.deleteOnExit()
			}
			controller.dumpToFileAndReset(testExecFile!!)
		} catch (e: IOException) {
			throw DumpException("Failed to write coverage to disk into $testExecFile!", e)
		}
		return null
	}

	@Throws(IOException::class, CoverageGenerationException::class)
	override fun testRunEnd(partial: Boolean) {
		if (testExecFile == null) {
			logger.warn("Tried to end a test run that contained no tests!")
			clearTestRun()
			return
		}

		handleTestwiseCoverageJsonReady(createTestwiseCoverageReport(partial))
	}

	/**
	 * Hook that is invoked when the JSON is ready for processed further.
	 */
	@Throws(IOException::class)
	protected abstract fun handleTestwiseCoverageJsonReady(json: String)

	/**
	 * Creates a testwise coverage report from the coverage collected in [testExecFile] and the test execution
	 * information in [testExecutions].
	 */
	@Throws(IOException::class, CoverageGenerationException::class)
	private fun createTestwiseCoverageReport(partial: Boolean): String {
		val executionUniformPaths = testExecutions.map { it.uniformPath }

		logger.debug(
			"Creating testwise coverage from available tests `{}`, test executions `{}`, exec file and partial {}",
			availableTests.map { it.uniformPath },
			executionUniformPaths, partial)
		reportGenerator.updateClassDirCache()
		val testwiseCoverage = reportGenerator.convert(testExecFile!!)
		logger.debug(
			"Created testwise coverage report (containing coverage for tests `{}`)",
			testwiseCoverage.tests.values.map(TestCoverageBuilder::uniformPath)
		)

		val report = TestwiseCoverageReportBuilder.createFrom(
			availableTests,
			testwiseCoverage.tests.values, testExecutions, partial
		)

		testExecFile?.delete()
		testExecFile = null
		clearTestRun()

		return report.serializeToJson()
	}

	private fun clearTestRun() {
		availableTests.clear()
		testExecutions.clear()
	}
}
