package com.teamscale.report.testwise.jacoco.cache

import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.testwise.model.builder.FileCoverageBuilder
import com.teamscale.report.util.ClasspathWildcardIncludeFilter
import com.teamscale.report.util.ILogger
import org.jacoco.core.data.ExecutionData
import org.jacoco.report.JavaNames

/**
 * Holds [ClassCoverageLookup]s for all analyzed classes.
 *
 * @param logger The logger to use for logging.
 * @param duplicateClassFileBehavior Whether to ignore non-identical duplicates of class files.
 */
class ProbesCache(
	private val logger: ILogger,
	private val duplicateClassFileBehavior: EDuplicateClassFileBehavior
) {
	/** A mapping from class ID (CRC64 of the class file) to [ClassCoverageLookup].  */
	private val classCoverageLookups = hashMapOf<Long, ClassCoverageLookup>()

	/** Holds all fully qualified class names that are already contained in the cache.  */
	private val containedClasses = mutableSetOf<String>()
	private val containedJars = mutableMapOf<Long, Int>()
	private val classNotFoundLogger = ClassNotFoundLogger(logger)

	/** Adds a new class entry to the cache and returns its [ClassCoverageLookup].  */
	fun createClass(classId: Long, className: String): ClassCoverageLookup {
		if (containedClasses.contains(className)) {
			if (duplicateClassFileBehavior != EDuplicateClassFileBehavior.IGNORE) {
				logger.warn(
					"Non-identical class file for class $className. This happens when a class with the " +
							"same fully-qualified name is loaded twice but the two loaded class files are not identical. " +
							"A common reason for this is that the same library or shared code is included twice in your " +
							"application but in two different versions. The produced coverage for this class may not be " +
							"accurate or may even be unusable. To fix this problem, please resolve the conflict between " +
							"both class files in your application."
				)
			}
			check(duplicateClassFileBehavior != EDuplicateClassFileBehavior.FAIL) { "Found non-identical class file for class $className. See logs for more details." }
		}
		containedClasses.add(className)
		val classCoverageLookup = ClassCoverageLookup(className)
		classCoverageLookups[classId] = classCoverageLookup
		return classCoverageLookup
	}

	/** Returns whether a class with the given class ID has already been analyzed.  */
	fun containsClassId(classId: Long) =
		classCoverageLookups.containsKey(classId)

	/**
	 * Returns the number of found class files in a cached jar file. Otherwise 0.
	 */
	fun countForJarId(jarId: Long) =
		containedJars.getOrDefault(jarId, 0)

	/**
	 * Adds a jar id along with the count of class files found in the jar.
	 */
	fun addJarId(jarId: Long, count: Int) {
		containedJars[jarId] = count
	}

	/**
	 * Converts the given [ExecutionData] to [FileCoverageBuilder] using the cached lookups or null if the
	 * class file of this class has not been included in the analysis or was not covered.
	 */
	@Throws(CoverageGenerationException::class)
	fun getCoverage(
		executionData: ExecutionData,
		locationIncludeFilter: ClasspathWildcardIncludeFilter
	): FileCoverageBuilder? {
		val classId = executionData.id
		if (!containsClassId(classId)) {
			if (locationIncludeFilter.isIncluded(executionData.name)) {
				classNotFoundLogger.log(JavaNames().getQualifiedClassName(executionData.name))
			}
			return null
		}
		if (!executionData.hasHits()) {
			return null
		}

		return classCoverageLookups[classId]?.getFileCoverage(executionData, logger)
	}

	/** Returns true if the cache does not contain coverage for any class.  */
	val isEmpty: Boolean
		get() = classCoverageLookups.isEmpty()

	fun flushLogger() {
		classNotFoundLogger.flush()
	}
}
