package com.teamscale.jacoco.agent.options.sapnwdi

import com.teamscale.client.CommitDescriptor
import com.teamscale.jacoco.agent.upload.DelayedMultiUploaderBase
import com.teamscale.jacoco.agent.upload.IUploader
import java.util.function.BiFunction

/**
 * Wraps multiple [IUploader]s to delay uploads until a [CommitDescriptor] is asynchronously made
 * available for each application. Whenever a dump happens, the coverage is uploaded to all projects for which a
 * corresponding commit has already been found. Uploads for application that have not committed at that time are skipped.
 * 
 * 
 * This is safe assuming that the marker class is the central entry point for the application, and therefore there should
 * not be any relevant coverage for the application as long as the marker class has not been loaded.
 */
class DelayedSapNwdiMultiUploader(
	private val uploaderFactory: BiFunction<CommitDescriptor, SapNwdiApplication, IUploader>
) : DelayedMultiUploaderBase(), IUploader {
	/** The wrapped uploader instances.  */
	private val uploaders = mutableMapOf<SapNwdiApplication, IUploader>()

	/**
	 * Visible for testing. Allows tests to control the [Executor] to test the asynchronous functionality of this
	 * class.
	 */
	init {
		registerShutdownHook()
	}

	/** Registers the shutdown hook.  */
	private fun registerShutdownHook() {
		Runtime.getRuntime().addShutdownHook(Thread {
			if (wrappedUploaders.isEmpty()) {
				logger.error(
					"The application was shut down before any commit could be found for the SAP NWDI marker classes." +
							" The recorded coverage is lost." +
							" Ensure every NWDI marker class is present in a JAR with a valid git.properties," +
							" and enable debug logging via the 'logging-config' option to see which classes were inspected."
				)
			}
		})
	}

	/** Sets the commit info detected for the application.  */
	fun setCommitForApplication(commit: CommitDescriptor, application: SapNwdiApplication) {
		logger.info("Found commit for ${application.markerClass}: $commit")
		uploaders[application] = uploaderFactory.apply(commit, application)
	}

	override val wrappedUploaders: MutableCollection<IUploader>
		get() = uploaders.values
}
