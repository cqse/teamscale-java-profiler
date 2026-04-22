package com.teamscale

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests the compact coverage aggregation plugin across multiple subprojects,
 * specifically that the aggregation report is still generated when tests fail with --continue.
 */
class CompactCoverageAggregationTest : TeamscalePluginTestBase() {

	@BeforeEach
	fun init() {
		setupSettings()
		setupRootBuildFile()
		setupSubprojectMod1()
	}

	private fun setupSettings() {
		rootProject.settingsFile.writeText(
			"""
dependencyResolutionManagement {
	repositories {
		mavenLocal()
		mavenCentral()
	}
}

include 'mod1'
			""".trimIndent()
		)
	}

	private fun setupRootBuildFile() {
		rootProject.buildFile.writeText(
			"""
import com.teamscale.aggregation.TestSuiteCompatibilityUtil
import com.teamscale.aggregation.compact.AggregateCompactCoverageReport

plugins {
	id 'java'
	id 'com.teamscale'
	id 'com.teamscale.aggregation'
}

teamscale {
	commit {
		revision = "abcd1337"
	}
	repository = "myRepoId"
}

dependencies {
	reportAggregation(subprojects)
}

reporting {
	reports {
		unitTestAggregateCompactCoverageReport(AggregateCompactCoverageReport) { testSuiteName = 'unitTest' }
	}
}

subprojects {
	apply plugin: 'com.teamscale'

	tasks.register('runUnitTests', Test) {
		useJUnitPlatform()
		testClassesDirs = sourceSets.test.output.classesDirs
		classpath = sourceSets.test.runtimeClasspath
	}
	TestSuiteCompatibilityUtil.exposeTestForAggregation(tasks.named('runUnitTests'), 'unitTest')
}
			""".trimIndent()
		)
	}

	private fun setupSubprojectMod1() {
		java.io.File(rootProject.dir("mod1"), "build.gradle").writeText(
			"""
plugins {
	id 'java'
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:5.12.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
			""".trimIndent()
		)

		rootProject.file("mod1/src/main/java/com/example/Lib.java").writeText(
			"""
package com.example;

public class Lib {
	public static int add(int a, int b) {
		return a + b;
	}
}
			""".trimIndent()
		)

		rootProject.file("mod1/src/test/java/com/example/LibTest.java").writeText(
			"""
package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LibTest {
	@Test
	void failingTest() {
		assertEquals(3, Lib.add(1, 2));
		throw new RuntimeException("intentional failure");
	}
}
			""".trimIndent()
		)
	}

	@Test
	fun `compact coverage aggregation runs despite test failure with --continue`() {
		val build = runExpectingError("--continue", "clean", "unitTestAggregateCompactCoverageReport")

		assertThat(build.task(":mod1:runUnitTests")?.outcome)
			.isEqualTo(TaskOutcome.FAILED)
		assertThat(build.task(":unitTestAggregateCompactCoverageReport")?.outcome)
			.isEqualTo(TaskOutcome.SUCCESS)
	}
}
