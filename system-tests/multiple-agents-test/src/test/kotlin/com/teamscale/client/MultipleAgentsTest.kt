package com.teamscale.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Paths
import kotlin.io.path.readLines

class MultipleAgentsTest {
	@Test
	@Throws(IOException::class)
	fun systemTest() {
		assertThat(LOG_DIRECTORY).exists()
		val lines = LOG_DIRECTORY.resolve("teamscale-jacoco-agent.log").readLines()
		assertThat(lines)
			.anyMatch { it.contains("Using multiple java agents could interfere with coverage recording") }
		assertThat(lines)
			.anyMatch { it.contains("For best results consider registering the Teamscale Java Profiler first.") }
	}

	companion object {
		private val LOG_DIRECTORY = Paths.get("logTest").resolve("logs")
	}
}
