package com.teamscale.jacoco.agent.convert

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Tests the command line parsing of the [ConvertCommand]. */
class ConvertCommandTest {

	/** Ensures that the command line is rejected if one of the required options is missing. */
	@ParameterizedTest
	@ValueSource(strings = ["--class-dir", "--in", "--out"])
	fun `missing required option is rejected`(missingOption: String) {
		val arguments = mapOf("--class-dir" to "classes", "--in" to "coverage.exec", "--out" to "coverage.xml")
			.filterKeys { it != missingOption }
			.flatMap { (option, value) -> listOf(option, value) }
		val jCommander = JCommander.newBuilder().addObject(ConvertCommand()).build()

		assertThatThrownBy { jCommander.parse(*arguments.toTypedArray()) }
			.isInstanceOf(ParameterException::class.java)
			.hasMessageContaining("The following option is required")
			.hasMessageContaining(missingOption)
	}
}
