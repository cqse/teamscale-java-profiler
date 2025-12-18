package com.teamscale.maven.tia;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.StringUtils;
import com.teamscale.maven.TeamscaleMojoBase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * Base class for TIA Mojos. Provides all necessary functionality but can be subclassed to change the partition.
 * <p>
 * For this plugin to work, you must either
 *
 * <ul>
 *     <li>Make Surefire and Failsafe use our JUnit 5 test engine</li>
 *     <li>Send test start and end events to the Java agent themselves</li>
 * </ul>
 * <p>
 * To use our JUnit 5 impacted-test-engine, you must declare it as a test dependency. Example:
 *
 * <pre>{@code
 * <dependencies>
 * <dependency>
 * <groupId>com.teamscale</groupId>
 * <artifactId>impacted-test-engine</artifactId>
 * <version>30.0.0</version>
 * <scope>test</scope>
 * </dependency>
 * </dependencies>
 * }</pre>
 * <p>
 * To send test events yourself, you can use our TIA client library (Maven coordinates: com.teamscale:tia-client).
 * <p>
 * The log file of the agent is written to {@code ${project.build.directory}/tia/agent.log}.
 */
public abstract class TiaMojoBase extends TeamscaleMojoBase {

	/**
	 * Name of the surefire/failsafe option to pass in
	 * <a href="https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#includeJUnit5Engines">included
	 * engines</a>
	 */
	private static final String INCLUDE_JUNIT5_ENGINES_OPTION = "includeJUnit5Engines";

	/**
	 * Name of the surefire/failsafe option to pass in
	 * <a href="https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#excludejunit5engines">excluded
	 * engines</a>
	 */
	private static final String EXCLUDE_JUNIT5_ENGINES_OPTION = "excludeJUnit5Engines";

	/**
	 * Impacted tests are calculated from "baselineCommit" to "commit". This sets the baseline.
	 */
	@Parameter
	public String baselineCommit;

	/**
	 * Impacted tests are calculated from "baselineCommit" to "commit". The baselineRevision sets the baselineCommit
	 * with the help of a VCS revision (e.g. git SHA1) instead of a branch and timestamp
	 */
	@Parameter
	public String baselineRevision;

	/**
	 * You can optionally specify which code should be included in the coverage instrumentation. Each pattern is applied
	 * to the fully qualified class names of the profiled system. Use {@code *} to match any number characters and
	 * {@code ?} to match any single character.
	 * <p>
	 * Classes that match any of the include patterns are included, unless any exclude pattern excludes them.
	 */
	@Parameter
	public String[] includes;

	/**
	 * You can optionally specify which code should be excluded from the coverage instrumentation. Each pattern is
	 * applied to the fully qualified class names of the profiled system. Use {@code *} to match any number characters
	 * and {@code ?} to match any single character.
	 * <p>
	 * Classes that match any of the exclude patterns are excluded, even if they are included by an include pattern.
	 */
	@Parameter
	public String[] excludes;

	/**
	 * In order to instrument the system under test, a Java agent must be attached to the JVM of the system. The JVM
	 * command line arguments to achieve this are by default written to the property {@code argLine}, which is
	 * automatically picked up by Surefire and Failsafe and applied to the JVMs these plugins start. You can override
	 * the name of this property if you wish to manually apply the command line arguments yourself, e.g. if your system
	 * under test is started by some other plugin like the Spring boot starter.
	 */
	@Parameter
	public String propertyName;

	/**
	 * Port on which the Java agent listens for commands from this plugin. The default value 0 will tell the agent to
	 * automatically search for an open port.
	 */
	@Parameter(defaultValue = "0")
	public String agentPort;

	/**
	 * Optional additional arguments to send to the agent. Each argument must be of the form {@code KEY=VALUE}.
	 */
	@Parameter
	public String[] additionalAgentOptions;


	/**
	 * Changes the log level of the agent and impacted test engine to DEBUG.
	 */
	@Parameter(defaultValue = "false", property = "debugLogging")
	public boolean debugLogging;

	/**
	 * Executes all tests, not only impacted ones if set. Defaults to false.
	 */
	@Parameter(defaultValue = "false", property = "runAllTests")
	public boolean runAllTests;

	/**
	 * Executes only impacted tests, not all ones if set. Defaults to true.
	 */
	@Parameter(defaultValue = "true", property = "runImpacted")
	public boolean runImpacted;

	/**
	 * Map of resolved Maven artifacts. Provided automatically by Maven.
	 */
	@Parameter(property = "plugin.artifactMap", required = true, readonly = true)
	public Map<String, Artifact> pluginArtifactMap;

	/**
	 * The project build directory (usually: {@code ./target}). Provided automatically by Maven.
	 */
	@Parameter(defaultValue = "${project.build.directory}", readonly = true)
	public String projectBuildDir;

	private Path targetDirectory;

	@Override
	public void execute() throws MojoFailureException, MojoExecutionException {
		super.execute();

		if (!StringUtils.isEmpty(baselineCommit) && !StringUtils.isEmpty(baselineRevision)) {
			getLog().warn("Both baselineRevision and baselineCommit are set but only one of them is needed. " +
					"The revision will be preferred in this case. If that's not intended, please do not set the baselineRevision manually.");
		}

		if (skip) {
			return;
		}

		Plugin testPlugin = getTestPlugin(getTestPluginArtifact());
		if (testPlugin != null) {
			configureTestPlugin();
			for (PluginExecution execution : testPlugin.getExecutions()) {
				validateTestPluginConfiguration(execution);
			}
		}

		targetDirectory = Paths.get(projectBuildDir, getOutputDirectoryName()).toAbsolutePath();
		createTargetDirectory();

		resolveCommitOrRevision();

		setTiaProperties();

		Path agentConfigFile = createAgentConfigFiles(agentPort);
		Path logFilePath = targetDirectory.resolve("agent.log");
		setArgLine(agentConfigFile, logFilePath);
	}

	/** The name of the directory within the build dir into which the execution data should be written. */
	protected abstract String getOutputDirectoryName();

	private void setTiaProperties() {
		setTiaProperty("enabled", "true");
		setTiaProperty("reportDirectory", targetDirectory.toString());
		setTiaProperty("server.url", teamscaleUrl);
		setTiaProperty("server.project", projectId);
		setTiaProperty("server.userName", username);
		setTiaProperty("server.userAccessToken", accessToken);

		if (!StringUtils.isEmpty(resolvedRevision)) {
			setTiaProperty("endRevision", resolvedRevision);
		} else {
			setTiaProperty("endCommit", resolvedCommit);
		}

		if (!StringUtils.isEmpty(baselineRevision)) {
			setTiaProperty("baselineRevision", baselineRevision);
		} else {
			setTiaProperty("baseline", baselineCommit);
		}

		setTiaProperty("repository", repository);
		setTiaProperty("partition", getPartition());
		if (agentPort.equals("0")) {
			agentPort = findAvailablePort();
		}

		setTiaProperty("agentsUrls", "http://localhost:" + agentPort);
		setTiaProperty("runImpacted", Boolean.valueOf(runImpacted).toString());
		setTiaProperty("runAllTests", Boolean.valueOf(runAllTests).toString());

		if (debugLogging) {
			setTiaProperty("logLevel", "DEBUG");
			Path engineLogFilePath = targetDirectory.resolve("engine.log");
			setTiaProperty("logFilePath", engineLogFilePath.toString());
		}
	}

	/**
	 * Automatically find an available port.
	 */
	private String findAvailablePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			int port = socket.getLocalPort();
			getLog().info("Automatically set server port to " + port);
			return String.valueOf(port);
		} catch (IOException e) {
			getLog().error("Port blocked, trying again.", e);
			return findAvailablePort();
		}
	}

	/**
	 * Sets the teamscale-test-impacted engine as only includedEngine and passes all previous engine configuration to
	 * the impacted test engine instead.
	 */
	private void configureTestPlugin() {
		enforcePropertyValue(INCLUDE_JUNIT5_ENGINES_OPTION, "includedEngines", "teamscale-test-impacted");
		enforcePropertyValue(EXCLUDE_JUNIT5_ENGINES_OPTION, "excludedEngines", "");
	}

	private void enforcePropertyValue(String engineOption, String impactedEngineSuffix,
			String newValue) {
		overrideProperty(engineOption, impactedEngineSuffix, newValue, session.getCurrentProject().getProperties());
		overrideProperty(engineOption, impactedEngineSuffix, newValue, session.getUserProperties());
	}

	private void overrideProperty(String engineOption, String impactedEngineSuffix, String newValue,
			Properties properties) {
		Object originalValue = properties.put(getPropertyName(engineOption), newValue);
		if (originalValue instanceof String && !StringUtils.isEmpty((String) originalValue) && !newValue.equals(
				originalValue)) {
			setTiaProperty(impactedEngineSuffix, (String) originalValue);
		}
	}

	private void validateTestPluginConfiguration(PluginExecution execution) throws MojoFailureException {
		Xpp3Dom configurationDom = (Xpp3Dom) execution.getConfiguration();
		if (configurationDom == null) {
			return;
		}

		validateEngineNotConfigured(configurationDom, INCLUDE_JUNIT5_ENGINES_OPTION);
		validateEngineNotConfigured(configurationDom, EXCLUDE_JUNIT5_ENGINES_OPTION);

		validateParallelizationParameter(configurationDom, "threadCount");
		validateParallelizationParameter(configurationDom, "forkCount");

		Xpp3Dom parameterDom = configurationDom.getChild("reuseForks");
		if (parameterDom == null) {
			return;
		}
		String value = parameterDom.getValue();
		if (value != null && !value.equals("true")) {
			getLog().warn(
					"You configured surefire to not reuse forks." +
							" This has been shown to lead to performance decreases in combination with the Teamscale Maven Plugin." +
							" If you notice performance problems, please have a look at our troubleshooting section for possible solutions: https://docs.teamscale.com/howto/providing-testwise-coverage/#troubleshooting.");
		}
	}

	private void validateEngineNotConfigured(Xpp3Dom configurationDom,
			String xmlConfigurationName) throws MojoFailureException {
		Xpp3Dom engines = configurationDom.getChild(xmlConfigurationName);
		if (engines != null) {
			throw new MojoFailureException(
					"You configured JUnit 5 engines in the " + getTestPluginArtifact() + " plugin via the " + xmlConfigurationName + " configuration parameter." +
							" This is currently not supported when performing Test Impact analysis." +
							" Please add the " + xmlConfigurationName + " via the " + getPropertyName(
							xmlConfigurationName) + " property.");
		}
	}

	@NotNull
	private String getPropertyName(String xmlConfigurationName) {
		return getTestPluginPropertyPrefix() + "." + xmlConfigurationName;
	}

	@Nullable
	private Plugin getTestPlugin(String testPluginArtifact) {
		Map<String, Plugin> plugins = session.getCurrentProject().getModel().getBuild().getPluginsAsMap();
		return plugins.get(testPluginArtifact);
	}

	private void validateParallelizationParameter(Xpp3Dom configurationDom,
			String parallelizationParameter) throws MojoFailureException {
		Xpp3Dom parameterDom = configurationDom.getChild(parallelizationParameter);
		if (parameterDom == null) {
			return;
		}

		String value = parameterDom.getValue();
		if (value != null && !value.equals("1")) {
			throw new MojoFailureException(
					"You configured parallel tests in the " + getTestPluginArtifact() + " plugin via the " + parallelizationParameter + " configuration parameter." +
							" Parallel tests are not supported when performing Test Impact analysis as they prevent recording testwise coverage." +
							" Please disable parallel tests when running Test Impact analysis.");
		}
	}

	/**
	 * @return the partition to upload testwise coverage to.
	 */
	protected abstract String getPartition();

	/**
	 * @return the artifact name of the test plugin (e.g. Surefire, Failsafe).
	 */
	protected abstract String getTestPluginArtifact();

	/** @return The prefix of the properties that are used to pass parameters to the plugin. */
	protected abstract String getTestPluginPropertyPrefix();

	/**
	 * @return whether this Mojo applies to integration tests.
	 * <p>
	 * Depending on this, different properties are used to set the argLine.
	 */
	protected abstract boolean isIntegrationTest();

	private void createTargetDirectory() throws MojoFailureException {
		try {
			if (targetDirectory.toFile().exists()) {
				FileSystemUtils.deleteRecursively(targetDirectory.toFile());
			}
			Files.createDirectories(targetDirectory);
		} catch (IOException e) {
			throw new MojoFailureException("Could not create target directory " + targetDirectory, e);
		}
	}

	private void setArgLine(Path agentConfigFile, Path logFilePath) {
		String agentLogLevel = "INFO";
		if (debugLogging) {
			agentLogLevel = "DEBUG";
		}

		ArgLine.cleanOldArgLines(session, getLog());
		ArgLine.applyToMavenProject(
				new ArgLine(additionalAgentOptions, agentLogLevel, findAgentJarFile(), agentConfigFile, logFilePath),
				session, getLog(), propertyName, isIntegrationTest());
	}

	private Path createAgentConfigFiles(String agentPort) throws MojoFailureException {
		Path loggingConfigPath = targetDirectory.resolve("logback.xml");
		try (OutputStream loggingConfigOutputStream = Files.newOutputStream(loggingConfigPath)) {
			FileSystemUtils.copy(readAgentLogbackConfig(), loggingConfigOutputStream);
		} catch (IOException e) {
			throw new MojoFailureException("Writing the logging configuration file for the TIA agent failed." +
					" Make sure the path " + loggingConfigPath + " is writeable.", e);
		}

		Path configFilePath = targetDirectory.resolve("agent-at-port-" + agentPort + ".properties");
		String agentConfig = createAgentConfig(loggingConfigPath, targetDirectory);
		try {
			Files.write(configFilePath, Collections.singleton(agentConfig));
		} catch (IOException e) {
			throw new MojoFailureException("Writing the configuration file for the TIA agent failed." +
					" Make sure the path " + configFilePath + " is writeable.", e);
		}

		getLog().info("Agent config file created at " + configFilePath);
		return configFilePath;
	}

	private InputStream readAgentLogbackConfig() {
		return TiaMojoBase.class.getResourceAsStream("logback-agent.xml");
	}

	private String createAgentConfig(Path loggingConfigPath, Path agentOutputDirectory) {
		String config = "mode=testwise" +
				"\ntia-mode=exec-file" +
				"\nhttp-server-port=" + agentPort +
				"\nlogging-config=" + loggingConfigPath +
				"\nout=" + agentOutputDirectory.toAbsolutePath();
		if (includes != null && includes.length > 0) {
			config += "\nincludes=" + String.join(";", includes);
		}
		if (excludes != null && excludes.length > 0) {
			config += "\nexcludes=" + String.join(";", excludes);
		}
		return config;
	}

	private Path findAgentJarFile() {
		Artifact agentArtifact = pluginArtifactMap.get("com.teamscale:teamscale-jacoco-agent");
		return agentArtifact.getFile().toPath();
	}

	/**
	 * Sets a property in the TIA namespace. It seems that, depending on Maven version and which other plugins are used,
	 * different types of properties are respected both during the build and during tests (as e.g. failsafe tests are
	 * often run in a separate JVM spawned by Maven). So we set our properties in every possible way to make sure the
	 * plugin works out of the box in most situations.
	 */
	private void setTiaProperty(String name, String value) {
		if (value != null) {
			String fullyQualifiedName = "teamscale.test.impacted." + name;
			getLog().debug("Setting property " + name + "=" + value);
			session.getUserProperties().setProperty(fullyQualifiedName, value);
			session.getSystemProperties().setProperty(fullyQualifiedName, value);
			System.setProperty(fullyQualifiedName, value);
		}
	}
}
