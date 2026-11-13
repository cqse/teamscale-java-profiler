package com.teamscale.client

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

internal class ProxySystemPropertiesTest {
	private val properties = ProxySystemProperties(ProxySystemProperties.Protocol.HTTP)
	private val httpsProperties = ProxySystemProperties(ProxySystemProperties.Protocol.HTTPS)
	private val teamscaleProperties = TeamscaleProxySystemProperties(ProxySystemProperties.Protocol.HTTPS)

	@AfterEach
	fun teardown() {
		properties.clear()
		httpsProperties.clear()
		teamscaleProperties.clear()
	}

	@Test
	fun testPortParsing() {
		properties.proxyPort = 9876
		assertThat(properties.proxyPort).isEqualTo(9876)
		assertThatThrownBy {
			properties.proxyPort = 0
		}.hasMessage("Port must be a positive integer")
		assertThatThrownBy {
			properties.proxyPort = 65536
		}.hasMessage("Port must be less than or equal to 65535")
		properties.clear()
		assertThat(properties.proxyPort).isEqualTo(-1)
	}

	@Test
	fun testReadsJvmProxySystemProperties() {
		System.setProperty("https.proxyHost", "proxy.mycompany.com")
		System.setProperty("https.proxyPort", "3128")

		assertThat(httpsProperties.proxyHost).isEqualTo("proxy.mycompany.com")
		assertThat(httpsProperties.proxyPort).isEqualTo(3128)
	}

	@Test
	fun testWritesTeamscaleProxySystemProperties() {
		teamscaleProperties.proxyHost = "proxy.mycompany.com"
		teamscaleProperties.proxyUser = "user"

		assertThat(System.getProperty("teamscale.https.proxyHost")).isEqualTo("proxy.mycompany.com")
		assertThat(System.getProperty("teamscale.https.proxyUser")).isEqualTo("user")
	}

	@Test
	fun testNonProxyHosts() {
		assertThat(teamscaleProperties.nonProxyHosts).isNull()

		// The JVM applies http.nonProxyHosts to HTTPS, too
		System.setProperty("http.nonProxyHosts", "*.mycompany.com")
		assertThat(teamscaleProperties.nonProxyHosts).isEqualTo("*.mycompany.com")

		teamscaleProperties.nonProxyHosts = "teamscale.mycompany.com"
		assertThat(System.getProperty("teamscale.https.nonProxyHosts")).isEqualTo("teamscale.mycompany.com")
		assertThat(teamscaleProperties.nonProxyHosts).isEqualTo("teamscale.mycompany.com")
	}
}
