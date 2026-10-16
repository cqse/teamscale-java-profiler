package com.teamscale.report.testwise.jacoco.cache

import com.teamscale.report.jacoco.FilteringAnalyzer
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.internal.analysis.CachingClassAnalyzer
import org.jacoco.core.internal.analysis.ClassCoverageImpl
import org.jacoco.core.internal.analysis.StringPool
import org.jacoco.core.internal.data.CRC64
import org.jacoco.core.internal.flow.ClassProbesAdapter
import org.jacoco.core.internal.instr.InstrSupport
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

/**
 * An [AnalyzerCache] instance processes a set of Java class/jar/war/... files and builds a cache for each of the classes.
 *
 * For every class that gets found, [analyzeClass] is called. A class is identified by its class ID, which
 * is a CRC64 checksum of the class file. We process each class with `CachingClassAnalyzer` to fill a cache.
 */
class AnalyzerCache(
	private val probesCache: ProbesCache,
	locationIncludeFilter: ClasspathWildcardIncludeFilter,
	logger: ILogger
) : FilteringAnalyzer(null, null, locationIncludeFilter, logger) {
	private val stringPool = StringPool()

	/**
	 * Analyzes the given class. Instead of the original implementation in [Analyzer.analyzeClass] we
	 * don't use concrete execution data, but instead build a probe cache to speed up repeated lookups.
	 */
	override fun analyzeClass(source: ByteArray) {
		val classId = CRC64.classId(source)
		if (probesCache.containsClassId(classId)) {
			return
		}
		val reader = InstrSupport.classReaderFor(source)

		// Dummy class coverage object that allows us to subclass ClassAnalyzer with CachingClassAnalyzer and reuse its
		// IFilterContext implementation
		val dummyClassCoverage = ClassCoverageImpl(
			reader.className, classId, false
		)

		val classAnalyzer = CachingClassAnalyzer(
			probesCache.createClass(classId, reader.className),
			dummyClassCoverage,
			stringPool
		)
		val visitor = ClassProbesAdapter(classAnalyzer, false)
		reader.accept(visitor, 0)
	}

	/**
	 * Adds caching for jar files to the analyze jar functionality.
	 *
	 * The jar ID of a jar on disk is computed from the file, so the jar is only held in memory while the ID is
	 * computed. The jar itself is then streamed from [input] while it is analyzed.
	 */
	@Throws(IOException::class)
	override fun analyzeJar(input: InputStream, location: String): Int {
		val file = File(location)
		// Checks the file system instead of looking for '@' in the location, because real paths may contain '@' as
		// well (e.g. the Jenkins workspace `workspace@2`). Such jars must still be streamed instead of buffered.
		if (!file.isFile) {
			return analyzeJarInsideArchive(input, location)
		}
		return analyzeJarWithCache(CRC64.classId(Files.readAllBytes(file.toPath())), input, location)
	}

	/**
	 * Analyzes a jar that is an entry of another archive (JAR, WAR, EAR, ZIP, ...). Such a jar has a location like
	 * `app.war@WEB-INF/lib/inner.jar`, which is not a file on disk. Its bytes are only available from [input], so
	 * they are buffered to compute the jar ID and to analyze the jar.
	 */
	@Throws(IOException::class)
	private fun analyzeJarInsideArchive(input: InputStream, location: String): Int {
		val bytes = try {
			input.readBytes()
		} catch (e: IOException) {
			throw analyzerError(location, e)
		}
		return analyzeJarWithCache(CRC64.classId(bytes), bytes.inputStream(), location)
	}

	/** Analyzes the given jar unless a jar with the same [jarId] has been analyzed before.  */
	private fun analyzeJarWithCache(jarId: Long, input: InputStream, location: String): Int {
		val probesCountForJarId = probesCache.countForJarId(jarId)
		if (probesCountForJarId != 0) {
			return probesCountForJarId
		}
		val count = super.analyzeJar(input, location)
		probesCache.addJarId(jarId, count)
		return count
	}
}
