package com.teamscale.jacoco.agent.upload.azure

import com.teamscale.client.EReportFormat
import com.teamscale.client.FileSystemUtils.normalizeSeparators
import com.teamscale.client.FileSystemUtils.replaceFilePathFilenameWith
import com.teamscale.jacoco.agent.upload.HttpZipUploaderBase
import com.teamscale.jacoco.agent.upload.IUploadRetry
import com.teamscale.jacoco.agent.upload.UploaderException
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageHttpUtils.EHttpMethod
import com.teamscale.jacoco.agent.upload.azure.AzureFileStorageHttpUtils.baseHeaders
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.AUTHORIZATION
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_LENGTH
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.CONTENT_TYPE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_CONTENT_LENGTH
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_RANGE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_TYPE
import com.teamscale.jacoco.agent.upload.azure.AzureHttpHeader.X_MS_WRITE
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader
import com.teamscale.report.jacoco.CoverageFile
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.create
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern
import kotlin.Throws
import kotlin.text.format
import kotlin.text.lowercase

/** Uploads the coverage archive to a provided azure file storage.  */
class AzureFileStorageUploader(
	config: AzureFileStorageConfig,
	additionalMetaDataFiles: List<Path>
) : HttpZipUploaderBase<IAzureUploadApi>(
	config.url!!,
	additionalMetaDataFiles,
	IAzureUploadApi::class.java
), IUploadRetry {
	/** The access key for the azure file storage  */
	private var accessKey = config.accessKey

	/** The account for the azure file storage  */
	private var account = getAccount()

	/** Constructor.  */
	init {
		validateUploadUrl()
	}

	override fun markFileForUploadRetry(coverageFile: CoverageFile) {
		val uploadMetadataFile = File(
			replaceFilePathFilenameWith(
				normalizeSeparators(coverageFile.toString()),
				coverageFile.name + TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX
			)
		)
		try {
			uploadMetadataFile.createNewFile()
		} catch (_: IOException) {
			logger.warn(
				"Failed to create metadata file for automatic upload retry of {}. Please manually retry the coverage upload to Azure.",
				coverageFile
			)
			uploadMetadataFile.delete()
		}
	}

	override fun reupload(coverageFile: CoverageFile, properties: Properties) {
		// The azure uploader does not have any special reupload properties, so it will
		// just use the normal upload instead.
		upload(coverageFile)
	}

	/**
	 * Extracts and returns the account of the provided azure file storage from the URL.
	 */
	@Throws(UploaderException::class)
	private fun getAccount(): String {
		val matcher = AZURE_FILE_STORAGE_HOST_PATTERN.matcher(uploadUrl.host)
		if (matcher.matches()) {
			return matcher.group(1)
		} else {
			throw UploaderException(
				"URL is malformed. Must be in the format \"https://<account>.file.core.windows.net/<share>/\", but was instead: $uploadUrl"
			)
		}
	}

	override fun describe() = "Uploading coverage to the Azure File Storage at $uploadUrl"

	@Throws(IOException::class, UploaderException::class)
	override fun uploadCoverageZip(coverageFile: File): Response<ResponseBody> {
		val fileName = createFileName()
		if (checkFile(fileName).isSuccessful) {
			logger.warn("The file $fileName does already exists at $uploadUrl")
		}

		return createAndFillFile(coverageFile, fileName)
	}

	/**
	 * Makes sure that the upload url is valid and that it exists on the file storage. If some directories do not
	 * exists, they will be created.
	 */
	@Throws(UploaderException::class)
	private fun validateUploadUrl() {
		val pathParts = uploadUrl.pathSegments

		if (pathParts.size < 2) {
			throw UploaderException(
				"${uploadUrl.toUrl().path} is too short for a file path on the storage. At least the share must be provided: https://<account>.file.core.windows.net/<share>/"
			)
		}

		try {
			checkAndCreatePath(pathParts)
		} catch (e: IOException) {
			throw UploaderException(
				"Checking the validity of ${uploadUrl.toUrl().path} failed. There is probably something wrong with the URL or a problem with the account/key: ", e
			)
		}
	}

	/**
	 * Checks the directory path in the azure url. Creates any missing directories.
	 */
	@Throws(IOException::class, UploaderException::class)
	private fun checkAndCreatePath(pathParts: List<String>) {
		(2..<pathParts.size).forEach { i ->
			val directoryPath = "/${pathParts.subList(0, i).joinToString("/")}/"
			if (!checkDirectory(directoryPath).isSuccessful) {
				val mkdirResponse = createDirectory(directoryPath)
				if (!mkdirResponse.isSuccessful) {
					throw UploaderException("Creation of path '/$directoryPath' was unsuccessful", mkdirResponse)
				}
			}
		}
	}

	/** Creates a file name for the zip-archive containing the coverage.  */
	private fun createFileName() = "${EReportFormat.JACOCO.name.lowercase(Locale.getDefault())}-${System.currentTimeMillis()}.zip"

	/** Checks if the file with the given name exists  */
	@Throws(IOException::class, UploaderException::class)
	private fun checkFile(fileName: String): Response<Void> {
		val filePath = "${uploadUrl.toUrl().path}$fileName"

		val headers = baseHeaders.toMutableMap()
		val queryParameters = mutableMapOf<String, String>()

		val auth = AzureFileStorageHttpUtils.getAuthorizationString(
			EHttpMethod.HEAD, account, accessKey, filePath, headers, queryParameters
		)

		headers[AUTHORIZATION] = auth
		return api.head(filePath, headers, queryParameters).execute()
	}

	/** Checks if the directory given by the specified path does exist.  */
	@Throws(IOException::class, UploaderException::class)
	private fun checkDirectory(directoryPath: String): Response<Void> {
		val headers = baseHeaders.toMutableMap()

		val queryParameters = mutableMapOf<String, String>()
		queryParameters["restype"] = "directory"

		val auth = AzureFileStorageHttpUtils.getAuthorizationString(
			EHttpMethod.HEAD, account, accessKey, directoryPath, headers, queryParameters
		)

		headers[AUTHORIZATION] = auth
		return api.head(directoryPath, headers, queryParameters).execute()
	}

	/**
	 * Creates the directory specified by the given path. The path must contain the share where it should be created
	 * on.
	 */
	@Throws(IOException::class, UploaderException::class)
	private fun createDirectory(directoryPath: String): Response<ResponseBody> {
		val headers = baseHeaders.toMutableMap()

		val queryParameters = mutableMapOf<String, String>()
		queryParameters["restype"] = "directory"

		val auth = AzureFileStorageHttpUtils.getAuthorizationString(
			EHttpMethod.PUT, account, accessKey, directoryPath, headers, queryParameters
		)

		headers[AUTHORIZATION] = auth
		return api.put(directoryPath, headers, queryParameters).execute()
	}

	/** Creates and fills a file with the given data and name.  */
	@Throws(UploaderException::class, IOException::class)
	private fun createAndFillFile(zipFile: File, fileName: String): Response<ResponseBody> {
		val response = createFile(zipFile, fileName)
		if (response.isSuccessful) {
			return fillFile(zipFile, fileName)
		}
		logger.error("Creation of file '$fileName' was unsuccessful.")
		return response
	}

	/**
	 * Creates an empty file with the given name. The size is defined by the length of the given byte array.
	 */
	@Throws(IOException::class, UploaderException::class)
	private fun createFile(zipFile: File, fileName: String): Response<ResponseBody> {
		val filePath = "${uploadUrl.toUrl().path}$fileName"

		val headers = baseHeaders.toMutableMap()
		headers[X_MS_CONTENT_LENGTH] = zipFile.length().toString()
		headers[X_MS_TYPE] = "file"

		val queryParameters = mutableMapOf<String, String>()

		val auth = AzureFileStorageHttpUtils.getAuthorizationString(
			EHttpMethod.PUT, account, accessKey, filePath, headers, queryParameters
		)

		headers[AUTHORIZATION] = auth
		return api.put(filePath, headers, queryParameters).execute()
	}

	/**
	 * Fills the file defined by the name with the given data. Should be used with [.createFile],
	 * because the request only writes exactly the length of the given data, so the file should be exactly as big as the
	 * data, otherwise it will be partially filled or is not big enough.
	 */
	@Throws(IOException::class, UploaderException::class)
	private fun fillFile(zipFile: File, fileName: String): Response<ResponseBody> {
		val filePath = uploadUrl.toUrl().path + fileName

		val range = "bytes=0-${zipFile.length() - 1}"
		val contentType = "application/octet-stream"

		val headers = baseHeaders.toMutableMap()
		headers[X_MS_WRITE] = "update"
		headers[X_MS_RANGE] = range
		headers[CONTENT_LENGTH] = zipFile.length().toString()
		headers[CONTENT_TYPE] = contentType

		val queryParameters = mutableMapOf<String, String>()
		queryParameters["comp"] = "range"

		val auth = AzureFileStorageHttpUtils.getAuthorizationString(
			EHttpMethod.PUT, account, accessKey, filePath, headers, queryParameters
		)
		headers[AUTHORIZATION] = auth
		val content = zipFile.asRequestBody(contentType.toMediaTypeOrNull())
		return api.putData(filePath, headers, queryParameters, content).execute()
	}

	companion object {
		/** Pattern matches the host of a azure file storage  */
		private val AZURE_FILE_STORAGE_HOST_PATTERN: Pattern = Pattern
			.compile("^(\\w*)\\.file\\.core\\.windows\\.net$")
	}
}
