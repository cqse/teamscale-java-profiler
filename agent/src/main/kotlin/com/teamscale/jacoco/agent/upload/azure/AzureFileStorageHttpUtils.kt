package com.teamscale.jacoco.agent.upload.azure

import com.teamscale.jacoco.agent.upload.UploaderException
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_ENCODING
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_LANGUAGE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_LENGTH
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_MD_5
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_TYPE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.DATE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_MATCH
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_MODIFIED_SINCE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_NONE_MATCH
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.IF_UNMODIFIED_SINCE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.RANGE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_DATE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_VERSION
import java.io.UnsupportedEncodingException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Utils class for communicating with an azure file storage.  */ /* package */
internal object AzureFileStorageHttpUtils {
	/** Version of the azure file storage. Must be in every request  */
	private const val VERSION = "2018-03-28"

	/** Formatting pattern for every date in a request  */
	private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("E, dd MMM y HH:mm:ss z").withZone(
		ZoneId.of("GMT")
	)

	/** Creates the string that must be signed as the authorization for the request.  */
	private fun createSignString(
		httpMethod: EHttpMethod, headers: Map<String, String>, account: String?,
		path: String?, queryParameters: Map<String, String>
	): String {
		require(headers.keys.containsAll(listOf(X_MS_DATE, X_MS_VERSION))) {
			"Headers for the azure request cannot be empty! At least 'x-ms-version' and 'x-ms-date' must be set"
		}

		val keys = listOf(
			CONTENT_ENCODING, CONTENT_LANGUAGE, CONTENT_LENGTH, CONTENT_MD_5, CONTENT_TYPE, DATE, IF_MODIFIED_SINCE,
			IF_MATCH, IF_NONE_MATCH, IF_UNMODIFIED_SINCE, RANGE
		).map { headers.getOrDefault(it, "") }

		val xmsHeader = headers.filter { it.key.startsWith("x-ms") }
		return listOf( httpMethod.toString(),
			keys, xmsHeader.createCanonicalizedString(),
			createCanonicalizedResources(account, path, queryParameters)
		).joinToString()
	}

	/** Creates the string for the canonicalized resources.  */
	private fun createCanonicalizedResources(
		account: String?,
		path: String?,
		options: Map<String, String>
	): String {
		var canonicalizedResources = "/$account$path"

		if (options.isNotEmpty()) {
			canonicalizedResources += "\n" + options.createCanonicalizedString()
		}

		return canonicalizedResources
	}

	/** Creates a string with a map where each key-value pair is in a newline separated by a colon.  */
	private fun Map<String, String>.createCanonicalizedString() =
		toSortedMap().map { (key, value) -> "$key:${value}" }.joinToString("\n")

	/** Creates the string which is needed for the authorization of an azure file storage request.  */ /* package */
	@JvmStatic
	@Throws(UploaderException::class)
	fun getAuthorizationString(
		method: EHttpMethod, account: String, key: String?, path: String?,
		headers: Map<String, String>, queryParameters: Map<String, String>
	): String {
		val stringToSign = createSignString(method, headers, account, path, queryParameters)

		try {
			val mac = Mac.getInstance("HmacSHA256")
			mac.init(SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"))
			val authKey = String(Base64.getEncoder().encode(mac.doFinal(stringToSign.toByteArray(charset("UTF-8")))))
			return "SharedKey $account:$authKey"
		} catch (e: NoSuchAlgorithmException) {
			throw UploaderException("Something is really wrong...", e)
		} catch (e: UnsupportedEncodingException) {
			throw UploaderException("Something is really wrong...", e)
		} catch (e: InvalidKeyException) {
			throw UploaderException("The given access key is malformed: $key", e)
		} catch (e: IllegalArgumentException) {
			throw UploaderException("The given access key is malformed: $key", e)
		}
	}

	@JvmStatic
	val baseHeaders: Map<String, String>
		/** Returns the list of headers which must be present at every request  */
		get() = mapOf(
			X_MS_VERSION to VERSION,
			X_MS_DATE to FORMAT.format(LocalDateTime.now())
		)

	/** Simple enum for all available HTTP methods.  */
	enum class EHttpMethod {
		PUT,
		HEAD
	}
}

