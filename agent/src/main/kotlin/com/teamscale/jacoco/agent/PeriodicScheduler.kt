package com.teamscale.jacoco.agent

import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration

/** Runs actions periodically. Tests replace it to check a schedule without waiting for it. */
interface PeriodicScheduler {
	/** Runs [action] every [period], first after one [period]. Returns a function that cancels these runs. */
	fun schedule(period: Duration, action: () -> Unit): () -> Unit

	/** Cancels every run of this scheduler. */
	fun shutdown()
}

/** Runs actions periodically on a daemon [Timer] thread with the given name. */
class TimerScheduler(threadName: String) : PeriodicScheduler {
	private val timer = Timer(threadName, true)

	override fun schedule(period: Duration, action: () -> Unit): () -> Unit {
		val periodInMillis = period.inWholeMilliseconds
		val task = timer.scheduleAtFixedRate(periodInMillis, periodInMillis) { action() }
		return { task.cancel() }
	}

	override fun shutdown() {
		timer.cancel()
	}
}
