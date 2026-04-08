package com.teamscale.jacoco.agent.upload.teamscale

import com.teamscale.client.*
import com.teamscale.client.CommitDescriptor.Companion.parse
import com.teamscale.client.FileSystemUtils.normalizeSeparators
import com.teamscale.client.FileSystemUtils.replaceFilePathFilenameWith
import com.teamscale.client.StringUtils.emptyToNull
import com.teamscale.client.StringUtils.nullToEmpty
import com.teamscale.jacoco.agent.benchmark
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.upload.IUploadRetry
import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.jacoco.agent.util.AgentUtils
import com.teamscale.jacoco.agent.util.Benchmark
import com.teamscale.report.jacoco.CoverageFile
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*

/** Uploads XML Coverage to a Teamscale instance.  */
class TeamscaleUploader(
	@JvmField val teamscaleServer: TeamscaleServer
) : IUploader, IUploadRetry {
	private val logger = getLogger(this)

	override fun upload(coverageFile: CoverageFile) {
		doUpload(coverageFile, teamscaleServer)
	}

	override fun reupload(coverageFile: CoverageFile, properties: Properties) {
		val server = TeamscaleServer()
		server.project = properties.getProperty(ETeamscaleServerProperties.PROJECT.name)
		server.commit = parse(properties.getProperty(ETeamscaleServerProperties.COMMIT.name))
		server.partition = properties.getProperty(ETeamscaleServerProperties.PARTITION.name)
		server.revision = emptyToNull(properties.getProperty(ETeamscaleServerProperties.REVISION.name))
		server.repository = emptyToNull(properties.getProperty(ETeamscaleServerProperties.REPOSITORY.name))
		server.userAccessToken = teamscaleServer.userAccessToken
		server.userName = teamscaleServer.userName
		server.url = teamscaleServer.url
		server.message = properties.getProperty(ETeamscaleServerProperties.MESSAGE.name)
		doUpload(coverageFile, server)
	}

	private fun doUpload(coverageFile: CoverageFile, teamscaleServer: TeamscaleServer) {
		benchmark("Uploading report to Teamscale") {
			if (tryUploading(coverageFile, teamscaleServer)) {
				deleteCoverageFile(coverageFile)
			} else {
				logger.warn(
					("Failed to upload coverage to Teamscale. "
							+ "Won't delete local file {} so that the upload can automatically be retried upon profiler restart. "
							+ "Upload can also be retried manually."), coverageFile
				)
				markFileForUploadRetry(coverageFile)
			}
		}
	}

	override fun markFileForUploadRetry(coverageFile: CoverageFile) {
		val uploadMetadataFile = File(
			replaceFilePathFilenameWith(
				normalizeSeparators(coverageFile.toString()),
				coverageFile.name + RETRY_UPLOAD_FILE_SUFFIX
			)
		)
		val serverProperties = this.createServerProperties()
		try {
			OutputStreamWriter(
				Files.newOutputStream(uploadMetadataFile.toPath()),
				StandardCharsets.UTF_8
			).use { writer ->
				serverProperties.store(writer, null)
			}
		} catch (_: IOException) {
			logger.warn(
				"Failed to create metadata file for automatic upload retry of {}. Please manually retry the coverage upload to Teamscale.",
				coverageFile
			)
			uploadMetadataFile.delete()
		}
	}

	/**
	 * Creates server properties to be written in a properties file.
	 */
	private fun createServerProperties() = Properties().apply {
		setProperty(ETeamscaleServerProperties.PROJECT.name, teamscaleServer.project)
		setProperty(ETeamscaleServerProperties.PARTITION.name, teamscaleServer.partition)
		if (teamscaleServer.commit != null) {
			setProperty(ETeamscaleServerProperties.COMMIT.name, teamscaleServer.commit.toString())
		}
		setProperty(ETeamscaleServerProperties.REVISION.name, nullToEmpty(teamscaleServer.revision))
		setProperty(
			ETeamscaleServerProperties.REPOSITORY.name,
			nullToEmpty(teamscaleServer.repository)
		)
		setProperty(ETeamscaleServerProperties.MESSAGE.name, teamscaleServer.message)
	}

	private fun deleteCoverageFile(coverageFile: CoverageFile) {
		try {
			coverageFile.delete()
		} catch (e: IOException) {
			logger.warn(
				"The upload to Teamscale was successful, but the deletion of the coverage file {} failed. "
						+ "You can delete it yourself anytime - it is no longer needed.", coverageFile, e
			)
		}
	}

	/** Performs the upload and returns `true` if successful.  */
	private fun tryUploading(coverageFile: CoverageFile, teamscaleServer: TeamscaleServer): Boolean {
		logger.debug("Uploading JaCoCo artifact to {}", teamscaleServer)

		try {
			// Cannot be executed in the constructor as this causes issues in WildFly server
			// (See #100)
			TeamscaleServiceGenerator.createService<ITeamscaleService>(
				teamscaleServer.url!!,
				teamscaleServer.userName!!,
				teamscaleServer.userAccessToken!!,
				AgentUtils.USER_AGENT
			).uploadReport(
				teamscaleServer.project!!,
				teamscaleServer.commit,
				teamscaleServer.revision,
				teamscaleServer.repository, teamscaleServer.partition!!, EReportFormat.JACOCO,
				teamscaleServer.message!!, coverageFile.createFormRequestBody()
			)
			return true
		} catch (e: IOException) {
			logger.error("Failed to upload coverage to {}", teamscaleServer, e)
			return false
		}
	}

	override fun describe(): String {
		return "Uploading to " + teamscaleServer
	}

	companion object {
		/**
		 * The properties file suffix for unsuccessful coverage uploads.
		 */
		const val RETRY_UPLOAD_FILE_SUFFIX: String = "_upload-retry.properties"
	}
}
