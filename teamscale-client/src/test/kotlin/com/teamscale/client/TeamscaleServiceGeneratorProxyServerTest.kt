package com.teamscale.client

import com.teamscale.client.HttpUtils.PROXY_AUTHORIZATION_HTTP_HEADER
import com.teamscale.client.TeamscaleServiceGenerator.buildUserAgent
import com.teamscale.client.TeamscaleServiceGenerator.createService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Tests that our Retrofit + OkHttp client is using the Java proxy system properties (`http.proxy*`) if set
 */
internal class TeamscaleServiceGeneratorProxyServerTest {
	private var mockProxyServer: MockWebServer? = null
	private val proxySystemProperties = ProxySystemProperties(
		ProxySystemProperties.Protocol.HTTP
	)
	private val teamscaleProxySystemProperties = TeamscaleProxySystemProperties(
		ProxySystemProperties.Protocol.HTTP
	)

	@BeforeEach
	@Throws(IOException::class)
	fun setUp() {
		mockProxyServer = MockWebServer()
		mockProxyServer?.start()
	}

	@Test
	@Throws(Exception::class)
	fun testTeamscaleProxyAuthentication() {
		val incorrectValue = "incorrect"
		// the teamscale-specific options should take precedence over the global ones
		proxySystemProperties.proxyHost = incorrectValue
		proxySystemProperties.proxyPort = 1
		proxySystemProperties.proxyUser = incorrectValue
		proxySystemProperties.proxyPassword = incorrectValue

		teamscaleProxySystemProperties.proxyHost = mockProxyServer?.hostName
		teamscaleProxySystemProperties.proxyPort = mockProxyServer?.port ?: 1

		val proxyUser = "myProxyUser"
		val proxyPassword = "myProxyPassword"
		val base64EncodedBasicAuth = Base64.getEncoder().encodeToString(
			("$proxyUser:$proxyPassword").toByteArray(StandardCharsets.UTF_8)
		)
		teamscaleProxySystemProperties.proxyUser = proxyUser
		teamscaleProxySystemProperties.proxyPassword = proxyPassword

		assertProxyAuthenticationIsUsed(base64EncodedBasicAuth)
	}

	@Throws(InterruptedException::class, IOException::class)
	private fun assertProxyAuthenticationIsUsed(base64EncodedBasicAuth: String) {
		val service = createService(
			ITeamscaleService::class.java,
			"http://localhost:1337".toHttpUrl(),
			"someUser", "someAccesstoken",
			userAgent = buildUserAgent("Test Tool", "1.0.0")
		)

		// First time Retrofit/OkHttp tires without proxy auth.
		// When we return 407 Proxy Authentication Required, it retries with proxy authentication.
		mockProxyServer?.enqueue(MockResponse().setResponseCode(407))
		mockProxyServer?.enqueue(MockResponse().setResponseCode(200))
		service.sendHeartbeat(
			"",
			ProfilerInfo(ProcessInformation("", "", 0), null)
		).execute()

		Assertions.assertThat(mockProxyServer?.requestCount).isEqualTo(2)

		mockProxyServer?.takeRequest() // First request which doesn't have the proxy authentication set yet
		val requestWithProxyAuth = mockProxyServer?.takeRequest() // Request we are actually interested in

		Assertions.assertThat(requestWithProxyAuth?.getHeader(PROXY_AUTHORIZATION_HTTP_HEADER))
			.isEqualTo("Basic $base64EncodedBasicAuth")
	}

	@AfterEach
	@Throws(IOException::class)
	fun tearDown() {
		proxySystemProperties.clear()
		teamscaleProxySystemProperties.clear()

		mockProxyServer?.shutdown()
		mockProxyServer?.close()
	}
}
