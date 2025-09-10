package com.teamscale.maven.tia;

import com.teamscale.client.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Composes a new argLine based on the current one and input about the desired agent configuration.
 */
public class ArgLine {

	private final String[] additionalAgentOptions;
	private final String agentLogLevel;
	private final Path agentJarFile;
	private final Path agentConfigFile;
	private final Path logFilePath;

	public ArgLine(String[] additionalAgentOptions, String agentLogLevel, Path agentJarFile,
			Path agentConfigFile, Path logFilePath) {
		this.additionalAgentOptions = additionalAgentOptions;
		this.agentLogLevel = agentLogLevel;
		this.agentJarFile = agentJarFile;
		this.agentConfigFile = agentConfigFile;
		this.logFilePath = logFilePath;
	}

	/** Applies the given {@link ArgLine} to the given {@link MavenSession}. */
	public static void applyToMavenProject(ArgLine argLine, MavenSession session, Log log,
			String userDefinedPropertyName, boolean isIntegrationTest) {
		MavenProject mavenProject = session.getCurrentProject();
		ArgLineProperty effectiveProperty = ArgLine.getEffectiveProperty(userDefinedPropertyName, mavenProject,
				isIntegrationTest);

		String oldArgLine = effectiveProperty.getValue(session);
		String newArgLine = argLine.prependTo(oldArgLine);

		effectiveProperty.setValue(session, newArgLine);
		log.info(effectiveProperty.propertyName + " set to " + newArgLine);
	}

	/**
	 * Removes any occurrences of our agent from all {@link ArgLineProperty#STANDARD_PROPERTIES}.
	 */
	public static void cleanOldArgLines(MavenSession session, Log log) {
		for (ArgLineProperty property : ArgLineProperty.STANDARD_PROPERTIES) {
			String oldArgLine = property.getValue(session);
			if (StringUtils.isEmpty(oldArgLine)) {
				continue;
			}

			String newArgLine = removePreviousTiaAgent(oldArgLine);
			if (!oldArgLine.equals(newArgLine)) {
				log.info("Removed agent from property " + property.propertyName);
				property.setValue(session, newArgLine);
			}
		}
	}

	/**
	 * Takes the given old argLine, removes any previous invocation of our agent and prepends a new one based on the
	 * constructor parameters of this class. Preserves all other options in the old argLine.
	 */
	/*package*/ String prependTo(String oldArgLine) {
		String jvmOptions = createJvmOptions();
		if (StringUtils.isEmpty(oldArgLine)) {
			return jvmOptions;
		}

		return jvmOptions + " " + oldArgLine;
	}

	private String createJvmOptions() {
		List<String> jvmOptions = new ArrayList<>();
		jvmOptions.add("-Dteamscale.markstart");
		jvmOptions.add(createJavaAgentArgument());
		jvmOptions.add("-DTEAMSCALE_AGENT_LOG_FILE=" + logFilePath);
		jvmOptions.add("-DTEAMSCALE_AGENT_LOG_LEVEL=" + agentLogLevel);
		jvmOptions.add("-Dteamscale.markend");
		return jvmOptions.stream().map(ArgLine::quoteCommandLineOptionIfNecessary)
				.collect(Collectors.joining(" "));
	}

	private static String quoteCommandLineOptionIfNecessary(String option) {
		if (option.matches(".*\\s+.*")) {
			return "'" + option + "'";
		} else {
			return option;
		}
	}

	private String createJavaAgentArgument() {
		List<String> agentOptions = new ArrayList<>();
		agentOptions.add("config-file=" + agentConfigFile.toAbsolutePath());
		if (additionalAgentOptions != null) {
			agentOptions.addAll(Arrays.asList(additionalAgentOptions));
		}
		return "-javaagent:" + agentJarFile.toAbsolutePath() + "=" + String.join(",", agentOptions);
	}

	/**
	 * Determines the property in which to set the argLine. By default, this is the property used by the testing
	 * framework of the current project's packaging. The user may override this by providing their own property name.
	 */
	private static ArgLineProperty getEffectiveProperty(String userDefinedPropertyName, MavenProject mavenProject,
			boolean isIntegrationTest) {
		if (!StringUtils.isEmpty(userDefinedPropertyName)) {
			return ArgLineProperty.projectProperty(userDefinedPropertyName);
		}

		if (isIntegrationTest && hasSpringBootPluginEnabled(mavenProject)) {
			return ArgLineProperty.SPRING_BOOT_ARG_LINE;
		}

		if ("eclipse-test-plugin".equals(mavenProject.getPackaging())) {
			return ArgLineProperty.TYCHO_ARG_LINE;
		}
		return ArgLineProperty.SUREFIRE_ARG_LINE;
	}

	private static boolean hasSpringBootPluginEnabled(MavenProject mavenProject) {
		return mavenProject.getBuildPlugins().stream()
				.anyMatch(plugin -> plugin.getArtifactId().equals("spring-boot-maven-plugin"));
	}

	/**
	 * Removes any previous invocation of our agent from the given argLine. This is necessary in case we want to
	 * instrument unit and integration tests but with different arguments.
	 */
	/*package*/
	static String removePreviousTiaAgent(String argLine) {
		if (argLine == null) {
			return "";
		}
		return argLine.replaceAll("-Dteamscale.markstart.*teamscale.markend", "");
	}

}
