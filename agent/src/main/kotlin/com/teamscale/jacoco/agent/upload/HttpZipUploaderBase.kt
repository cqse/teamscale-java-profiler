package com.teamscale.jacoco.agent.upload

import com.teamscale.client.FileSystemUtils.readFileBinary
import com.teamscale.client.HttpUtils.createRetrofit
import com.teamscale.jacoco.agent.benchmark
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.util.Benchmark
import com.teamscale.report.jacoco.CoverageFile
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.slf4j.Logger
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Base class for uploading the coverage zip to a provided url  */
abstract class HttpZipUploaderBase<T>
/** Constructor.  */(
	/** The URL to upload to.  */
	@JvmField
	protected var uploadUrl: HttpUrl,
	/** Additional files to include in the uploaded zip.  */
	protected val additionalMetaDataFiles: MutableList<Path>,
	/** The API class.  */
	private val apiClass: Class<T>
) : IUploader {
	/** The logger.  */
	@JvmField
	protected val logger: Logger = getLogger(this)

	/** The API which performs the upload  */
	protected val api: T by lazy {
		val retrofit = createRetrofit(
			{ baseUrl(uploadUrl) },
			{ configureOkHttp(this) }
		)
		retrofit.create(apiClass)
	}

	/** Template method to configure the OkHttp Client.  */
	protected open fun configureOkHttp(builder: OkHttpClient.Builder) {
	}

	/** Uploads the coverage zip to the server  */
	@Throws(IOException::class, UploaderException::class)
	protected abstract fun uploadCoverageZip(coverageFile: File): Response<ResponseBody>

	override fun upload(coverageFile: CoverageFile) {
		try {
			benchmark("Uploading report via HTTP") {
				if (tryUpload(coverageFile)) {
					coverageFile.delete()
				} else {
					logger.warn(
						("Failed to upload coverage to Teamscale. "
								+ "Won't delete local file {} so that the upload can automatically be retried upon profiler restart. "
								+ "Upload can also be retried manually."), coverageFile
					)
					(this as? IUploadRetry)?.markFileForUploadRetry(coverageFile)
				}
			}
		} catch (_: IOException) {
			logger.warn("Could not delete file {} after upload", coverageFile)
		}
	}

	/** Performs the upload and returns `true` if successful.  */
	protected fun tryUpload(coverageFile: CoverageFile): Boolean {
		logger.debug("Uploading coverage to {}", uploadUrl)

		val zipFile: File
		try {
			zipFile = createZipFile(coverageFile)
		} catch (e: IOException) {
			logger.error("Failed to compile coverage zip file for upload to {}", uploadUrl, e)
			return false
		}

		try {
			val response = uploadCoverageZip(zipFile)
			if (response.isSuccessful) {
				return true
			}

			var errorBody = "<no server response>"
			if (response.errorBody() != null) {
				errorBody = response.errorBody()!!.string()
			}

			logger.error(
				"Failed to upload coverage to {}. Request failed with error code {}. Error:\n{}", uploadUrl,
				response.code(), errorBody
			)
			return false
		} catch (e: IOException) {
			logger.error("Failed to upload coverage to {}. Probably a network problem", uploadUrl, e)
			return false
		} catch (e: UploaderException) {
			logger.error("Failed to upload coverage to {}. The configuration is probably incorrect", uploadUrl, e)
			return false
		} finally {
			zipFile.delete()
		}
	}

	/**
	 * Creates the zip file in the system temp directory to upload which includes the given coverage XML and all
	 * [.additionalMetaDataFiles]. The file is marked to be deleted on exit.
	 */
	@Throws(IOException::class)
	private fun createZipFile(coverageFile: CoverageFile): File {
		val zipFile = Files.createTempFile(coverageFile.nameWithoutExtension, ".zip").toFile()
		zipFile.deleteOnExit()
		FileOutputStream(zipFile).use { fileOutputStream ->
			ZipOutputStream(fileOutputStream).use { zipOutputStream ->
				fillZipFile(zipOutputStream, coverageFile)
				return zipFile
			}
		}
	}

	/**
	 * Fills the upload zip file with the given coverage XML and all [.additionalMetaDataFiles].
	 */
	@Throws(IOException::class)
	private fun fillZipFile(zipOutputStream: ZipOutputStream, coverageFile: CoverageFile) {
		zipOutputStream.putNextEntry(ZipEntry(getZipEntryCoverageFileName(coverageFile)))
		coverageFile.copyStream(zipOutputStream)

		for (additionalFile in additionalMetaDataFiles) {
			zipOutputStream.putNextEntry(ZipEntry(additionalFile.fileName.toString()))
			zipOutputStream.write(readFileBinary(additionalFile.toFile()))
		}
	}

	protected open fun getZipEntryCoverageFileName(coverageFile: CoverageFile): String {
		return "coverage.xml"
	}
}
