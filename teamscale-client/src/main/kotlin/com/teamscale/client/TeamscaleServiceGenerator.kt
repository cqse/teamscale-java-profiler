package com.teamscale.client

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import java.io.File
import java.io.IOException
import java.time.Duration

/** Helper class for generating a teamscale compatible service.  */
object TeamscaleServiceGenerator {
	/** Builds the User-Agent string for the given tool name and version. */
	@JvmStatic
	fun buildUserAgent(toolName: String, version: String) = "$toolName/$version"

	/**
	 * Generates a [Retrofit] instance for the given service, which uses basic auth to authenticate against the
	 * server and which sets the accept header to json.
	 */
	@JvmStatic
	@JvmOverloads
	fun <S> createService(
		serviceClass: Class<S>,
		baseUrl: HttpUrl,
		username: String,
		accessToken: String,
		userAgent: String,
		readTimeout: Duration = HttpUtils.DEFAULT_READ_TIMEOUT,
		writeTimeout: Duration = HttpUtils.DEFAULT_WRITE_TIMEOUT,
		vararg interceptors: Interceptor
	) = createServiceWithRequestLogging(
		serviceClass, baseUrl, username, accessToken, null, readTimeout, writeTimeout, userAgent, *interceptors
	)

	/**
	 * Generates a [Retrofit] instance for the given service, which uses basic auth to authenticate against the
	 * server and which sets the accept-header to json. Logs requests and responses to the given logfile.
	 */
	fun <S> createServiceWithRequestLogging(
		serviceClass: Class<S>,
		baseUrl: HttpUrl,
		username: String,
		accessToken: String,
		logfile: File?,
		readTimeout: Duration,
		writeTimeout: Duration,
		userAgent: String,
		vararg interceptors: Interceptor
	): S = HttpUtils.createRetrofit(
		{ retrofitBuilder ->
			retrofitBuilder.baseUrl(baseUrl)
				.addConverterFactory(JacksonConverterFactory.create(JsonUtils.OBJECT_MAPPER))
		},
		{ okHttpBuilder ->
			okHttpBuilder.addInterceptors(*interceptors)
				.addInterceptor(HttpUtils.getBasicAuthInterceptor(username, accessToken))
				.addInterceptor(AcceptJsonInterceptor())
				.addNetworkInterceptor(CustomUserAgentInterceptor(userAgent))
			logfile?.let { okHttpBuilder.addInterceptor(FileLoggingInterceptor(it)) }
		},
		readTimeout, writeTimeout
	).create(serviceClass)

	private fun OkHttpClient.Builder.addInterceptors(
		vararg interceptors: Interceptor
	): OkHttpClient.Builder {
		interceptors.forEach { interceptor ->
			addInterceptor(interceptor)
		}
		return this
	}

	/**
	 * Sets an `Accept: application/json` header on all requests.
	 */
	private class AcceptJsonInterceptor : Interceptor {
		@Throws(IOException::class)
		override fun intercept(chain: Interceptor.Chain): Response {
			val newRequest = chain.request().newBuilder().header("Accept", "application/json").build()
			return chain.proceed(newRequest)
		}
	}

	/**
	 * Sets the custom user agent header on all requests.
	 */
	class CustomUserAgentInterceptor(private val userAgent: String) : Interceptor {
		@Throws(IOException::class)
		override fun intercept(chain: Interceptor.Chain): Response {
			val newRequest = chain.request().newBuilder().header("User-Agent", userAgent).build()
			return chain.proceed(newRequest)
		}
	}
}
