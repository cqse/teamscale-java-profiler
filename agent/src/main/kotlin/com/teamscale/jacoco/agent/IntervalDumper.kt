package com.teamscale.jacoco.agent

import org.slf4j.Logger
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the agent's interval dumps. Dumps every hour once an interval below one hour saw a successful upload, see
 * [DumpIntervalGuard].
 */
class IntervalDumper(
	private val configuredIntervalInMinutes: Int,
	/** Whether the agent sends its coverage to a server, rather than only writing it to the local disk. */
	isUploading: Boolean,
	private val logger: Logger,
	private val scheduler: PeriodicScheduler,
	private val dump: () -> Unit
) {
	private val guard = DumpIntervalGuard(configuredIntervalInMinutes, isUploading)

	private var cancelCurrentSchedule: (() -> Unit)? = null

	private var isStopped = false

	/** Logs the interval and schedules the first dump. */
	@Synchronized
	fun start() {
		if (guard.isShortUploadInterval) {
			logger.warn(
				"You configured an interval smaller than 1h. This causes excessive amounts of data to be sent to the" +
						" server. This interval applies until the first successful upload, then the interval increases to 1h."
			)
		} else {
			logger.info("Dumping every $configuredIntervalInMinutes minutes.")
		}
		scheduleDumps()
	}

	/**
	 * Records that a dump uploaded coverage. The first such upload under an interval below one hour reschedules the
	 * dumps to every hour and logs the increase. After [stop], it does neither.
	 */
	@Synchronized
	fun recordSuccessfulUpload() {
		if (isStopped || !guard.raiseAfterSuccessfulUpload()) {
			return
		}

		logger.info(
			"Increased the coverage dump interval from $configuredIntervalInMinutes" +
					" to ${guard.dumpIntervalInMinutes} minutes."
		)
		scheduleDumps()
	}

	/** Cancels every dump this dumper scheduled. */
	@Synchronized
	fun stop() {
		isStopped = true
		scheduler.shutdown()
	}

	private fun scheduleDumps() {
		cancelCurrentSchedule?.invoke()
		cancelCurrentSchedule = scheduler.schedule(guard.dumpIntervalInMinutes.minutes, dump)
	}
}
