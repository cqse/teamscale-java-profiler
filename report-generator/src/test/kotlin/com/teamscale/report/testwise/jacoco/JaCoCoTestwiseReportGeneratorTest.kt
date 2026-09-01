package com.teamscale.report.testwise.jacoco

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.TestDetails
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.ReportUtils.getTestwiseCoverageReportAsString
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestwiseCoverage
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import com.teamscale.report.testwise.model.builder.TestCoverageBuilder
import com.teamscale.report.testwise.model.builder.TestwiseCoverageReportBuilder.Companion.createFrom
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.test.TestDataBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.io.File

/** Tests for the [JaCoCoTestwiseReportGenerator] class.  */
class JaCoCoTestwiseReportGeneratorTest : TestDataBase() {
	@Test
	fun testSmokeTestTestwiseReportGeneration() {
		val report = runReportGenerator("jacoco/cqddl/classes.zip", "jacoco/cqddl/coverage.exec")
		val expected = useTestFile("jacoco/cqddl/report.json.expected").readText()
		JSONAssert.assertEquals(expected, report, JSONCompareMode.STRICT)
	}

	@Test
	fun testSampleTestwiseReportGeneration() {
		val report = runReportGenerator("jacoco/sample/classes.zip", "jacoco/sample/coverage.exec")
		val expected = useTestFile("jacoco/sample/report.json.expected").readText()
		JSONAssert.assertEquals(expected, report, JSONCompareMode.STRICT)
	}

	@Test
	fun defaultPackageIsHandledAsEmptyPath() {
		val report = runReportGenerator("jacoco/default-package/classes.zip", "jacoco/default-package/coverage.exec")
		val expected = useTestFile("jacoco/default-package/report.json.expected").readText()
		JSONAssert.assertEquals(expected, report, JSONCompareMode.STRICT)
	}

	/**
	 * A test that was dumped more than once, e.g. once per parameter set of an enclosing `@ParameterizedClass`, has to
	 * be passed on exactly once, with the coverage of all of its dumps merged. The dumps of one test may be spread
	 * over several *.exec files, which is simulated here by converting the same file twice.
	 */
	@Test
	fun testRepeatedDumpsOfTheSameTestAreMerged() {
		val executionDataFile = useTestFile("jacoco/sample/coverage.exec")

		val oneDumpPerTest = convertAndConsumePerTest(listOf(executionDataFile))
		val twoDumpsPerTest = convertAndConsumePerTest(listOf(executionDataFile, executionDataFile))

		assertThat(twoDumpsPerTest.map { it.uniformPath })
			.containsExactlyInAnyOrderElementsOf(oneDumpPerTest.map { it.uniformPath })
		assertThat(twoDumpsPerTest.asReportString()).isEqualTo(oneDumpPerTest.asReportString())
	}

	@Throws(Exception::class)
	private fun runReportGenerator(testDataFolder: String, execFileName: String): String {
		val testwiseCoverage = createReportGenerator(testDataFolder).convert(useTestFile(execFileName))
		return getTestwiseCoverageReportAsString(testwiseCoverage.generateDummyReport())
	}

	/** Collects the coverage that the generator passes on for the tests in the given *.exec files. */
	private fun convertAndConsumePerTest(executionDataFiles: List<File>): List<TestCoverageBuilder> {
		val coverage = mutableListOf<TestCoverageBuilder>()
		createReportGenerator("jacoco/sample/classes.zip")
			.convertAndConsumePerTest(executionDataFiles, coverage::add)
		return coverage
	}

	private fun List<TestCoverageBuilder>.asReportString(): String {
		val testwiseCoverage = TestwiseCoverage()
		forEach { testwiseCoverage.add(it) }
		return getTestwiseCoverageReportAsString(testwiseCoverage.generateDummyReport())
	}

	private fun createReportGenerator(testDataFolder: String) =
		JaCoCoTestwiseReportGenerator(
			listOf(useTestFile(testDataFolder)),
			ClasspathWildcardIncludeFilter(null, null), EDuplicateClassFileBehavior.IGNORE,
			Mockito.mock()
		)

	companion object {
		/** Generates a fake coverage report object that wraps the given [TestwiseCoverage].  */
		fun TestwiseCoverage.generateDummyReport(): TestwiseCoverageReport {
			val testDetails = tests.values.map {
				TestDetails(it.uniformPath, "/path/to/source", "content")
			}
			val testExecutions = tests.values.map {
				TestExecution(
					it.uniformPath, it.uniformPath.length.toLong(),
					ETestExecutionResult.PASSED
				)
			}
			return createFrom(testDetails, tests.values, testExecutions, true)
		}
	}
}
