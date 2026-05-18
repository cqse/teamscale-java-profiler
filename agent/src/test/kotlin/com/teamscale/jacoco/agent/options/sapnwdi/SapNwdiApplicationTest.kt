package com.teamscale.jacoco.agent.options.sapnwdi

import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication.Companion.parseApplications
import org.assertj.core.api.Assertions
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.Test

/** Tests for parsing the SAP NWDI application definition.  */
class SapNwdiApplicationTest {
	@Test
	fun testEmptyConfig() {
		Assertions.assertThatThrownBy { parseApplications("") }
			.hasMessage("Application definition is expected not to be empty.")
		Assertions.assertThatThrownBy { parseApplications(";") }
			.hasMessage("Application definition is expected not to be empty.")
	}

	@Test
	fun testIncompleteMarkerClassConfig() {
		Assertions.assertThatThrownBy { parseApplications(":alias") }
			.hasMessage("Marker class is not given for :alias!")
	}

	@Test
	fun testIncompleteProjectConfig() {
		Assertions.assertThatThrownBy { parseApplications("class:") }
			.hasMessage(
				"Application definition class: is expected to contain a marker class and project separated by a colon."
			)
	}

	@Test
	@Throws(Exception::class)
	fun testSingleApplication() {
		val configuration = parseApplications("com.teamscale.test2.Bar:alias")
		Assertions.assertThat(configuration).element(0)
			.satisfies(ThrowingConsumer { application ->
				Assertions.assertThat(application!!.markerClass).isEqualTo("com.teamscale.test2.Bar")
				Assertions.assertThat(application.teamscaleProject).isEqualTo("alias")
			})
	}

	@Test
	@Throws(Exception::class)
	fun testMultipleApplications() {
		val configuration =
			parseApplications("com.teamscale.test1.Bar:alias; com.teamscale.test2.Bar:id")
		Assertions.assertThat(configuration).element(0)
			.satisfies(ThrowingConsumer { application ->
				Assertions.assertThat(application!!.markerClass).isEqualTo("com.teamscale.test1.Bar")
				Assertions.assertThat(application.teamscaleProject).isEqualTo("alias")
			})
		Assertions.assertThat(configuration).element(1)
			.satisfies(ThrowingConsumer { application ->
				Assertions.assertThat(application!!.markerClass).isEqualTo("com.teamscale.test2.Bar")
				Assertions.assertThat(application.teamscaleProject).isEqualTo("id")
			})
	}
}
