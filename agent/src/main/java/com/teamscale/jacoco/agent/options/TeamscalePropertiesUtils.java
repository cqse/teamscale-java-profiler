package com.teamscale.jacoco.agent.options;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.jacoco.agent.util.AgentUtils;
import okhttp3.HttpUrl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Utilities for working with the teamscale.properties file that contains access credentials for the Teamscale
 * instance.
 */
public class TeamscalePropertiesUtils {

	private static final Path TEAMSCALE_PROPERTIES_PATH = AgentUtils.getAgentDirectory()
			.resolve("teamscale.properties");

	/**
	 * Tries to open {@link #TEAMSCALE_PROPERTIES_PATH} and parse that properties file to obtain
	 * {@link TeamscaleCredentials}.
	 *
	 * @return the parsed credentials or null in case the teamscale.properties file doesn't exist.
	 * @throws AgentOptionParseException in case the teamscale.properties file exists but can't be read or parsed.
	 */
	public static TeamscaleCredentials parseCredentials() throws AgentOptionParseException {
		return parseCredentials(TEAMSCALE_PROPERTIES_PATH);
	}

	/**
	 * Same as {@link #parseCredentials()} but testable since the path is not hardcoded.
	 */
	/*package*/
	static TeamscaleCredentials parseCredentials(
			Path teamscalePropertiesPath) throws AgentOptionParseException {
		if (!Files.exists(teamscalePropertiesPath)) {
			return null;
		}

		try {
			Properties properties = FileSystemUtils.readProperties(teamscalePropertiesPath.toFile());
			return parseProperties(properties);
		} catch (IOException e) {
			throw new AgentOptionParseException("Failed to read " + teamscalePropertiesPath, e);
		}
	}

	private static TeamscaleCredentials parseProperties(Properties properties) throws AgentOptionParseException {
		String urlString = properties.getProperty("url");
		if (urlString == null) {
			throw new AgentOptionParseException("teamscale.properties is missing the url field");
		}

		HttpUrl url;
		try {
			url = HttpUrl.get(urlString);
		} catch (IllegalArgumentException e) {
			throw new AgentOptionParseException("teamscale.properties contained malformed URL " + urlString, e);
		}

		String userName = properties.getProperty("username");
		if (userName == null) {
			throw new AgentOptionParseException("teamscale.properties is missing the username field");
		}

		String accessKey = properties.getProperty("accesskey");
		if (accessKey == null) {
			throw new AgentOptionParseException("teamscale.properties is missing the accesskey field");
		}

		return new TeamscaleCredentials(url, userName, accessKey);
	}

}
