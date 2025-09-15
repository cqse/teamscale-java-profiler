package com.teamscale.jacoco.agent.options;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.ProxySystemProperties;
import com.teamscale.client.StringUtils;
import com.teamscale.client.TeamscaleProxySystemProperties;
import com.teamscale.report.util.ILogger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Parses agent command line options related to the proxy settings.
 */
public class TeamscaleProxyOptions {

	private final ILogger logger;

	/** The host of the proxy server. */
	/* package */ String proxyHost;

	/** The port of the proxy server. */
	/* package */ int proxyPort;

	/** The password for the proxy user. */
	/* package */ String proxyPassword;

	public void setProxyPasswordPath(Path proxyPasswordPath) {
		this.proxyPasswordPath = proxyPasswordPath;
	}

	/** A path to the file that contains the password for the proxy authentication. */
	/* package */ Path proxyPasswordPath;

	/** The username of the proxy user. */
	/* package */ String proxyUser;

	private final ProxySystemProperties.Protocol protocol;

	/** Constructor. */
	public TeamscaleProxyOptions(ProxySystemProperties.Protocol protocol, ILogger logger) {
		this.protocol = protocol;
		this.logger = logger;
		ProxySystemProperties proxySystemProperties = new ProxySystemProperties(protocol);
		proxyHost = proxySystemProperties.getProxyHost();
		try {
			proxyPort = proxySystemProperties.getProxyPort();
		} catch (ProxySystemProperties.IncorrectPortFormatException e) {
			proxyPort = -1;
			logger.warn(e.getMessage());
		}
		proxyUser = proxySystemProperties.getProxyUser();
		proxyPassword = proxySystemProperties.getProxyPassword();
	}

	/**
	 * Processes the command-line options for proxies.
	 *
	 * @return true if it has successfully processed the given option.
	 */
	public boolean handleTeamscaleProxyOptions(String key, String value) throws AgentOptionParseException {
		if ("host".equals(key)) {
			proxyHost = value;
			return true;
		}
		String proxyPortOption = "port";
		if (proxyPortOption.equals(key)) {
			try {
				proxyPort = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				throw new AgentOptionParseException("Could not parse proxy port \"" + value +
						"\" set via \"" + proxyPortOption + "\"", e);
			}
			return true;
		}
		if ("user".equals(key)) {
			proxyUser = value;
			return true;
		} else if ("password".equals(key)) {
			proxyPassword = value;
			return true;
		}
		return false;
	}

	/** Stores the teamscale-specific proxy settings as system properties to make them always available. */
	public void putTeamscaleProxyOptionsIntoSystemProperties() {
		TeamscaleProxySystemProperties teamscaleProxySystemProperties = new TeamscaleProxySystemProperties(protocol);
		if (!StringUtils.isEmpty(proxyHost)) {
			teamscaleProxySystemProperties.setProxyHost(proxyHost);
		}
		if (proxyPort > 0) {
			teamscaleProxySystemProperties.setProxyPort(proxyPort);
		}
		if (!StringUtils.isEmpty(proxyUser)) {
			teamscaleProxySystemProperties.setProxyUser(proxyUser);
		}
		if (!StringUtils.isEmpty(proxyPassword)) {
			teamscaleProxySystemProperties.setProxyPassword(proxyPassword);
		}

		setProxyPasswordFromFile(proxyPasswordPath);
	}

	/**
	 * Sets the proxy password JVM property from a file for the protocol in this instance of
	 * {@link TeamscaleProxyOptions}.
	 */
	private void setProxyPasswordFromFile(Path proxyPasswordFilePath) {
		if (proxyPasswordFilePath == null) {
			return;
		}
		try {
			String proxyPassword = FileSystemUtils.readFileUTF8(proxyPasswordFilePath.toFile()).trim();
			new TeamscaleProxySystemProperties(protocol).setProxyPassword(proxyPassword);
		} catch (IOException e) {
			logger.error(
					"Unable to open file containing proxy password. Please make sure the file exists and the user has the permissions to read the file.",
					e);
		}
	}
}
