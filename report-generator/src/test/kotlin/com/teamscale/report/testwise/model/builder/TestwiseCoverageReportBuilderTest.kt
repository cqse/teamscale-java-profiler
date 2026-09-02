package com.teamscale.report.testwise.model.builder

import com.teamscale.client.TestDetails
import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Tests for [TestwiseCoverageReportBuilder]. */
internal class TestwiseCoverageReportBuilderTest {

	private val uniformPath = "com/example/ParameterizedTest/testMethod()"

	private fun coverage(fileName: String, vararg lines: Int) =
		TestCoverageBuilder(uniformPath).apply {
			add(FileCoverageBuilder("com/example", fileName).apply { lines.forEach { addLine(it) } })
		}

	/** Builds a report for the single test [uniformPath] from the given coverage and executions. */
	private fun report(
		coverage: List<TestCoverageBuilder> = emptyList(),
		executions: List<TestExecution> = emptyList()
	) = TestwiseCoverageReportBuilder.createFrom(
		listOf(TestDetails(uniformPath, "com/example/ParameterizedTest", null)), coverage, executions, false
	)

	/**
	 * A test is executed more than once if it is part of a `@ParameterizedClass`, once per parameter set. All of those
	 * executions belong to the same test, so their coverage has to be merged instead of overwriting each other.
	 */
	@Test
	fun testCoverageOfRepeatedExecutionsIsMerged() {
		val report = report(coverage = listOf(coverage("Calculator.java", 3, 6), coverage("Calculator.java", 3, 9)))

		assertThat(report.tests).hasSize(1)
		assertThat(report.tests.single().paths.single().files.single().coveredLines).isEqualTo("3,6,9")
	}

	/** Coverage of different files reported by repeated executions must all end up on the test. */
	@Test
	fun testCoverageOfRepeatedExecutionsInDifferentFilesIsMerged() {
		val report = report(coverage = listOf(coverage("Calculator.java", 3), coverage("Multiplier.java", 4)))

		assertThat(report.tests.single().paths.single().files)
			.extracting<String> { it.fileName }
			.containsExactly("Calculator.java", "Multiplier.java")
	}

	/**
	 * The durations of repeated executions are summed up and the most severe result wins, so that a failure in one
	 * parameter set is not hidden by another one that passed afterwards.
	 */
	@Test
	fun testRepeatedExecutionsAreAggregated() {
		val report = report(
			executions = listOf(
				TestExecution(uniformPath, 20L, ETestExecutionResult.FAILURE, "boom"),
				TestExecution(uniformPath, 30L, ETestExecutionResult.PASSED)
			)
		)

		assertThat(report.tests.single().result).isEqualTo(ETestExecutionResult.FAILURE)
		assertThat(report.tests.single().duration).isEqualTo(0.05)
		assertThat(report.tests.single().message).contains("boom")
	}

	/** A failure in a later parameter set must win as well, and the messages of both executions must be kept. */
	@Test
	fun testFailureAfterPassedExecutionIsReported() {
		val report = report(
			executions = listOf(
				TestExecution(uniformPath, 20L, ETestExecutionResult.PASSED, "first parameter set"),
				TestExecution(uniformPath, 30L, ETestExecutionResult.FAILURE, "boom")
			)
		)

		assertThat(report.tests.single().result).isEqualTo(ETestExecutionResult.FAILURE)
		assertThat(report.tests.single().message).isEqualTo("first parameter set\n\nboom")
	}

	/**
	 * The declaration order of [ETestExecutionResult] is not a severity order, so aggregating must not rely on it: a
	 * test that ran and passed must not be reported as skipped, and a failure must not be hidden by an execution
	 * whose result the profiler did not learn.
	 */
	@Test
	fun testAggregationDoesNotFollowTheDeclarationOrderOfTheResults() {
		assertThat(aggregatedResultOf(ETestExecutionResult.SKIPPED, ETestExecutionResult.PASSED))
			.isEqualTo(ETestExecutionResult.PASSED)
		assertThat(aggregatedResultOf(ETestExecutionResult.FAILURE, ETestExecutionResult.INCONCLUSIVE))
			.isEqualTo(ETestExecutionResult.FAILURE)
	}

	/** Returns the result reported for a test that was executed once with each of the given results. */
	private fun aggregatedResultOf(vararg results: ETestExecutionResult) =
		report(executions = results.map { TestExecution(uniformPath, 10L, it) }).tests.single().result

	/** Executions of a `@ParameterizedTest` still carry the invocation index, which must be stripped. */
	@Test
	fun testExecutionsWithParameterizedTestArgumentsAreResolved() {
		val report = report(
			executions = listOf(
				TestExecution("$uniformPath[1]", 10L, ETestExecutionResult.PASSED),
				TestExecution("$uniformPath[2]", 10L, ETestExecutionResult.PASSED)
			)
		)

		assertThat(report.tests).hasSize(1)
		assertThat(report.tests.single().duration).isEqualTo(0.02)
	}
}
