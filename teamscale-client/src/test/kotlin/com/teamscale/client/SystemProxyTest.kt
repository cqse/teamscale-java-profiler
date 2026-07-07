package com.teamscale.client

import com.github.markusbernhardt.proxy.selector.pac.PacProxySelector
import com.github.markusbernhardt.proxy.selector.pac.UrlPacScriptSource
import com.teamscale.client.TeamscaleServiceGenerator.buildUserAgent
import com.teamscale.client.TeamscaleServiceGenerator.createService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Tests that the profiler resolves the operating system's proxy configuration, including Proxy-Auto-Config (PAC) files.
 *
 * These tests install a direct (no-proxy) [ProxySelector] as the JVM default so that a request is only ever routed
 * through [proxyServer] if the profiler installs the detected system proxy selector on its OkHttp client.
 */
internal class SystemProxyTest {

	/** The proxy that the system configuration / PAC file points at and that requests should be routed through. */
	private lateinit var proxyServer: MockWebServer

	private val originalDefaultSelector: ProxySelector = ProxySelector.getDefault()

	private val originalSystemProxySelectorSupplier = HttpUtils.systemProxySelectorSupplier

	/** A [ProxySelector] that never returns a proxy, i.e., every connection stays direct. */
	private val directSelector = object : ProxySelector() {
		override fun select(uri: URI?) = listOf(Proxy.NO_PROXY)
		override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
			// Nothing to do
		}
	}

	@BeforeEach
	fun setUp() {
		proxyServer = MockWebServer()
		proxyServer.start()
		// Make the JVM default a direct connection, so requests only reach proxyServer if the profiler installs the
		// system proxy selector itself.
		ProxySelector.setDefault(directSelector)
	}

	@AfterEach
	fun tearDown() {
		ProxySelector.setDefault(originalDefaultSelector)
		HttpUtils.systemProxySelectorSupplier = originalSystemProxySelectorSupplier
		System.clearProperty("java.net.useSystemProxies")
		System.clearProperty("http.proxyHost")
		System.clearProperty("http.proxyPort")
		proxyServer.shutdown()
		proxyServer.close()
	}

	/**
	 * When `-Djava.net.useSystemProxies=true` is set and no explicit `teamscale.*` proxy is configured, the request is
	 * routed through the OS-configured proxy (here provided via the standard `http.proxyHost` JVM properties, which
	 * proxy-vole reads). The direct default selector guarantees the proxy is only hit because the profiler installed the
	 * detected selector.
	 */
	@Test
	fun `system proxy is used when useSystemProxies is enabled`() {
		System.setProperty("java.net.useSystemProxies", "true")
		System.setProperty("http.proxyHost", proxyServer.hostName)
		System.setProperty("http.proxyPort", proxyServer.port.toString())

		makeRequestThroughProfiler()

		assertThat(proxyServer.requestCount).isEqualTo(1)
	}

	/**
	 * Verifies that a PAC file is evaluated and that the resulting proxy is used by our OkHttp client. The PAC-aware
	 * selector is what the system detection resolves to, and the profiler installs it on its client; OkHttp then
	 * evaluates the PAC script and routes through the returned proxy.
	 */
	@Test
	fun `proxy from a PAC file is evaluated and used`() {
		val pacServer = MockWebServer()
		val pacScript =
			"""function FindProxyForURL(url, host) { return "PROXY ${proxyServer.hostName}:${proxyServer.port}"; }"""
		pacServer.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest) =
				MockResponse()
					.setHeader("Content-Type", "application/x-ns-proxy-autoconfig")
					.setBody(pacScript)
		}
		pacServer.start()

		try {
			System.setProperty("java.net.useSystemProxies", "true")
			// Simulate the OS proxy detection resolving to a PAC-based selector.
			HttpUtils.systemProxySelectorSupplier = {
				PacProxySelector(UrlPacScriptSource(pacServer.url("/proxy.pac").toString()))
			}

			makeRequestThroughProfiler()

			assertThat(proxyServer.requestCount).isEqualTo(1)
		} finally {
			pacServer.shutdown()
			pacServer.close()
		}
	}

	/**
	 * Without the opt-in flag, the system proxy is never consulted and the connection stays direct, even when the OS
	 * would report a proxy.
	 */
	@Test
	fun `no proxy is used without opt-in`() {
		HttpUtils.systemProxySelectorSupplier = { directSelectorReturning(proxyServer) }

		makeRequestThroughProfiler()

		assertThat(proxyServer.requestCount).isZero()
	}

	/** A [ProxySelector] that routes every request through the given server. */
	private fun directSelectorReturning(server: MockWebServer) = object : ProxySelector() {
		override fun select(uri: URI?) =
			listOf(Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress(server.hostName, server.port)))

		override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
			// Nothing to do
		}
	}

	/** Sends one request to a (non-existent) Teamscale server through a client built the same way the profiler builds it. */
	private fun makeRequestThroughProfiler() {
		val service = createService<ITeamscaleService>(
			"http://teamscale.example.com".toHttpUrl(),
			"someUser", "someAccessToken",
			userAgent = buildUserAgent("Test Tool", "1.0.0")
		)
		proxyServer.enqueue(MockResponse().setResponseCode(200))
		runCatching {
			service.sendHeartbeat("", ProfilerInfo(ProcessInformation("", "", 0), null)).execute()
		}
	}
}
