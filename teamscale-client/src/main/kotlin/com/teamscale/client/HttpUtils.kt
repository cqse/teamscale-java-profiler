package com.teamscale.client

import com.github.markusbernhardt.proxy.ProxySearch
import okhttp3.Authenticator
import okhttp3.Credentials.basic
import okhttp3.Interceptor
import okhttp3.OkHttpClient.Builder
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import javax.net.ssl.*

/**
 * Utility functions to set up [Retrofit] and [okhttp3.OkHttpClient].
 */
object HttpUtils {
	private val LOGGER = LoggerFactory.getLogger(HttpUtils::class.java)

	/**
	 * Default read timeout in seconds.
	 */
	val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(60)

	/**
	 * Default write timeout in seconds.
	 */
	val DEFAULT_WRITE_TIMEOUT: Duration = Duration.ofSeconds(60)

	/**
	 * HTTP header used for authenticating against a proxy server
	 */
	const val PROXY_AUTHORIZATION_HTTP_HEADER = "Proxy-Authorization"

	/** Controls whether [okhttp3.OkHttpClient]s built with this class will validate SSL certificates.  */
	private var shouldValidateSsl = true

	fun setShouldValidateSsl(shouldValidateSsl: Boolean) {
		HttpUtils.shouldValidateSsl = shouldValidateSsl
	}

	/**
	 * Creates a new [Retrofit] with proper defaults. The instance and the corresponding [okhttp3.OkHttpClient] can
	 * be customized with the given action. Read and write timeouts are set according to the default values.
	 */
	@JvmOverloads
	@JvmStatic
	fun createRetrofit(
		retrofitBuilderAction: Retrofit.Builder.() -> Unit,
		okHttpBuilderAction: Builder.() -> Unit,
		readTimeout: Duration = DEFAULT_READ_TIMEOUT,
		writeTimeout: Duration = DEFAULT_WRITE_TIMEOUT
	): Retrofit {
		val httpClientBuilder = Builder().apply {
			setTimeouts(readTimeout, writeTimeout)
			setUpSslValidation()
			setUpProxyServer()
		}
		okHttpBuilderAction(httpClientBuilder)

		val builder = Retrofit.Builder().client(httpClientBuilder.build())
		retrofitBuilderAction(builder)
		return builder.build()
	}

	/**
	 * Java and/or OkHttp do not pick up the http.proxy* and https.proxy* system properties reliably. We need to teach
	 * OkHttp to always pick them up.
	 *
	 *
	 * Sources: [https://memorynotfound.com/configure-http-proxy-settings-java/](https://memorynotfound.com/configure-http-proxy-settings-java/)
	 * &
	 * [https://stackoverflow.com/a/35567936](https://stackoverflow.com/a/35567936)
	 */
	private fun Builder.setUpProxyServer() {
		val explicitProxyConfigured = setUpProxyServerForProtocol(ProxySystemProperties.Protocol.HTTPS, this) ||
				setUpProxyServerForProtocol(ProxySystemProperties.Protocol.HTTP, this)

		// No explicit proxy configured: auto-detect the proxy from the operating system (including PAC files).
		if (!explicitProxyConfigured) {
			setUpSystemProxySelector()
		}
	}

	/**
	 * Supplies the operating system's [ProxySelector] including Proxy-Auto-Config (PAC) support.
	 * Overridable in tests to inject a deterministic selector.
	 */
	internal var systemProxySelectorSupplier: () -> ProxySelector? = {
		ProxySearch.getDefaultProxySearch().proxySelector
	}
		set(value) = synchronized(this) {
			field = value
			cachedSystemProxySelector = null
		}

	/**
	 * Caches a *successful* system [ProxySelector] detection (wrapped in an [Optional] to also cache the
	 * "no proxy configured" result). The OS proxy configuration does not change during the lifetime of the JVM, so the
	 * potentially expensive detection (PAC download, WPAD lookup, native OS calls) only needs to run once. `null` means
	 * "not yet detected". A detection *failure* is deliberately not cached (see [detectSystemProxySelector]).
	 */
	private var cachedSystemProxySelector: Optional<ProxySelector>? = null

	/**
	 * Installs a [ProxySelector] that reads the operating system's proxy configuration including Proxy-Auto-Config (PAC)
	 * files. This is only enabled when the user sets `-Djava.net.useSystemProxies=true`.
	 *
	 * Any failure while detecting the system proxy is logged and ignored so that the connection falls back to a direct
	 * connection instead of preventing the profiler from starting.
	 */
	private fun Builder.setUpSystemProxySelector() {
		if (!System.getProperty("java.net.useSystemProxies").toBoolean()) {
			return
		}

		val proxySelector = detectSystemProxySelector()
		if (proxySelector == null) {
			LOGGER.debug("java.net.useSystemProxies is set but no system proxy could be detected.")
			return
		}
		LOGGER.debug("Using the system proxy configuration (including PAC files) to reach Teamscale.")
		proxySelector(proxySelector)
	}

	/**
	 * Detects the system [ProxySelector] once and caches a successful result (see [cachedSystemProxySelector]).
	 * Synchronized so that concurrent client construction runs the potentially expensive detection at most once.
	 */
	@Synchronized
	private fun detectSystemProxySelector(): ProxySelector? {
		cachedSystemProxySelector?.let { return it.orElse(null) }

		val proxySelector = try {
			systemProxySelectorSupplier()
		} catch (e: Exception) {
			LOGGER.warn(
				"Failed to detect the system proxy configuration. Falling back to a direct connection." +
						" Configure the proxy explicitly via the proxy-http-host/proxy-https-host options if needed.",
				e
			)
			return null
		}
		cachedSystemProxySelector = Optional.ofNullable(proxySelector)
		return proxySelector
	}

	private fun setUpProxyServerForProtocol(
		protocol: ProxySystemProperties.Protocol,
		httpClientBuilder: Builder
	): Boolean {
		val proxySystemProperties = TeamscaleProxySystemProperties(protocol)
		try {
			if (!proxySystemProperties.isProxyServerSet()) {
				return false
			}

			val host = proxySystemProperties.proxyHost ?: return false
			useProxyServer(httpClientBuilder, host, proxySystemProperties.proxyPort)
		} catch (e: ProxySystemProperties.IncorrectPortFormatException) {
			LOGGER.warn(
				"Ignoring invalid proxy port from system properties (http.proxyPort/https.proxyPort): {}." +
						" The proxy will not be used until a valid port is configured.",
				e.message
			)
			return false
		}

		if (proxySystemProperties.isProxyAuthSet()) {
			val user = proxySystemProperties.proxyUser ?: return false
			val password = proxySystemProperties.proxyPassword ?: return false
			useProxyAuthenticator(httpClientBuilder, user, password)
		}

		return true
	}

	private fun useProxyServer(httpClientBuilder: Builder, proxyHost: String, proxyPort: Int) {
		httpClientBuilder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
	}

	private fun useProxyAuthenticator(httpClientBuilder: Builder, user: String, password: String) {
		val proxyAuthenticator = Authenticator { _, response ->
			response.request.newBuilder()
				.header(PROXY_AUTHORIZATION_HTTP_HEADER, basic(user, password))
				.build()
		}
		httpClientBuilder.proxyAuthenticator(proxyAuthenticator)
	}

	/**
	 * Sets sensible defaults for the [okhttp3.OkHttpClient].
	 */
	private fun Builder.setTimeouts(readTimeout: Duration, writeTimeout: Duration) {
		// In a K8s environment, it might take quite some time until the K8s networking is fully initialized and a connection can be established.
		// Just setting the connectTimout does only retry once, but not wait for the given duration if the ConnectException is thrown very quickly.
		addInterceptor(ConnectExceptionRetryInterceptor(Duration.ofMinutes(1)))
		readTimeout(readTimeout)
		writeTimeout(writeTimeout)
	}

	/**
	 * Enables or disables SSL certificate validation for the [Retrofit] instance
	 */
	private fun Builder.setUpSslValidation() {
		if (shouldValidateSsl) {
			// this is the default behaviour of OkHttp, so we don't need to do anything
			return
		}

		val sslSocketFactory: SSLSocketFactory
		try {
			val sslContext = SSLContext.getInstance("TLS")
			sslContext.init(null, arrayOf<TrustManager>(TrustAllCertificatesManager), SecureRandom())
			sslSocketFactory = sslContext.socketFactory
		} catch (e: GeneralSecurityException) {
			LOGGER.error("Could not disable SSL certificate validation. Leaving it enabled", e)
			return
		}

		// this causes OkHttp to accept all certificates
		sslSocketFactory(sslSocketFactory, TrustAllCertificatesManager)
		// this causes it to ignore invalid host names in the certificates
		hostnameVerifier { _, _ -> true }
	}

	/**
	 * Returns the error body of the given response or a replacement string in case it is null.
	 */
	@Throws(IOException::class)
	fun <T> getErrorBodyStringSafe(response: retrofit2.Response<T>): String {
		val errorBody = response.errorBody() ?: return "<no response body provided>"
		return errorBody.string()
	}

	/**
	 * Returns an interceptor, which adds a basic auth header to a request.
	 */
	fun getBasicAuthInterceptor(username: String, password: String): Interceptor {
		val credentials = "$username:$password"
		val basic = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())

		return Interceptor { chain ->
			val newRequest = chain.request().newBuilder().header("Authorization", basic).build()
			chain.proceed(newRequest)
		}
	}

	/**
	 * A simple implementation of [X509TrustManager] that simple trusts every certificate.
	 */
	object TrustAllCertificatesManager : X509TrustManager {
		/** Returns `null`.  */
		override fun getAcceptedIssuers() = arrayOf<X509Certificate>()

		/** Does nothing.  */
		override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
			// Nothing to do
		}

		/** Does nothing.  */
		override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
			// Nothing to do
		}
	}
}
