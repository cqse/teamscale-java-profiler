package com.teamscale.report.cobertura

import org.jacoco.core.JaCoCo
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.analysis.IClassCoverage
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.IMethodCoverage
import org.jacoco.core.analysis.IPackageCoverage
import org.jacoco.core.analysis.ISourceNode
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

/**
 * Writes a JaCoCo coverage model as a Cobertura XML report following the `coverage-04` DTD.
 *
 * JaCoCo only records whether a probe fired, not how often. Hit counts are therefore reported as 1 for covered lines
 * and 0 for uncovered ones, which is the convention all JaCoCo-to-Cobertura conversions use. For the same reason the
 * `<conditions>` element is omitted: the per-condition detail it requires does not exist in JaCoCo's data. The
 * aggregated `condition-coverage` attribute, which is what coverage consumers read, is written instead.
 *
 * The report's `version` attribute names the JaCoCo version the coverage was analyzed with, since no Cobertura is
 * involved in producing it.
 */
class CoberturaReportWriter(private val output: OutputStream) {

	/**
	 * Writes the given coverage data as a Cobertura report.
	 *
	 * @param bundle The coverage to write. Its packages and classes make up the report.
	 * @param timestamp The report's creation time in milliseconds since the epoch.
	 */
	@Throws(IOException::class)
	fun write(bundle: IBundleCoverage, timestamp: Long) {
		XmlStreamWriter(output, "coverage", COBERTURA_DTD_URL).use { xml ->
			xml.element(
				"coverage",
				"line-rate" to bundle.lineCounter.rate,
				"branch-rate" to bundle.branchCounter.rate,
				"lines-covered" to bundle.lineCounter.coveredCount,
				"lines-valid" to bundle.lineCounter.totalCount,
				"branches-covered" to bundle.branchCounter.coveredCount,
				"branches-valid" to bundle.branchCounter.totalCount,
				"complexity" to bundle.complexityCounter.totalCount,
				"version" to JaCoCo.VERSION,
				"timestamp" to timestamp
			) {
				xml.element("packages") {
					bundle.packages.forEach { xml.writePackage(it) }
				}
			}
		}
	}

	private fun XmlStreamWriter.writePackage(packageCoverage: IPackageCoverage) {
		element(
			"package",
			"name" to packageCoverage.name.toDottedName(),
			"line-rate" to packageCoverage.lineCounter.rate,
			"branch-rate" to packageCoverage.branchCounter.rate,
			"complexity" to packageCoverage.complexityCounter.totalCount
		) {
			element("classes") {
				packageCoverage.classes.forEach { writeClass(it) }
			}
		}
	}

	private fun XmlStreamWriter.writeClass(classCoverage: IClassCoverage) {
		element(
			"class",
			"name" to classCoverage.name.toDottedName(),
			"filename" to classCoverage.sourceFilePath,
			"line-rate" to classCoverage.lineCounter.rate,
			"branch-rate" to classCoverage.branchCounter.rate,
			"complexity" to classCoverage.complexityCounter.totalCount
		) {
			element("methods") {
				classCoverage.methods.forEach { writeMethod(it) }
			}
			writeLines(classCoverage)
		}
	}

	private fun XmlStreamWriter.writeMethod(methodCoverage: IMethodCoverage) {
		element(
			"method",
			"name" to methodCoverage.name,
			"signature" to methodCoverage.desc,
			"line-rate" to methodCoverage.lineCounter.rate,
			"branch-rate" to methodCoverage.branchCounter.rate,
			"complexity" to methodCoverage.complexityCounter.totalCount
		) {
			writeLines(methodCoverage)
		}
	}

	private fun XmlStreamWriter.writeLines(node: ISourceNode) {
		element("lines") {
			if (node.firstLine == ISourceNode.UNKNOWN_LINE) return@element
			(node.firstLine..node.lastLine).forEach { lineNumber ->
				val line = node.getLine(lineNumber)
				if (line.status == ICounter.EMPTY) return@forEach
				val attributes = mutableListOf<Pair<String, Any>>(
					"number" to lineNumber,
					"hits" to if (line.status == ICounter.NOT_COVERED) 0 else 1
				)
				if (line.branchCounter.totalCount > 0) {
					attributes.add("branch" to "true")
					attributes.add("condition-coverage" to line.branchCounter.conditionCoverage)
				}
				element("line", *attributes.toTypedArray())
			}
		}
	}

	/** Converts a JaCoCo VM name (e.g. `com/example/Foo`) to the dotted name Cobertura expects. */
	private fun String.toDottedName() = replace('/', '.')

	/** The path of the source file a class was compiled from, relative to the source root. */
	private val IClassCoverage.sourceFilePath: String
		get() = if (packageName.isNullOrEmpty()) sourceFileName.orEmpty() else "$packageName/$sourceFileName"

	/**
	 * The covered fraction of this counter, formatted as Cobertura expects it. A counter without any items counts as
	 * fully covered, which matches Cobertura's own behavior for classes without lines or branches.
	 */
	private val ICounter.rate: String
		get() = if (totalCount == 0) "1.0" else String.format(Locale.ROOT, "%.4f", coveredRatio)

	/** The branch coverage of a single line, formatted as Cobertura's `condition-coverage` attribute. */
	private val ICounter.conditionCoverage: String
		get() = "${coveredCount * 100 / totalCount}% ($coveredCount/$totalCount)"

	companion object {
		/** The DTD all Cobertura reports declare as their system identifier. */
		private const val COBERTURA_DTD_URL = "http://cobertura.sourceforge.net/xml/coverage-04.dtd"
	}
}
