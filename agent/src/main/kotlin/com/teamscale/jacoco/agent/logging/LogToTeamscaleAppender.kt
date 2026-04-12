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
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

/**
 * Custom log appender that sends logs to Teamscale; it buffers log that were not sent due to connection issues and
 * sends them later.
 */
class LogToTeamscaleAppender : AppenderBase<ILoggingEvent>() {
	/** The unique ID of the profiler  */
	private var profilerId: String? = null

	/**
	 * Buffer for unsent logs. We use a set here to allow for removing entries fast after sending them to Teamscale was
	 * successful.
	 */
	private val logBuffer = LinkedHashSet<ProfilerLogEntry>()

	/** Scheduler for sending logs after the configured time interval  */
	private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1) { r ->
		// Make the thread a daemon so that it does not prevent the JVM from terminating.
		val t = Executors.defaultThreadFactory().newThread(r)
		t.setDaemon(true)
		t
	}

	/** Active log flushing threads  */
	private val activeLogFlushes: MutableSet<CompletableFuture<Void>> =
		Collections.newSetFromMap(IdentityHashMap())

	/** Is there a flush going on right now?  */
	private val isFlusing = AtomicBoolean(false)

	override fun start() {
		super.start()
		scheduler.scheduleAtFixedRate({
			synchronized(activeLogFlushes) {
				activeLogFlushes.removeIf { it.isDone }
				if (activeLogFlushes.isEmpty()) flush()
			}
		}, FLUSH_INTERVAL.toMillis(), FLUSH_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)
	}

	override fun append(eventObject: ILoggingEvent) {
		synchronized(logBuffer) {
			logBuffer.add(formatLog(eventObject))
			if (logBuffer.size >= BATCH_SIZE) flush()
		}
	}

	private fun formatLog(eventObject: ILoggingEvent): ProfilerLogEntry {
		val trace = LoggingUtils.getStackTraceFromEvent(eventObject)
		val timestamp = eventObject.timeStamp
		val message = eventObject.formattedMessage
		val severity = eventObject.level.toString()
		return ProfilerLogEntry(timestamp, message, trace, severity)
	}

	private fun flush() {
		sendLogs()
	}

	/** Send logs in a separate thread  */
	private fun sendLogs() {
		synchronized(activeLogFlushes) {
			activeLogFlushes.add(CompletableFuture.runAsync {
				if (isFlusing.compareAndSet(false, true)) {
					try {
						val client = teamscaleClient ?: return@runAsync // There might be no connection configured.

						val logsToSend: MutableList<ProfilerLogEntry>
						synchronized(logBuffer) {
							logsToSend = logBuffer.toMutableList()
						}

						val call = client.postProfilerLog(profilerId!!, logsToSend)
						val response = call.execute()
						check(response.isSuccessful) { "Failed to send log: HTTP error code : ${response.code()}" }

						synchronized(logBuffer) {
							// Removing the logs that have been sent after the fact.
							// This handles problems with lost network connections.
							logBuffer.removeAll(logsToSend.toSet())
						}
					} catch (e: Exception) {
						// We do not report on exceptions here.
						if (e !is ConnectException) {
							addStatus(ErrorStatus("Sending logs to Teamscale failed: ${e.message}", this, e))
						}
					} finally {
						isFlusing.set(false)
					}
				}
			}.whenComplete(BiConsumer { _, _ ->
				synchronized(activeLogFlushes) {
					activeLogFlushes.removeIf { it.isDone }
				}
			}))
		}
	}

	override fun stop() {
		// Already flush here once to make sure that we do not miss too much.
		flush()

		scheduler.shutdown()
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow()
			}
		} catch (_: InterruptedException) {
			scheduler.shutdownNow()
		}

		// A final flush after the scheduler has been shut down.
		flush()

		// Block until all flushes are done
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