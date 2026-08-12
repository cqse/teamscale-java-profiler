package com.teamscale.logging

import com.teamscale.test.commons.ProcessUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Regression tests for TS-23151: a misconfigured profiler must report the problem on the console.
 *
 * By default the agent logs into a temporary directory that users have no realistic way of finding, and it never
 * fails the profiled application. So if these messages do not reach the console, the only symptom of a broken
 * configuration is an application that runs perfectly and silently collects no coverage.
 *
 * These tests replace the former manual `sample-app/run-log-test.sh` script.
 */
class InvalidOptionsLoggingSystemTest {

	/**
	 * Options that fail to parse are reported on stderr. This covers errors raised while the options are still being
	 * parsed, i.e. before the configured logging is up, which is what makes them easy to lose.
	 */
	@Test
	fun optionParseErrorIsPrintedToTheConsole() {
		val result = ProcessUtils.execute(
			"java", "-javaagent:$AGENT_JAR=config-id=foo", "-jar", SYSTEM_UNDER_TEST_JAR
		)

		assertThat(result.stderr)
			.`as`("the parse error must reach the console and not only the log file")
			.contains("Failed to parse agent options")
			.contains("teamscale-server-url")
		assertThat(result.exitCode)
			.`as`("a configuration error must never stop the profiled application from starting")
			.isEqualTo(0)
		assertThat(result.stdout).contains("Production code")
	}

	/**
	 * If the log directory cannot be written to, the agent says so on the console instead of losing the message it
	 * was about to write into exactly that directory.
	 */
	@Test
	@DisabledOnOs(OS.WINDOWS, disabledReason = "file permissions behave differently on Windows")
	fun unwritableLogDirectoryIsReportedOnTheConsole(@TempDir tempDirectory: Path) {
		val logDirectory = Files.createDirectory(tempDirectory.resolve("read-only"))
		logDirectory.toFile().setWritable(false)
		assumeTrue(!Files.isWritable(logDirectory), "requires a non-writable directory, so cannot run as root")

		val result = ProcessUtils.execute(
			"java", "-javaagent:$AGENT_JAR=debug=$logDirectory", "-jar", SYSTEM_UNDER_TEST_JAR
		)

		assertThat(result.stdout)
			.`as`("the agent must report that it cannot write its logs")
			.contains("Could not create debug log directory")
			.contains("Falling back to console-only logging")
		assertThat(result.exitCode).isEqualTo(0)
		assertThat(result.stdout).contains("Production code")
	}

	companion object {
		private val AGENT_JAR: String = System.getenv("AGENT_JAR")
		private val SYSTEM_UNDER_TEST_JAR: String = System.getenv("SYSTEM_UNDER_TEST_JAR")
	}
}
