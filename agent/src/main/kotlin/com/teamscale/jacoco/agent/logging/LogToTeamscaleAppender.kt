package com.teamscale.jacoco.agent.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.status.ErrorStatus
import com.teamscale.client.ITeamscaleService
import com.teamscale.client.ProfilerLogEntry
import com.teamscale.jacoco.agent.options.AgentOptions
import java.net.ConnectException
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Custom log appender that sends logs to Teamscale; it buffers log that were not sent due to connection issues and
 * sends them later.
 */
class LogToTeamscaleAppender : AppenderBase<ILoggingEvent>() {
	/** The unique ID of the profiler  */
	private var profilerId: String? = null

	/**
	 * Thread-safe buffer for unsent logs. Using [ConcurrentLinkedQueue] for lock-free producer-consumer access.
	 */
	private val logBuffer = ConcurrentLinkedQueue<ProfilerLogEntry>()

	/** Scheduler for sending logs after the configured time interval  */
	private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1) { r ->
		val t = Executors.defaultThreadFactory().newThread(r)
		t.setDaemon(true)
		t
	}

	/** Active log flushing futures, tracked so [stop] can wait for them to finish.  */
	private val activeLogFlushes = CopyOnWriteArrayList<CompletableFuture<Void>>()

	override fun start() {
		super.start()
		scheduler.scheduleAtFixedRate(
			{ flush() },
			FLUSH_INTERVAL.toMillis(), FLUSH_INTERVAL.toMillis(), TimeUnit.MILLISECONDS
		)
	}

	override fun append(eventObject: ILoggingEvent) {
		logBuffer.add(formatLog(eventObject))
		if (logBuffer.size >= BATCH_SIZE) flush()
	}

	private fun formatLog(eventObject: ILoggingEvent): ProfilerLogEntry {
		val trace = LoggingUtils.getStackTraceFromEvent(eventObject)
		val timestamp = eventObject.timeStamp
		val message = eventObject.formattedMessage
		val severity = eventObject.level.toString()
		return ProfilerLogEntry(timestamp, message, trace, severity)
	}

	private fun flush() {
		val batch = mutableListOf<ProfilerLogEntry>()
		var count = 0
		while (count < BATCH_SIZE) {
			val entry = logBuffer.poll() ?: break
			batch.add(entry)
			count++
		}
		if (batch.isEmpty()) return

		val future = CompletableFuture.runAsync {
			val client = teamscaleClient ?: return@runAsync

			try {
				val response = client.postProfilerLog(profilerId!!, batch).execute()
				check(response.isSuccessful) { "Failed to send log: HTTP error code : ${response.code()}" }
			} catch (e: Exception) {
				if (e is ConnectException) {
					logBuffer.addAll(batch)
				} else {
					addStatus(ErrorStatus("Sending logs to Teamscale failed: ${e.message}", this, e))
					logBuffer.addAll(batch)
				}
			}
		}
		future.whenComplete { _, _ -> activeLogFlushes.remove(future) }
		activeLogFlushes.add(future)
	}

	override fun stop() {
		flush()

		scheduler.shutdown()
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow()
			}
		} catch (_: InterruptedException) {
			scheduler.shutdownNow()
		}

		flush()

		CompletableFuture.allOf(*activeLogFlushes.toTypedArray()).join()

		super.stop()
	}

	fun setTeamscaleClient(teamscaleClient: ITeamscaleService?) {
		Companion.teamscaleClient = teamscaleClient
	}

	fun setProfilerId(profilerId: String) {
		this.profilerId = profilerId
	}

	companion object {
		/** Flush the logs after N elements are in the queue  */
		private const val BATCH_SIZE = 50

		/** Flush the logs in the given time interval  */
		private val FLUSH_INTERVAL: Duration = Duration.ofSeconds(3)

		/** The service client for sending logs to Teamscale  */
		private var teamscaleClient: ITeamscaleService? = null

		/**
		 * Add the [LogToTeamscaleAppender] to the logging configuration and
		 * enable/start it.
		 */
		fun addTeamscaleAppenderTo(context: LoggerContext, agentOptions: AgentOptions): Boolean {
			val client = agentOptions.createTeamscaleClient(false)
			if (client == null || agentOptions.configurationViaTeamscale == null) {
				return false
			}

			context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
				val logToTeamscaleAppender = LogToTeamscaleAppender().apply {
					setContext(context)
					setProfilerId(agentOptions.configurationViaTeamscale!!.profilerId!!)
					setTeamscaleClient(client.service)
					start()
				}
				addAppender(logToTeamscaleAppender)
			}

			return true
		}
	}
}
