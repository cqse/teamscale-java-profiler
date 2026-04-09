package com.teamscale.jacoco.agent.upload.artifactory

import com.teamscale.client.StringUtils.stripSuffix
import com.teamscale.jacoco.agent.commit_resolution.git_properties.CommitInfo
import com.teamscale.jacoco.agent.commit_resolution.git_properties.GitPropertiesLocatorUtils
import com.teamscale.jacoco.agent.commit_resolution.git_properties.InvalidGitPropertiesException
import com.teamscale.jacoco.agent.options.AgentOptionParseException
import com.teamscale.jacoco.agent.options.AgentOptionsParser
import com.teamscale.jacoco.agent.upload.UploaderException
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_API_KEY_OPTION
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_LEGACY_PATH_OPTION
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_PARTITION
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_PASSWORD_OPTION
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_PATH_SUFFIX
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_USER_OPTION
import com.teamscale.jacoco.agent.upload.artifactory.ArtifactoryConfig.Companion.ARTIFACTORY_ZIP_PATH_OPTION
import okhttp3.HttpUrl
import java.io.File
import java.io.IOException
import java.time.format.DateTimeFormatter

/** Config necessary to upload files to an azure file storage.  */
class ArtifactoryConfig {
	/** Related to [ARTIFACTORY_USER_OPTION]  */
	@JvmField
	var url: HttpUrl? = null

	/** Related to [ARTIFACTORY_USER_OPTION]  */
	@JvmField
	var user: String? = null

	/** Related to [ARTIFACTORY_PASSWORD_OPTION]  */
	@JvmField
	var password: String? = null

	/** Related to [ARTIFACTORY_LEGACY_PATH_OPTION]  */
	var legacyPath: Boolean = false

	/** Related to [ARTIFACTORY_ZIP_PATH_OPTION]  */
	var zipPath: String? = null

	/** Related to [ARTIFACTORY_PATH_SUFFIX]  */
	var pathSuffix: String? = null

	/** The information regarding a commit.  */
	@JvmField
	var commitInfo: CommitInfo? = null

	/** Related to [ARTIFACTORY_API_KEY_OPTION]  */
	@JvmField
	var apiKey: String? = null

	/** Related to [ARTIFACTORY_PARTITION]  */
	@JvmField
	var partition: String? = null

	/** Checks if all required options are set to upload to artifactory.  */
	fun hasAllRequiredFieldsSet(): Boolean {
		val requiredAuthOptionsSet = (user != null && password != null) || apiKey != null
		val partitionSet = partition != null || legacyPath
		return url != null && partitionSet && requiredAuthOptionsSet
	}

	/** Checks if all required fields are null.  */
	fun hasAllRequiredFieldsNull() = url == null && user == null && password == null && apiKey == null && partition == null

	/** Checks whether commit and revision are set.  */
	fun hasCommitInfo() = commitInfo != null

	companion object {
		/**
		 * Option to specify the artifactory URL. This shall be the entire path down to the directory to which the coverage
		 * should be uploaded to, not only the base url of artifactory.
		 */
		const val ARTIFACTORY_URL_OPTION: String = "artifactory-url"

		/**
		 * Username that shall be used for basic auth. Alternative to basic auth is to use an API key with the
		 * [ARTIFACTORY_API_KEY_OPTION]
		 */
		const val ARTIFACTORY_USER_OPTION: String = "artifactory-user"

		/**
		 * Password that shall be used for basic auth. Alternative to basic auth is to use an API key with the
		 * [ARTIFACTORY_API_KEY_OPTION]
		 */
		const val ARTIFACTORY_PASSWORD_OPTION: String = "artifactory-password"

		/**
		 * API key that shall be used to authenticate requests to artifactory with the
		 * [ArtifactoryUploader.ARTIFACTORY_API_HEADER]. Alternatively
		 * basic auth with username ([ARTIFACTORY_USER_OPTION]) and password
		 * ([ARTIFACTORY_PASSWORD_OPTION]) can be used.
		 */
		const val ARTIFACTORY_API_KEY_OPTION: String = "artifactory-api-key"

		/**
		 * Option that specifies if the legacy path for uploading files to artifactory should be used instead of the new
		 * standard path.
		 */
		const val ARTIFACTORY_LEGACY_PATH_OPTION: String = "artifactory-legacy-path"

		/**
		 * Option that specifies under which path the coverage file shall lie within the zip file that is created for the
		 * upload.
		 */
		const val ARTIFACTORY_ZIP_PATH_OPTION: String = "artifactory-zip-path"

		/**
		 * Option that specifies intermediate directories which should be appended.
		 */
		const val ARTIFACTORY_PATH_SUFFIX: String = "artifactory-path-suffix"

		/**
		 * Specifies the location of the JAR file which includes the git.properties file.
		 */
		const val ARTIFACTORY_GIT_PROPERTIES_JAR_OPTION: String = "artifactory-git-properties-jar"

		/**
		 * Specifies the date format in which the commit timestamp in the git.properties file is formatted.
		 */
		const val ARTIFACTORY_GIT_PROPERTIES_COMMIT_DATE_FORMAT_OPTION: String =
			"artifactory-git-properties-commit-date-format"

		/**
		 * Specifies the partition for which the upload is.
		 */
		const val ARTIFACTORY_PARTITION: String = "artifactory-partition"

		/**
		 * Handles all command-line options prefixed with 'artifactory-'
		 * 
		 * @return true if it has successfully processed the given option.
		 */
		@JvmStatic
		@Throws(AgentOptionParseException::class)
		fun handleArtifactoryOptions(options: ArtifactoryConfig, key: String, value: String): Boolean {
			when (key) {
				ARTIFACTORY_URL_OPTION -> {
					options.url = AgentOptionsParser.parseUrl(key, value)
					return true
				}
				ARTIFACTORY_USER_OPTION -> {
					options.user = value
					return true
				}
				ARTIFACTORY_PASSWORD_OPTION -> {
					options.password = value
					return true
				}
				ARTIFACTORY_LEGACY_PATH_OPTION -> {
					options.legacyPath = value.toBoolean()
					return true
				}
				ARTIFACTORY_ZIP_PATH_OPTION -> {
					options.zipPath = stripSuffix(value, "/")
					return true
				}
				ARTIFACTORY_PATH_SUFFIX -> {
					options.pathSuffix = stripSuffix(value, "/")
					return true
				}
				ARTIFACTORY_API_KEY_OPTION -> {
					options.apiKey = value
					return true
				}
				ARTIFACTORY_PARTITION -> {
					options.partition = value
					return true
				}
				else -> return false
			}
		}

		/** Parses the commit information form a git.properties file.  */
		@JvmStatic
		@Throws(UploaderException::class)
		fun parseGitProperties(
			jarFile: File, searchRecursively: Boolean, gitPropertiesCommitTimeFormat: DateTimeFormatter?
		): CommitInfo? {
			try {
				val commitInfo = GitPropertiesLocatorUtils.getCommitInfoFromGitProperties(
					jarFile,
					true,
					searchRecursively,
					gitPropertiesCommitTimeFormat
				)
				if (commitInfo.isEmpty()) {
					throw UploaderException("Found no git.properties files in $jarFile")
				}
				if (commitInfo.size > 1) {
					throw UploaderException(
						("Found multiple git.properties files in " + jarFile
								+ ". Uploading to multiple projects is currently not possible with Artifactory. "
								+ "Please contact CQSE if you need this feature.")
					)
				}
				return commitInfo.firstOrNull()
			} catch (e: IOException) {
				throw UploaderException("Could not locate a valid git.properties file in $jarFile", e)
			} catch (e: InvalidGitPropertiesException) {
				throw UploaderException("Could not locate a valid git.properties file in $jarFile", e)
			}
		}
	}
}
