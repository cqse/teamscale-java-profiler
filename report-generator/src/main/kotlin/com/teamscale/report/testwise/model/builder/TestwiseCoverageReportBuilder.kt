package com.teamscale.report.testwise.model.builder

import com.teamscale.client.TestDetails
import com.teamscale.report.testwise.model.TestExecution
import com.teamscale.report.testwise.model.TestwiseCoverageReport

/** Container for coverage produced by multiple tests.  */
class TestwiseCoverageReportBuilder {
	/** A mapping from test ID to [TestCoverageBuilder].  */
	private val tests = mutableMapOf<String, TestInfoBuilder>()

	private fun build(partial: Boolean): TestwiseCoverageReport {
		val report = TestwiseCoverageReport(partial)
		tests.values
			.sortedBy { it.uniformPath }
			.map { it.build() }
			.forEach { report.tests.add(it) }
		return report
	}

	companion object {
		/**
		 * Adds the [TestCoverageBuilder] to the map. If there is already a test with the same ID the coverage is
		 * merged.
		 */
		fun createFrom(
			testDetailsList: Collection<TestDetails>,
			testCoverage: Collection<TestCoverageBuilder>,
			testExecutions: Collection<TestExecution>,
			partial: Boolean
		): TestwiseCoverageReport {
			val report = TestwiseCoverageReportBuilder()
			testDetailsList.forEach { testDetails ->
				TestInfoBuilder(testDetails.uniformPath).also {
					it.setDetails(testDetails)
					report.tests[testDetails.uniformPath] = it
				}
			}
			testCoverage.forEach { coverage ->
				resolveUniformPath(report, coverage.uniformPath)?.setCoverage(coverage)
			}
			testExecutions.forEach { testExecution ->
				val path = testExecution.uniformPath ?: return@forEach
				resolveUniformPath(report, path)?.setExecution(testExecution)
			}
			return report.build(partial)
		}

		private fun resolveUniformPath(report: TestwiseCoverageReportBuilder, uniformPath: String) =
			if (report.tests.containsKey(uniformPath)) {
				report.tests[uniformPath]
			} else {
				val shortenedUniformPath = stripParameterizedTestArguments(uniformPath)
				report.tests[shortenedUniformPath]
			} ?: run {
				System.err.println("No container found for test '$uniformPath'!"); null
			}

		/**
		 * Removes parameterized test arguments from the given uniform path.
		 */
		fun stripParameterizedTestArguments(uniformPath: String) =
			uniformPath.replaceFirst("(.*\\))\\[.*]".toRegex(), "$1")
	}
}
