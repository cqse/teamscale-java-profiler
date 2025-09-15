package com.teamscale.jacoco.agent.options;

import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.util.AgentUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/** Builder for the JaCoCo agent options string. */
public class JacocoAgentOptionsBuilder {
	private final Logger logger = LoggingUtils.getLogger(this);

	private final AgentOptions agentOptions;

	public JacocoAgentOptionsBuilder(AgentOptions agentOptions) {
		this.agentOptions = agentOptions;
	}

	/**
	 * Returns the options to pass to the JaCoCo agent.
	 */
	public String createJacocoAgentOptions() throws AgentOptionParseException, IOException {
		StringBuilder builder = new StringBuilder(getModeSpecificOptions());
		if (agentOptions.jacocoIncludes != null) {
			builder.append(",includes=").append(agentOptions.jacocoIncludes);
		}
		if (agentOptions.jacocoExcludes != null) {
			logger.debug("Using default excludes: " + AgentOptions.DEFAULT_EXCLUDES);
			builder.append(",excludes=").append(agentOptions.jacocoExcludes);
		}

		// Don't dump class files in testwise mode when coverage is written to an exec file
		boolean needsClassFiles = agentOptions.mode == EMode.NORMAL || agentOptions.testwiseCoverageMode != ETestwiseCoverageMode.EXEC_FILE;
		if (agentOptions.classDirectoriesOrZips.isEmpty() && needsClassFiles) {
			Path tempDir = createTemporaryDumpDirectory();
			tempDir.toFile().deleteOnExit();
			builder.append(",classdumpdir=").append(tempDir.toAbsolutePath());

			agentOptions.classDirectoriesOrZips = Collections.singletonList(tempDir.toFile());
		}

		agentOptions.additionalJacocoOptions
				.forEach(pair -> builder.append(",").append(pair.getFirst()).append("=").append(pair.getSecond()));

		return builder.toString();
	}

	private Path createTemporaryDumpDirectory() throws AgentOptionParseException {
		try {
			return Files.createDirectory(AgentUtils.getMainTempDirectory().resolve("jacoco-class-dump"));
		} catch (IOException e) {
			logger.warn("Unable to create temporary directory in default location. Trying in system temp directory.");
		}

		try {
			return Files.createTempDirectory("jacoco-class-dump");
		} catch (IOException e) {
			logger.warn("Unable to create temporary directory in default location. Trying in output directory.");
		}

		try {
			return Files.createTempDirectory(agentOptions.getOutputDirectory(), "jacoco-class-dump");
		} catch (IOException e) {
			logger.warn("Unable to create temporary directory in output directory. Trying in agent's directory.");
		}

		Path agentDirectory = AgentUtils.getAgentDirectory();
		if (agentDirectory == null) {
			throw new AgentOptionParseException("Could not resolve directory that contains the agent");
		}
		try {
			return Files.createTempDirectory(agentDirectory, "jacoco-class-dump");
		} catch (IOException e) {
			throw new AgentOptionParseException("Unable to create a temporary directory anywhere", e);
		}
	}

	/**
	 * Returns additional options for JaCoCo depending on the selected {@link AgentOptions#mode} and
	 * {@link AgentOptions#testwiseCoverageMode}.
	 */
	String getModeSpecificOptions() throws IOException {
		if (agentOptions
				.useTestwiseCoverageMode() && agentOptions.testwiseCoverageMode == ETestwiseCoverageMode.EXEC_FILE) {
			// when writing to a .exec file, we can instruct JaCoCo to do so directly
			return "destfile=" + agentOptions.createNewFileInOutputDirectory("jacoco", "exec").getAbsolutePath();
		} else {
			// otherwise we don't need JaCoCo to perform any output of the .exec information
			return "output=none";
		}
	}

}
