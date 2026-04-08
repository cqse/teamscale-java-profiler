package com.teamscale.jacoco.agent.upload.delay

import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.upload.IUploader
import com.teamscale.jacoco.agent.util.DaemonThreadFactory
import com.teamscale.report.jacoco.CoverageFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Function
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

/**
 * Wraps an [IUploader] and in order to delay upload until a all
 * information describing a commit is asynchronously made available.
 */
class DelayedUploader<T> internal constructor(
	private val wrappedUploaderFactory: Function<T, IUploader>,
	private val cacheDir: Path,
	private val executor: Executor
) : IUploader {
	private val logger = getLogger(this)
	private var wrappedUploader: IUploader? = null

	constructor(wrappedUploaderFactory: Function<T, IUploader>, cacheDir: Path) : this(
		wrappedUploaderFactory, cacheDir, Executors.newSingleThreadExecutor(
			DaemonThreadFactory(DelayedUploader::class.java, "Delayed cache upload thread")
		)
	)

	/**
	 * Visible for testing. Allows tests to control the [Executor] to test the
	 * asynchronous functionality of this class.
	 */
	/* package */
	init {
		registerShutdownHook()
	}

	private fun registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(Thread {
			if (wrappedUploader == null) {
				logger.error(
					("The application was shut down before a commit could be found. The recorded coverage"
							+ " is still cached in {} but will not be automatically processed. You configured the"
							+ " agent to auto-detect the commit to which the recorded coverage should be uploaded to"
							+ " Teamscale. In order to fix this problem, you need to provide a git.properties file"
							+ " in all of the profiled Jar/War/Ear/... files. If you're using Gradle or"
							+ " Maven, you can use a plugin to create a proper git.properties file for you, see"
							+ " https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto-git-info"
							+ "\nTo debug problems with git.properties, please enable debug logging for the agent via"
							+ " the logging-config parameter."), cacheDir.toAbsolutePath()
				)
			}
		})
	}

	@Synchronized
	override fun upload(coverageFile: CoverageFile) {
		if (wrappedUploader == null) {
			logger.info(
				"The commit to upload to has not yet been found. Caching coverage XML in {}",
				cacheDir.toAbsolutePath()
			)
		} else {
			wrappedUploader?.upload(coverageFile)
		}
	}

	override fun describe() =
		wrappedUploader?.describe() ?: "Temporary cache until commit is resolved: ${cacheDir.toAbsolutePath()}"

	/**
	 * Sets the commit to upload the XMLs to and asynchronously triggers the upload
	 * of all cached XMLs. This method should only be called once.
	 */
	@Synchronized
	fun setCommitAndTriggerAsynchronousUpload(information: T) {
		if (wrappedUploader == null) {
			wrappedUploader = wrappedUploaderFactory.apply(information)
			logger.info(
				"Commit to upload to has been found: {}. Uploading any cached XMLs now to {}", information,
				wrappedUploader?.describe()
			)
			executor.execute { uploadCachedXmls() }
		} else {
			logger.error(
				"Tried to set upload commit multiple times (old uploader: {}, new commit: {}). This is a programming error. Please report a bug.",
				wrappedUploader?.describe(), information
			)
		}
	}

	private fun uploadCachedXmls() {
		try {
			if (!cacheDir.isDirectory()) {
				// Found data before XML was dumped
				return
			}
			val xmlFilesStream = cacheDir.walk().filter { path ->
				val fileName = path.fileName.toString()
				fileName.startsWith("jacoco-") && fileName.endsWith(".xml")
			}
			xmlFilesStream.forEach { path -> wrappedUploader?.upload(CoverageFile(path.toFile())) }
			logger.debug("Finished upload of cached XMLs to {}", wrappedUploader?.describe())
		} catch (e: IOException) {
			logger.error("Failed to list cached coverage XML files in {}", cacheDir.toAbsolutePath(), e)
		}
	}
}
