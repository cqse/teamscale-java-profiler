package com.teamscale.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Ensures that the agent warns at startup when a dump `interval` smaller than 1h is configured (see PST-122), so the
 * user is told that the interval will be applied only once and then raised to 1h.
 */
class DumpIntervalTooSmallTest {
	@Test
	@Throws(Exception::class)
	fun systemTest() {
		assertTrue(LOG_DIRECTORY.exists())
		val logContent = LOG_DIRECTORY.resolve("teamscale-jacoco-agent.log")
			.readLines()
			.joinToString("\n")
		assertThat(logContent).containsPattern("WARN.*You configured an interval smaller than 1h")
	}

	companion object {
		private val LOG_DIRECTORY = Paths.get("logTest").resolve("logs")
	}
}
