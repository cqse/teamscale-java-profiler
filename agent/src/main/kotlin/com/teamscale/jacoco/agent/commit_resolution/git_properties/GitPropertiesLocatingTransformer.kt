package com.teamscale.jacoco.agent.commit_resolution.git_properties

import com.teamscale.jacoco.agent.logging.LoggingUtils.getLogger
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import java.io.File
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import java.util.concurrent.ConcurrentSkipListSet

/**
 * [ClassFileTransformer] that doesn't change the loaded classes but searches their corresponding Jar/War/Ear/...
 * files for a git.properties file.
 */
class GitPropertiesLocatingTransformer(
	private val locator: IGitPropertiesLocator,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter
) : ClassFileTransformer {
	private val logger = getLogger(this)
	private val seenJars = ConcurrentSkipListSet<String>()

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

		try {
			val codeSource = protectionDomain.codeSource
			if (codeSource == null || codeSource.location == null) {
				// unknown when this can happen, we suspect when code is generated at runtime
				// but there's nothing else we can do here in either case.
				// codeSource.getLocation() is null e.g. when executing Pixelitor with Java14 for class sun/reflect/misc/Trampoline
				logger.debug(
					"Could not locate code source for class {}. Skipping git.properties search for this class",
					className
				)
				return null
			}

			val jarOrClassFolderUrl = codeSource.location
			val searchRoot = GitPropertiesLocatorUtils.extractGitPropertiesSearchRoot(
				jarOrClassFolderUrl
			)
			if (searchRoot == null || searchRoot.first == null) {
				logger.warn(
					"Not searching location for git.properties with unknown protocol or extension {}." +
							" If this location contains your git.properties, please report this warning as a" +
							" bug to CQSE. In that case, auto-discovery of git.properties will not work.",
					jarOrClassFolderUrl
				)
				return null
			}

			if (hasLocationAlreadyBeenSearched(searchRoot.first!!)) {
				return null
			}

			logger.debug("Scheduling asynchronous search for git.properties in {}", searchRoot)
			locator.searchFileForGitPropertiesAsync(searchRoot.first, searchRoot.second!!)
		} catch (e: Throwable) {
			// we catch Throwable to be sure that we log all errors as anything thrown from this method is
			// silently discarded by the JVM
			logger.error("Failed to process class {} in search of git.properties", className, e)
		}
		return null
	}

	private fun hasLocationAlreadyBeenSearched(location: File) = !seenJars.add(location.toString())
}
