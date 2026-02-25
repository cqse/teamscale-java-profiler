package com.teamscale.jacoco.agent.convert

import com.teamscale.client.TestDetails
import com.teamscale.jacoco.agent.benchmark
import com.teamscale.jacoco.agent.logging.LoggingUtils
import com.teamscale.jacoco.agent.options.AgentOptionParseException
import com.teamscale.jacoco.agent.util.Benchmark
import com.teamscale.report.ReportUtils
import com.teamscale.report.ReportUtils.listFiles
import com.teamscale.report.jacoco.EmptyReportException
import com.teamscale.report.jacoco.JaCoCoXmlReportGenerator
import com.teamscale.report.testwise.ETestArtifactFormat
import com.teamscale.report.testwise.TestwiseCoverageReportWriter
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.factory.TestInfoFactory
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.CommandLineLogger
import com.teamscale.report.util.ILogger
import java.io.File
import java.io.IOException
import java.lang.String
import java.nio.file.Paths
import kotlin.Array
import kotlin.Throws
import kotlin.use

/** Converts one .exec binary coverage file to XML.  */
class Converter
/** Constructor.  */(
	/** The command line arguments.  */
	private val arguments: ConvertCommand
) {
	/** Converts one .exec binary coverage file to XML.  */
	@Throws(IOException::class)
	fun runJaCoCoReportGeneration() {
		val logger = LoggingUtils.getLogger(this)
		val generator = JaCoCoXmlReportGenerator(
			arguments.getClassDirectoriesOrZips(),
			wildcardIncludeExcludeFilter,
			arguments.duplicateClassFileBehavior,
			arguments.shouldIgnoreUncoveredClasses,
			LoggingUtils.wrap(logger)
		)

		val jacocoExecutionDataList = listFiles(ETestArtifactFormat.JACOCO, arguments.getInputFiles())
		try {
			benchmark("Generating the XML report") {
				generator.convertExecFilesToReport(jacocoExecutionDataList, Paths.get(arguments.outputFile).toFile())
			}
		} catch (e: EmptyReportException) {
			logger.warn("Converted report was empty.", e)
		}
	}

	/** Converts one .exec binary coverage file, test details and test execution files to JSON testwise coverage.  */
	@Throws(IOException::class, AgentOptionParseException::class)
	fun runTestwiseCoverageReportGeneration() {
		val testDetails = ReportUtils.readObjects(
			ETestArtifactFormat.TEST_LIST,
			Array<TestDetails>::class.java,
			arguments.getInputFiles()
		)
		val testExecutions = ReportUtils.readObjects(
			ETestArtifactFormat.TEST_EXECUTION,
			Array<TestExecution>::class.java,
			arguments.getInputFiles()
		)

		val jacocoExecutionDataList = listFiles(ETestArtifactFormat.JACOCO, arguments.getInputFiles())
		val logger = CommandLineLogger()

		val generator = JaCoCoTestwiseReportGenerator(
			arguments.getClassDirectoriesOrZips(),
			this.wildcardIncludeExcludeFilter,
			arguments.duplicateClassFileBehavior,
			logger
		)

		benchmark("Generating the testwise coverage report") {
			logger.info("Writing report with ${testDetails.size} Details/${testExecutions.size} Results")
			TestwiseCoverageReportWriter(
				TestInfoFactory(testDetails, testExecutions),
				arguments.getOutputFile(),
				arguments.splitAfter, null
			).use { coverageWriter ->
				jacocoExecutionDataList.forEach { executionDataFile ->
					generator.convertAndConsume(executionDataFile, coverageWriter)
				}
			}
		}
	}

	private val wildcardIncludeExcludeFilter: ClasspathWildcardIncludeFilter
		get() = ClasspathWildcardIncludeFilter(
			String.join(":", arguments.locationIncludeFilters),
			String.join(":", arguments.locationExcludeFilters)
		)
}
