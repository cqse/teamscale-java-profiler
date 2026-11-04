package com.teamscale

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.junit.jupiter.api.Test

/** Tests how the plugin combines its default excludes with the JaCoCo excludes that the build sets. */
class TeamscalePluginBuildExcludesTest {

	private val buildExclude = "*\$MockitoMock\$*"

	@Test
	fun `plugin appends its default excludes to read-only excludes of the build`() {
		val excludes = testTaskExcludesAfterApplyingPlugin(listOf(buildExclude))

		assertThat(excludes)
			.`as`("The excludes should be the excludes of the build followed by the default excludes")
			.containsExactlyElementsOf(listOf(buildExclude) + defaultExcludes())
	}

	@Test
	fun `plugin appends its default excludes to mutable excludes of the build`() {
		val excludes = testTaskExcludesAfterApplyingPlugin(mutableListOf(buildExclude))

		assertThat(excludes)
			.`as`("The excludes should be the excludes of the build followed by the default excludes")
			.containsExactlyElementsOf(listOf(buildExclude) + defaultExcludes())
	}

	private fun testTaskExcludesAfterApplyingPlugin(buildExcludes: List<String>): List<String>? {
		val project = ProjectBuilder.builder().build()
		project.pluginManager.apply(JavaPlugin::class.java)
		project.pluginManager.apply(JacocoPlugin::class.java)
		project.tasks.withType<TestTask>().configureEach {
			extensions.configure<JacocoTaskExtension> { excludes = buildExcludes }
		}

		project.pluginManager.apply(TeamscalePlugin::class.java)
		return testTaskExcludesOf(project)
	}

	private fun defaultExcludes(): List<String> {
		val project = ProjectBuilder.builder().build()
		project.pluginManager.apply(TeamscalePlugin::class.java)
		val defaultExcludes = testTaskExcludesOf(project).orEmpty()

		assertThat(defaultExcludes)
			.`as`("The plugin should add default excludes to a test task without excludes of the build")
			.isNotEmpty()
		return defaultExcludes
	}

	private fun testTaskExcludesOf(project: Project) =
		project.tasks.named<TestTask>("test").get().extensions.getByType<JacocoTaskExtension>().excludes
}
