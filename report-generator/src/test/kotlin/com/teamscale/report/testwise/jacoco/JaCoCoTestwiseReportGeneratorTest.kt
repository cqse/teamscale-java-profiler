package com.teamscale.report.testwise.jacoco

import com.teamscale.client.FileSystemUtils
import com.teamscale.client.TestDetails
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.ReportUtils.getTestwiseCoverageReportAsString
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestwiseCoverage
import com.teamscale.report.testwise.model.TestwiseCoverageReport
import com.teamscale.report.testwise.model.builder.TestwiseCoverageReportBuilder.Companion.createFrom
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.test.TestDataBase
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

/** Tests for the [JaCoCoTestwiseReportGenerator] class.  */
class JaCoCoTestwiseReportGeneratorTest : TestDataBase() {
	@Test
	fun testSmokeTestTestwiseReportGeneration() {
		val report = runReportGenerator("jacoco/cqddl/classes.zip", "jacoco/cqddl/coverage.exec")
		val expected = FileSystemUtils.readFileUTF8(useTestFile("jacoco/cqddl/report.json.expected"))
		JSONAssert.assertEquals(expected, report, JSONCompareMode.STRICT)
	}

	@Test
	fun testSampleTestwiseReportGeneration() {
		val report = runReportGenerator("jacoco/sample/classes.zip", "jacoco/sample/coverage.exec")
		val expected = FileSystemUtils.readFileUTF8(useTestFile("jacoco/sample/report.json.expected"))
		JSONAssert.assertEquals(expected, report, JSONCompareMode.STRICT)
	}

	@Test
	fun defaultPackageIsHandledAsEmptyPath() {
		val report = runReportGenerator("jacoco/default-package/classes.zip", "jacoco/default-package/coverage.exec")
		val expected = FileSystemUtils.readFileUTF8(useTestFile("jacoco/default-package/report.json.expected"))
		JSONAssert.assertEquals(expected, report, JSONCompareMode.STRICT)
	}

	@Throws(Exception::class)
	private fun runReportGenerator(testDataFolder: String, execFileName: String): String {
		val classFileFolder = useTestFile(testDataFolder)
		val includeFilter = ClasspathWildcardIncludeFilter(null, null)
		val testwiseCoverage = JaCoCoTestwiseReportGenerator(
			listOf(classFileFolder),
			includeFilter, EDuplicateClassFileBehavior.IGNORE,
			Mockito.mock()
		).convert(useTestFile(execFileName))
		return getTestwiseCoverageReportAsString(testwiseCoverage.generateDummyReport())
	}

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
