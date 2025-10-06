package com.teamscale.plugin.fixtures

import java.io.File

/** The root of the test project. */
class TestRootProject(projectDir: File) : TestProject(projectDir) {

	/** The settings file in the project. */
	val settingsFile = file("settings.gradle")

	/** Copies over some code files into the project. */
	fun withSingleProject() {
		File("test-project").copyRecursively(projectDir)
	}

	/** Creates a subproject within the project and registers it in the #settingsFile. */
	fun subproject(projectName: String, configureProject: TestProject.() -> Unit): TestProject {
		if (!settingsFile.readText().contains(projectName)) {
			settingsFile.appendText(
				"""
                include '$projectName'
            """.trimIndent()
			)
		}
		val sub = subproject(projectName)
		configureProject(sub)
		return sub
	}

	/** Creates a subproject within the project and registers it in the #settingsFile. */
	fun subproject(projectName: String): TestProject {
		return TestProject(dir(projectName))
	}

	/** Initializes the project with some defaults that we use across all tests. */
	fun defaultProjectSetup() {
		withDependencyResolutionManagement()
		withTeamscalePlugin()
		withJunitDependencies()
	}

	fun withDependencyResolutionManagement() {
		settingsFile.writeText(
			"""
	dependencyResolutionManagement {
		repositories {
			mavenLocal()
			mavenCentral()
		}
	}
			""".trimIndent()
		)
	}

	/** Adds the teamscale server configuration. */
	fun withServerConfig(
		user: String = TeamscaleConstants.USER,
		accessToken: String = TeamscaleConstants.ACCESS_TOKEN,
		port: Int = TeamscaleConstants.PORT
	) {
		buildFile.appendText(
			"""

teamscale {
	server {
		url = 'http://127.0.0.1:${port}'
		userName = '${user}'
		userAccessToken = '${accessToken}'
		project = 'test'
	}
}
		""".trimIndent()
		)
	}

	/** Specifies a branch and timestamp as commit. */
	fun withBranchAndTimestamp() {
		buildFile.appendText(
			"""
			
teamscale {
	commit {
		timestamp = 1544512967526L
		branchName = "master"
	}
}
		""".trimIndent()
		)
	}

	/** Excludes some tests that are failing (on purpose) */
	fun excludeFailingTests() {
		buildFile.appendText(
			"""

tasks.withType(Test).configureEach {
	exclude '**/FailingRepeatedTest*'
}
		""".trimIndent()
		)
	}

	/** Sets up two test tasks and a corresponding report task. */
	fun defineTestTasks(jacocoIncludes: String = "com.example.project.*") {
		buildFile.appendText(
			"""
			
tasks.register('unitTest', Test) {
	useJUnitPlatform {
		excludeTags 'integration'
	}
	jacoco.includes = [ '${jacocoIncludes}' ]
	testLogging {
		// events "started", "skipped", "failed"
		exceptionFormat = "short"
		afterSuite { desc, result ->
			if (!desc.parent) { // will match the outermost suite
				def output = "Results: ${'$'}{result.resultType} (${'$'}{result.testCount} tests, ${'$'}{result.successfulTestCount} successes, ${'$'}{result.failedTestCount} failures, ${'$'}{result.skippedTestCount} skipped)"
				def startItem = '|  ', endItem = '  |'
				def repeatLength = startItem.length() + output.length() + endItem.length()
				println('\n' + ('-' * repeatLength) + '\n' + startItem + output + endItem + '\n' + ('-' * repeatLength))
			}
		}
	}
	testClassesDirs = testing.suites.test.sources.output.classesDirs
	classpath = testing.suites.test.sources.runtimeClasspath
	teamscale {
		collectTestwiseCoverage = true
		runImpacted = System.getProperty("impacted") != null
		runAllTests = System.getProperty("runAllTests") != null
		partition = 'Unit Tests'
	}
	finalizedBy('unitTestReport')
}

tasks.register('unitTestReport', com.teamscale.reporting.testwise.TestwiseCoverageReport) {
	executionData(tasks.unitTest)
}

tasks.register('integrationTest', Test) {
	useJUnitPlatform {
		includeTags 'integration'
	}

	jacoco.includes = [ 'com.example.project.*' ]
	testClassesDirs = testing.suites.test.sources.output.classesDirs
	classpath = testing.suites.test.sources.runtimeClasspath
	teamscale {
		collectTestwiseCoverage = true
		partition = 'Integration Tests'
	}
}
		""".trimIndent()
		)
	}

	/** Defines an upload task. */
	fun defineUploadTask() {
		buildFile.appendText(
			"""

tasks.register('unitTestReportUpload', com.teamscale.TeamscaleUpload) {
	partition = 'Unit Tests'
	from(tasks.unitTestReport)
}
		""".trimIndent()
		)
	}
}
