package com.teamscale.client

import okhttp3.Authenticator
import okhttp3.Credentials.basic
import okhttp3.Interceptor
import okhttp3.OkHttpClient.Builder
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import java.util.function.Consumer
import javax.net.ssl.*

/**
 * Utility functions to set up [Retrofit] and [okhttp3.OkHttpClient].
 */
object HttpUtils {
	private val LOGGER = LoggerFactory.getLogger(HttpUtils::class.java)

	/**
	 * Default read timeout in seconds.
	 */
	@JvmField
	val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(60)

	/**
	 * Default write timeout in seconds.
	 */
	@JvmField
	val DEFAULT_WRITE_TIMEOUT: Duration = Duration.ofSeconds(60)

	/**
	 * HTTP header used for authenticating against a proxy server
	 */
	const val PROXY_AUTHORIZATION_HTTP_HEADER = "Proxy-Authorization"

	/** Controls whether [okhttp3.OkHttpClient]s built with this class will validate SSL certificates.  */
	private var shouldValidateSsl = true

	/** @see .shouldValidateSsl
	 */
	@JvmStatic
	fun setShouldValidateSsl(shouldValidateSsl: Boolean) {
		HttpUtils.shouldValidateSsl = shouldValidateSsl
	}

	/**
	 * Creates a new [Retrofit] with proper defaults. The instance and the corresponding [okhttp3.OkHttpClient] can
	 * be customized with the given action. Timeouts for reading and writing can be customized.
	 */
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
		val setHttpsProxyWasSuccessful = setUpProxyServerForProtocol(
			ProxySystemProperties.Protocol.HTTPS,
			this
		)
		if (!setHttpsProxyWasSuccessful) {
			setUpProxyServerForProtocol(ProxySystemProperties.Protocol.HTTP, this)
		}
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
			LOGGER.warn(e.message)
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
	@JvmStatic
	fun <T> getErrorBodyStringSafe(response: retrofit2.Response<T>): String {
		val errorBody = response.errorBody() ?: return "<no response body provided>"
		return errorBody.string()
	}

	/**
	 * Returns an interceptor, which adds a basic auth header to a request.
	 */
	@JvmStatic
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
