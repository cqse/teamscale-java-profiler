/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2018 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.options;

import com.teamscale.client.EReportFormat;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.ProxySystemProperties;
import com.teamscale.client.StringUtils;
import com.teamscale.client.TeamscaleClient;
import com.teamscale.client.TeamscaleServer;
import com.teamscale.jacoco.agent.commandline.Validator;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitMultiProjectPropertiesLocator;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatingTransformer;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils;
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitSingleProjectPropertiesLocator;
import com.teamscale.jacoco.agent.commit_resolution.sapnwdi.NwdiMarkerClassLocatingTransformer;
import com.teamscale.jacoco.agent.configuration.ConfigurationViaTeamscale;
import com.teamscale.jacoco.agent.options.sapnwdi.DelayedSapNwdiMultiUploader;
import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication;
import com.teamscale.jacoco.agent.upload.IUploader;
import com.teamscale.jacoco.agent.upload.LocalDiskUploader;
import com.teamscale.jacoco.agent.upload.UploaderException;
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig;
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryUploader;
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageConfig;
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageUploader;
import com.teamscale.jacoco.agent.upload.delay.DelayedUploader;
import com.teamscale.jacoco.agent.upload.teamscale.DelayedTeamscaleMultiProjectUploader;
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleConfig;
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader;
import com.teamscale.jacoco.agent.util.AgentUtils;
import com.teamscale.report.EDuplicateClassFileBehavior;
import com.teamscale.report.util.ClasspathWildcardIncludeFilter;
import com.teamscale.report.util.ILogger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.conqat.lib.commons.assertion.CCSMAssert;
import org.conqat.lib.commons.collections.PairList;
import org.jacoco.core.runtime.WildcardMatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses agent command line options.
 */
public class AgentOptions {

	/**
	 * Can be used to format {@link LocalDate} to the format "yyyy-MM-dd-HH-mm-ss.SSS"
	 */
	/* package */ static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
			.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS", Locale.ENGLISH);

	/**
	 * The default excludes applied to JaCoCo. These are packages that should never be profiled. Excluding them makes
	 * debugging traces easier and reduces trace size and warnings about unmatched classes in Teamscale.
	 */
	public static final String DEFAULT_EXCLUDES = "shadow.*:com.sun.*:sun.*:org.eclipse.*:org.junit.*:junit.*:org.apache.*:org.slf4j.*:javax.*:org.gradle.*:java.*:org.jboss.*:org.wildfly.*:org.springframework.*:com.fasterxml.*:jakarta.*:org.aspectj.*:org.h2.*:org.hibernate.*:org.assertj.*:org.mockito.*:org.thymeleaf.*";

	private final ILogger logger;

	/** See {@link AgentOptions#GIT_PROPERTIES_JAR_OPTION} */
	/* package */ File gitPropertiesJar;

	/**
	 * Related to {@link AgentOptions#GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION}
	 */
	public DateTimeFormatter gitPropertiesCommitTimeFormat = null;

	/** Option name that allows to specify a jar file that contains the git commit hash in a git.properties file. */
	public static final String GIT_PROPERTIES_JAR_OPTION = "git-properties-jar";

	/**
	 * Specifies the date format in which the commit timestamp in the git.properties file is formatted.
	 */
	public static final String GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION = "git-properties-commit-date-format";

	/**
	 * The original options passed to the agent.
	 */
	/* package */ String originalOptionsString;

	/** Whether debug logging is active or not. */
	/* package */ boolean debugLogging = false;

	/** Explicitly defined log file. */
	/* package */ Path debugLogDirectory = null;

	/**
	 * The directories and/or zips that contain all class files being profiled. Never null. If this is empty, classes
	 * should be dumped to a temporary directory which should be used as the class-dir.
	 */
	/* package */ List<File> classDirectoriesOrZips = new ArrayList<>();

	/**
	 * The logging configuration file.
	 */
	/* package */ Path loggingConfig = null;

	/**
	 * The directory to write the XML traces to.
	 */
	private Path outputDirectory;

	/** Contains the options related to teamscale-specific proxy settings for http. */
	/* package */ TeamscaleProxyOptions teamscaleProxyOptionsForHttp;

	/** Contains the options related to teamscale-specific proxy settings for https. */
	/* package */ TeamscaleProxyOptions teamscaleProxyOptionsForHttps;

	/**
	 * Additional metadata files to upload together with the coverage XML.
	 */
	/* package */ List<Path> additionalMetaDataFiles = new ArrayList<>();

	/** Whether the agent should be run in testwise coverage mode or normal mode. */
	/* package */ EMode mode = EMode.NORMAL;

	/**
	 * The interval in minutes for dumping XML data.
	 */
	/* package */ int dumpIntervalInMinutes = 480;

	/** Whether to dump coverage when the JVM shuts down. */
	/* package */ boolean shouldDumpOnExit = true;

	/**
	 * Whether to search directories and jar files recursively for git.properties files
	 */
	/* package */ boolean searchGitPropertiesRecursively = true;

	/**
	 * Whether to validate SSL certificates, defaults to true.
	 */
	/* package */ boolean validateSsl = true;

	/**
	 * Whether to ignore duplicate, non-identical class files.
	 */
	/* package */ EDuplicateClassFileBehavior duplicateClassFileBehavior = EDuplicateClassFileBehavior.WARN;

	/**
	 * Include patterns for fully qualified class names to pass on to JaCoCo. See {@link WildcardMatcher} for the
	 * pattern syntax. Individual patterns must be separated by ":".
	 */
	/* package */ String jacocoIncludes = null;

	/**
	 * Exclude patterns for fully qualified class names to pass on to JaCoCo. See {@link WildcardMatcher} for the
	 * pattern syntax. Individual patterns must be separated by ":".
	 */
	/* package */ String jacocoExcludes = DEFAULT_EXCLUDES;

	/**
	 * Additional user-provided options to pass to JaCoCo.
	 */
	/* package */ PairList<String, String> additionalJacocoOptions = new PairList<>();

	/**
	 * The teamscale server to which coverage should be uploaded.
	 */
	/* package */ TeamscaleServer teamscaleServer = new TeamscaleServer();

	/**
	 * How testwise coverage should be handled in test-wise mode.
	 */
	/*package*/ ETestwiseCoverageMode testwiseCoverageMode = ETestwiseCoverageMode.EXEC_FILE;

	/**
	 * The port on which the HTTP server should be listening.
	 */
	/* package */ Integer httpServerPort = null;

	/**
	 * Whether classes without coverage should be skipped from the XML report.
	 */
	/* package */ boolean ignoreUncoveredClasses = false;

	/**
	 * The configuration necessary to upload files to an azure file storage
	 */
	/* package */ ArtifactoryConfig artifactoryConfig = new ArtifactoryConfig();

	/**
	 * The configuration necessary to upload files to an azure file storage
	 */
	/* package */ AzureFileStorageConfig azureFileStorageConfig = new AzureFileStorageConfig();

	/**
	 * The configuration necessary when used in an SAP NetWeaver Java environment.
	 */
	/* package */ List<SapNwdiApplication> sapNetWeaverJavaApplications = new ArrayList<>();

	/**
	 * Whether to obfuscate security related configuration options when dumping them into the log or onto the console or
	 * not.
	 */
	/* package */ boolean obfuscateSecurityRelatedOutputs = true;

	/**
	 * Helper class that holds the process information, Teamscale client and profiler configuration and allows to
	 * continuously update the profiler's info in Teamscale in the background via
	 * {@link ConfigurationViaTeamscale#startHeartbeatThreadAndRegisterShutdownHook()}.
	 */
	public ConfigurationViaTeamscale configurationViaTeamscale;

	public AgentOptions(ILogger logger) {
		this.logger = logger;
		setParentOutputDirectory(AgentUtils.getMainTempDirectory().resolve("coverage"));
		teamscaleProxyOptionsForHttp = new TeamscaleProxyOptions(
				ProxySystemProperties.Protocol.HTTP, logger);
		teamscaleProxyOptionsForHttps = new TeamscaleProxyOptions(
				ProxySystemProperties.Protocol.HTTPS, logger);
	}

	/** @see #debugLogging */
	public boolean isDebugLogging() {
		return debugLogging;
	}

	/** @see #debugLogDirectory */
	public Path getDebugLogDirectory() {
		return debugLogDirectory;
	}

	/**
	 * @see #originalOptionsString
	 */
	public String getOriginalOptionsString() {
		return originalOptionsString;
	}

	/**
	 * Remove parts of the API key for security reasons from the options string. String is used for logging purposes.
	 * <p>
	 * Given, for example, "config-file=jacocoagent.properties,teamscale-access-token=unlYgehaYYYhbPAegNWV3WgjOzxkmNHn"
	 * we produce a string with obfuscation:
	 * "config-file=jacocoagent.properties,teamscale-access-token=************mNHn"
	 */
	public String getObfuscatedOptionsString() {
		if (getOriginalOptionsString() == null) {
			return "";
		}

		Pattern pattern = Pattern.compile("(.*-access-token=)([^,]+)(.*)");
		Matcher match = pattern.matcher(getOriginalOptionsString());
		if (match.find()) {
			String apiKey = match.group(2);
			String obfuscatedApiKey = String.format("************%s", apiKey.substring(Math.max(0,
					apiKey.length() - 4)));
			return String.format("%s%s%s", match.group(1), obfuscatedApiKey, match.group(3));
		}

		return getOriginalOptionsString();
	}

	/**
	 * Validates the options and returns a validator with all validation errors.
	 */
	/* package */ Validator getValidator() {
		Validator validator = new Validator();

		validateFilePaths(validator);

		if (loggingConfig != null) {
			validateLoggingConfig(validator);
		}

		validateTeamscaleUploadConfig(validator);

		validateUploadConfig(validator);

		validateSapNetWeaverConfig(validator);

		if (useTestwiseCoverageMode()) {
			validateTestwiseCoverageConfig(validator);
		}

		return validator;
	}

	private void validateFilePaths(Validator validator) {
		for (File path : classDirectoriesOrZips) {
			validator.isTrue(path.exists(), "Path '" + path + "' does not exist");
			validator.isTrue(path.canRead(), "Path '" + path + "' is not readable");
		}
	}

	private void validateLoggingConfig(Validator validator) {
		validator.ensure(() -> {
			CCSMAssert.isTrue(Files.exists(loggingConfig),
					"The path provided for the logging configuration does not exist: " + loggingConfig);
			CCSMAssert.isTrue(Files.isRegularFile(loggingConfig),
					"The path provided for the logging configuration is not a file: " + loggingConfig);
			CCSMAssert.isTrue(Files.isReadable(loggingConfig),
					"The file provided for the logging configuration is not readable: " + loggingConfig);
			CCSMAssert.isTrue("xml".equalsIgnoreCase(FileSystemUtils.getFileExtension(loggingConfig.toFile())),
					"The logging configuration file must have the file extension .xml and be a valid XML file");
		});
	}

	private void validateTeamscaleUploadConfig(Validator validator) {
		validator.isTrue(
				teamscaleServer.hasAllFieldsNull() || teamscaleServer.canConnectToTeamscale() || teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload() || teamscaleServer.isConfiguredForMultiProjectUpload(),
				"You did provide some options prefixed with 'teamscale-', but not all required ones!");

		validator.isFalse(teamscaleServer.isConfiguredForMultiProjectUpload() && (teamscaleServer.revision != null
						|| teamscaleServer.commit != null),
				"You tried to provide a commit to upload to directly. This is not possible, since you" +
						" did not provide the 'teamscale-project' to upload to. Please either specify the 'teamscale-project'" +
						" property, or provide the respective projects and commits via all the profiled Jar/War/Ear/...s' " +
						" git.properties files.");

		validator.isTrue(teamscaleServer.revision == null || teamscaleServer.commit == null,
				"'" + TeamscaleConfig.TEAMSCALE_REVISION_OPTION + "' and '" + TeamscaleConfig.TEAMSCALE_REVISION_MANIFEST_JAR_OPTION + "' are incompatible with '" + TeamscaleConfig.TEAMSCALE_COMMIT_OPTION + "' and '" +
						TeamscaleConfig.TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION + "'.");

		validator.isTrue(teamscaleServer.project == null || teamscaleServer.partition != null,
				"You configured a 'teamscale-project' but no 'teamscale-partition' to upload to.");
	}

	private void validateUploadConfig(Validator validator) {
		validator.isTrue((artifactoryConfig.hasAllRequiredFieldsSet() || artifactoryConfig
						.hasAllRequiredFieldsNull()),
				String.format("If you want to upload data to Artifactory you need to provide " +
								"'%s', '%s' and an authentication method (either '%s' and '%s' or '%s') ",
						ArtifactoryConfig.ARTIFACTORY_URL_OPTION,
						ArtifactoryConfig.ARTIFACTORY_PARTITION,
						ArtifactoryConfig.ARTIFACTORY_USER_OPTION, ArtifactoryConfig.ARTIFACTORY_PASSWORD_OPTION,
						ArtifactoryConfig.ARTIFACTORY_API_KEY_OPTION));

		validator.isTrue((azureFileStorageConfig.hasAllRequiredFieldsSet() || azureFileStorageConfig
						.hasAllRequiredFieldsNull()),
				"If you want to upload data to an Azure file storage you need to provide both " +
						"'azure-url' and 'azure-key' ");

		long configuredStores = Stream
				.of(artifactoryConfig.hasAllRequiredFieldsSet(), azureFileStorageConfig.hasAllRequiredFieldsSet(),
						teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload(),
						teamscaleServer.isConfiguredForMultiProjectUpload()).filter(x -> x)
				.count();

		validator.isTrue(configuredStores <= 1, "You cannot configure multiple upload stores, " +
				"such as a Teamscale instance, upload URL, Azure file storage or artifactory");
	}

	private void validateSapNetWeaverConfig(Validator validator) {
		if (sapNetWeaverJavaApplications.isEmpty()) {
			return;
		}

		validator.isTrue(teamscaleServer.project == null,
				"You provided an SAP NWDI applications config and a teamscale-project. This is not allowed. " +
						"The project must be specified via sap-nwdi-applications!");

		validator.isTrue(teamscaleServer.project != null || teamscaleServer.isConfiguredForMultiProjectUpload(),
				"You provided an SAP NWDI applications config, but the 'teamscale-' upload options are incomplete.");

	}

	private void validateTestwiseCoverageConfig(Validator validator) {
		validator.isTrue(httpServerPort != null,
				"You use 'mode=testwise' but did not specify the required option 'http-server-port'!");

		validator.isTrue(testwiseCoverageMode != ETestwiseCoverageMode.TEAMSCALE_UPLOAD
						|| teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload(),
				"You use 'tia-mode=teamscale-upload' but did not set all required 'teamscale-' fields to facilitate" +
						" a connection to Teamscale!");

		validator.isTrue(
				testwiseCoverageMode != ETestwiseCoverageMode.TEAMSCALE_UPLOAD || teamscaleServer.hasCommitOrRevision(),
				"You use 'tia-mode=teamscale-upload' but did not provide a revision or commit via the agent's '" + TeamscaleConfig.TEAMSCALE_REVISION_OPTION + "', '" +
						TeamscaleConfig.TEAMSCALE_REVISION_MANIFEST_JAR_OPTION + "', '" + TeamscaleConfig.TEAMSCALE_COMMIT_OPTION +
						"', '" + TeamscaleConfig.TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION + "' or '" +
						AgentOptions.GIT_PROPERTIES_JAR_OPTION + "' option." +
						" Auto-detecting the git.properties is currently not supported in this mode.");
	}

	/**
	 * Creates a {@link TeamscaleClient} based on the agent options. Returns null if the user did not fully configure a
	 * Teamscale connection.
	 */
	public @Nullable TeamscaleClient createTeamscaleClient(boolean requireSingleProjectUploadConfig) {
		if (teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload() ||
				!requireSingleProjectUploadConfig && teamscaleServer.isConfiguredForServerConnection()) {
			return new TeamscaleClient(teamscaleServer.url.toString(), teamscaleServer.userName,
					teamscaleServer.userAccessToken, teamscaleServer.project);
		}
		return null;
	}

	/** All available upload methods. */
	/*package*/ enum EUploadMethod {
		/** Saving coverage files on disk. */
		LOCAL_DISK,
		/** Sending coverage to a single Teamscale project. */
		TEAMSCALE_SINGLE_PROJECT,
		/** Sending coverage to multiple Teamscale projects. */
		TEAMSCALE_MULTI_PROJECT,
		/** Sending coverage to multiple Teamscale projects based on SAP NWDI application definitions. */
		SAP_NWDI_TEAMSCALE,
		/** Sending coverage to an Artifactory. */
		ARTIFACTORY,
		/** Sending coverage to Azure file storage. */
		AZURE_FILE_STORAGE,
	}

	/** Determines the upload method that should be used based on the set options. */
	/*package*/ EUploadMethod determineUploadMethod() {
		if (artifactoryConfig.hasAllRequiredFieldsSet()) {
			return EUploadMethod.ARTIFACTORY;
		}
		if (azureFileStorageConfig.hasAllRequiredFieldsSet()) {
			return EUploadMethod.AZURE_FILE_STORAGE;
		}
		if (!sapNetWeaverJavaApplications.isEmpty()) {
			return EUploadMethod.SAP_NWDI_TEAMSCALE;
		}
		if (teamscaleServer.isConfiguredForMultiProjectUpload()) {
			return EUploadMethod.TEAMSCALE_MULTI_PROJECT;
		}
		if (teamscaleServer.isConfiguredForSingleProjectTeamscaleUpload()) {
			return EUploadMethod.TEAMSCALE_SINGLE_PROJECT;
		}
		return EUploadMethod.LOCAL_DISK;
	}

	/**
	 * Creates an uploader for the coverage XMLs.
	 */
	public IUploader createUploader(Instrumentation instrumentation) throws UploaderException {
		EUploadMethod uploadMethod = determineUploadMethod();
		switch (uploadMethod) {
			case TEAMSCALE_MULTI_PROJECT:
				return createTeamscaleMultiProjectUploader(instrumentation);
			case TEAMSCALE_SINGLE_PROJECT:
				return createTeamscaleSingleProjectUploader(instrumentation);
			case ARTIFACTORY:
				return createArtifactoryUploader(instrumentation);
			case AZURE_FILE_STORAGE:
				return new AzureFileStorageUploader(azureFileStorageConfig,
						additionalMetaDataFiles);
			case SAP_NWDI_TEAMSCALE: {
				logger.info("NWDI configuration detected. The Agent will try and" +
						" auto-detect commit information by searching all profiled Jar/War/Ear/... files.");
				return createNwdiTeamscaleUploader(instrumentation);
			}
			case LOCAL_DISK:
				return new LocalDiskUploader();
			default:
				throw new RuntimeException("Unhandled upload method " + uploadMethod + "."
						+ " This is a bug, please report this to CQSE.");
		}
	}

	@NotNull
	private IUploader createArtifactoryUploader(Instrumentation instrumentation) throws UploaderException {
		if (gitPropertiesJar != null) {
			logger.info("You did not provide a commit to upload to directly, so the Agent will try to" +
					"auto-detect it by searching the provided " + GIT_PROPERTIES_JAR_OPTION + " at " +
					gitPropertiesJar.getAbsolutePath() + " for a git.properties file.");
			artifactoryConfig.commitInfo = ArtifactoryConfig.parseGitProperties(gitPropertiesJar,
					this.searchGitPropertiesRecursively, this.gitPropertiesCommitTimeFormat);
		}
		if (!artifactoryConfig.hasCommitInfo()) {
			logger.info("You did not provide a commit to upload to directly, so the Agent will try and" +
					" auto-detect it by searching all profiled Jar/War/Ear/... files for a git.properties file.");
			return createDelayedArtifactoryUploader(instrumentation);
		}
		return new ArtifactoryUploader(artifactoryConfig,
				additionalMetaDataFiles, getReportFormat());
	}

	@NotNull
	private IUploader createTeamscaleSingleProjectUploader(Instrumentation instrumentation) {
		if (teamscaleServer.hasCommitOrRevision()) {
			return new TeamscaleUploader(teamscaleServer);
		}

		DelayedUploader<ProjectAndCommit> uploader = createDelayedSingleProjectTeamscaleUploader();

		if (gitPropertiesJar != null) {
			logger.info("You did not provide a commit to upload to directly, so the Agent will try to" +
					"auto-detect it by searching the provided " + GIT_PROPERTIES_JAR_OPTION + " at " +
					gitPropertiesJar.getAbsolutePath() + " for a git.properties file.");
			startGitPropertiesSearchInJarFile(uploader, gitPropertiesJar);
			return uploader;
		}

		logger.info("You did not provide a commit to upload to directly, so the Agent will try and" +
				" auto-detect it by searching all profiled Jar/War/Ear/... files for a git.properties file.");
		registerSingleGitPropertiesLocator(uploader, instrumentation);
		return uploader;
	}

	@NotNull
	private DelayedTeamscaleMultiProjectUploader createTeamscaleMultiProjectUploader(
			Instrumentation instrumentation) {
		DelayedTeamscaleMultiProjectUploader uploader = new DelayedTeamscaleMultiProjectUploader(
				(project, commitInfo) -> {
					if (commitInfo.preferCommitDescriptorOverRevision || StringUtils.isEmpty(commitInfo.revision)) {
						return teamscaleServer.withProjectAndCommit(project, commitInfo.commit);
					}
					return teamscaleServer.withProjectAndRevision(project, commitInfo.revision);
				});

		if (gitPropertiesJar != null) {
			logger.info(
					"You did not provide a Teamscale project to upload to directly, so the Agent will try and" +
							" auto-detect it by searching the provided " + GIT_PROPERTIES_JAR_OPTION + " at " +
							gitPropertiesJar.getAbsolutePath() + " for a git.properties file.");

			startMultiGitPropertiesFileSearchInJarFile(uploader, gitPropertiesJar);
			return uploader;
		}

		logger.info("You did not provide a Teamscale project to upload to directly, so the Agent will try and" +
				" auto-detect it by searching all profiled Jar/War/Ear/... files for git.properties files" +
				" with the 'teamscale.project' field set.");
		registerMultiGitPropertiesLocator(uploader, instrumentation);
		return uploader;
	}

	private void startGitPropertiesSearchInJarFile(DelayedUploader<ProjectAndCommit> uploader,
			File gitPropertiesJar) {
		GitSingleProjectPropertiesLocator<ProjectAndCommit> locator = new GitSingleProjectPropertiesLocator<>(uploader,
				GitPropertiesLocatorUtils::getProjectRevisionsFromGitProperties, this.searchGitPropertiesRecursively,
				this.gitPropertiesCommitTimeFormat);
		locator.searchFileForGitPropertiesAsync(gitPropertiesJar, true);
	}

	private void registerSingleGitPropertiesLocator(DelayedUploader<ProjectAndCommit> uploader,
			Instrumentation instrumentation) {
		GitSingleProjectPropertiesLocator<ProjectAndCommit> locator = new GitSingleProjectPropertiesLocator<>(uploader,
				GitPropertiesLocatorUtils::getProjectRevisionsFromGitProperties, this.searchGitPropertiesRecursively,
				this.gitPropertiesCommitTimeFormat);
		instrumentation.addTransformer(new GitPropertiesLocatingTransformer(locator, getLocationIncludeFilter()));
	}

	private DelayedUploader<ProjectAndCommit> createDelayedSingleProjectTeamscaleUploader() {
		return new DelayedUploader<>(
				projectAndCommit -> {
					if (!StringUtils.isEmpty(projectAndCommit.getProject()) && !teamscaleServer.project
							.equals(projectAndCommit.getProject())) {
						logger.warn(
								"Teamscale project '" + teamscaleServer.project + "' specified in the agent configuration is not the same as the Teamscale project '" + projectAndCommit.getProject() + "' specified in git.properties file(s). Proceeding to upload to the" +
										" Teamscale project '" + teamscaleServer.project + "' specified in the agent configuration.");
					}
					if (projectAndCommit.getCommitInfo().preferCommitDescriptorOverRevision ||
							StringUtils.isEmpty(projectAndCommit.getCommitInfo().revision)) {
						teamscaleServer.commit = projectAndCommit.getCommitInfo().commit;
					} else {
						teamscaleServer.revision = projectAndCommit.getCommitInfo().revision;
					}
					return new TeamscaleUploader(teamscaleServer);
				}, outputDirectory);
	}

	private void startMultiGitPropertiesFileSearchInJarFile(DelayedTeamscaleMultiProjectUploader uploader,
			File gitPropertiesJar) {
		GitMultiProjectPropertiesLocator locator = new GitMultiProjectPropertiesLocator(uploader,
				this.searchGitPropertiesRecursively, this.gitPropertiesCommitTimeFormat);
		locator.searchFileForGitPropertiesAsync(gitPropertiesJar, true);
	}

	private void registerMultiGitPropertiesLocator(DelayedTeamscaleMultiProjectUploader uploader,
			Instrumentation instrumentation) {
		GitMultiProjectPropertiesLocator locator = new GitMultiProjectPropertiesLocator(uploader,
				this.searchGitPropertiesRecursively, this.gitPropertiesCommitTimeFormat);
		instrumentation.addTransformer(new GitPropertiesLocatingTransformer(locator, getLocationIncludeFilter()));
	}

	private IUploader createDelayedArtifactoryUploader(Instrumentation instrumentation) {
		DelayedUploader<CommitInfo> uploader = new DelayedUploader<>(
				commitInfo -> {
					artifactoryConfig.commitInfo = commitInfo;
					return new ArtifactoryUploader(artifactoryConfig, additionalMetaDataFiles,
							getReportFormat());
				}, outputDirectory);
		GitSingleProjectPropertiesLocator<CommitInfo> locator = new GitSingleProjectPropertiesLocator<>(
				uploader,
				GitPropertiesLocatorUtils::getCommitInfoFromGitProperties,
				this.searchGitPropertiesRecursively, this.gitPropertiesCommitTimeFormat);
		instrumentation.addTransformer(new GitPropertiesLocatingTransformer(locator, getLocationIncludeFilter()));
		return uploader;
	}

	private IUploader createNwdiTeamscaleUploader(Instrumentation instrumentation) {
		DelayedSapNwdiMultiUploader uploader = new DelayedSapNwdiMultiUploader(
				(commit, application) -> new TeamscaleUploader(
						teamscaleServer.withProjectAndCommit(application.getTeamscaleProject(), commit)));
		instrumentation.addTransformer(new NwdiMarkerClassLocatingTransformer(uploader, getLocationIncludeFilter(),
				sapNetWeaverJavaApplications));
		return uploader;
	}

	private EReportFormat getReportFormat() {
		if (useTestwiseCoverageMode()) {
			return EReportFormat.TESTWISE_COVERAGE;
		}
		return EReportFormat.JACOCO;
	}

	/**
	 * @see #classDirectoriesOrZips
	 */
	public List<File> getClassDirectoriesOrZips() {
		return classDirectoriesOrZips;
	}

	/** @see #teamscaleServer */
	public TeamscaleServer getTeamscaleServerOptions() {
		return teamscaleServer;
	}

	/**
	 * Get the directory to which the coverage files are written to
	 */
	public Path getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * Creates a new file with the given prefix, extension and current timestamp and ensures that the parent folder
	 * actually exists.
	 */
	public File createNewFileInOutputDirectory(String prefix, String extension) throws IOException {
		FileSystemUtils.ensureDirectoryExists(outputDirectory.toFile());
		return outputDirectory.resolve(prefix + "-" + LocalDateTime.now().format(DATE_TIME_FORMATTER) + "." + extension)
				.toFile();
	}

	/**
	 * Creates a new file with the given prefix, extension and current timestamp and ensures that the parent folder
	 * actually exists. One output folder is created per partition.
	 */
	public File createNewFileInPartitionOutputDirectory(String prefix, String extension) throws IOException {
		Path partitionOutputDir = outputDirectory.resolve(safeFolderName(getTeamscaleServerOptions().partition));
		FileSystemUtils.ensureDirectoryExists(partitionOutputDir.toFile());
		return partitionOutputDir.resolve(
				prefix + "-" + LocalDateTime.now().format(DATE_TIME_FORMATTER) + "." + extension).toFile();
	}

	private static Path safeFolderName(String folderName) {
		String result = folderName.replaceAll("[<>:\"/|?*]", "")
				.replaceAll("\\.+", "dot")
				.replaceAll("\\x00", "")
				.replaceAll("[. ]$", "");

		if (result.isEmpty()) {
			return Paths.get("default");
		} else {
			return Paths.get(result);
		}
	}

	/**
	 * Sets the parent of the output directory for this run. The output directory itself will be created in this folder
	 * is named after the current timestamp with the format yyyy-MM-dd-HH-mm-ss.SSS
	 */
	public void setParentOutputDirectory(Path outputDirectoryParent) {
		outputDirectory = outputDirectoryParent.resolve(LocalDateTime.now().format(DATE_TIME_FORMATTER));
	}

	/**
	 * @see #dumpIntervalInMinutes
	 */
	public int getDumpIntervalInMinutes() {
		return dumpIntervalInMinutes;
	}

	/**
	 * @see #duplicateClassFileBehavior
	 */
	public EDuplicateClassFileBehavior getDuplicateClassFileBehavior() {
		return duplicateClassFileBehavior;
	}

	/** Returns whether the config indicates to use Test Impact mode. */
	public boolean useTestwiseCoverageMode() {
		return mode == EMode.TESTWISE;
	}

	/**
	 * Returns the port at which the http server should listen for test execution information or null if disabled.
	 */
	public Integer getHttpServerPort() {
		return httpServerPort;
	}

	/**
	 * @see #loggingConfig
	 */
	public Path getLoggingConfig() {
		return loggingConfig;
	}

	/**
	 * @see #validateSsl
	 */
	public boolean shouldValidateSsl() {
		return validateSsl;
	}

	/**
	 * @see #obfuscateSecurityRelatedOutputs
	 */
	public boolean shouldObfuscateSecurityRelatedOutputs() {
		return obfuscateSecurityRelatedOutputs;
	}

	/**
	 * @see #jacocoIncludes
	 * @see #jacocoExcludes
	 */
	public ClasspathWildcardIncludeFilter getLocationIncludeFilter() {
		return new ClasspathWildcardIncludeFilter(jacocoIncludes, jacocoExcludes);
	}

	/** Whether coverage should be dumped in regular intervals. */
	public boolean shouldDumpInIntervals() {
		return dumpIntervalInMinutes > 0;
	}

	/** Whether coverage should be dumped on JVM shutdown. */
	public boolean shouldDumpOnExit() {
		return shouldDumpOnExit;
	}

	public ETestwiseCoverageMode getTestwiseCoverageMode() {
		return testwiseCoverageMode;
	}

	/** @see #ignoreUncoveredClasses */
	public boolean shouldIgnoreUncoveredClasses() {
		return ignoreUncoveredClasses;
	}

	/** @return the {@link TeamscaleProxyOptions} for the given protocol. */
	public TeamscaleProxyOptions getTeamscaleProxyOptions(ProxySystemProperties.Protocol protocol) {
		if (protocol == ProxySystemProperties.Protocol.HTTP) {
			return teamscaleProxyOptionsForHttp;
		}
		return teamscaleProxyOptionsForHttps;
	}
}
