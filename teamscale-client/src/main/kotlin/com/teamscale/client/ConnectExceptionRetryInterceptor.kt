package com.teamscale.client

import okhttp3.Interceptor
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ConnectException
import java.time.Duration
import kotlin.math.min


/**
 * An OkHttp Interceptor that retries requests when a ConnectException occurs,
 * continuing retries for up to the given duration before giving up.
 */
class ConnectExceptionRetryInterceptor(private val timeout: Duration) : Interceptor {

	private val LOGGER = LoggerFactory.getLogger(ConnectExceptionRetryInterceptor::class.java)

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val startTime = System.currentTimeMillis()
		val timeoutMillis = timeout.toMillis()
		var attemptCount = 0
		var lastException: ConnectException? = null

		while (System.currentTimeMillis() - startTime < timeoutMillis) {
			try {
				// Attempt to proceed with the request
				return chain.proceed(request)
			} catch (e: ConnectException) {
				lastException = e
				attemptCount++

				// Log the failure and retry information
				val timeSpent = System.currentTimeMillis() - startTime
				val timeLeft = timeoutMillis - timeSpent

				if (timeLeft <= 0) {
					break // No time left to retry
				}

				// Calculate backoff delay with jitter
				val delayMillis = calculateBackoffDelay(attemptCount)

				// Log retry attempt
				LOGGER.debug("Connection failed (attempt $attemptCount): ${e.message}")
				LOGGER.debug("Retrying in ${delayMillis}ms, time left: ${timeLeft}ms")

				// Wait before next retry
				Thread.sleep(min(delayMillis, timeLeft))
			} catch (e: IOException) {
				// Rethrow non-connect exceptions
				throw e
			}
		}

		// If we reach here, we've timed out without a successful response
		throw lastException ?: IOException(
			"Failed to execute request to Teamscale after $timeoutMillis ms of connect retries." +
					" Verify network connectivity and that the Teamscale host is reachable."
		)
	}

	/**
	 * Calculates the backoff delay using exponential backoff with jitter.
	 */
	fun calculateBackoffDelay(attemptCount: Int): Long {
		val baseDelayMs = 500L // Start with 500ms
		val maxDelayMs = 5000L // Max 5 seconds

		// Exponential backoff: 500ms, 1000ms, 2000ms, 4000ms, etc.
		val exponentialDelay = baseDelayMs * (1 shl (attemptCount - 1))

		// Add random jitter (±20%) to prevent thundering herd
		val jitterFactor = 0.8 + Math.random() * 0.4
		val delayWithJitter = (exponentialDelay * jitterFactor).toLong()

		// Cap the delay at maxDelayMs
		return delayWithJitter.coerceAtMost(maxDelayMs)
	}
}
