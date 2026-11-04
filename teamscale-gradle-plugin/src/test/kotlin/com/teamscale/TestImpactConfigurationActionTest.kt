package com.teamscale

import com.teamscale.extension.TeamscalePluginExtension
import com.teamscale.extension.TeamscaleTaskExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/** Tests how [TestImpactConfigurationAction] configures the JUnit Platform engines of a test task. */
class TestImpactConfigurationActionTest {

	@Test
	fun `plugin adds the impacted test engine to read-only engine excludes of the build`() {
		val project = ProjectBuilder.builder().build()
		project.pluginManager.apply(TeamscalePlugin::class.java)
		val testTask = project.tasks.named<TestTask>("test").get()
		testTask.useJUnitPlatform { excludeEngines = setOf("junit-vintage") }

		runTestImpactConfigurationAction(testTask)

		assertThat((testTask.options as JUnitPlatformOptions).excludeEngines)
			.`as`("The test task should exclude the engine of the build and the impacted test engine")
			.containsExactlyInAnyOrder("junit-vintage", "teamscale-test-impacted")
	}

	private fun runTestImpactConfigurationAction(testTask: TestTask) {
		val pluginExtension = testTask.project.extensions.getByType<TeamscalePluginExtension>()
		val taskExtension = testTask.extensions.getByType<TeamscaleTaskExtension>()
		TestImpactConfigurationAction(pluginExtension, taskExtension).execute(testTask)
	}
}
