package com.teamscale.client

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

internal class ProxySystemPropertiesTest {
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
	fun testUsesStandardJvmProxyPropertyKeys() {
		properties.proxyHost = "myHost"
		properties.proxyPort = 1234

		assertThat(System.getProperty("http.proxyHost")).isEqualTo("myHost")
		assertThat(System.getProperty("http.proxyPort")).isEqualTo("1234")
		assertThat(System.getProperty("http..proxyHost")).isNull()
		properties.clear()

		// The Teamscale-specific variant keeps its "teamscale." prefix so it does not collide with the JVM properties.
		val teamscaleProperties = TeamscaleProxySystemProperties(ProxySystemProperties.Protocol.HTTP)
		teamscaleProperties.proxyHost = "teamscaleHost"
		assertThat(System.getProperty("teamscale.http.proxyHost")).isEqualTo("teamscaleHost")
		teamscaleProperties.clear()
	}

	companion object {
		private val properties = ProxySystemProperties(ProxySystemProperties.Protocol.HTTP)

		@JvmStatic
		@AfterAll
		fun teardown() {
			properties.clear()
		}
	}
}