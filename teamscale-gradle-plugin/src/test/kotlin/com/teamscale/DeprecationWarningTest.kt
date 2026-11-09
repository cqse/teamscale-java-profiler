package com.teamscale

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests that applying the plugins emits no deprecation warnings. Runs on the Gradle version that builds the plugin,
 * since new deprecations only show up on newer Gradle versions than the minimum supported one.
 */
class DeprecationWarningTest : TeamscalePluginTestBase() {

	override val gradleVersion: String? = null

	@BeforeEach
	fun init() {
		rootProject.settingsFile.writeText("rootProject.name = 'deprecations'")
	}

	@Test
	fun `aggregation plugin emits no deprecation warning`() {
		rootProject.buildFile.writeText(
			"""
plugins {
	id 'com.teamscale.aggregation'
}
			""".trimIndent()
		)

		run("help", "--warning-mode=fail")
	}

	@Test
	fun `aggregation plugin in a Java project emits no deprecation warning`() {
		rootProject.buildFile.writeText(
			"""
plugins {
	id 'java'
	id 'com.teamscale'
	id 'com.teamscale.aggregation'
}
			""".trimIndent()
		)

		run("help", "--warning-mode=fail")
	}
}
