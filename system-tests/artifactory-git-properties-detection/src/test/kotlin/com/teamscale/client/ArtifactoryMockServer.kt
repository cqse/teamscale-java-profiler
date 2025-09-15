package com.teamscale.client

import spark.Request
import spark.Response
import spark.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.servlet.http.HttpServletResponse

/**
 * Mocks a Artifactory server: stores all uploaded reports so tests can run assertions on them.
 */
class ArtifactoryMockServer(port: Int) {
	/** All reports uploaded to this Teamscale instance.  */
	val uploadedReports = mutableListOf<Pair<String, String>>()
	private val service = Service.ignite()

	init {
		service.port(port)
		service.put(":path", ::handleReport)
		service.exception<Exception>(Exception::class.java) { exception, _, response ->
			response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
			response.body("Exception: ${exception.message}")
		}
		service.notFound { request, response ->
			response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
			"Unexpected request: ${request.requestMethod()} ${request.uri()}"
		}
		service.awaitInitialization()
	}

	@Throws(IOException::class)
	private fun handleReport(request: Request, response: Response): String {
		processZipEntries(ByteArrayInputStream(request.bodyAsBytes())) { entry, content ->
			if (!entry.isDirectory) {
				uploadedReports.add(Pair("${request.params("path")} -> ${entry.getName()}", content.toString()))
			}
		}
		return "success"
	}

	/**
	 * Shuts down the mock server and waits for it to be stopped.
	 */
	fun shutdown() {
		service.stop()
		service.awaitStop()
	}

	companion object {
		@Throws(IOException::class)
		private fun processZipEntries(
			zipStream: InputStream,
			entryConsumer: (ZipEntry, ByteArrayOutputStream) -> Unit
		) {
			val bufferSize = 1024
			ZipInputStream(zipStream).use { zipInput ->
				while (true) {
					val zipEntry = zipInput.nextEntry ?: break
					val entryContent = ByteArrayOutputStream()
					val buffer = ByteArray(bufferSize)
					var bytesRead: Int
					while (zipInput.read(buffer).also { bytesRead = it } != -1) {
						entryContent.write(buffer, 0, bytesRead)
					}
					entryConsumer(zipEntry, entryContent)
				}
			}
		}
	}
}
