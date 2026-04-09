package com.teamscale.jacoco.agent.upload.teamscale

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.client.TeamscaleServer
import com.teamscale.jacoco.agent.options.AgentOptionParseException
import com.teamscale.jacoco.agent.options.AgentOptionsParser
import com.teamscale.jacoco.agent.options.FilePatternResolver
import com.teamscale.report.util.BashFileSkippingInputStream
import com.teamscale.report.util.ILogger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.jar.JarInputStream
import java.util.jar.Manifest

/** Config necessary for direct Teamscale upload.  */
class TeamscaleConfig(
	private val logger: ILogger,
	private val filePatternResolver: FilePatternResolver
) {
	/**
	 * Handles all command line options prefixed with "teamscale-".
	 * @return true if it has successfully processed the given option.
	 */
	@Throws(AgentOptionParseException::class)
	fun handleTeamscaleOptions(
		teamscaleServer: TeamscaleServer,
		key: String, value: String
	): Boolean {
		when (key) {
			"teamscale-server-url" -> {
				teamscaleServer.url = AgentOptionsParser.parseUrl(key, value)
				return true
			}
			"teamscale-project" -> {
				teamscaleServer.project = value
				return true
			}
			"teamscale-user" -> {
				teamscaleServer.userName = value
				return true
			}
			"teamscale-access-token" -> {
				teamscaleServer.userAccessToken = value
				return true
			}
			TEAMSCALE_PARTITION_OPTION -> {
				teamscaleServer.partition = value
				return true
			}
			TEAMSCALE_COMMIT_OPTION -> {
				teamscaleServer.commit = parseCommit(value)
				return true
			}
			TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION -> {
				teamscaleServer.commit = getCommitFromManifest(
					AgentOptionsParser.parsePath(filePatternResolver, key, value).toFile()
				)
				return true
			}
			"teamscale-message" -> {
				teamscaleServer.message = value
				return true
			}
			TEAMSCALE_REVISION_OPTION -> {
				teamscaleServer.revision = value
				return true
			}
			"teamscale-repository" -> {
				teamscaleServer.repository = value
				return true
			}
			TEAMSCALE_REVISION_MANIFEST_JAR_OPTION -> {
				teamscaleServer.revision = getRevisionFromManifest(
					AgentOptionsParser.parsePath(filePatternResolver, key, value).toFile()
				)
				return true
			}
			else -> return false
		}
	}

	/**
	 * Parses the the string representation of a commit to a [CommitDescriptor] object.
	 * The expected format is "branch:timestamp".
	 */
	@Throws(AgentOptionParseException::class)
	private fun parseCommit(commit: String): CommitDescriptor {
		val split = commit.split(":".toRegex()).dropLastWhile { it.isEmpty() }
		if (split.size != 2) {
			throw AgentOptionParseException("Invalid commit given $commit")
		}
		return CommitDescriptor(split[0], split[1])
	}

	/**
	 * Reads `Branch` and `Timestamp` entries from the given jar/war file's manifest and builds a commit descriptor out
	 * of it.
	 */
	@Throws(AgentOptionParseException::class)
	private fun getCommitFromManifest(jarFile: File): CommitDescriptor {
		val manifest = getManifestFromJarFile(jarFile)
		val branch = manifest.mainAttributes.getValue("Branch")
		val timestamp = manifest.mainAttributes.getValue("Timestamp")
		if (branch.isNullOrEmpty()) {
			throw AgentOptionParseException("No entry 'Branch' in MANIFEST")
		} else if (timestamp.isNullOrEmpty()) {
			throw AgentOptionParseException("No entry 'Timestamp' in MANIFEST")
		}
		logger.debug("Found commit $branch:$timestamp in file $jarFile")
		return CommitDescriptor(branch, timestamp)
	}

	/**
	 * Reads `Git_Commit` entry from the given jar/war file's manifest and sets it as revision.
	 */
	@Throws(AgentOptionParseException::class)
	private fun getRevisionFromManifest(jarFile: File): String {
		val manifest = getManifestFromJarFile(jarFile)
		var revision = manifest.mainAttributes.getValue("Revision")
		if (revision.isNullOrEmpty()) {
			// currently needed option for a customer
			if (manifest.getAttributes("Git") != null) {
				revision = manifest.getAttributes("Git").getValue("Git_Commit")
			}

			if (revision.isNullOrEmpty()) {
				throw AgentOptionParseException("No entry 'Revision' in MANIFEST")
			}
		}
		logger.debug("Found revision $revision in file $jarFile")
		return revision
	}

	/**
	 * Reads the JarFile to extract the MANIFEST.MF.
	 */
	@Throws(AgentOptionParseException::class)
	private fun getManifestFromJarFile(jarFile: File): Manifest {
		try {
			JarInputStream(
				BashFileSkippingInputStream(Files.newInputStream(jarFile.toPath()))
			).use { jarStream ->
				val manifest = jarStream.manifest ?: throw AgentOptionParseException(
					"Unable to read manifest from $jarFile. Maybe the manifest is corrupt?"
				)
				return manifest
			}
		} catch (e: IOException) {
			throw AgentOptionParseException(
				"Reading jar ${jarFile.absolutePath} for obtaining commit descriptor from MANIFEST failed", e
			)
		}
	}

	companion object {
		/** Option name that allows to specify to which branch coverage should be uploaded to (branch:timestamp).  */
		const val TEAMSCALE_COMMIT_OPTION: String = "teamscale-commit"

		/** Option name that allows to specify a git commit hash to which coverage should be uploaded to.  */
		const val TEAMSCALE_REVISION_OPTION: String = "teamscale-revision"

		/** Option name that allows to specify a jar file that contains the git commit hash in a MANIFEST.MF file.  */
		const val TEAMSCALE_REVISION_MANIFEST_JAR_OPTION: String = "teamscale-revision-manifest-jar"

		/** Option name that allows to specify a jar file that contains the branch name and timestamp in a MANIFEST.MF file.  */
		const val TEAMSCALE_COMMIT_MANIFEST_JAR_OPTION: String = "teamscale-commit-manifest-jar"

		/** Option name that allows to specify a partition to which coverage should be uploaded to.  */
		const val TEAMSCALE_PARTITION_OPTION: String = "teamscale-partition"
	}
}
