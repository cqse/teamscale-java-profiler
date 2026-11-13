package com.teamscale.report.cobertura

import org.assertj.core.api.Assertions.assertThat
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.internal.analysis.BundleCoverageImpl
import org.jacoco.core.internal.analysis.ClassCoverageImpl
import org.jacoco.core.internal.analysis.CounterImpl
import org.jacoco.core.internal.analysis.MethodCoverageImpl
import org.jacoco.core.internal.analysis.SourceFileCoverageImpl
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Tests that the Cobertura report writer produces the structure and attributes the format prescribes. */
class CoberturaReportWriterTest {

	@Test
	fun `writes the prolog and doctype of the coverage-04 format`() {
		val report = write(bundleWithSampleClass())

		assertThat(report).startsWith(
			"""
			<?xml version="1.0" encoding="UTF-8"?>
			<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">
			""".trimIndent()
		)
	}

	@Test
	fun `writes packages and classes with dotted names and the source file path`() {
		val report = write(bundleWithSampleClass())

		assertThat(report).contains("""<package name="com.example"""")
		assertThat(report).contains("""<class name="com.example.MyClass" filename="com/example/MyClass.java"""")
		assertThat(report).contains("""<method name="doSomething" signature="()V"""")
	}

	@Test
	fun `reports covered lines with one hit and uncovered lines with zero hits`() {
		val report = write(bundleWithSampleClass())

		assertThat(report).contains("""<line number="1" hits="1"/>""")
		assertThat(report).contains("""<line number="4" hits="0"/>""")
	}

	@Test
	fun `reports partly covered lines as covered since JaCoCo does not count hits`() {
		val report = write(bundleWithSampleClass())

		assertThat(report).contains("""<line number="2" hits="1" branch="true" condition-coverage="50% (1/2)"/>""")
	}

	@Test
	fun `does not write lines without code`() {
		val report = write(bundleWithSampleClass())

		assertThat(report).doesNotContain("""number="3"""")
	}

	@Test
	fun `writes the aggregated counters of the whole report`() {
		val report = write(bundleWithSampleClass(), timestamp = 1234)

		assertThat(report).contains("""line-rate="0.6667"""")
		assertThat(report).contains("""lines-covered="2" lines-valid="3"""")
		assertThat(report).contains("""branches-covered="1" branches-valid="2"""")
		assertThat(report).contains("""timestamp="1234"""")
	}

	@Test
	fun `treats a class without lines or branches as fully covered`() {
		val classWithoutCode = ClassCoverageImpl("com/example/Marker", 4711, false).apply {
			setSourceFileName("Marker.java")
		}

		val report = write(bundle(classWithoutCode))

		assertThat(report).contains(
			"""<class name="com.example.Marker" filename="com/example/Marker.java" line-rate="1.0" branch-rate="1.0""""
		)
	}

	@Test
	fun `escapes characters that are not allowed in attribute values`() {
		val constructor = MethodCoverageImpl("<init>", "()V", null).apply {
			increment(CounterImpl.COUNTER_0_1, CounterImpl.COUNTER_0_0, 1)
			incrementMethodCounter()
		}
		val classWithConstructor = ClassCoverageImpl("com/example/MyClass", 4711, false).apply {
			setSourceFileName("MyClass.java")
			addMethod(constructor)
		}

		val report = write(bundle(classWithConstructor))

		assertThat(report).contains("""<method name="&lt;init&gt;" signature="()V"""")
	}

	private fun write(bundle: IBundleCoverage, timestamp: Long = 0): String {
		val output = ByteArrayOutputStream()
		CoberturaReportWriter(output).write(bundle, timestamp)
		return output.toString(StandardCharsets.UTF_8.name())
	}

	/**
	 * Builds a bundle the same way JaCoCo's `CoverageBuilder` does, including the source file coverage. The source
	 * files matter because JaCoCo aggregates the package and bundle counters from them rather than from the classes.
	 */
	private fun bundle(vararg classes: ClassCoverageImpl): IBundleCoverage {
		val sourceFiles = classes.map { classCoverage ->
			SourceFileCoverageImpl(classCoverage.sourceFileName, classCoverage.packageName).apply {
				increment(classCoverage)
			}
		}
		return BundleCoverageImpl("dummybundle", classes.toList(), sourceFiles)
	}

	/**
	 * A bundle with a single class whose only method has a fully covered line 1, a partly covered line 2 with two
	 * branches, an uncovered line 4 and no code on line 3.
	 */
	private fun bundleWithSampleClass(): IBundleCoverage {
		val method = MethodCoverageImpl("doSomething", "()V", null).apply {
			increment(CounterImpl.COUNTER_0_1, CounterImpl.COUNTER_0_0, 1)
			increment(CounterImpl.getInstance(1, 1), CounterImpl.getInstance(1, 1), 2)
			increment(CounterImpl.COUNTER_1_0, CounterImpl.COUNTER_0_0, 4)
			incrementMethodCounter()
		}
		val classCoverage = ClassCoverageImpl("com/example/MyClass", 4711, false).apply {
			setSourceFileName("MyClass.java")
			addMethod(method)
		}
		return bundle(classCoverage)
	}
}
