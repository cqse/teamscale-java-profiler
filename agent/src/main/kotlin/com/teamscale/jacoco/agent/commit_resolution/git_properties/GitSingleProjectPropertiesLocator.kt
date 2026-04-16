package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.upload.delay.DelayedUploader
import com.teamscale.jacoco.agent.util.DaemonThreadFactory
import java.io.File
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Searches a Jar/War/Ear/... file for a git.properties file in order to enable upload for the commit described therein,
 * e.g. to Teamscale, via a [DelayedUploader].
 */
class GitSingleProjectPropertiesLocator<T>(
	private val uploader: DelayedUploader<T>,
	private val recursiveSearch: Boolean,
	private val gitPropertiesCommitTimeFormat: DateTimeFormatter?,
	private val executor: Executor = Executors.newSingleThreadExecutor(
		DaemonThreadFactory(
			GitSingleProjectPropertiesLocator::class.java,
			"git.properties Jar scanner thread"
		)
	),
	private val dataExtractor: DataExtractor<T>
) : IGitPropertiesLocator {
	private val logger = getLogger(this)
	private var foundData: T? = null
	private var jarFileWithGitProperties: File? = null

	/**
	 * Asynchronously searches the given jar file for a git.properties file.
	 */
	override fun searchFileForGitPropertiesAsync(file: File, isJarFile: Boolean) {
		executor.execute { searchFile(file, isJarFile) }
	}

	private fun searchFile(file: File, isJarFile: Boolean) {
		logger.debug("Searching jar file {} for a single git.properties", file)
		try {
			val data = dataExtractor.extractData(file, isJarFile, recursiveSearch, gitPropertiesCommitTimeFormat)
			if (data.isEmpty()) {
				logger.debug("No git.properties files found in {}", file.toString())
				return
			}
			if (data.size > 1) {
				logger.warn(
					"Multiple git.properties files found in {}", file.toString() +
							". Using the first one: " + data.first()
				)
			}
			val dataEntry = data.first()

			if (foundData != null) {
				if (foundData != dataEntry) {
					logger.warn(
						"Found inconsistent git.properties files: {} contained data {} while {} contained {}." +
								" Please ensure that all git.properties files of your application are consistent." +
								" Otherwise, you may" +
								" be uploading to the wrong project/commit which will result in incorrect coverage data" +
								" displayed in Teamscale. If you cannot fix the inconsistency, you can manually" +
								" specify a Jar/War/Ear/... file from which to read the correct git.properties" +
								" file with the agent's teamscale-git-properties-jar parameter.",
						jarFileWithGitProperties, foundData, file, data
					)
				}
				return
			}

			logger.debug(
				"Found git.properties file in {} and found commit descriptor {}", file.toString(),
				dataEntry
			)
			foundData = dataEntry
			jarFileWithGitProperties = file
			uploader.setCommitAndTriggerAsynchronousUpload(dataEntry)
		} catch (e: IOException) {
			logger.error("Error during asynchronous search for git.properties in {}", file.toString(), e)
		} catch (e: InvalidGitPropertiesException) {
			logger.error("Error during asynchronous search for git.properties in {}", file.toString(), e)
		}
	}

	/** Functional interface for data extraction from a jar file.  */
	fun interface DataExtractor<T> {
		/** Extracts data from the JAR.  */
		@Throws(IOException::class, InvalidGitPropertiesException::class)
		fun extractData(
			file: File,
			isJarFile: Boolean,
			recursiveSearch: Boolean,
			gitPropertiesCommitTimeFormat: DateTimeFormatter?
		): List<T>
	}
}
