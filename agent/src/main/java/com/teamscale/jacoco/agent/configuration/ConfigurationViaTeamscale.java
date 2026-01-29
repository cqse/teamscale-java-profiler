package com.teamscale.jacoco.agent.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.teamscale.client.ITeamscaleService;
import com.teamscale.client.JsonUtils;
import com.teamscale.client.ProcessInformation;
import com.teamscale.client.ProfilerConfiguration;
import com.teamscale.client.ProfilerInfo;
import com.teamscale.client.ProfilerRegistration;
import com.teamscale.client.TeamscaleServiceGenerator;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.util.AgentUtils;
import com.teamscale.report.util.ILogger;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for holding the configuration that was retrieved from Teamscale and sending regular heartbeat events to
 * keep the profiler information in Teamscale up to date.
 */
public class ConfigurationViaTeamscale {

	/**
	 * Two minute timeout. This is quite high to account for an eventual high load on the Teamscale server. This is a
	 * tradeoff between fast application startup and potentially missing test coverage.
	 */
	private static final Duration LONG_TIMEOUT = Duration.ofMinutes(2);

	/**
	 * The UUID that Teamscale assigned to this instance of the profiler during the registration. This ID needs to be
	 * used when communicating with Teamscale.
	 */
	private final String profilerId;

	private final ITeamscaleService teamscaleClient;
	private final ProfilerInfo profilerInfo;

	public ConfigurationViaTeamscale(ITeamscaleService teamscaleClient, ProfilerRegistration profilerRegistration,
			ProcessInformation processInformation) {
		this.teamscaleClient = teamscaleClient;
		this.profilerId = profilerRegistration.profilerId;
		this.profilerInfo = new ProfilerInfo(processInformation, profilerRegistration.profilerConfiguration);
	}

	/**
	 * Tries to retrieve the profiler configuration from Teamscale. In case retrieval fails the method throws a
	 * {@link AgentOptionReceiveException}.
	 */
	public static @NotNull ConfigurationViaTeamscale retrieve(ILogger logger, String configurationId, HttpUrl url,
			String userName,
			String userAccessToken) throws AgentOptionReceiveException {
		ITeamscaleService teamscaleClient = TeamscaleServiceGenerator
				.createService(ITeamscaleService.class, url, userName, userAccessToken, AgentUtils.USER_AGENT,
						LONG_TIMEOUT, LONG_TIMEOUT);
		try {
			ProcessInformation processInformation = new ProcessInformationRetriever(logger).getProcessInformation();
			Response<ResponseBody> response = teamscaleClient.registerProfiler(configurationId,
					processInformation).execute();
			if (!response.isSuccessful()) {
				throw new AgentOptionReceiveException(
						"Failed to retrieve profiler configuration from Teamscale due to failed request. Http status: " + response.code()
								+ " Body: " + response.errorBody().string());
			}

			ResponseBody body = response.body();
			return parseProfilerRegistration(body, response, teamscaleClient, processInformation);
		} catch (IOException e) {
			// we include the causing error message in this exception's message since this causes it to be printed
			// to stderr which is much more helpful than just saying "something didn't work"
			throw new AgentOptionReceiveException(
					"Failed to retrieve profiler configuration from Teamscale due to network error: " + LoggingUtils.getStackTraceAsString(
							e),
					e);
		}
	}

	private static @NotNull ConfigurationViaTeamscale parseProfilerRegistration(ResponseBody body,
			Response<ResponseBody> response, ITeamscaleService teamscaleClient,
			ProcessInformation processInformation) throws AgentOptionReceiveException, IOException {
		if (body == null) {
			throw new AgentOptionReceiveException(
					"Failed to retrieve profiler configuration from Teamscale due to empty response. HTTP code: " + response.code());
		}
		// We may only call this once
		String bodyString = body.string();
		try {
			ProfilerRegistration registration = JsonUtils.deserialize(bodyString,
					ProfilerRegistration.class);
			if (registration == null) {
				throw new AgentOptionReceiveException(
						"Failed to retrieve profiler configuration from Teamscale due to invalid JSON. HTTP code: " + response.code() + " Response: " + bodyString);
			}
			return new ConfigurationViaTeamscale(teamscaleClient, registration, processInformation);
		} catch (JsonProcessingException e) {
			throw new AgentOptionReceiveException(
					"Failed to retrieve profiler configuration from Teamscale due to invalid JSON. HTTP code: " + response.code() + " Response: " + bodyString,
					e);
		}
	}

	/** Returns the profiler configuration that was retrieved from Teamscale. */
	public ProfilerConfiguration getProfilerConfiguration() {
		return profilerInfo.profilerConfiguration;
	}


	/**
	 * Starts a heartbeat thread and registers a shutdown hook.
	 * <p>
	 * This spawns a new thread every minute which sends a heartbeat to Teamscale. It also registers a shutdown hook
	 * that unregisters the profiler from Teamscale.
	 */
	public void startHeartbeatThreadAndRegisterShutdownHook() {
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable);
			thread.setDaemon(true);
			return thread;
		});

		executor.scheduleAtFixedRate(this::sendHeartbeat, 1, 1, TimeUnit.MINUTES);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			executor.shutdownNow();
			unregisterProfiler();
		}));
	}

	private void sendHeartbeat() {
		try {
			Response<ResponseBody> response = teamscaleClient.sendHeartbeat(profilerId, profilerInfo).execute();
			if (!response.isSuccessful()) {
				LoggingUtils.getLogger(this)
						.error("Failed to send heartbeat. Teamscale responded with: " + response.errorBody().string());
			}
		} catch (IOException e) {
			LoggingUtils.getLogger(this).error("Failed to send heartbeat to Teamscale!", e);
		}
	}

	/** Unregisters the profiler in Teamscale (marks it as shut down). */
	public void unregisterProfiler() {
		try {
			Response<ResponseBody> response = teamscaleClient.unregisterProfiler(profilerId).execute();
			if (response.code() == 405) {
				response = teamscaleClient.unregisterProfilerLegacy(profilerId).execute();
			}
			if (!response.isSuccessful()) {
				LoggingUtils.getLogger(this)
						.error("Failed to unregister profiler. Teamscale responded with: " + response.errorBody()
								.string());
			}
		} catch (IOException e) {
			LoggingUtils.getLogger(this).error("Failed to unregister profiler!", e);
		}
	}

	public String getProfilerId() {
		return profilerId;
	}
}
