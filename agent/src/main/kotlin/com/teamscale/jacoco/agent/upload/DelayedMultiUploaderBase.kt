package com.teamscale.jacoco.agent.upload

import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.report.jacoco.CoverageFile
import org.slf4j.Logger

/**
 * Base class for wrapper uploaders that allow uploading the same coverage to
 * multiple locations.
 */
abstract class DelayedMultiUploaderBase : IUploader {
	@JvmField
	protected val logger: Logger = getLogger(this)

	@Synchronized
	override fun upload(coverageFile: CoverageFile) {
		val wrappedUploaders = this.wrappedUploaders
		wrappedUploaders.forEach { _ -> coverageFile.acquireReference() }
		if (wrappedUploaders.isEmpty()) {
			logger.warn("No commits have been found yet to which coverage should be uploaded. Discarding coverage")
		} else {
			wrappedUploaders.forEach { wrappedUploader ->
				wrappedUploader.upload(coverageFile)
			}
		}
	}

	override fun describe(): String {
		if (!wrappedUploaders.isEmpty()) {
			return wrappedUploaders.joinToString { it.describe() }
		}
		return "Temporary stand-in until commit is resolved"
	}

	/** Returns the actual uploaders that this multiuploader wraps.  */
	protected abstract val wrappedUploaders: MutableCollection<IUploader>
}
