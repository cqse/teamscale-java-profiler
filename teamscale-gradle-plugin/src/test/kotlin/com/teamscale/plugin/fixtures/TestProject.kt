package com.teamscale.plugin.fixtures

import java.io.File

/** Builder for a test project. */
open class TestProject(
	/** The project directory. */
	val projectDir: File
) {

	/** The build output directory. */
	val buildDir = file("build")

	/** The build gradle file. */
	val buildFile = file("build.gradle")

	/** Constructs a directory within the project. */
	fun dir(path: String): File {
		val dir = if (path.startsWith("/")) {
			File(path)
		} else {
			File(projectDir, path)
		}
		dir.mkdirs()
		return dir
	}

	/** Constructs a file path within the project */
	fun file(path: String): File {
		return if (path.contains("/")) {
			val parentPath = path.substringBeforeLast("/")
			val fileName = path.substringAfterLast("/")
			val parentDir = dir(parentPath)

			File(parentDir, fileName)
		} else {
			File(projectDir, path)
		}
	}

	/** Applies the given base plugin and the teamscale plugin with a basic commit info. */
	fun withTeamscalePlugin(basePlugin: String = "java") {
		buildFile.appendText(
			"""
plugins {
	id '${basePlugin}'
	id 'com.teamscale'
}

teamscale {
	commit {
		revision = "abcd1337"
	}
	repository = "myRepoId"
}
	""".trimIndent()
		)
	}

	/** Adds the Junit 4 and 5 dependencies. */
	fun withJunitDependencies(version: String = "5.12.0") {
		buildFile.appendText(
			"""
				
dependencies {
	// JUnit Jupiter
	testImplementation(platform("org.junit:junit-bom:$version"))
	testImplementation("org.junit.jupiter:junit-jupiter")

	// If you also want to support JUnit 3 and JUnit 4 tests
	testImplementation("junit:junit:4.13.2")
	testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
				""".trimIndent()
		)
	}
}
