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

	/**
	 * A test is executed more than once if it is part of a `@ParameterizedClass`, once per parameter set. All of those
	 * executions belong to the same test, so their coverage has to be merged instead of overwriting each other.
	 */
	@Test
	fun testCoverageOfRepeatedExecutionsIsMerged() {
		val report = TestwiseCoverageReportBuilder.createFrom(
			listOf(TestDetails(uniformPath, "com/example/ParameterizedTest", null)),
			listOf(coverage("Calculator.java", 3, 6), coverage("Calculator.java", 3, 9)),
			emptyList(),
			false
		)

		assertThat(report.tests).hasSize(1)
		assertThat(report.tests.single().paths.single().files.single().coveredLines).isEqualTo("3,6,9")
	}

	/** Coverage of different files reported by repeated executions must all end up on the test. */
	@Test
	fun testCoverageOfRepeatedExecutionsInDifferentFilesIsMerged() {
		val report = TestwiseCoverageReportBuilder.createFrom(
			listOf(TestDetails(uniformPath, "com/example/ParameterizedTest", null)),
			listOf(coverage("Calculator.java", 3), coverage("Multiplier.java", 4)),
			emptyList(),
			false
		)

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
		val report = TestwiseCoverageReportBuilder.createFrom(
			listOf(TestDetails(uniformPath, "com/example/ParameterizedTest", null)),
			emptyList(),
			listOf(
				TestExecution(uniformPath, 20L, ETestExecutionResult.FAILURE, "boom"),
				TestExecution(uniformPath, 30L, ETestExecutionResult.PASSED)
			),
			false
		)

		assertThat(report.tests.single().result).isEqualTo(ETestExecutionResult.FAILURE)
		assertThat(report.tests.single().duration).isEqualTo(0.05)
		assertThat(report.tests.single().message).contains("boom")
	}

	/** Executions of a `@ParameterizedTest` still carry the invocation index, which must be stripped. */
	@Test
	fun testExecutionsWithParameterizedTestArgumentsAreResolved() {
		val report = TestwiseCoverageReportBuilder.createFrom(
			listOf(TestDetails(uniformPath, "com/example/ParameterizedTest", null)),
			emptyList(),
			listOf(
				TestExecution("$uniformPath[1]", 10L, ETestExecutionResult.PASSED),
				TestExecution("$uniformPath[2]", 10L, ETestExecutionResult.PASSED)
			),
			false
		)

		assertThat(report.tests).hasSize(1)
		assertThat(report.tests.single().duration).isEqualTo(0.02)
	}
}
