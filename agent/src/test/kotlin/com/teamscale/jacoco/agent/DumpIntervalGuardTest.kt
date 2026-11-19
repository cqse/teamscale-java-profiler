package com.teamscale.jacoco.agent

import com.teamscale.jacoco.agent.DumpIntervalGuard.Companion.MINIMUM_UPLOAD_INTERVAL_IN_MINUTES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DumpIntervalGuardTest {
	@Test
	fun keepsShortUploadIntervalUntilFirstSuccessfulUpload() {
		val guard = DumpIntervalGuard(1, isUploading = true)

		assertThat(guard.isShortUploadInterval)
			.`as`("An interval of one minute for an agent that uploads should count as short")
			.isTrue()
		assertThat(guard.dumpIntervalInMinutes)
			.`as`("An agent that has not uploaded yet should keep its short interval")
			.isEqualTo(1)
	}

	@Test
	fun raisesShortUploadIntervalToOneHourAfterFirstSuccessfulUpload() {
		val guard = DumpIntervalGuard(1, isUploading = true)

		val hasRaised = guard.raiseAfterSuccessfulUpload()

		assertThat(hasRaised).`as`("The first successful upload should raise a short interval").isTrue()
		assertThat(guard.dumpIntervalInMinutes)
			.`as`("An agent that uploaded should dump every hour")
			.isEqualTo(MINIMUM_UPLOAD_INTERVAL_IN_MINUTES)
	}

	@Test
	fun raisesShortUploadIntervalOnlyOnce() {
		val guard = DumpIntervalGuard(1, isUploading = true)

		guard.raiseAfterSuccessfulUpload()

		assertThat(guard.raiseAfterSuccessfulUpload())
			.`as`("A second successful upload should not raise the interval again")
			.isFalse()
	}

	@Test
	fun appliesIntervalOfOneHourOrMoreAsConfigured() {
		val oneHour = DumpIntervalGuard(60, isUploading = true)
		val eightHours = DumpIntervalGuard(480, isUploading = true)

		assertThat(oneHour.isShortUploadInterval).`as`("An interval of one hour should not count as short").isFalse()
		assertThat(oneHour.raiseAfterSuccessfulUpload()).`as`("An interval of one hour should never be raised").isFalse()
		assertThat(eightHours.raiseAfterSuccessfulUpload()).`as`("A long interval should never be raised").isFalse()
		assertThat(eightHours.dumpIntervalInMinutes).`as`("A long interval should stay as configured").isEqualTo(480)
	}

	@Test
	fun keepsShortIntervalOfAgentThatOnlyWritesToLocalDisk() {
		val guard = DumpIntervalGuard(1, isUploading = false)

		assertThat(guard.isShortUploadInterval)
			.`as`("A short interval of an agent that only writes to the local disk should not count as short")
			.isFalse()
		assertThat(guard.raiseAfterSuccessfulUpload())
			.`as`("An agent that only writes to the local disk should keep its short interval")
			.isFalse()
		assertThat(guard.dumpIntervalInMinutes).isEqualTo(1)
	}

	@Test
	fun leavesDisabledIntervalAlone() {
		val guard = DumpIntervalGuard(0, isUploading = true)

		assertThat(guard.isShortUploadInterval)
			.`as`("An interval of 0 turns off interval dumps and should not count as short")
			.isFalse()
		assertThat(guard.raiseAfterSuccessfulUpload())
			.`as`("An upload through the HTTP /dump endpoint should not start interval dumps")
			.isFalse()
		assertThat(guard.dumpIntervalInMinutes).isEqualTo(0)
	}
}
