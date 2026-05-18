package com.teamscale.jacoco.agent.logging

import com.teamscale.jacoco.agent.logging.LoggingUtils.getStackTraceAsString
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class LoggingUtilsTest {
	@Test
	fun testGetStackTraceAsString() {
		val causeEx = Exception("Test cause exception")
		val exception = Exception("Test exception", causeEx)
		val stackTrace = getStackTraceAsString(exception)

		Assertions.assertThat(stackTrace).contains("Test cause exception").contains("Test exception").contains("at ")
	}
}
