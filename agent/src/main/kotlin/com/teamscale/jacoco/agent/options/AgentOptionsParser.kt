package com.teamscale.jacoco.agent.options

import com.teamscale.client.FileSystemUtils.readFileUTF8
import com.teamscale.client.HttpUtils.setShouldValidateSsl
import com.teamscale.client.ProxySystemProperties
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.client.StringUtils.splitLinesAsList
import com.teamscale.client.StringUtils.stripPrefix
import com.teamscale.jacoco.agent.configuration.AgentOptionReceiveException
import com.teamscale.jacoco.agent.configuration.ConfigurationViaTeamscale
import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication.Companion.parseApplications
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.handleArtifactoryOptions
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageConfig.Companion.handleAzureFileStorageOptions
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig
import com.teamscale.report.util.ILogger
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Parses agent command line options.
 */
class AgentOptionsParser @VisibleForTesting internal constructor(
	private val logger: ILogger,
	private val environmentConfigId: String?,
	private val environmentConfigFile: String?,
	private val credentials: TeamscaleCredentials?,
	private val environmentAccessToken: String?
) {
	private val filePatternResolver = FilePatternResolver(logger)
	private val teamscaleConfig = TeamscaleConfig(logger, filePatternResolver)
	private val collectedErrors = mutableListOf<Exception>()

	/**
	 * Throw the first collected exception, if present.
	 */
	@VisibleForTesting
	@Throws(Exception::class)
	fun throwOnCollectedErrors() {
		collectedErrors.forEach { throw it }
	}

	/**
	 * Parses the given command-line options.
	 */
	/* package */
	@Throws(AgentOptionParseException::class, AgentOptionReceiveException::class)
	fun parse(optionsString: String?): AgentOptions {
		var optionsString = optionsString
		if (optionsString == null) {
			optionsString = ""
		}
		logger.debug("Parsing options: $optionsString")

		val options = AgentOptions(logger)
		options.originalOptionsString = optionsString

		presetCredentialOptions(options)

		if (!isEmpty(optionsString)) {
			val optionParts = optionsString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			optionParts.forEach { optionPart ->
				try {
					handleOptionPart(options, optionPart)
				} catch (e: Exception) {
					collectedErrors.add(e)
				}
			}
		}

		// we have to put the proxy options into system properties before reading the configuration from Teamscale as we
		// might need them to connect to Teamscale
		putTeamscaleProxyOptionsIntoSystemProperties(options)

		handleConfigId(options)
		handleConfigFile(options)

		val validator = options.validator
		if (!validator.isValid) {
			collectedErrors.add(AgentOptionParseException("Invalid options given: ${validator.errorMessage}"))
		}

		return options
	}

	private fun presetCredentialOptions(options: AgentOptions) {
		if (credentials != null) {
			options.teamscaleServer.url = credentials.url
			options.teamscaleServer.userName = credentials.userName
			options.teamscaleServer.userAccessToken = credentials.accessKey
		}
		if (environmentAccessToken != null) {
			options.teamscaleServer.userAccessToken = environmentAccessToken
		}
	}

	@Throws(AgentOptionReceiveException::class, AgentOptionParseException::class)
	private fun handleConfigId(options: AgentOptions) {
		if (environmentConfigId != null) {
			if (options.teamscaleServer.configId != null) {
				logger.warn(
					"You specified an ID for a profiler configuration in Teamscale both in the agent options and using an environment variable." +
							" The environment variable will override the ID specified using the agent options." +
							" Please use one or the other."
				)
			}
			handleOptionPart(options, "config-id=$environmentConfigId")
		}

		readConfigFromTeamscale(options)
	}

	@Throws(AgentOptionParseException::class)
	private fun handleConfigFile(options: AgentOptions) {
		if (environmentConfigFile != null) {
			handleOptionPart(options, "config-file=$environmentConfigFile")
		}

		if (environmentConfigId != null && environmentConfigFile != null) {
			logger.warn(
				"You specified both an ID for a profiler configuration in Teamscale and a config file." +
						" The config file will override the Teamscale configuration." +
						" Please use one or the other."
			)
		}
	}

	/**
	 * Parses and stores the given option in the format `key=value`.
	 */
	@Throws(AgentOptionParseException::class)
	private fun handleOptionPart(
		options: AgentOptions,
		optionPart: String
	) {
		val (key, value) = parseOption(optionPart)
		handleOption(options, key, value)
	}

	/**
	 * Parses and stores the option with the given key and value.
	 */
	@Throws(AgentOptionParseException::class)
	private fun handleOption(
		options: AgentOptions,
		key: String, value: String
	) {
		if (key.startsWith(DEBUG)) {
			handleDebugOption(options, value)
			return
		}
		if (key.startsWith("jacoco-")) {
			options.additionalJacocoOptions.add(key.substring(7) to value)
			return
		}
		if (key.startsWith("teamscale-") && teamscaleConfig.handleTeamscaleOptions(options.teamscaleServer, key, value)) return
		if (key.startsWith("artifactory-") && handleArtifactoryOptions(options.artifactoryConfig, key, value)) return
		if (key.startsWith("azure-") && handleAzureFileStorageOptions(options.azureFileStorageConfig, key, value)) return
		if (key.startsWith("proxy-") && handleProxyOptions(options, stripPrefix(key, "proxy-"), value)) return
		if (handleAgentOptions(options, key, value)) return
		throw AgentOptionParseException("Unknown option: $key")
	}

	@Throws(AgentOptionParseException::class)
	private fun handleProxyOptions(
		options: AgentOptions, key: String, value: String
	): Boolean {
		val httpsPrefix = "${ProxySystemProperties.Protocol.HTTPS}-"
		if (key.startsWith(httpsPrefix)
			&& options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTPS)!!
				.handleTeamscaleProxyOptions(stripPrefix(key, httpsPrefix), value)
		) return true

		val httpPrefix = ProxySystemProperties.Protocol.HTTP.toString() + "-"
		if (key.startsWith(httpPrefix)
			&& options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP)!!
				.handleTeamscaleProxyOptions(stripPrefix(key, httpPrefix), value)
		) return true

		if (key == "password-file") {
			val proxyPasswordPath = parsePath(filePatternResolver, key, value)
			options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTPS)!!
				.proxyPasswordPath = proxyPasswordPath
			options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP)!!
				.proxyPasswordPath = proxyPasswordPath
			return true
		}
		return false
	}

	/** Parses and stores the debug logging file path if given.  */
	private fun handleDebugOption(options: AgentOptions, value: String) {
		if (value.equals("false", ignoreCase = true)) return
		options.isDebugLogging = true
		if (!value.isEmpty() && !value.equals("true", ignoreCase = true)) {
			options.debugLogDirectory = Paths.get(value)
		}
	}

	@Throws(AgentOptionParseException::class)
	private fun parseOption(optionPart: String): Pair<String, String> {
		val keyAndValue = optionPart.split("=".toRegex(), limit = 2).toTypedArray()
		if (keyAndValue.size < 2) {
			throw AgentOptionParseException("Got an option without any value: $optionPart")
		}

		val key = keyAndValue[0].lowercase(Locale.getDefault())
		var value = keyAndValue[1]

		// Remove quotes, which may be used to pass arguments with spaces via
		// the command line
		if (value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length - 1)
		}
		return key to value
	}

	/**
	 * Handles all common command line options for the agent.
	 * 
	 * @return true if it has successfully processed the given option.
	 */
	@Throws(AgentOptionParseException::class)
	private fun handleAgentOptions(options: AgentOptions, key: String, value: String): Boolean {
		when (key) {
			"config-id" -> {
				options.teamscaleServer.configId = value
				return true
			}
			CONFIG_FILE_OPTION -> {
				readConfigFromFile(options, parsePath(filePatternResolver, key, value)!!.toFile())
				return true
			}
			LOGGING_CONFIG_OPTION -> {
				options.loggingConfig = parsePath(filePatternResolver, key, value)
				return true
			}
			"interval" -> {
				options.dumpIntervalInMinutes = parseInt(key, value)
				return true
			}
			"validate-ssl" -> {
				options.validateSsl = value.toBoolean()
				return true
			}
			"out" -> {
				options.setParentOutputDirectory(parsePath(filePatternResolver, key, value)!!)
				return true
			}
			"upload-metadata" -> {
				try {
					options.additionalMetaDataFiles = splitMultiOptionValue(value).map { path -> Paths.get(path) }
				} catch (e: InvalidPathException) {
					throw AgentOptionParseException("Invalid path given for option 'upload-metadata'", e)
				}
				return true
			}
			"duplicates" -> {
				options.duplicateClassFileBehavior = parseEnumValue(key, value)
				return true
			}
			"ignore-uncovered-classes" -> {
				options.ignoreUncoveredClasses = value.toBoolean()
				return true
			}
			"obfuscate-security-related-outputs" -> {
				options.obfuscateSecurityRelatedOutputs = value.toBoolean()
				return true
			}
			"dump-on-exit" -> {
				options.shouldDumpOnExit = value.toBoolean()
				return true
			}
			"search-git-properties-recursively" -> {
				options.searchGitPropertiesRecursively = value.toBoolean()
				return true
			}
			ArtifactoryConfig.ARTIFACTORY_GIT_PROPERTIES_JAR_OPTION -> {
				logger.warn(
					"The option " + ArtifactoryConfig.ARTIFACTORY_GIT_PROPERTIES_JAR_OPTION + " is deprecated. It still has an effect, " +
							"but should be replaced with the equivalent option " + AgentOptions.GIT_PROPERTIES_JAR_OPTION + "."
				)
				options.gitPropertiesJar = getGitPropertiesJarFile(value)
				return true
			}
			AgentOptions.GIT_PROPERTIES_JAR_OPTION -> {
				options.gitPropertiesJar = getGitPropertiesJarFile(value)
				return true
			}
			ArtifactoryConfig.ARTIFACTORY_GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION -> {
				logger.warn(
					"The option " + ArtifactoryConfig.ARTIFACTORY_GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION + " is deprecated. It still has an effect, " +
							"but should be replaced with the equivalent option " + AgentOptions.GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION + "."
				)
				options.gitPropertiesCommitTimeFormat = DateTimeFormatter.ofPattern(value)
				return true
			}
			AgentOptions.GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION -> {
				options.gitPropertiesCommitTimeFormat = DateTimeFormatter.ofPattern(value)
				return true
			}
			"mode" -> {
				options.mode = parseEnumValue(key, value)
				return true
			}
			"includes" -> {
				options.jacocoIncludes = value.replace(";".toRegex(), ":")
				return true
			}
			"excludes" -> {
				options.jacocoExcludes = value.replace(";".toRegex(), ":") + ":" + AgentOptions.DEFAULT_EXCLUDES
				return true
			}
			"class-dir" -> {
				val list = splitMultiOptionValue(value)
				try {
					options.classDirectoriesOrZips = ClasspathUtils.resolveClasspathTextFiles(
						key, filePatternResolver, list
					)
				} catch (e: IOException) {
					throw AgentOptionParseException(e)
				}
				return true
			}
			"http-server-port" -> {
				options.httpServerPort = parseInt(key, value)
				return true
			}
			"sap-nwdi-applications" -> {
				options.sapNetWeaverJavaApplications = parseApplications(value)
				return true
			}
			"tia-mode" -> {
				options.testwiseCoverageMode = parseEnumValue(key, value)
				return true
			}
			else -> return false
		}
	}

	@Throws(AgentOptionParseException::class, AgentOptionReceiveException::class)
	private fun readConfigFromTeamscale(options: AgentOptions) {
		if (options.teamscaleServer.configId == null) return
		if (!options.teamscaleServer.isConfiguredForServerConnection) {
			throw AgentOptionParseException(
				"Config-id '${options.teamscaleServer.configId}' specified without teamscale url/user/accessKey! These options must be provided locally via config-file or command line argument."
			)
		}
		// Set ssl validation option in case it needs to be off before trying to reach Teamscale.
		setShouldValidateSsl(options.validateSsl)
		val configuration = ConfigurationViaTeamscale.retrieve(
			logger,
			options.teamscaleServer.configId,
			options.teamscaleServer.url!!,
			options.teamscaleServer.userName!!,
			options.teamscaleServer.userAccessToken!!
		)
		options.configurationViaTeamscale = configuration
		logger.debug("Received the following options from Teamscale: ${configuration.profilerConfiguration!!.configurationOptions}")
		readConfigFromString(options, configuration.profilerConfiguration!!.configurationOptions)
	}

	private fun getGitPropertiesJarFile(path: String): File? {
		val jarFile = File(path)
		if (!jarFile.exists()) {
			logger.warn("The path provided with the ${AgentOptions.GIT_PROPERTIES_JAR_OPTION} option does not exist: $path. Continuing without searching it for git.properties files.")
			return null
		}
		if (!jarFile.isFile()) {
			logger.warn("The path provided with the ${AgentOptions.GIT_PROPERTIES_JAR_OPTION} option is not a regular file (probably a folder instead): $path. Continuing without searching it for git.properties files.")
			return null
		}
		return jarFile
	}

	/**
	 * Reads configuration parameters from the given file. The expected format is basically the same as for the command
	 * line, but line breaks are also considered as separators. e.g. class-dir=out # Some comment includes=test.*
	 * excludes=third.party.*
	 */
	@Throws(AgentOptionParseException::class)
	private fun readConfigFromFile(
		options: AgentOptions, configFile: File
	) {
		try {
			val content = readFileUTF8(configFile)
			readConfigFromString(options, content)
		} catch (e: FileNotFoundException) {
			throw AgentOptionParseException("File ${configFile.absolutePath} given for option 'config-file' not found", e)
		} catch (e: IOException) {
			throw AgentOptionParseException("An error occurred while reading the config file ${configFile.absolutePath}", e)
		}
	}

	private fun readConfigFromString(options: AgentOptions, content: String?) {
		splitLinesAsList(content).forEach { optionKeyValue ->
			try {
				val trimmedOption = optionKeyValue.trim { it <= ' ' }
				if (trimmedOption.isEmpty() || trimmedOption.startsWith(COMMENT_PREFIX)) {
					return@forEach
				}
				handleOptionPart(options, optionKeyValue)
			} catch (e: Exception) {
				collectedErrors.add(e)
			}
		}
	}

	@Throws(AgentOptionParseException::class)
	private fun parseInt(key: String?, value: String): Int {
		try {
			return value.toInt()
		} catch (_: NumberFormatException) {
			throw AgentOptionParseException("Invalid non-numeric value for option `${key}`: $value")
		}
	}

	companion object {
		/** The name of the option for providing the logging config.  */
		const val LOGGING_CONFIG_OPTION: String = "logging-config"

		/** The name of the option for providing the config file.  */
		const val CONFIG_FILE_OPTION: String = "config-file"

		/** Character which starts a comment in the config file.  */
		private const val COMMENT_PREFIX = "#"

		/** The name of the option that enables debug logging.  */
		const val DEBUG: String = "debug"

		/**
		 * Parses the given command-line options.
		 * 
		 * @param environmentConfigId    The Profiler configuration ID given via an environment variable.
		 * @param environmentConfigFile  The Profiler configuration file given via an environment variable.
		 * @param credentials            Optional Teamscale credentials from a teamscale.properties file.
		 * @param environmentAccessToken Optional access token for accessing Teamscale, read from an env variable.
		 */
		@Throws(AgentOptionParseException::class, AgentOptionReceiveException::class)
		fun parse(
			optionsString: String,
			environmentConfigId: String?,
			environmentConfigFile: String?, credentials: TeamscaleCredentials?,
			environmentAccessToken: String?,
			logger: ILogger
		): Pair<AgentOptions, List<Exception>> {
			val parser = AgentOptionsParser(
				logger, environmentConfigId, environmentConfigFile,
				credentials, environmentAccessToken
			)
			val options = parser.parse(optionsString)
			return options to parser.collectedErrors
		}

		/**
		 * Stores the agent options for proxies in the [com.teamscale.client.TeamscaleProxySystemProperties] and overwrites the password
		 * with the password found in the proxy-password-file if necessary.
		 */
		@JvmStatic
		@VisibleForTesting
		fun putTeamscaleProxyOptionsIntoSystemProperties(options: AgentOptions) {
			options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP)
				?.putTeamscaleProxyOptionsIntoSystemProperties()
			options.getTeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTPS)
				?.putTeamscaleProxyOptionsIntoSystemProperties()
		}

		/**
		 * Interprets the given pattern as an Ant pattern and resolves it to one existing [Path]. If the given path is
		 * relative, it is resolved relative to the current working directory. If more than one file matches the pattern,
		 * one of the matching files is used without any guarantees as to which. The selection is, however, guaranteed to be
		 * deterministic, i.e. if you run the pattern twice and get the same set of files, the same file will be picked each
		 * time.
		 */
		@Throws(AgentOptionParseException::class)
		fun parsePath(
			filePatternResolver: FilePatternResolver,
			optionName: String?,
			pattern: String?
		): Path? {
			try {
				return filePatternResolver.parsePath(optionName, pattern)
			} catch (e: IOException) {
				throw AgentOptionParseException(e)
			}
		}

		/**
		 * Parses the given value as a URL.
		 */
		@Throws(AgentOptionParseException::class)
		fun parseUrl(key: String?, value: String): HttpUrl {
			var value = value
			if (!value.endsWith("/")) {
				value += "/"
			}

			// default to HTTP if no scheme is given and port is not 443, default to HTTPS if no scheme is given AND port is 443
			if (!value.startsWith("http://") && !value.startsWith("https://")) {
				val url: HttpUrl = getUrl(key, "http://$value")
				value = if (url.port == 443) {
					"https://$value"
				} else {
					"http://$value"
				}
			}

			return getUrl(key, value)
		}

		@Throws(AgentOptionParseException::class)
		private fun getUrl(key: String?, value: String) = value.toHttpUrlOrNull() ?: throw AgentOptionParseException("Invalid URL given for option '$key'")

		/**
		 * Splits the given value at semicolons.
		 */
		private fun splitMultiOptionValue(value: String) = value.split(";".toRegex()).dropLastWhile { it.isEmpty() }

		inline fun <reified T : Enum<T>> parseEnumValue(key: String, value: String) = try {
			enumValueOf<T>(value.uppercase().replace("-", "_"))
		} catch (e: IllegalArgumentException) {
			val validValues = enumValues<T>().joinToString(", ") { it.name }
			throw AgentOptionParseException("Invalid value for option `$key`. Valid values: $validValues", e)
		}
	}
}
