package com.teamscale.report.jacoco

import com.teamscale.report.util.BashFileSkippingInputStream
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.JaCoCo
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.ICoverageVisitor
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.internal.instr.InstrSupport
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.io.InputStream

/**
 * [Analyzer] that filters the analyzed class files by their class name.
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
) : Analyzer(executionData, coverageVisitor) {
	/** {@inheritDoc}  */
	@Throws(IOException::class)
	override fun analyzeAll(input: InputStream, location: String): Int {
		try {
			if (location.endsWith(".jar")) {
				return analyzeJar(input, location)
			}
			return super.analyzeAll(input, location)
		} catch (e: AnalyzerException) {
			throw e
		} catch (e: IOException) {
			// JaCoCo's error for this location (e.g. a corrupt ZIP) lacks the message of its cause and a hint
			throw analyzerError(location, e.cause as? Exception ?: e)
		}
	}

	@Throws(IOException::class)
	override fun analyzeClass(buffer: ByteArray, location: String) {
		try {
			val (reader, className) = readHeader(buffer, location) ?: return
			if (isIncluded(className, location)) {
				analyzeIncludedClass(buffer, reader, location)
			}
		} catch (cause: RuntimeException) {
			if (cause.isUnsupportedClassFile()) {
				val className = readClassNameOfUnsupportedClassFile(buffer)
				if (className != null && !isIncluded(className, location)) {
					return
				}
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
	 * Creates a reader for the class file and reads its class name. This only reads the constant pool, which is cheap
	 * compared to the analysis of the whole class. A corrupt class file is logged and skipped by returning `null` instead
	 * of failing the analysis: without its class name, we cannot tell whether it is excluded.
	 */
	private fun readHeader(buffer: ByteArray, location: String): Pair<ClassReader, String>? {
		try {
			val reader = InstrSupport.classReaderFor(buffer)
			return reader to reader.className
		} catch (cause: RuntimeException) {
			if (cause.isUnsupportedClassFile()) {
				throw cause
			}
			logger.warn(
				"Skipping class file '$location', whose class name cannot be read ($cause). The file is probably corrupt.",
				cause
			)
			return null
		}
	}

	/**
	 * Reads the class name of a class file whose version ASM does not support from a copy with a supported version, so
	 * that such a class file can still be excluded. Returns `null` if the class name cannot be read anyway, e.g.
	 * because the class file uses constant pool entries that ASM does not know.
	 */
	private fun readClassNameOfUnsupportedClassFile(buffer: ByteArray): String? {
		val supportedCopy = buffer.copyOf()
		InstrSupport.setMajorVersion(Opcodes.V1_8, supportedCopy)
		return try {
			ClassReader(supportedCopy).className
		} catch (_: RuntimeException) {
			null
		}
	}

	/** Tests if the class with the given JVM internal name passes the filter and logs if it does not. */
	private fun isIncluded(className: String, location: String): Boolean {
		if (locationIncludeFilter.isIncluded(className)) {
			return true
		}
		logger.debug("Excluding class file $location ($className)")
		return false
	}

	/**
	 * Analyzes a class that passed the filter. [reader] is the filter's reader of [buffer], which JaCoCo's analysis
	 * cannot take, so only subclasses reuse it. Errors are thrown as [RuntimeException], so that [analyzeClass] reports
	 * them uniformly.
	 */
	protected open fun analyzeIncludedClass(buffer: ByteArray, reader: ClassReader, location: String) {
		try {
			super.analyzeClass(buffer, location)
		} catch (e: IOException) {
			// JaCoCo wraps the RuntimeException of the analysis into an IOException
			throw e.cause as? RuntimeException ?: e
		}
	}

	/** Creates an [IOException] which includes the affected file location and JaCoCo version. */
	protected fun analyzerError(location: String, cause: Exception): IOException = AnalyzerException(
		"Could not analyze coverage for '$location' (JaCoCo ${JaCoCo.VERSION}/${JaCoCo.COMMITID_SHORT}): " +
				"${cause.message}. Verify that the file is a readable .class, JAR, ZIP, GZ, or Pack200 archive" +
				" and that it is not corrupt.",
		cause
	)

	/**
	 * Error of [analyzerError], which [analyzeAll] passes on unchanged to the locations that contain its location.
	 *
	 * @param message The exception message.
	 * @param cause The error that occurred during the analysis.
	 */
	private class AnalyzerException(message: String, cause: Exception) : IOException(message, cause)

	/**
	 * Checks if the error indicates that the class file might be newer than what is currently supported by
	 * JaCoCo. The concrete error message seems to depend on the used JVM, so we only check for "Unsupported" which seems
	 * to be common amongst all of them.
	 */
	private fun RuntimeException.isUnsupportedClassFile() =
		this is IllegalArgumentException && message?.startsWith("Unsupported") == true

	/**
	 * Analyzes a JAR, skipping a shell script in front of it (e.g. Spring Boot executable JARs) that would otherwise
	 * keep JaCoCo from detecting the ZIP format.
	 */
	@Throws(IOException::class)
	protected open fun analyzeJar(input: InputStream, location: String) =
		super.analyzeAll(BashFileSkippingInputStream(input), location)
}
