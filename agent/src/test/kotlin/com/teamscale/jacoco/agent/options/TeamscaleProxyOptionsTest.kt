package com.teamscale.jacoco.agent.options

import com.teamscale.client.ProxySystemProperties
import com.teamscale.report.util.CommandLineLogger
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class TeamscaleProxyOptionsTest {
	@Test
	fun testTeamscaleProxyOptionsFilledWithJVMOptionsOnInit() {
		val proxySystemProperties = ProxySystemProperties(ProxySystemProperties.Protocol.HTTP)
		val expectedHost = "testHost"
		proxySystemProperties.proxyHost = expectedHost
		val expectedPort = 1234
		proxySystemProperties.proxyPort = expectedPort
		val expectedUser = "testUser"
		proxySystemProperties.proxyUser = expectedUser
		val expectedPassword = "testPassword"
		proxySystemProperties.proxyPassword = expectedPassword

		val teamscaleProxyOptions = TeamscaleProxyOptions(ProxySystemProperties.Protocol.HTTP, CommandLineLogger())
		Assertions.assertThat(teamscaleProxyOptions.proxyHost).isEqualTo(expectedHost)
		Assertions.assertThat(teamscaleProxyOptions.proxyPort).isEqualTo(expectedPort)
		Assertions.assertThat(teamscaleProxyOptions.proxyUser).isEqualTo(expectedUser)
		Assertions.assertThat(teamscaleProxyOptions.proxyPassword).isEqualTo(expectedPassword)

		proxySystemProperties.clear()
	}
}