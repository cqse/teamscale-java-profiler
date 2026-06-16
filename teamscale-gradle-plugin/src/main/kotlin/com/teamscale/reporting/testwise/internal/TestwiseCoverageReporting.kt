package com.teamscale.reporting.testwise.internal

import com.teamscale.client.ClusteredTestDetails
import com.teamscale.config.*
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.ReportUtils
import com.teamscale.report.testwise.ETestArtifactFormat
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestwiseCoverage
import com.teamscale.report.testwise.model.builder.TestwiseCoverageReportBuilder
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.utils.wrapInILogger
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.*
import java.io.File


/** Generates the actual testwise coverage report from the individual files produced by the Teamscale JaCoCo agent. */
class TestwiseCoverageReporting(
	private val logger: Logger,
	private val partial: Boolean,
	private val classDirs: Collection<File>,
	private val predicate: ClasspathWildcardIncludeFilter,
	private val reportOutputDirs: Collection<File>,
	private val reportOutputLocation: File
) {

	/** Generates a testwise coverage report and stores it on disk. */
	fun generateTestwiseCoverageReports() {
		val testDetails =
			ReportUtils.readObjects(
				ETestArtifactFormat.TEST_LIST,
				Array<ClusteredTestDetails>::class.java,
				reportOutputDirs
			)
		val testExecutions = ReportUtils.readObjects(
			ETestArtifactFormat.TEST_EXECUTION,
			Array<TestExecution>::class.java,
			reportOutputDirs
		)
		val jacocoExecutionData = ReportUtils.listFiles(ETestArtifactFormat.JACOCO, reportOutputDirs)
		if (jacocoExecutionData.isEmpty()) {
			logger.error(
				"No execution data provided." +
						" Make sure the upstream Test task has the JaCoCo extension enabled" +
						" (e.g. via the 'jacoco' plugin)."
			)
			return
		}

		val testwiseCoverage = buildTestwiseCoverage(jacocoExecutionData)

		logger.info("Generating report with ${testDetails.size} Details/${testwiseCoverage.tests.size} Coverage/${testExecutions.size} Results")

		if (testExecutions.isNotEmpty() && testwiseCoverage.tests.all { it.value.isEmpty }) {
			logger.error("No coverage was recorded for any of the executed tests! Check your jacoco include/exclude patterns on the Test task.")
			return
		}

		val report = TestwiseCoverageReportBuilder.createFrom(
			testDetails,
			testwiseCoverage.tests.values,
			testExecutions,
			partial
		)
		logger.info("Writing report to ${reportOutputLocation.absolutePath}")
		ReportUtils.writeTestwiseCoverageReport(reportOutputLocation, report)
	}

	/** Collects JaCoCo's exec files from the artifacts folders and merges it with js coverage. */
	private fun buildTestwiseCoverage(jacocoExecutionData: List<File>): TestwiseCoverage {
		logger.info("Generating testwise coverage for $jacocoExecutionData")

		val jaCoCoTestwiseReportGenerator = JaCoCoTestwiseReportGenerator(
			classDirs,
			predicate,
			EDuplicateClassFileBehavior.IGNORE,
			logger.wrapInILogger()
		)

		val testwiseCoverage = TestwiseCoverage()
		for (file in jacocoExecutionData) {
			testwiseCoverage.add(jaCoCoTestwiseReportGenerator.convert(file))
		}
		return testwiseCoverage
	}
}
