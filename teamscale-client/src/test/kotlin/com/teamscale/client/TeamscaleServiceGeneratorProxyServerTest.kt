package com.teamscale.client

import com.teamscale.client.HttpUtils.PROXY_AUTHORIZATION_HTTP_HEADER
import com.teamscale.client.TeamscaleServiceGenerator.buildUserAgent
import com.teamscale.client.TeamscaleServiceGenerator.createService
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.Proxy
import java.net.URI
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
		// The JVM may preset properties, e.g. http.nonProxyHosts from the macOS network settings
		proxySystemProperties.clear()
		teamscaleProxySystemProperties.clear()

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

	@Test
	fun testLocalhostIsNotProxiedByDefault() {
		teamscaleProxySystemProperties.proxyHost = mockProxyServer?.hostName
		teamscaleProxySystemProperties.proxyPort = mockProxyServer?.port ?: 1

		// Answers right away if the request wrongly goes through the proxy, instead of blocking until the read timeout
		mockProxyServer?.enqueue(MockResponse().setResponseCode(502))

		MockWebServer().use { teamscaleServer ->
			teamscaleServer.enqueue(MockResponse().setResponseCode(200))
			sendHeartbeat(teamscaleServer.url("/").newBuilder().host("localhost").build())

			Assertions.assertThat(teamscaleServer.requestCount).isEqualTo(1)
			Assertions.assertThat(mockProxyServer?.requestCount).isEqualTo(0)
		}
	}

	@Test
	fun testNonProxyHostsAreNotProxied() {
		teamscaleProxySystemProperties.proxyHost = mockProxyServer?.hostName
		teamscaleProxySystemProperties.proxyPort = mockProxyServer?.port ?: 1
		proxySystemProperties.nonProxyHosts = "*.mycompany.com"

		val client = HttpUtils.createRetrofit(
			{ baseUrl("http://teamscale.mycompany.com") }, {}
		).callFactory() as OkHttpClient

		Assertions.assertThat(client.proxy).isNull()
		Assertions.assertThat(client.proxySelector.select(URI("https://teamscale.mycompany.com")))
			.containsExactly(Proxy.NO_PROXY)
		Assertions.assertThat(client.proxySelector.select(URI("https://teamscale.example.com")).map { it.type() })
			.containsExactly(Proxy.Type.HTTP)
	}

	@Throws(InterruptedException::class, IOException::class)
	private fun assertProxyAuthenticationIsUsed(base64EncodedBasicAuth: String) {
		// First time Retrofit/OkHttp tires without proxy auth.
		// When we return 407 Proxy Authentication Required, it retries with proxy authentication.
		mockProxyServer?.enqueue(MockResponse().setResponseCode(407))
		mockProxyServer?.enqueue(MockResponse().setResponseCode(200))
		// Not localhost, which is never proxied by default
		sendHeartbeat("http://teamscale.example.com:1337".toHttpUrl())

		Assertions.assertThat(mockProxyServer?.requestCount).isEqualTo(2)

		mockProxyServer?.takeRequest() // First request which doesn't have the proxy authentication set yet
		val requestWithProxyAuth = mockProxyServer?.takeRequest() // Request we are actually interested in

		Assertions.assertThat(requestWithProxyAuth?.getHeader(PROXY_AUTHORIZATION_HTTP_HEADER))
			.isEqualTo("Basic $base64EncodedBasicAuth")
	}

	private fun sendHeartbeat(teamscaleUrl: HttpUrl) {
		val service = createService<ITeamscaleService>(
			teamscaleUrl,
			"someUser", "someAccesstoken",
			userAgent = buildUserAgent("Test Tool", "1.0.0")
		)
		service.sendHeartbeat(
			"",
			ProfilerInfo(ProcessInformation("", "", 0), null)
		).execute()
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
