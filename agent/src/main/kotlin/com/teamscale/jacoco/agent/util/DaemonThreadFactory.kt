package com.teamscale.jacoco.agent.util

import java.util.concurrent.ThreadFactory

/**
 * [java.util.concurrent.ThreadFactory] that only produces deamon threads (threads that don't prevent JVM shutdown) with a fixed name.
 */
class DaemonThreadFactory(owningClass: Class<*>, threadName: String?) : ThreadFactory {
	private val threadName = "Teamscale Java Profiler ${owningClass.getSimpleName()} $threadName"

	override fun newThread(runnable: Runnable) =
		Thread(runnable, threadName).apply {
			setDaemon(true)
		}
}