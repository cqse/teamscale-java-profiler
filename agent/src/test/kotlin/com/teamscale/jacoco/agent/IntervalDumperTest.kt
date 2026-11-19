package com.teamscale.jacoco.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class IntervalDumperTest {
	private val logger = mock<Logger>()

	private val scheduler = FakeScheduler()

	@Test
	fun schedulesDumpsAtConfiguredShortInterval() {
		var dumpCount = 0
		val dumper = IntervalDumper(1, isUploading = true, logger, scheduler) { dumpCount++ }

		dumper.start()
		scheduler.activeSchedules.single().action()

		assertThat(scheduler.activeSchedules.map { it.period })
			.`as`("An agent that has not uploaded yet should dump at its short interval")
			.containsExactly(1.minutes)
		assertThat(dumpCount).`as`("Each scheduled run should dump").isEqualTo(1)
	}

	@Test
	fun warnsAtStartAboutShortUploadInterval() {
		IntervalDumper(1, isUploading = true, logger, scheduler) {}.start()

		verify(logger).warn(
			"You configured an interval smaller than 1h. This causes excessive amounts of data to be sent to the" +
					" server. This interval applies until the first successful upload, then the interval increases to 1h."
		)
	}

	@Test
	fun logsLongIntervalAtStartWithoutWarning() {
		IntervalDumper(480, isUploading = true, logger, scheduler) {}.start()

		verify(logger).info("Dumping every 480 minutes.")
		verify(logger, never()).warn(any<String>())
	}

	@Test
	fun reschedulesEveryHourAfterFirstSuccessfulUpload() {
		val dumper = IntervalDumper(1, isUploading = true, logger, scheduler) {}
		dumper.start()

		dumper.recordSuccessfulUpload()

		assertThat(scheduler.activeSchedules.map { it.period })
			.`as`("The first successful upload should replace the short schedule by an hourly one")
			.containsExactly(60.minutes)
		verify(logger).info("Increased the coverage dump interval from 1 to 60 minutes.")
	}

	@Test
	fun keepsHourlyScheduleAfterLaterUploads() {
		val dumper = IntervalDumper(1, isUploading = true, logger, scheduler) {}
		dumper.start()

		dumper.recordSuccessfulUpload()
		dumper.recordSuccessfulUpload()

		assertThat(scheduler.schedules)
			.`as`("A later successful upload should not schedule again")
			.hasSize(2)
	}

	@Test
	fun keepsScheduleOfAgentThatOnlyWritesToLocalDisk() {
		val dumper = IntervalDumper(1, isUploading = false, logger, scheduler) {}
		dumper.start()

		dumper.recordSuccessfulUpload()

		assertThat(scheduler.activeSchedules.map { it.period })
			.`as`("An agent that only writes to the local disk should keep dumping every minute")
			.containsExactly(1.minutes)
		verify(logger, never()).warn(any<String>())
	}

	@Test
	fun neitherReschedulesNorLogsIncreaseAfterStop() {
		val dumper = IntervalDumper(1, isUploading = true, logger, scheduler) {}
		dumper.start()

		dumper.stop()
		dumper.recordSuccessfulUpload()

		assertThat(scheduler.isShutDown).`as`("Stopping should shut the scheduler down").isTrue()
		assertThat(scheduler.schedules)
			.`as`("An upload by the dump at shutdown should not schedule again")
			.hasSize(1)
		verify(logger, never()).info("Increased the coverage dump interval from 1 to 60 minutes.")
	}

	private class FakeScheduler : PeriodicScheduler {
		val schedules = mutableListOf<Schedule>()

		var isShutDown = false

		val activeSchedules
			get() = schedules.filterNot { it.isCancelled }

		override fun schedule(period: Duration, action: () -> Unit): () -> Unit {
			val schedule = Schedule(period, action)
			schedules += schedule
			return { schedule.isCancelled = true }
		}

		override fun shutdown() {
			isShutDown = true
		}

		class Schedule(val period: Duration, val action: () -> Unit) {
			var isCancelled = false
		}
	}
}
