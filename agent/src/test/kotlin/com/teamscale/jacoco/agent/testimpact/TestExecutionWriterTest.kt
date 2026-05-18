package com.teamscale.jacoco.agent.testimpact

import com.teamscale.report.testwise.model.ETestExecutionResult
import com.teamscale.report.testwise.model.TestExecution
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class TestExecutionWriterTest {
	@Test
	@Throws(Exception::class)
	fun testOneExecution(@TempDir tempDir: Path) {
		val tempFile = tempDir.resolve("executions.json")
		val writer = TestExecutionWriter(tempFile.toFile())
		writer.append(TestExecution("test1", 123, ETestExecutionResult.PASSED))
		val json = Files.readAllLines(tempFile).joinToString("\n")
		Assertions.assertThat(json)
			.isEqualTo("[{\"uniformPath\":\"test1\",\"durationMillis\":123,\"result\":\"PASSED\"}]")
	}

	@Test
	@Throws(Exception::class)
	fun testMultipleExecutions(@TempDir tempDir: Path) {
		val tempFile = tempDir.resolve("executions.json")
		val writer = TestExecutionWriter(tempFile.toFile())
		writer.append(TestExecution("test1", 123, ETestExecutionResult.PASSED))
		writer.append(TestExecution("test2", 123, ETestExecutionResult.PASSED))
		writer.append(TestExecution("test3", 123, ETestExecutionResult.PASSED))
		val json = Files.readAllLines(tempFile).joinToString("\n")
		Assertions.assertThat(json).isEqualTo(
			"[{\"uniformPath\":\"test1\",\"durationMillis\":123,\"result\":\"PASSED\"}" +
					",{\"uniformPath\":\"test2\",\"durationMillis\":123,\"result\":\"PASSED\"}" +
					",{\"uniformPath\":\"test3\",\"durationMillis\":123,\"result\":\"PASSED\"}]"
		)
	}
}
