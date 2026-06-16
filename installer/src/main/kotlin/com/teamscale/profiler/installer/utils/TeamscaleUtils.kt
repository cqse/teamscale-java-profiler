package com.teamscale.profiler.installer.utils

import com.teamscale.profiler.installer.*
import okhttp3.Credentials.basic
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Utilities for interacting with a Teamscale instance.  */
object TeamscaleUtils {
	private var validateSsl = true

	/** Disables SSL validation for all Teamscale connections via this class.  */
	fun disableSslValidation() {
		validateSsl = false
	}

	/** Ensures that we can connect to Teamscale with the given credentials.  */ // this IntelliJ warning is a false positive due to the okhttp property and method overloads
	@Throws(FatalInstallerError::class)
	fun TeamscaleCredentials.checkTeamscaleConnection() {
		val client = OkHttpUtils.createClient(validateSsl, 30)

		// we use the project list as a test to see if the Teamscale credentials are valid.
		// any other API that requires a logged-in user would be fine as well.
		val url = url?.resolve("api/v8.8/projects")
		checkNotNull(url) { "Cannot resolve API endpoint against URL ${this.url}" }

		val request = Request.Builder()
			.url(url)
			.header("Authorization", basic(username!!, accessKey!!))
			.build()

		try {
			client.newCall(request).execute().use { response ->
				handleErrors(response, this)
			}
		} catch (e: SSLException) {
			throw FatalInstallerError(
				"""Failed to connect via HTTPS to ${this.url}
				Please ensure that your Teamscale instance is reachable under ${this.url} and that it is configured for HTTPS, not HTTP. E.g. open that URL in your browser and verify that you can connect successfully.

				If you want to accept self-signed or broken certificates without an error you can use --insecure.
				""".trimIndent(), e
			)
		} catch (e: UnknownHostException) {
			throw FatalInstallerError(
				"The host ${this.url} could not be resolved. Please ensure you have no typo and that this host is reachable from this server.", e
			)
		} catch (e: ConnectException) {
			throw FatalInstallerError(
				"The host ${this.url} refused a connection. Please ensure that you have no typo and that this endpoint is reachable and not blocked by firewalls.", e
			)
		} catch (e: SocketTimeoutException) {
			throw FatalInstallerError(
				"Request timeout reached. Please ensure that you have no typo and that this endpoint is reachable and not blocked by firewalls.", e
			)
		} catch (e: IOException) {
			throw FatalInstallerError(
				"Teamscale is not reachable from this machine. Please check firewall settings.", e
			)
		}
	}

	@Throws(FatalInstallerError::class)
	private fun handleErrors(response: Response, credentials: TeamscaleCredentials) {
		if (response.isRedirect) {
			var location = response.header("Location")
			if (location == null) {
				location = "<server did not provide a location header>"
			}
			throw FatalInstallerError(
				"You provided an incorrect URL. The server responded with a redirect to '$location'. This may e.g. happen if you used HTTP instead of HTTPS. Please use the correct URL for Teamscale instead."
			)
		}

		if (response.code == 401) {
			val editUserUrl = credentials.url!!.getEditUserUrl(credentials.username)
			throw FatalInstallerError(
				"""
				You provided incorrect credentials. Either the user '${credentials.username}' does not exist in Teamscale or the access key you provided is incorrect. Please check both the username and access key in Teamscale under Admin > Users: $editUserUrl
				Please use the user's access key, not their password.
				""".trimIndent()
			)
		}

		if (!response.isSuccessful) {
			throw FatalInstallerError(
				"Unexpected response from Teamscale at ${credentials.url}," +
						" HTTP ${response.code} ${response.message}: ${response.body.string()}."
			)
		}
	}

	/** Returns a URL to the edit page of the given user.  */
	private fun HttpUrl.getEditUserUrl(username: String?) =
		newBuilder().addPathSegment("admin").addPathSegment("users")
			.addQueryParameter("action", "edit").addQueryParameter("username", username)
			.build().toString()

}
