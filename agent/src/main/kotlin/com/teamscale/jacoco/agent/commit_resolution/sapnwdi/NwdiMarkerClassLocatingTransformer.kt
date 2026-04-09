package com.teamscale.jacoco.agent.commit_resolution.sapnwdi

import com.teamscale.client.CommitDescriptor
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.jacoco.agent.options.sapnwdi.DelayedSapNwdiMultiUploader
import com.teamscale.jacoco.agent.options.sapnwdi.SapNwdiApplication
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import java.lang.instrument.ClassFileTransformer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.security.ProtectionDomain
import java.util.function.Function
import java.util.stream.Collectors

/**
 * [ClassFileTransformer] that doesn't change the loaded classes but guesses the rough commit timestamp by
 * inspecting the last modification date of the applications marker class file.
 */
class NwdiMarkerClassLocatingTransformer(
	private val store: DelayedSapNwdiMultiUploader,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	apps: MutableCollection<SapNwdiApplication>
) : ClassFileTransformer {
	private val logger = getLogger(this)
	private val markerClassesToApplications =
		apps.associateBy { it.markerClass.replace('.', '/') }

	override fun transform(
		classLoader: ClassLoader?,
		className: String,
		aClass: Class<*>?,
		protectionDomain: ProtectionDomain?,
		classFileContent: ByteArray?
	): ByteArray? {
		if (protectionDomain == null) {
			// happens for e.g. java.lang. We can ignore these classes
			return null
		}

		if (className.isEmpty() || !locationIncludeFilter.isIncluded(className)) {
			// only search in jar files of included classes
			return null
		}

		// only kick off search if the marker class was found.
		val application = markerClassesToApplications[className] ?: return null

		try {
			// unknown when this can happen, we suspect when code is generated at runtime
			// but there's nothing else we can do here in either case
			val codeSource = protectionDomain.codeSource ?: return null

			val jarOrClassFolderUrl = codeSource.location
			logger.debug("Found {} in {}", className, jarOrClassFolderUrl)

			if (jarOrClassFolderUrl.protocol.equals("file", ignoreCase = true)) {
				val file = Paths.get(jarOrClassFolderUrl.toURI())
				val attr = Files.readAttributes(file, BasicFileAttributes::class.java)
				val commitDescriptor = CommitDescriptor(
					DTR_BRIDGE_DEFAULT_BRANCH, attr.lastModifiedTime().toMillis()
				)
				store.setCommitForApplication(commitDescriptor, application)
			}
		} catch (e: Throwable) {
			// we catch Throwable to be sure that we log all errors as anything thrown from this method is
			// silently discarded by the JVM
			logger.error(
				"Failed to process class {} trying to determine its last modification timestamp.", className, e
			)
		}
		return null
	}

	companion object {
		/** The Design time repository-git-bridge (DTR-bridge) currently only exports a single branch named master.  */
		private const val DTR_BRIDGE_DEFAULT_BRANCH = "master"
	}
}
