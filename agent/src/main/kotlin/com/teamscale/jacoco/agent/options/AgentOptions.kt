package com.teamscale.jacoco.agent.options

import com.teamscale.client.EReportFormat
import com.teamscale.client.FileSystemUtils.ensureDirectoryExists
import com.teamscale.client.FileSystemUtils.getFileExtension
import com.teamscale.client.ProxySystemProperties
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.client.TeamscaleClient
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.commandline.Validator
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitMultiProjectPropertiesLocator
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatingTransformer
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.getCommitInfoFromGitProperties
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils.getProjectRevisionsFromGitProperties
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitSingleProjectPropertiesLocator
import com.teamscale.jacoco.agent.commit_resolution.sapnwdi.NwdiMarkerClassLocatingTransformer
import com.teamscale.jacoco.agent.configuration.ConfigurationViaTeamscale
import com.teamscale.jacoco.agent.options.AgentOptions.Companion.GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION
import com.teamscale.jacoco.agent.options.AgentOptions.Companion.GIT_PROPERTIES_JAR_OPTION
import com.teamscale.jacoco.agent.options.sapnwdi.DelayedSapNwdiMultiUploader
import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication
import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.jacoco.agent.upload.LocalDiskUploader
import com.teamscale.jacoco.agent.upload.UploaderException
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryUploader
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageConfig
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageUploader
import com.teamscale.jacoco.agent.upload.delay.DelayedUploader
import com.teamscale.jacoco.agent.upload.teamscale.DelayedTeamscaleMultiProjectUploader
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader
import com.teamscale.jacoco.agent.util.AgentUtils
import com.teamscale.jacoco.agent.util.AgentUtils.mainTempDirectory
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import java.io.File
import java.io.IOException
import java.lang.instrument.Instrumentation
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.math.max

/**
 * Parses agent command line options.
 */
open class AgentOptions(private val logger: ILogger) {
	/** See [GIT_PROPERTIES_JAR_OPTION]  */
	@JvmField
	var gitPropertiesJar: File? = null

	/**
	 * Related to [GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION]
	 */
	@JvmField
	var gitPropertiesCommitTimeFormat: DateTimeFormatter? = null

	/**
	 * The original options passed to the agent.
	 */
	@JvmField
	var originalOptionsString: String? = null

	/** Whether debug logging is active or not. */
	@JvmField
	var isDebugLogging: Boolean = false

	/** Explicitly defined log file. */
	@JvmField
	var debugLogDirectory: Path? = null

	/**
	 * The directories and/or zips that contain all class files being profiled. Never null. If this is empty, classes
	 * should be dumped to a temporary directory which should be used as the class-dir.
	 */
	@JvmField
	var classDirectoriesOrZips = mutableListOf<File>()

	/**
	 * The logging configuration file.
	 */
	@JvmField
	var loggingConfig: Path? = null

	/**
	 * The directory to write the XML traces to.
	 */
	var outputDirectory: Path? = null

	/** Contains the options related to teamscale-specific proxy settings for http.  */
	var teamscaleProxyOptionsForHttp: TeamscaleProxyOptions?

	/** Contains the options related to teamscale-specific proxy settings for https.  */
	var teamscaleProxyOptionsForHttps: TeamscaleProxyOptions?

	/** Additional metadata files to upload together with the coverage XML. */
	@JvmField
	var additionalMetaDataFiles = listOf<Path>()

	/** Whether the agent should be run in testwise coverage mode or normal mode.  */
	@JvmField
	var mode = EMode.NORMAL

	/** The interval in minutes for dumping XML data. */
	@JvmField
	var dumpIntervalInMinutes = 480

	/** Whether to dump coverage when the JVM shuts down. */
	@JvmField
	var shouldDumpOnExit = true

	/**
	 * Whether to search directories and jar files recursively for git.properties files
	 */
	@JvmField
	var searchGitPropertiesRecursively = true

	/**
	 * Whether to validate SSL certificates, defaults to true.
	 */
	@JvmField
	var validateSsl = true

	/**
	 * Whether to ignore duplicate, non-identical class files.
	 */
	@JvmField
	var duplicateClassFileBehavior = EDuplicateClassFileBehavior.WARN

	/**
	 * Include patterns for fully qualified class names to pass on to JaCoCo. See [org.jacoco.core.runtime.WildcardMatcher] for the
	 * pattern syntax. Individual patterns must be separated by ":".
	 */
	@JvmField
	var jacocoIncludes: String? = null

	/**
	 * Exclude patterns for fully qualified class names to pass on to JaCoCo. See [org.jacoco.core.runtime.WildcardMatcher] for the
	 * pattern syntax. Individual patterns must be separated by ":".
	 */
	@JvmField
	var jacocoExcludes = DEFAULT_EXCLUDES

	/**
	 * Additional user-provided options to pass to JaCoCo.
	 */
	@JvmField
	var additionalJacocoOptions = mutableListOf<Pair<String, String>>()

	/**
	 * The teamscale server to which coverage should be uploaded.
	 */
	@JvmField
	var teamscaleServer = TeamscaleServer()

	/**
	 * How testwise coverage should be handled in test-wise mode.
	 */
	@JvmField
	var testwiseCoverageMode = ETestwiseCoverageMode.EXEC_FILE

	/**
	 * Returns the port at which the http server should listen for test execution information or null if disabled.
	 */
	@JvmField
	var httpServerPort: Int? = null

	/**
	 * Whether classes without coverage should be skipped from the XML report.
	 */
	@JvmField
	var ignoreUncoveredClasses = false

	/**
	 * The configuration necessary to upload files to an azure file storage
	 */
	@JvmField
	var artifactoryConfig = ArtifactoryConfig()

	/**
	 * The configuration necessary to upload files to an azure file storage
	 */
	@JvmField
	var azureFileStorageConfig = AzureFileStorageConfig()

	/**
	 * The configuration necessary when used in an SAP NetWeaver Java environment.
	 */
	@JvmField
	var sapNetWeaverJavaApplications = listOf<SapNwdiApplication>()

	/**
	 * Whether to obfuscate security related configuration options when dumping them into the log or onto the console or
	 * not.
	 */
	@JvmField
	var obfuscateSecurityRelatedOutputs = true

	/**
	 * Helper class that holds the process information, Teamscale client and profiler configuration and allows to
	 * continuously update the profiler's info in Teamscale in the background via
	 * [ConfigurationViaTeamscale.startHeartbeatThreadAndRegisterShutdownHook].
	 */
	@JvmField
	var configurationViaTeamscale: ConfigurationViaTeamscale? = null

	init {
		setParentOutputDirectory(mainTempDirectory.resolve("coverage"))
		teamscaleProxyOptionsForHttp = TeamscaleProxyOptions(
			ProxySystemProperties.Protocol.HTTP, logger
		)
		teamscaleProxyOptionsForHttps = TeamscaleProxyOptions(
			ProxySystemProperties.Protocol.HTTPS, logger
		)
	}

	/**
	 * Remove parts of the API key for security reasons from the options string. String is used for logging purposes.
	 *
	 * Given, for example, "config-file=jacocoagent.properties,teamscale-access-token=unlYgehaYYYhbPAegNWV3WgjOzxkmNHn"
	 * we produce a string with obfuscation:
	 * "config-file=jacocoagent.properties,teamscale-access-token=************mNHn"
	 */
	val obfuscatedOptionsString: String?
		get() {
			val original = originalOptionsString ?: return ""

			val pattern = Pattern.compile("(.*-access-token=)([^,]+)(.*)")
			val match = pattern.matcher(original)
			if (match.find()) {
				val apiKey = match.group(2)
				val obfuscatedApiKey = "************${
					apiKey.substring(
						max(
							0,
							apiKey.length - 4
						)
					)
				}"
				return "${match.group(1)}$obfuscatedApiKey${match.group(3)}"
			}

			return originalOptionsString
		}

	/**
	 * Validates the options and returns a validator with all validation errors.
	 */
	val validator: Validator
		get() = Validator().apply {
			validateFilePaths()
			validateLoggingConfig()
			validateTeamscaleUploadConfig()
			validateUploadConfig()
			validateSapNetWeaverConfig()
			if (useTestwiseCoverageMode()) {
				validateTestwiseCoverageConfig()
			}
		}

	private fun Validator.validateFilePaths() {
		classDirectoriesOrZips.forEach { path ->
			isTrue(path.exists(), "Path '$path' does not exist")
			isTrue(path.canRead(), "Path '$path' is not readable")
		}
	}

	private fun Validator.validateLoggingConfig() {
		val loggingConfig = loggingConfig ?: return
		ensure {
			isTrue(
				loggingConfig.exists(),
				"The path provided for the logging configuration does not exist: $loggingConfig"
			)
			isTrue(
				loggingConfig.isRegularFile(),
				"The path provided for the logging configuration is not a file: $loggingConfig"
			)
			isTrue(
				loggingConfig.isReadable(),
				"The file provided for the logging configuration is not readable: $loggingConfig"
			)
			isTrue(
				"xml".equals(getFileExtension(loggingConfig.toFile()), ignoreCase = true),
				"The logging configuration file must have the file extension .xml and be a valid XML file"
			)
		}
	}

	private fun Validator.validateTeamscaleUploadConfig() {
		isTrue(
			teamscaleServer.hasAllFieldsNull() || teamscaleServer.canConnectToTeamscale() || teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload || teamscaleServer.isConfiguredForMultiProjectUpload,
			"You did provide some options prefixed with 'teamscale-', but not all required ones!"
		)
		isFalse(
			teamscaleServer.isConfiguredForMultiProjectUpload && (teamscaleServer.revision != null
					|| teamscaleServer.commit != null),
			"You tried to provide a commit to upload to directly. This is not possible, since you" +
					" did not provide the 'teamscale-project' to upload to. Please either specify the 'teamscale-project'" +
					" property, or provide the respective projects and commits via all the profiled Jar/War/Ear/...s' " +
					" git.properties files."
		)
		isTrue(
			teamscaleServer.revision == null || teamscaleServer.commit == null,
			"'" + TeamscaleConfig.TEAMSCALE_REVISION_OPTION + "' and '" + TeamscaleConfig.TEAMSCALE_REVISION_MANIFEST_JAR_OPTION + "' are incompatible with '" + TeamscaleConfig.TEAMSCALE_COMMIT_OPTION + "' and '" +
					TeamscaleConfig.TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION + "'."
		)
		isTrue(
			teamscaleServer.project == null || teamscaleServer.partition != null,
			"You configured a 'teamscale-project' but no 'teamscale-partition' to upload to."
		)
	}

	private fun Validator.validateUploadConfig() {
		isTrue(
			(artifactoryConfig.hasAllRequiredFieldsSet() || artifactoryConfig
				.hasAllRequiredFieldsNull()),
			String.format(
				"If you want to upload data to Artifactory you need to provide " +
						"'%s', '%s' and an authentication method (either '%s' and '%s' or '%s') ",
				ArtifactoryConfig.ARTIFACTORY_URL_OPTION,
				ArtifactoryConfig.ARTIFACTORY_PARTITION,
				ArtifactoryConfig.ARTIFACTORY_USER_OPTION, ArtifactoryConfig.ARTIFACTORY_PASSWORD_OPTION,
				ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION
			)
		)
		isTrue(
			(azureFileStorageConfig.hasAllRequiredFieldsSet() || azureFileStorageConfig
				.hasAllRequiredFieldsNull()),
			"If you want to upload data to an Azure file storage you need to provide both " +
					"'azure-url' and 'azure-key' "
		)
		val configuredStores = listOf(
			artifactoryConfig.hasAllRequiredFieldsSet(), azureFileStorageConfig.hasAllRequiredFieldsSet(),
			teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload,
			teamscaleServer.isConfiguredForMultiProjectUpload
		).count { x -> x }

		isTrue(
			configuredStores <= 1, "You cannot configure multiple upload stores, " +
					"such as a Teamscale instance, upload URL, Azure file storage or artifactory"
		)
	}

	private fun Validator.validateSapNetWeaverConfig() {
		if (sapNetWeaverJavaApplications.isEmpty()) return

		isTrue(
			teamscaleServer.project == null,
			"You provided an SAP NWDI applications config and a teamscale-project. This is not allowed. " +
					"The project must be specified via sap-nwdi-applications!"
		)
		isTrue(
			teamscaleServer.project != null || teamscaleServer.isConfiguredForMultiProjectUpload,
			"You provided an SAP NWDI applications config, but the 'teamscale-' upload options are incomplete."
		)
	}

	private fun Validator.validateTestwiseCoverageConfig() {
		isTrue(
			httpServerPort != null,
			"You use 'mode=testwise' but did not specify the required option 'http-server-port'!"
		)
		isTrue(
			testwiseCoverageMode != ETestwiseCoverageMode.TEAMSCALE_UPLOAD
					|| teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload,
			"You use 'tia-mode=teamscale-upload' but did not set all required 'teamscale-' fields to facilitate" +
					" a connection to Teamscale!"
		)
		isTrue(
			testwiseCoverageMode != ETestwiseCoverageMode.TEAMSCALE_UPLOAD || teamscaleServer.hasCommitOrRevision(),
			"You use 'tia-mode=teamscale-upload' but did not provide a revision or commit via the agent's '" + TeamscaleConfig.TEAMSCALE_REVISION_OPTION + "', '" +
					TeamscaleConfig.TEAMSCALE_REVISION_MANIFEST_JAR_OPTION + "', '" + TeamscaleConfig.TEAMSCALE_COMMIT_OPTION +
					"', '" + TeamscaleConfig.TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION + "' or '" +
					GIT_PROPERTIES_JAR_OPTION + "' option." +
					" Auto-detecting the git.properties is currently not supported in this mode."
		)
	}

	/**
	 * Creates a [TeamscaleClient] based on the agent options. Returns null if the user did not fully configure a
	 * Teamscale connection.
	 */
	fun createTeamscaleClient(requireSingleProjectUploadConfig: Boolean): TeamscaleClient? {
		if (teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload ||
			!requireSingleProjectUploadConfig && teamscaleServer.isConfiguredForServerConnection
		) {
			return TeamscaleClient(
				teamscaleServer.url.toString(), teamscaleServer.userName!!,
				teamscaleServer.userAccessToken!!, teamscaleServer.project,
				AgentUtils.USER_AGENT
			)
		}
		return null
	}

	/** All available upload methods.  */ /*package*/
	enum class EUploadMethod {
		/** Saving coverage files on disk.  */
		LOCAL_DISK,
		/** Sending coverage to a single Teamscale project.  */
		TEAMSCALE_SINGLE_PROJECT,
		/** Sending coverage to multiple Teamscale projects.  */
		TEAMSCALE_MULTI_PROJECT,
		/** Sending coverage to multiple Teamscale projects based on SAP NWDI application definitions.  */
		SAP_NWDI_TEAMSCALE,
		/** Sending coverage to an Artifactory.  */
		ARTIFACTORY,
		/** Sending coverage to Azure file storage.  */
		AZURE_FILE_STORAGE,
	}

	/** Determines the upload method that should be used based on the set options.  */ /*package*/
	fun determineUploadMethod() = when {
		artifactoryConfig.hasAllRequiredFieldsSet() -> EUploadMethod.ARTIFACTORY
		azureFileStorageConfig.hasAllRequiredFieldsSet() -> EUploadMethod.AZURE_FILE_STORAGE
		!sapNetWeaverJavaApplications.isEmpty() -> EUploadMethod.SAP_NWDI_TEAMSCALE
		teamscaleServer.isConfiguredForMultiProjectUpload -> EUploadMethod.TEAMSCALE_MULTI_PROJECT
		teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload -> EUploadMethod.TEAMSCALE_SINGLE_PROJECT
		else -> EUploadMethod.LOCAL_DISK
	}

	/** Creates an uploader for the coverage XMLs. */
	@Throws(UploaderException::class)
	fun createUploader(instrumentation: Instrumentation?) = when (determineUploadMethod()) {
		EUploadMethod.TEAMSCALE_MULTI_PROJECT -> createTeamscaleMultiProjectUploader(instrumentation)
		EUploadMethod.TEAMSCALE_SINGLE_PROJECT -> createTeamscaleSingleProjectUploader(instrumentation)
		EUploadMethod.ARTIFACTORY -> createArtifactoryUploader(instrumentation)
		EUploadMethod.AZURE_FILE_STORAGE -> AzureFileStorageUploader(
			azureFileStorageConfig,
			additionalMetaDataFiles
		)
		EUploadMethod.SAP_NWDI_TEAMSCALE -> {
			logger.info("NWDI configuration detected. The Agent will try and auto-detect commit information by searching all profiled Jar/War/Ear/... files.")
			createNwdiTeamscaleUploader(instrumentation)
		}
		EUploadMethod.LOCAL_DISK -> LocalDiskUploader()
	}

	@Throws(UploaderException::class)
	private fun createArtifactoryUploader(instrumentation: Instrumentation?): IUploader {
		gitPropertiesJar?.let { jar ->
			logger.info(
				"You did not provide a commit to upload to directly, so the Agent will try to" +
						"auto-detect it by searching the provided " + GIT_PROPERTIES_JAR_OPTION + " at " +
						jar.absolutePath + " for a git.properties file."
			)
			artifactoryConfig.commitInfo = ArtifactoryConfig.parseGitProperties(
				jar, searchGitPropertiesRecursively, gitPropertiesCommitTimeFormat
			)
		}
		if (!artifactoryConfig.hasCommitInfo()) {
			logger.info(
				"You did not provide a commit to upload to directly, so the Agent will try and" +
						" auto-detect it by searching all profiled Jar/War/Ear/... files for a git.properties file."
			)
			return createDelayedArtifactoryUploader(instrumentation)
		}
		return ArtifactoryUploader(artifactoryConfig, additionalMetaDataFiles, reportFormat)
	}

	private fun createTeamscaleSingleProjectUploader(instrumentation: Instrumentation?): IUploader {
		if (teamscaleServer.hasCommitOrRevision()) {
			return TeamscaleUploader(teamscaleServer)
		}

		val uploader = createDelayedSingleProjectTeamscaleUploader()

		gitPropertiesJar?.let { jar ->
			logger.info(
				"You did not provide a commit to upload to directly, so the Agent will try to" +
						"auto-detect it by searching the provided " + GIT_PROPERTIES_JAR_OPTION + " at " +
						jar.absolutePath + " for a git.properties file."
			)
			startGitPropertiesSearchInJarFile(uploader, jar)
			return uploader
		}

		logger.info(
			"You did not provide a commit to upload to directly, so the Agent will try and" +
					" auto-detect it by searching all profiled Jar/War/Ear/... files for a git.properties file."
		)
		registerSingleGitPropertiesLocator(uploader, instrumentation)
		return uploader
	}

	private fun createTeamscaleMultiProjectUploader(
		instrumentation: Instrumentation?
	): DelayedTeamscaleMultiProjectUploader {
		val uploader = DelayedTeamscaleMultiProjectUploader { project, commitInfo ->
			if (commitInfo!!.preferCommitDescriptorOverRevision || isEmpty(commitInfo.revision)) {
				return@DelayedTeamscaleMultiProjectUploader teamscaleServer.withProjectAndCommit(
					project!!,
					commitInfo.commit!!
				)
			}
			teamscaleServer.withProjectAndRevision(project!!, commitInfo.revision!!)
		}

		gitPropertiesJar?.let { jar ->
			logger.info(
				"You did not provide a Teamscale project to upload to directly, so the Agent will try and" +
						" auto-detect it by searching the provided " + GIT_PROPERTIES_JAR_OPTION + " at " +
						jar.absolutePath + " for a git.properties file."
			)
			startMultiGitPropertiesFileSearchInJarFile(uploader, jar)
			return uploader
		}
		logger.info(
			"You did not provide a Teamscale project to upload to directly, so the Agent will try and" +
					" auto-detect it by searching all profiled Jar/War/Ear/... files for git.properties files" +
					" with the 'teamscale.project' field set."
		)
		registerMultiGitPropertiesLocator(uploader, instrumentation)
		return uploader
	}

	private fun startGitPropertiesSearchInJarFile(
		uploader: DelayedUploader<ProjectAndCommit>,
		gitPropertiesJar: File
	) {
		GitSingleProjectPropertiesLocator(
			uploader, searchGitPropertiesRecursively, gitPropertiesCommitTimeFormat
		) { file, isJarFile, recursiveSearch, timeFormat ->
			getProjectRevisionsFromGitProperties(file, isJarFile, recursiveSearch, timeFormat)
		}.searchFileForGitPropertiesAsync(gitPropertiesJar, true)
	}

	private fun registerSingleGitPropertiesLocator(
		uploader: DelayedUploader<ProjectAndCommit>,
		instrumentation: Instrumentation?
	) {
		val locator = GitSingleProjectPropertiesLocator(
			uploader, searchGitPropertiesRecursively, gitPropertiesCommitTimeFormat
		) { file, isJarFile, recursiveSearch, timeFormat ->
			getProjectRevisionsFromGitProperties(file, isJarFile, recursiveSearch, timeFormat)
		}
		instrumentation?.addTransformer(GitPropertiesLocatingTransformer(locator, locationIncludeFilter))
	}

	private fun createDelayedSingleProjectTeamscaleUploader() =
		DelayedUploader<ProjectAndCommit>(outputDirectory!!) { projectAndCommit ->
			if (!isEmpty(projectAndCommit.project) && (teamscaleServer.project != projectAndCommit.project)) {
				logger.warn(
					"Teamscale project '" + teamscaleServer.project + "' specified in the agent configuration is not the same as the Teamscale project '" + projectAndCommit.project + "' specified in git.properties file(s). Proceeding to upload to the" +
							" Teamscale project '" + teamscaleServer.project + "' specified in the agent configuration."
				)
			}
			if (projectAndCommit.commitInfo!!.preferCommitDescriptorOverRevision ||
				isEmpty(projectAndCommit.commitInfo.revision)
			) {
				teamscaleServer.commit = projectAndCommit.commitInfo.commit
			} else {
				teamscaleServer.revision = projectAndCommit.commitInfo.revision
			}
			TeamscaleUploader(teamscaleServer)
		}

	private fun startMultiGitPropertiesFileSearchInJarFile(
		uploader: DelayedTeamscaleMultiProjectUploader,
		gitPropertiesJar: File
	) {
		GitMultiProjectPropertiesLocator(
			uploader, searchGitPropertiesRecursively, gitPropertiesCommitTimeFormat
		).searchFileForGitPropertiesAsync(gitPropertiesJar, true)
	}

	private fun registerMultiGitPropertiesLocator(
		uploader: DelayedTeamscaleMultiProjectUploader,
		instrumentation: Instrumentation?
	) {
		val locator = GitMultiProjectPropertiesLocator(
			uploader, searchGitPropertiesRecursively, gitPropertiesCommitTimeFormat
		)
		instrumentation?.addTransformer(GitPropertiesLocatingTransformer(locator, locationIncludeFilter))
	}

	private fun createDelayedArtifactoryUploader(instrumentation: Instrumentation?): IUploader {
		val uploader = DelayedUploader<CommitInfo>(outputDirectory!!) { commitInfo ->
			artifactoryConfig.commitInfo = commitInfo
			ArtifactoryUploader(artifactoryConfig, additionalMetaDataFiles, reportFormat)
		}
		val locator = GitSingleProjectPropertiesLocator(
			uploader, searchGitPropertiesRecursively, gitPropertiesCommitTimeFormat
		) { file, isJarFile, recursiveSearch, timeFormat ->
			getCommitInfoFromGitProperties(
				file, isJarFile, recursiveSearch, timeFormat
			)
		}
		instrumentation?.addTransformer(GitPropertiesLocatingTransformer(locator, locationIncludeFilter))
		return uploader
	}

	private fun createNwdiTeamscaleUploader(instrumentation: Instrumentation?): IUploader {
		val uploader = DelayedSapNwdiMultiUploader { commit, application ->
			TeamscaleUploader(
				teamscaleServer.withProjectAndCommit(application.teamscaleProject, commit)
			)
		}
		instrumentation?.addTransformer(
			NwdiMarkerClassLocatingTransformer(
				uploader, locationIncludeFilter, sapNetWeaverJavaApplications
			)
		)
		return uploader
	}

	private val reportFormat: EReportFormat
		get() = if (useTestwiseCoverageMode()) {
			EReportFormat.TESTWISE_COVERAGE
		} else EReportFormat.JACOCO

	/**
	 * Creates a new file with the given prefix, extension and current timestamp and ensures that the parent folder
	 * actually exists.
	 */
	@Throws(IOException::class)
	fun createNewFileInOutputDirectory(prefix: String, extension: String): File {
		ensureDirectoryExists(outputDirectory!!.toFile())
		return outputDirectory!!.resolve(
			"$prefix-${LocalDateTime.now().format(DATE_TIME_FORMATTER)}.$extension"
		).toFile()
	}

	/**
	 * Creates a new file with the given prefix, extension and current timestamp and ensures that the parent folder
	 * actually exists. One output folder is created per partition.
	 */
	@Throws(IOException::class)
	fun createNewFileInPartitionOutputDirectory(prefix: String, extension: String): File {
		val partitionOutputDir = outputDirectory!!.resolve(
			safeFolderName(teamscaleServer.partition!!)
		)
		ensureDirectoryExists(partitionOutputDir.toFile())
		return partitionOutputDir.resolve(
			"$prefix-${LocalDateTime.now().format(DATE_TIME_FORMATTER)}.$extension"
		).toFile()
	}

	/**
	 * Sets the parent of the output directory for this run. The output directory itself will be created in this folder
	 * is named after the current timestamp with the format yyyy-MM-dd-HH-mm-ss.SSS
	 */
	fun setParentOutputDirectory(outputDirectoryParent: Path) {
		outputDirectory = outputDirectoryParent.resolve(LocalDateTime.now().format(DATE_TIME_FORMATTER))
	}

	/** Returns whether the config indicates to use Test Impact mode.  */
	fun useTestwiseCoverageMode() = mode == EMode.TESTWISE

	val locationIncludeFilter: ClasspathWildcardIncludeFilter
		get() = ClasspathWildcardIncludeFilter(jacocoIncludes, jacocoExcludes)

	/** Whether coverage should be dumped in regular intervals.  */
	fun shouldDumpInIntervals() = dumpIntervalInMinutes > 0

	/** @return the [TeamscaleProxyOptions] for the given protocol. */
	fun getTeamscaleProxyOptions(protocol: ProxySystemProperties.Protocol?) =
		if (protocol == ProxySystemProperties.Protocol.HTTP) {
			teamscaleProxyOptionsForHttp
		} else teamscaleProxyOptionsForHttps

	companion object {
		/**
		 * Can be used to format [java.time.LocalDate] to the format "yyyy-MM-dd-HH-mm-ss.SSS"
		 */
		val DATE_TIME_FORMATTER: DateTimeFormatter =
			DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS", Locale.ENGLISH)

		/**
		 * The default excludes applied to JaCoCo. These are packages that should never be profiled. Excluding them makes
		 * debugging traces easier and reduces trace size and warnings about unmatched classes in Teamscale.
		 */
		const val DEFAULT_EXCLUDES =
			"shadow.*:com.sun.*:sun.*:org.eclipse.*:org.junit.*:junit.*:org.apache.*:org.slf4j.*:javax.*:org.gradle.*:java.*:org.jboss.*:org.wildfly.*:org.springframework.*:com.fasterxml.*:jakarta.*:org.aspectj.*:org.h2.*:org.hibernate.*:org.assertj.*:org.mockito.*:org.thymeleaf.*"

		/** Option name that allows to specify a jar file that contains the git commit hash in a git.properties file.  */
		const val GIT_PROPERTIES_JAR_OPTION = "git-properties-jar"

		/**
		 * Specifies the date format in which the commit timestamp in the git.properties file is formatted.
		 */
		const val GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION = "git-properties-commit-date-format"

		private fun safeFolderName(folderName: String): Path {
			val result = folderName.replace("[<>:\"/|?*]".toRegex(), "")
				.replace("\\.+".toRegex(), "dot")
				.replace("\\x00".toRegex(), "")
				.replace("[. ]$".toRegex(), "")

			return if (result.isEmpty()) {
				Paths.get("default")
			} else {
				Paths.get(result)
			}
		}
	}
}
