package com.teamscale.jacoco.agent.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.status.ErrorStatus;
import com.teamscale.client.ITeamscaleService;
import com.teamscale.client.ProfilerLogEntry;
import com.teamscale.client.TeamscaleClient;
import com.teamscale.jacoco.agent.options.AgentOptions;
import org.jetbrains.annotations.Nullable;
import retrofit2.Call;

import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.teamscale.jacoco.agent.logging.LoggingUtils.getStackTraceFromEvent;

/**
 * Custom log appender that sends logs to Teamscale; it buffers log that were not sent due to connection issues and
 * sends them later.
 */
public class LogToTeamscaleAppender extends AppenderBase<ILoggingEvent> {

	/** Flush the logs after N elements are in the queue */
	private static final int BATCH_SIZE = 50;

	/** Flush the logs in the given time interval */
	private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(3);

	/** The unique ID of the profiler */
	private String profilerId;

	/** The service client for sending logs to Teamscale */
	private static ITeamscaleService teamscaleClient;

	/**
	 * Buffer for unsent logs. We use a set here to allow for removing entries fast after sending them to Teamscale was
	 * successful.
	 */
	private final LinkedHashSet<ProfilerLogEntry> logBuffer = new LinkedHashSet<>();

	/** Scheduler for sending logs after the configured time interval */
	private final ScheduledExecutorService scheduler;

	/** Active log flushing threads */
	private final Set<CompletableFuture<Void>> activeLogFlushes = Collections.newSetFromMap(new IdentityHashMap<>());

	/** Is there a flush going on right now? */
	private final AtomicBoolean isFlusing = new AtomicBoolean(false);

	public LogToTeamscaleAppender() {
		this.scheduler = Executors.newScheduledThreadPool(1, r -> {
			// Make the thread a daemon so that it does not prevent the JVM from terminating.
			Thread t = Executors.defaultThreadFactory().newThread(r);
			t.setDaemon(true);
			return t;
		});
	}

	@Override
	public void start() {
		super.start();
		scheduler.scheduleAtFixedRate(() -> {
			synchronized (activeLogFlushes) {
				activeLogFlushes.removeIf(CompletableFuture::isDone);
				if (this.activeLogFlushes.isEmpty()) {
					flush();
				}
			}
		}, FLUSH_INTERVAL.toMillis(), FLUSH_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		synchronized (logBuffer) {
			logBuffer.add(formatLog(eventObject));
			if (logBuffer.size() >= BATCH_SIZE) {
				flush();
			}
		}
	}

	private ProfilerLogEntry formatLog(ILoggingEvent eventObject) {
		String trace = getStackTraceFromEvent(eventObject);
		long timestamp = eventObject.getTimeStamp();
		String message = eventObject.getFormattedMessage();
		String severity = eventObject.getLevel().toString();
		return new ProfilerLogEntry(timestamp, message, trace, severity);
	}

	private void flush() {
		sendLogs();
	}

	/** Send logs in a separate thread */
	private void sendLogs() {
		synchronized (activeLogFlushes) {
			activeLogFlushes.add(CompletableFuture.runAsync(() -> {
				if (isFlusing.compareAndSet(false, true)) {
					try {
						if (teamscaleClient == null) {
							// There might be no connection configured.
							return;
						}

						List<ProfilerLogEntry> logsToSend;
						synchronized (logBuffer) {
							logsToSend = new ArrayList<>(logBuffer);
						}

						Call<Void> call = teamscaleClient.postProfilerLog(profilerId, logsToSend);
						retrofit2.Response<Void> response = call.execute();
						if (!response.isSuccessful()) {
							throw new IllegalStateException("Failed to send log: HTTP error code : " + response.code());
						}

						synchronized (logBuffer) {
							// Removing the logs that have been sent after the fact.
							// This handles problems with lost network connections.
							logsToSend.forEach(logBuffer::remove);
						}
					} catch (Exception e) {
						// We do not report on exceptions here.
						if (!(e instanceof ConnectException)) {
							addStatus(new ErrorStatus("Sending logs to Teamscale failed: " + e.getMessage(), this, e));
						}
					} finally {
						isFlusing.set(false);
					}
				}
			}).whenComplete((result, throwable) -> {
				synchronized (activeLogFlushes) {
					activeLogFlushes.removeIf(CompletableFuture::isDone);
				}
			}));
		}
	}

	@Override
	public void stop() {
		// Already flush here once to make sure that we do not miss too much.
		flush();

		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
		}

		// A final flush after the scheduler has been shut down.
		flush();

		// Block until all flushes are done
		CompletableFuture.allOf(activeLogFlushes.toArray(new CompletableFuture[0])).join();

		super.stop();
	}

	public void setTeamscaleClient(ITeamscaleService teamscaleClient) {
		this.teamscaleClient = teamscaleClient;
	}

	public void setProfilerId(String profilerId) {
		this.profilerId = profilerId;
	}

	/**
	 * Add the {@link com.teamscale.jacoco.agent.logging.LogToTeamscaleAppender} to the logging configuration and
	 * enable/start it.
	 */
	public static boolean addTeamscaleAppenderTo(LoggerContext context, AgentOptions agentOptions) {
		@Nullable TeamscaleClient client = agentOptions.createTeamscaleClient(
				false);
		if (client == null || agentOptions.configurationViaTeamscale == null) {
			return false;
		}

		ITeamscaleService serviceClient = client.getService();
		LogToTeamscaleAppender logToTeamscaleAppender = new LogToTeamscaleAppender();
		logToTeamscaleAppender.setContext(context);
		logToTeamscaleAppender.setProfilerId(agentOptions.configurationViaTeamscale.getProfilerId());
		logToTeamscaleAppender.setTeamscaleClient(serviceClient);
		logToTeamscaleAppender.start();

		Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
		rootLogger.addAppender(logToTeamscaleAppender);

		return true;
	}

}
