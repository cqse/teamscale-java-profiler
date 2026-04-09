package com.teamscale.jacoco.agent.upload.artifactory

import com.teamscale.client.CommitDescriptor.Companion.parse
import com.teamscale.client.EReportFormat
import com.teamscale.client.FileSystemUtils.normalizeSeparators
import com.teamscale.client.FileSystemUtils.replaceFilePathFilenameWith
import com.teamscale.client.HttpUtils.getBasicAuthInterceptor
import com.teamscale.client.StringUtils.emptyToNull
import com.teamscale.client.StringUtils.nullToEmpty
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.upload.HttpZipUploaderBase
import com.teamscale.jacoco.agent.upload.IUploadRetry
import com.teamscale.jacoco.agent.upload.teamscale.ETeamscaleServerProperties
import com.teamscale.jacoco.agent.upload.teamscale.TeamscaleUploader
import com.teamscale.report.jacoco.CoverageFile
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.Throws

/**
 * Uploads XMLs to Artifactory.
 */
class ArtifactoryUploader(
	private val artifactoryConfig: ArtifactoryConfig,
	additionalMetaDataFiles: MutableList<Path>,
	reportFormat: EReportFormat
) : HttpZipUploaderBase<IArtifactoryUploadApi>(
	artifactoryConfig.url!!,
	additionalMetaDataFiles,
	IArtifactoryUploadApi::class.java
), IUploadRetry {
	private val coverageFormat = reportFormat.name.lowercase(Locale.getDefault())
	private var uploadPath: String? = null

	override fun markFileForUploadRetry(coverageFile: CoverageFile) {
		val uploadMetadataFile = File(
			replaceFilePathFilenameWith(
				normalizeSeparators(coverageFile.toString()),
				"${coverageFile.name}${TeamscaleUploader.RETRY_UPLOAD_FILE_SUFFIX}"
			)
		)
		val properties = createArtifactoryProperties()
		try {
			FileWriter(uploadMetadataFile).use { writer ->
				properties.store(writer, null)
			}
		} catch (_: IOException) {
			logger.warn(
				"Failed to create metadata file for automatic upload retry of {}. Please manually retry the coverage upload to Azure.",
				coverageFile
			)
			uploadMetadataFile.delete()
		}
	}

	override fun reupload(coverageFile: CoverageFile, properties: Properties) {
		val config = ArtifactoryConfig()
		config.url = artifactoryConfig.url
		config.user = artifactoryConfig.user
		config.password = artifactoryConfig.password
		config.legacyPath = artifactoryConfig.legacyPath
		config.zipPath = artifactoryConfig.zipPath
		config.pathSuffix = artifactoryConfig.pathSuffix
		val revision = properties.getProperty(ETeamscaleServerProperties.REVISION.name)
		val commitString = properties.getProperty(ETeamscaleServerProperties.COMMIT.name)
		config.commitInfo = CommitInfo(revision, parse(commitString))
		config.apiKey = artifactoryConfig.apiKey
		config.partition = emptyToNull(properties.getProperty(ETeamscaleServerProperties.PARTITION.name))
		setUploadPath(coverageFile, config)
		super.upload(coverageFile)
	}

	/** Creates properties from the artifactory configs.  */
	private fun createArtifactoryProperties() = Properties().apply {
		setProperty(ETeamscaleServerProperties.REVISION.name, artifactoryConfig.commitInfo!!.revision)
		setProperty(ETeamscaleServerProperties.COMMIT.name, artifactoryConfig.commitInfo!!.commit.toString())
		setProperty(ETeamscaleServerProperties.PARTITION.name, nullToEmpty(artifactoryConfig.partition))
	}

	override fun configureOkHttp(builder: OkHttpClient.Builder) {
		super.configureOkHttp(builder)
		if (artifactoryConfig.apiKey != null) {
			builder.addInterceptor(this.artifactoryApiHeaderInterceptor)
		} else {
			builder.addInterceptor(
				getBasicAuthInterceptor(artifactoryConfig.user!!, artifactoryConfig.password!!)
			)
		}
	}

	private fun setUploadPath(coverageFile: CoverageFile, artifactoryConfig: ArtifactoryConfig) {
		val commit = artifactoryConfig.commitInfo?.commit ?: return
		val revision = artifactoryConfig.commitInfo?.revision ?: return
		val timeRev = "${commit.timestamp}-${revision}"
		val fileName = "${coverageFile.nameWithoutExtension}.zip"

		uploadPath = if (artifactoryConfig.legacyPath) {
			"${commit.branchName}/$timeRev/$fileName"
		} else {
			val suffixSegment = artifactoryConfig.pathSuffix?.let { "$it/" } ?: ""
			"uploads/${commit.branchName}/$timeRev/${artifactoryConfig.partition}/$coverageFormat/$suffixSegment$fileName"
		}
	}

	override fun upload(coverageFile: CoverageFile) {
		setUploadPath(coverageFile, this.artifactoryConfig)
		super.upload(coverageFile)
	}

	@Throws(IOException::class)
	override fun uploadCoverageZip(coverageFile: File): Response<ResponseBody> =
		api.uploadCoverageZip(uploadPath!!, coverageFile)

	override fun getZipEntryCoverageFileName(coverageFile: CoverageFile): String {
		var path = coverageFile.name
		artifactoryConfig.zipPath?.let { path = "$it/$path" }
		return path
	}

	/** {@inheritDoc}  */
	override fun describe() = "Uploading to $uploadUrl"

	private val artifactoryApiHeaderInterceptor: Interceptor
		get() = Interceptor { chain ->
			val newRequest = chain.request().newBuilder()
				.header(ARTIFACTORY_API_HEADER, artifactoryConfig.apiKey!!)
				.build()
			chain.proceed(newRequest)
		}

	companion object {
		/**
		 * Header that can be used as alternative to basic authentication to authenticate requests against artifactory. For
		 * details check https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API
		 */
		const val ARTIFACTORY_API_HEADER: String = "X-JFrog-Art-Api"
	}
}
