package com.teamscale.jacoco.agent.util;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.TeamscaleServiceGenerator;
import com.teamscale.jacoco.agent.PreMain;
import com.teamscale.jacoco.agent.configuration.ProcessInformationRetriever;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

/** General utilities for working with the agent. */
public class AgentUtils {

	/** Version of this program. */
	public static final String VERSION;

	/** User-Agent header value for HTTP requests. */
	public static final String USER_AGENT;

	private static Path mainTempDirectory = null;

	static {
		ResourceBundle bundle = ResourceBundle.getBundle("com.teamscale.jacoco.agent.app");
		VERSION = bundle.getString("version");
		USER_AGENT = TeamscaleServiceGenerator.buildUserAgent("Teamscale Java Profiler", VERSION);
	}

	/**
	 * Returns the main temporary directory where all agent temp files should be placed.
	 */
	public static Path getMainTempDirectory() {
		if (mainTempDirectory == null) {
			try {
				// We add a trailing hyphen here to visually separate the PID from the random number that Java appends
				// to the name to make it unique
				mainTempDirectory = Files.createTempDirectory("teamscale-java-profiler-" +
						FileSystemUtils.toSafeFilename(ProcessInformationRetriever.getPID()) + "-");
			} catch (IOException e) {
				throw new RuntimeException("Failed to create temporary directory for agent files", e);
			}
		}
		return mainTempDirectory;
	}

	/** Returns the directory that contains the agent installation. */
	public static Path getAgentDirectory() {
		try {
			URI jarFileUri = PreMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			// we assume that the dist zip is extracted and the agent jar not moved
			Path jarDirectory = Paths.get(jarFileUri).getParent();
			Path installDirectory = jarDirectory.getParent();
			if (installDirectory == null) {
				// happens when the jar file is stored in the root directory
				return jarDirectory;
			}
			return installDirectory;
		} catch (URISyntaxException e) {
			throw new RuntimeException("Failed to obtain agent directory. This is a bug, please report it.", e);
		}
	}

}
