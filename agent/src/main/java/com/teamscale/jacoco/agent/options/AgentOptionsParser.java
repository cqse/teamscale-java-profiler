/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.options;

import com.google.common.annotations.VisibleForTesting;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.HttpUtils;
import com.teamscale.client.ProxySystemProperties;
import com.teamscale.client.StringUtils;
import com.teamscale.client.TeamscaleProxySystemProperties;
import com.teamscale.jacoco.agent.commandline.Validator;
import com.teamscale.jacoco.agent.configuration.AgentOptionReceiveException;
import com.teamscale.jacoco.agent.configuration.ConfigurationViaTeamscale;
import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication;
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig;
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageConfig;
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig;
import com.teamscale.report.EDuplicateClassFileBehavior;
import com.teamscale.report.util.ILogger;
import okhttp3.HttpUrl;
import org.apache.commons.compress.utils.Lists;
import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.collections.Pair;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.ARTIFACTORY_GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION;
import static com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.ARTIFACTORY_GIT_PROPERTIES_JAR_OPTION;
import static java.util.stream.Collectors.joining;

/**
 * Parses agent command line options.
 */
public class AgentOptionsParser {

	/** The name of the option for providing the logging config. */
	public static final String LOGGING_CONFIG_OPTION = "logging-config";

	/** The name of the option for providing the config file. */
	public static final String CONFIG_FILE_OPTION = "config-file";

	/** Character which starts a comment in the config file. */
	private static final String COMMENT_PREFIX = "#";

	/** The name of the option that enables debug logging. */
	public static final String DEBUG = "debug";

	private final ILogger logger;
	private final FilePatternResolver filePatternResolver;
	private final TeamscaleConfig teamscaleConfig;
	private final String environmentConfigId;
	private final String environmentConfigFile;
	@Nullable
	private final TeamscaleCredentials credentials;
	@Nullable
	private final String environmentAccessToken;
	private final List<Exception> collectedErrors;

	/**
	 * Parses the given command-line options.
	 *
	 * @param environmentConfigId    The Profiler configuration ID given via an environment variable.
	 * @param environmentConfigFile  The Profiler configuration file given via an environment variable.
	 * @param credentials            Optional Teamscale credentials from a teamscale.properties file.
	 * @param environmentAccessToken Optional access token for accessing Teamscale, read from an env variable.
	 */
	public static Pair<AgentOptions, List<Exception>> parse(String optionsString, String environmentConfigId,
			String environmentConfigFile, @Nullable TeamscaleCredentials credentials,
			@Nullable String environmentAccessToken,
			ILogger logger) throws AgentOptionParseException, AgentOptionReceiveException {
		AgentOptionsParser parser = new AgentOptionsParser(logger, environmentConfigId, environmentConfigFile,
				credentials, environmentAccessToken);
		AgentOptions options = parser.parse(optionsString);
		return Pair.createPair(options, parser.getCollectedErrors());
	}

	@VisibleForTesting
	AgentOptionsParser(ILogger logger, String environmentConfigId, String environmentConfigFile,
			@Nullable TeamscaleCredentials credentials, @Nullable String environmentAccessToken) {
		this.logger = logger;
		this.filePatternResolver = new FilePatternResolver(logger);
		this.teamscaleConfig = new TeamscaleConfig(logger, filePatternResolver);
		this.environmentConfigId = environmentConfigId;
		this.environmentConfigFile = environmentConfigFile;
		this.credentials = credentials;
		this.environmentAccessToken = environmentAccessToken;
		this.collectedErrors = Lists.newArrayList();
	}

	private List<Exception> getCollectedErrors() {
		return collectedErrors;
	}

	/**
	 * Throw the first collected exception, if present.
	 */
	@VisibleForTesting
	public void throwOnCollectedErrors() throws Exception {
		for (Exception e : collectedErrors) {
			throw e;
		}
	}

	/**
	 * Parses the given command-line options.
	 */
	/* package */ AgentOptions parse(
			String optionsString) throws AgentOptionParseException, AgentOptionReceiveException {

		if (optionsString == null) {
			optionsString = "";
		}
		logger.debug("Parsing options: " + optionsString);

		AgentOptions options = new AgentOptions(logger);
		options.originalOptionsString = optionsString;

		presetCredentialOptions(options);

		if (!StringUtils.isEmpty(optionsString)) {
			String[] optionParts = optionsString.split(",");
			for (String optionPart : optionParts) {
				try {
					handleOptionPart(options, optionPart);
				} catch (Exception e) {
					collectedErrors.add(e);
				}
			}
		}

		// we have to put the proxy options into system properties before reading the configuration from Teamscale as we
		// might need them to connect to Teamscale
		putTeamscaleProxyOptionsIntoSystemProperties(options);

		handleConfigId(options);
		handleConfigFile(options);

		Validator validator = options.getValidator();
		if (!validator.isValid()) {
			collectedErrors.add(new AgentOptionParseException("Invalid options given: " + validator.getErrorMessage()));
		}

		return options;
	}

	private void presetCredentialOptions(AgentOptions options) {
		if (credentials != null) {
			options.teamscaleServer.url = credentials.url;
			options.teamscaleServer.userName = credentials.userName;
			options.teamscaleServer.userAccessToken = credentials.accessKey;
		}
		if (environmentAccessToken != null) {
			options.teamscaleServer.userAccessToken = environmentAccessToken;
		}
	}

	/**
	 * Stores the agent options for proxies in the {@link TeamscaleProxySystemProperties} and overwrites the password
	 * with the password found in the proxy-password-file if necessary.
	 */
	@VisibleForTesting
	public static void putTeamscaleProxyOptionsIntoSystemProperties(AgentOptions options) {
		options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP)
				.putTeamscaleProxyOptionsIntoSystemProperties();
		options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTPS)
				.putTeamscaleProxyOptionsIntoSystemProperties();
	}

	private void handleConfigId(AgentOptions options) throws AgentOptionReceiveException, AgentOptionParseException {
		if (environmentConfigId != null) {
			if (options.teamscaleServer.configId != null) {
				logger.warn(
						"You specified an ID for a profiler configuration in Teamscale both in the agent options and using an environment variable." +
								" The environment variable will override the ID specified using the agent options." +
								" Please use one or the other.");
			}
			handleOptionPart(options, "config-id=" + environmentConfigId);
		}

		readConfigFromTeamscale(options);
	}

	private void handleConfigFile(AgentOptions options) throws AgentOptionParseException {
		if (environmentConfigFile != null) {
			handleOptionPart(options, "config-file=" + environmentConfigFile);
		}

		if (environmentConfigId != null && environmentConfigFile != null) {
			logger.warn("You specified both an ID for a profiler configuration in Teamscale and a config file." +
					" The config file will override the Teamscale configuration." +
					" Please use one or the other.");
		}
	}

	/**
	 * Parses and stores the given option in the format <code>key=value</code>.
	 */
	private void handleOptionPart(AgentOptions options,
			String optionPart) throws AgentOptionParseException {
		Pair<String, String> keyAndValue = parseOption(optionPart);
		handleOption(options, keyAndValue.getFirst(), keyAndValue.getSecond());
	}

	/**
	 * Parses and stores the option with the given key and value.
	 */
	private void handleOption(AgentOptions options,
			String key, String value) throws AgentOptionParseException {
		if (key.startsWith(DEBUG)) {
			handleDebugOption(options, value);
			return;
		}
		if (key.startsWith("jacoco-")) {
			options.additionalJacocoOptions.add(key.substring(7), value);
			return;
		}
		if (key.startsWith("teamscale-") && teamscaleConfig.handleTeamscaleOptions(options.teamscaleServer, key,
				value)) {
			return;
		}
		if (key.startsWith("artifactory-") && ArtifactoryConfig
				.handleArtifactoryOptions(options.artifactoryConfig, key, value)) {
			return;
		}
		if (key.startsWith("azure-") && AzureFileStorageConfig
				.handleAzureFileStorageOptions(options.azureFileStorageConfig, key,
						value)) {
			return;
		}
		if (key.startsWith("proxy-") && handleProxyOptions(options, StringUtils.stripPrefix(key, "proxy-"), value
		)) {
			return;
		}
		if (handleAgentOptions(options, key, value)) {
			return;
		}
		throw new AgentOptionParseException("Unknown option: " + key);
	}

	private boolean handleProxyOptions(AgentOptions options, String key,
			String value) throws AgentOptionParseException {
		String httpsPrefix = ProxySystemProperties.Protocol.HTTPS + "-";
		if (key.startsWith(httpsPrefix)
				&& options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTPS)
				.handleTeamscaleProxyOptions(StringUtils.stripPrefix(
						key, httpsPrefix), value)) {
			return true;
		}

		String httpPrefix = ProxySystemProperties.Protocol.HTTP + "-";
		if (key.startsWith(httpPrefix)
				&& options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP)
				.handleTeamscaleProxyOptions(StringUtils.stripPrefix(
						key, httpPrefix), value)) {
			return true;
		}

		if (key.equals("password-file")) {
			Path proxyPasswordPath = parsePath(filePatternResolver, key, value);
			options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTPS)
					.setProxyPasswordPath(proxyPasswordPath);
			options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP)
					.setProxyPasswordPath(proxyPasswordPath);
			return true;
		}
		return false;
	}

	/** Parses and stores the debug logging file path if given. */
	private void handleDebugOption(AgentOptions options, String value) {
		if (value.equalsIgnoreCase("false")) {
			return;
		}
		options.debugLogging = true;
		if (!value.isEmpty() && !value.equalsIgnoreCase("true")) {
			options.debugLogDirectory = Paths.get(value);
		}
	}

	private Pair<String, String> parseOption(String optionPart) throws AgentOptionParseException {
		String[] keyAndValue = optionPart.split("=", 2);
		if (keyAndValue.length < 2) {
			throw new AgentOptionParseException("Got an option without any value: " + optionPart);
		}

		String key = keyAndValue[0].toLowerCase();
		String value = keyAndValue[1];

		// Remove quotes, which may be used to pass arguments with spaces via
		// the command line
		if (value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1);
		}
		return new Pair<>(key, value);
	}

	/**
	 * Handles all common command line options for the agent.
	 *
	 * @return true if it has successfully processed the given option.
	 */
	private boolean handleAgentOptions(AgentOptions options, String key, String value)
			throws AgentOptionParseException {
		switch (key) {
			case "config-id":
				options.teamscaleServer.configId = value;
				return true;
			case CONFIG_FILE_OPTION:
				readConfigFromFile(options, parsePath(filePatternResolver, key, value).toFile());
				return true;
			case LOGGING_CONFIG_OPTION:
				options.loggingConfig = parsePath(filePatternResolver, key, value);
				return true;
			case "interval":
				options.dumpIntervalInMinutes = parseInt(key, value);
				return true;
			case "validate-ssl":
				options.validateSsl = Boolean.parseBoolean(value);
				return true;
			case "out":
				options.setParentOutputDirectory(parsePath(filePatternResolver, key, value));
				return true;
			case "upload-metadata":
				try {
					options.additionalMetaDataFiles = CollectionUtils.map(splitMultiOptionValue(value), Paths::get);
				} catch (InvalidPathException e) {
					throw new AgentOptionParseException("Invalid path given for option 'upload-metadata'", e);
				}
				return true;
			case "duplicates":
				options.duplicateClassFileBehavior = parseEnumValue(key, value, EDuplicateClassFileBehavior.class);
				return true;
			case "ignore-uncovered-classes":
				options.ignoreUncoveredClasses = Boolean.parseBoolean(value);
				return true;
			case "obfuscate-security-related-outputs":
				options.obfuscateSecurityRelatedOutputs = Boolean.parseBoolean(value);
				return true;
			case "dump-on-exit":
				options.shouldDumpOnExit = Boolean.parseBoolean(value);
				return true;
			case "search-git-properties-recursively":
				options.searchGitPropertiesRecursively = Boolean.parseBoolean(value);
				return true;
			case ARTIFACTORY_GIT_PROPERTIES_JAR_OPTION:
				logger.warn(
						"The option " + ARTIFACTORY_GIT_PROPERTIES_JAR_OPTION + " is deprecated. It still has an effect, " +
								"but should be replaced with the equivalent option " + AgentOptions.GIT_PROPERTIES_JAR_OPTION + ".");
				// intended fallthrough (acts as alias)
			case AgentOptions.GIT_PROPERTIES_JAR_OPTION:
				options.gitPropertiesJar = getGitPropertiesJarFile(value);
				return true;
			case ARTIFACTORY_GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION:
				logger.warn(
						"The option " + ARTIFACTORY_GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION + " is deprecated. It still has an effect, " +
								"but should be replaced with the equivalent option " + AgentOptions.GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION + ".");
				// intended fallthrough (acts as alias)
			case AgentOptions.GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION:
				options.gitPropertiesCommitTimeFormat = DateTimeFormatter.ofPattern(value);
				return true;
			case "mode":
				options.mode = parseEnumValue(key, value, EMode.class);
				return true;
			case "includes":
				options.jacocoIncludes = value.replaceAll(";", ":");
				return true;
			case "excludes":
				options.jacocoExcludes = value.replaceAll(";", ":") + ":" + AgentOptions.DEFAULT_EXCLUDES;
				return true;
			case "class-dir":
				List<String> list = splitMultiOptionValue(value);
				try {
					options.classDirectoriesOrZips = ClasspathUtils.resolveClasspathTextFiles(key, filePatternResolver,
							list);
				} catch (IOException e) {
					throw new AgentOptionParseException(e);
				}
				return true;
			case "http-server-port":
				options.httpServerPort = parseInt(key, value);
				return true;
			case "sap-nwdi-applications":
				options.sapNetWeaverJavaApplications = SapNwdiApplication.parseApplications(value);
				return true;
			case "tia-mode":
				options.testwiseCoverageMode = AgentOptionsParser.parseEnumValue(key, value,
						ETestwiseCoverageMode.class);
				return true;
			default:
				return false;
		}
	}

	private void readConfigFromTeamscale(
			AgentOptions options) throws AgentOptionParseException, AgentOptionReceiveException {
		if (options.teamscaleServer.configId == null) {
			return;
		}
		if (!options.teamscaleServer.isConfiguredForServerConnection()) {
			throw new AgentOptionParseException(
					"Config-id '" + options.teamscaleServer.configId + "' specified without teamscale url/user/accessKey! These options must be provided locally via config-file or command line argument.");
		}
		// Set ssl validation option in case it needs to be off before trying to reach Teamscale.
		HttpUtils.setShouldValidateSsl(options.shouldValidateSsl());
		ConfigurationViaTeamscale configuration = ConfigurationViaTeamscale.retrieve(logger,
				options.teamscaleServer.configId,
				options.teamscaleServer.url,
				options.teamscaleServer.userName,
				options.teamscaleServer.userAccessToken);
		options.configurationViaTeamscale = configuration;
		logger.debug(
				"Received the following options from Teamscale: " + configuration.getProfilerConfiguration().configurationOptions);
		readConfigFromString(options, configuration.getProfilerConfiguration().configurationOptions);
	}

	private File getGitPropertiesJarFile(String path) {
		File jarFile = new File(path);
		if (!jarFile.exists()) {
			logger.warn(
					"The path provided with the " + AgentOptions.GIT_PROPERTIES_JAR_OPTION + " option does not exist: " + path + ". Continuing without searching it for git.properties files.");
			return null;
		}
		if (!jarFile.isFile()) {
			logger.warn(
					"The path provided with the " + AgentOptions.GIT_PROPERTIES_JAR_OPTION + " option is not a regular file (probably a folder instead): " + path + ". Continuing without searching it for git.properties files.");
			return null;
		}
		return jarFile;
	}

	/**
	 * Parses the given value as an enum constant case-insensitively and converts "-" to "_".
	 */
	public static <T extends Enum<T>> T parseEnumValue(String key, String value, Class<T> enumClass)
			throws AgentOptionParseException {
		try {
			return Enum.valueOf(enumClass, value.toUpperCase().replaceAll("-", "_"));
		} catch (IllegalArgumentException e) {
			String validValues = Arrays.stream(enumClass.getEnumConstants()).map(T::name).collect(joining(", "));
			throw new AgentOptionParseException("Invalid value for option `" + key + "`. Valid values: " + validValues,
					e);
		}
	}

	/**
	 * Reads configuration parameters from the given file. The expected format is basically the same as for the command
	 * line, but line breaks are also considered as separators. e.g. class-dir=out # Some comment includes=test.*
	 * excludes=third.party.*
	 */
	private void readConfigFromFile(AgentOptions options,
			File configFile) throws AgentOptionParseException {
		try {
			String content = FileSystemUtils.readFileUTF8(configFile);
			readConfigFromString(options, content);
		} catch (FileNotFoundException e) {
			throw new AgentOptionParseException(
					"File " + configFile.getAbsolutePath() + " given for option 'config-file' not found", e);
		} catch (IOException e) {
			throw new AgentOptionParseException(
					"An error occurred while reading the config file " + configFile.getAbsolutePath(), e);
		}
	}

	private void readConfigFromString(AgentOptions options, String content) {
		List<String> configFileKeyValues = org.conqat.lib.commons.string.StringUtils.splitLinesAsList(
				content);
		for (String optionKeyValue : configFileKeyValues) {
			try {
				String trimmedOption = optionKeyValue.trim();
				if (trimmedOption.isEmpty() || trimmedOption.startsWith(COMMENT_PREFIX)) {
					continue;
				}
				handleOptionPart(options, optionKeyValue);
			} catch (Exception e) {
				collectedErrors.add(e);
			}
		}
	}

	private int parseInt(String key, String value) throws AgentOptionParseException {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new AgentOptionParseException("Invalid non-numeric value for option `" + key + "`: " + value);
		}
	}

	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one existing {@link Path}. If the given path is
	 * relative, it is resolved relative to the current working directory. If more than one file matches the pattern,
	 * one of the matching files is used without any guarantees as to which. The selection is, however, guaranteed to be
	 * deterministic, i.e. if you run the pattern twice and get the same set of files, the same file will be picked each
	 * time.
	 */
	public static Path parsePath(FilePatternResolver filePatternResolver, String optionName,
			String pattern) throws AgentOptionParseException {
		try {
			return filePatternResolver.parsePath(optionName, pattern);
		} catch (IOException e) {
			throw new AgentOptionParseException(e);
		}
	}

	/**
	 * Parses the given value as a URL.
	 */
	public static HttpUrl parseUrl(String key, String value) throws AgentOptionParseException {
		if (!value.endsWith("/")) {
			value += "/";
		}

		// default to HTTP if no scheme is given and port is not 443, default to HTTPS if no scheme is given AND port is 443
		if (!value.startsWith("http://") && !value.startsWith("https://")) {
			HttpUrl url = getUrl(key, "http://" + value);
			if (url.port() == 443) {
				value = "https://" + value;
			} else {
				value = "http://" + value;
			}
		}

		return getUrl(key, value);
	}

	private static HttpUrl getUrl(String key, String value) throws AgentOptionParseException {
		HttpUrl url = HttpUrl.parse(value);
		if (url == null) {
			throw new AgentOptionParseException("Invalid URL given for option '" + key + "'");
		}
		return url;
	}

	/**
	 * Splits the given value at semicolons.
	 */
	private static List<String> splitMultiOptionValue(String value) {
		return Arrays.asList(value.split(";"));
	}
}
