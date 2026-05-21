package com.teamscale.report.jacoco

import com.teamscale.report.util.BashFileSkippingInputStream
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.analysis.ICoverageVisitor
import org.jacoco.core.data.ExecutionDataStore
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * [org.jacoco.core.analysis.Analyzer] that filters the analyzed class files based on a given predicate.
 *
 * @param executionData The execution data store.
 * @param coverageVisitor The coverage visitor.
 * @param locationIncludeFilter The filter for the analyzed class files.
 * @param logger The logger.
 */
open class FilteringAnalyzer(
	executionData: ExecutionDataStore?,
	coverageVisitor: ICoverageVisitor?,
	private val locationIncludeFilter: ClasspathWildcardIncludeFilter,
	private val logger: ILogger
) : OpenAnalyzer(executionData, coverageVisitor) {
	/** {@inheritDoc}  */
	@Throws(IOException::class)
	override fun analyzeAll(input: InputStream, location: String): Int {
		if (location.endsWith(".class") && !locationIncludeFilter.isIncluded(location)) {
			logger.debug("Excluding class file $location")
			return 1
		}
		if (location.endsWith(".jar")) {
			return analyzeJar(input, location)
		}
		return super.analyzeAll(input, location)
	}

	@Throws(IOException::class)
	override fun analyzeClass(buffer: ByteArray, location: String) {
		try {
			analyzeClass(buffer)
		} catch (cause: RuntimeException) {
			if (cause.isUnsupportedClassFile()) {
				logger.error(
					"Unsupported class file in '$location': ${cause.message}." +
							" This is usually a class file compiled for a Java version newer than the profiler supports" +
							" — exclude it via 'excludes' or upgrade the profiler.",
					cause
				)
			} else {
				throw analyzerError(location, cause)
			}
		}
	}

	/**
	 * Checks if the error indicates that the class file might be newer than what is currently supported by
	 * JaCoCo. The concrete error message seems to depend on the used JVM, so we only check for "Unsupported" which seems
	 * to be common amongst all of them.
	 */
	private fun RuntimeException.isUnsupportedClassFile() =
		this is IllegalArgumentException && message?.startsWith("Unsupported") == true

	/**
	 * Copied from [org.jacoco.core.analysis.Analyzer.analyzeZip] renamed to analyzeJar
	 * and added wrapping [BashFileSkippingInputStream].
	 */
	@Throws(IOException::class)
	protected open fun analyzeJar(input: InputStream, location: String): Int {
		val zip = ZipInputStream(BashFileSkippingInputStream(input))
		return generateSequence { zip.nextEntry(location) }
				.map { entry -> analyzeAll(zip, "$location@${entry.name}") }
				.sum()
	}

	/** Copied from [org.jacoco.core.analysis.Analyzer.nextEntry].  */
	@Throws(IOException::class)
	private fun ZipInputStream.nextEntry(location: String): ZipEntry? {
		try {
			return nextEntry
		} catch (e: IOException) {
			throw analyzerError(location, e)
		}
	}
}
