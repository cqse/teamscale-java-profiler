package com.teamscale.jacoco.agent;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.HttpUtils;
import com.teamscale.client.StringUtils;
import com.teamscale.jacoco.agent.configuration.AgentOptionReceiveException;
import com.teamscale.jacoco.agent.logging.DebugLogDirectoryPropertyDefiner;
import com.teamscale.jacoco.agent.logging.LogDirectoryPropertyDefiner;
import com.teamscale.jacoco.agent.logging.LogToTeamscaleAppender;
import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.options.AgentOptionParseException;
import com.teamscale.jacoco.agent.options.AgentOptions;
import com.teamscale.jacoco.agent.options.AgentOptionsParser;
import com.teamscale.jacoco.agent.options.FilePatternResolver;
import com.teamscale.jacoco.agent.options.JacocoAgentOptionsBuilder;
import com.teamscale.jacoco.agent.options.TeamscaleCredentials;
import com.teamscale.jacoco.agent.options.TeamscalePropertiesUtils;
import com.teamscale.jacoco.agent.testimpact.TestwiseCoverageAgent;
import com.teamscale.jacoco.agent.upload.UploaderException;
import com.teamscale.jacoco.agent.util.AgentUtils;
import com.teamscale.report.util.ILogger;
import kotlin.Pair;
import org.conqat.lib.commons.collections.CollectionUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static com.teamscale.jacoco.agent.logging.LoggingUtils.getLoggerContext;

/** Container class for the premain entry point for the agent. */
public class PreMain {

	private static LoggingUtils.LoggingResources loggingResources = null;

	/**
	 * System property that we use to prevent this agent from being attached to the same VM twice. This can happen if
	 * the agent is registered via multiple JVM environment variables and/or the command line at the same time.
	 */
	private static final String LOCKING_SYSTEM_PROPERTY = "TEAMSCALE_JAVA_PROFILER_ATTACHED";

	/**
	 * Environment variable from which to read the config ID to use. This is an ID for a profiler configuration that is
	 * stored in Teamscale.
	 */
	private static final String CONFIG_ID_ENVIRONMENT_VARIABLE = "TEAMSCALE_JAVA_PROFILER_CONFIG_ID";

	/** Environment variable from which to read the config file to use. */
	private static final String CONFIG_FILE_ENVIRONMENT_VARIABLE = "TEAMSCALE_JAVA_PROFILER_CONFIG_FILE";

	/** Environment variable from which to read the Teamscale access token. */
	private static final String ACCESS_TOKEN_ENVIRONMENT_VARIABLE = "TEAMSCALE_ACCESS_TOKEN";

	/**
	 * Entry point for the agent, called by the JVM.
	 */
	public static void premain(String options, Instrumentation instrumentation) throws Exception {
		if (System.getProperty(LOCKING_SYSTEM_PROPERTY) != null) {
			return;
		}
		System.setProperty(LOCKING_SYSTEM_PROPERTY, "true");

		String environmentConfigId = System.getenv(CONFIG_ID_ENVIRONMENT_VARIABLE);
		String environmentConfigFile = System.getenv(CONFIG_FILE_ENVIRONMENT_VARIABLE);
		if (StringUtils.isEmpty(options) && environmentConfigId == null && environmentConfigFile == null) {
			// profiler was registered globally and no config was set explicitly by the user, thus ignore this process
			// and don't profile anything
			return;
		}

		AgentOptions agentOptions = null;
		try {
			Pair<AgentOptions, List<Exception>> parseResult = getAndApplyAgentOptions(options, environmentConfigId,
					environmentConfigFile);
			agentOptions = parseResult.getFirst();

			// After parsing everything and configuring logging, we now
			// can throw the caught exceptions.
			for (Exception exception : parseResult.getSecond()) {
				throw exception;
			}
		} catch (AgentOptionParseException e) {
			getLoggerContext().getLogger(PreMain.class).error(e.getMessage(), e);

			// Flush logs to Teamscale, if configured.
			closeLoggingResources();

			// Unregister the profiler from Teamscale.
			if (agentOptions != null && agentOptions.configurationViaTeamscale != null) {
				agentOptions.configurationViaTeamscale.unregisterProfiler();
			}

			throw e;
		} catch (AgentOptionReceiveException e) {
			// When Teamscale is not available, we don't want to fail hard to still allow for testing even if no
			// coverage is collected (see TS-33237)
			return;
		}

		Logger logger = LoggingUtils.getLogger(Agent.class);

		logger.info("Teamscale Java profiler version " + AgentUtils.VERSION);
		logger.info("Starting JaCoCo's agent");
		JacocoAgentOptionsBuilder agentBuilder = new JacocoAgentOptionsBuilder(agentOptions);
		JaCoCoPreMain.premain(agentBuilder.createJacocoAgentOptions(), instrumentation, logger);

		if (agentOptions.configurationViaTeamscale != null) {
			agentOptions.configurationViaTeamscale.startHeartbeatThreadAndRegisterShutdownHook();
		}
		AgentBase agent = createAgent(agentOptions, instrumentation);
		agent.registerShutdownHook();
	}

	private static Pair<AgentOptions, List<Exception>> getAndApplyAgentOptions(String options,
			String environmentConfigId,
			String environmentConfigFile) throws AgentOptionParseException, IOException, AgentOptionReceiveException {

		DelayedLogger delayedLogger = new DelayedLogger();
		List<String> javaAgents = CollectionUtils.filter(ManagementFactory.getRuntimeMXBean().getInputArguments(),
				s -> s.contains("-javaagent"));
		// We allow multiple instances of the teamscale-jacoco-agent as we ensure with the #LOCKING_SYSTEM_PROPERTY to only use it once
		if (!javaAgents.stream().allMatch(javaAgent -> javaAgent.contains("teamscale-jacoco-agent.jar"))) {
			delayedLogger.warn("Using multiple java agents could interfere with coverage recording.");
		}
		if (!javaAgents.get(0).contains("teamscale-jacoco-agent.jar")) {
			delayedLogger.warn("For best results consider registering the Teamscale Java Profiler first.");
		}

		TeamscaleCredentials credentials = TeamscalePropertiesUtils.parseCredentials();
		if (credentials == null) {
			// As many users still don't use the installer based setup, this log message will be shown in almost every log.
			// We use a debug log, as this message can be confusing for customers that think a teamscale.properties file is synonymous with a config file.
			delayedLogger.debug(
					"No explicit teamscale.properties file given. Looking for Teamscale credentials in a config file or via a command line argument. This is expected unless the installer based setup was used.");
		}

		String environmentAccessToken = System.getenv(ACCESS_TOKEN_ENVIRONMENT_VARIABLE);

		Pair<AgentOptions, List<Exception>> parseResult;
		AgentOptions agentOptions;
		try {
			parseResult = AgentOptionsParser.parse(
					options, environmentConfigId, environmentConfigFile, credentials, environmentAccessToken,
					delayedLogger);
			agentOptions = parseResult.getFirst();
		} catch (AgentOptionParseException e) {
			try (LoggingUtils.LoggingResources ignored = initializeFallbackLogging(options, delayedLogger)) {
				delayedLogger.errorAndStdErr("Failed to parse agent options: " + e.getMessage(), e);
				attemptLogAndThrow(delayedLogger);
				throw e;
			}
		} catch (AgentOptionReceiveException e) {
			try (LoggingUtils.LoggingResources ignored = initializeFallbackLogging(options, delayedLogger)) {
				delayedLogger.errorAndStdErr(
						e.getMessage() + " The application should start up normally, but NO coverage will be collected! Check the log file for details.",
						e);
				attemptLogAndThrow(delayedLogger);
				throw e;
			}
		}

		initializeLogging(agentOptions, delayedLogger);
		Logger logger = LoggingUtils.getLogger(Agent.class);
		delayedLogger.logTo(logger);
		HttpUtils.setShouldValidateSsl(agentOptions.shouldValidateSsl());

		return parseResult;
	}

	private static void attemptLogAndThrow(DelayedLogger delayedLogger) {
		// We perform actual logging output after writing to console to
		// ensure the console is reached even in case of logging issues
		// (see TS-23151). We use the Agent class here (same as below)
		Logger logger = LoggingUtils.getLogger(Agent.class);
		delayedLogger.logTo(logger);
	}

	/** Initializes logging during {@link #premain(String, Instrumentation)} and also logs the log directory. */
	private static void initializeLogging(AgentOptions agentOptions, DelayedLogger logger) throws IOException {
		if (agentOptions.isDebugLogging()) {
			initializeDebugLogging(agentOptions, logger);
		} else {
			loggingResources = LoggingUtils.initializeLogging(agentOptions.getLoggingConfig());
			logger.info("Logging to " + new LogDirectoryPropertyDefiner().getPropertyValue());
		}

		if (agentOptions.getTeamscaleServerOptions().isConfiguredForServerConnection()) {
			if (LogToTeamscaleAppender.addTeamscaleAppenderTo(getLoggerContext(), agentOptions)) {
				logger.info("Logs are being forwarded to Teamscale at " + agentOptions.getTeamscaleServerOptions().url);
			}
		}
	}

	/** Closes the opened logging contexts. */
	static void closeLoggingResources() {
		loggingResources.close();
	}

	/**
	 * Returns in instance of the agent that was configured. Either an agent with interval based line-coverage dump or
	 * the HTTP server is used.
	 */
	private static AgentBase createAgent(AgentOptions agentOptions,
			Instrumentation instrumentation) throws UploaderException, IOException {
		if (agentOptions.useTestwiseCoverageMode()) {
			return TestwiseCoverageAgent.create(agentOptions);
		} else {
			return new Agent(agentOptions, instrumentation);
		}
	}

	/**
	 * Initializes debug logging during {@link #premain(String, Instrumentation)} and also logs the log directory if
	 * given.
	 */
	private static void initializeDebugLogging(AgentOptions agentOptions, DelayedLogger logger) {
		loggingResources = LoggingUtils.initializeDebugLogging(agentOptions.getDebugLogDirectory());
		Path logDirectory = Paths.get(new DebugLogDirectoryPropertyDefiner().getPropertyValue());
		if (FileSystemUtils.isValidPath(logDirectory.toString()) && Files.isWritable(logDirectory)) {
			logger.info("Logging to " + logDirectory);
		} else {
			logger.warn("Could not create " + logDirectory + ". Logging to console only.");
		}
	}

	/**
	 * Initializes fallback logging in case of an error during the parsing of the options to
	 * {@link #premain(String, Instrumentation)} (see TS-23151). This tries to extract the logging configuration and use
	 * this and falls back to the default logger.
	 */
	private static LoggingUtils.LoggingResources initializeFallbackLogging(String premainOptions,
			DelayedLogger delayedLogger) {
		if (premainOptions == null) {
			return LoggingUtils.initializeDefaultLogging();
		}
		for (String optionPart : premainOptions.split(",")) {
			if (optionPart.startsWith(AgentOptionsParser.DEBUG + "=")) {
				String value = optionPart.split("=", 2)[1];
				boolean debugDisabled = value.equalsIgnoreCase("false");
				boolean debugEnabled = value.equalsIgnoreCase("true");
				if (debugDisabled) {
					continue;
				}
				Path debugLogDirectory = null;
				if (!value.isEmpty() && !debugEnabled) {
					debugLogDirectory = Paths.get(value);
				}
				return LoggingUtils.initializeDebugLogging(debugLogDirectory);
			}
			if (optionPart.startsWith(AgentOptionsParser.LOGGING_CONFIG_OPTION + "=")) {
				return createFallbackLoggerFromConfig(optionPart.split("=", 2)[1], delayedLogger);
			}

			if (optionPart.startsWith(AgentOptionsParser.CONFIG_FILE_OPTION + "=")) {
				String configFileValue = optionPart.split("=", 2)[1];
				Optional<String> loggingConfigLine = Optional.empty();
				try {
					File configFile = new FilePatternResolver(delayedLogger).parsePath(
							AgentOptionsParser.CONFIG_FILE_OPTION, configFileValue).toFile();
					loggingConfigLine = FileSystemUtils.readLinesUTF8(configFile).stream()
							.filter(line -> line.startsWith(AgentOptionsParser.LOGGING_CONFIG_OPTION + "="))
							.findFirst();
				} catch (IOException e) {
					delayedLogger.error("Failed to load configuration from " + configFileValue + ": " + e.getMessage(),
							e);
				}
				if (loggingConfigLine.isPresent()) {
					return createFallbackLoggerFromConfig(loggingConfigLine.get().split("=", 2)[1], delayedLogger);
				}
			}
		}

		return LoggingUtils.initializeDefaultLogging();
	}

	/** Creates a fallback logger using the given config file. */
	private static LoggingUtils.LoggingResources createFallbackLoggerFromConfig(String configLocation,
			ILogger delayedLogger) {
		try {
			return LoggingUtils.initializeLogging(
					new FilePatternResolver(delayedLogger).parsePath(AgentOptionsParser.LOGGING_CONFIG_OPTION,
							configLocation));
		} catch (IOException e) {
			String message = "Failed to load log configuration from location " + configLocation + ": " + e.getMessage();
			delayedLogger.error(message, e);
			// output the message to console as well, as this might
			// otherwise not make it to the user
			System.err.println(message);
			return LoggingUtils.initializeDefaultLogging();
		}
	}
}
