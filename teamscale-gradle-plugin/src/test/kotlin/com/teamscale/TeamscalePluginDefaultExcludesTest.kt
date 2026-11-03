package com.teamscale

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.jacoco.core.runtime.WildcardMatcher
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Tests which classes the plugin's default excludes match. */
class TeamscalePluginDefaultExcludesTest {

	@ParameterizedTest
	@ValueSource(strings = ["com/x/IFoo\$MockitoMock\$123", "com/x/Foo\$\$FastClassByGuice\$\$abc"])
	fun `default excludes match Mockito mocks and Guice fast classes`(internalClassName: String) {
		assertThat(isExcludedByDefault(internalClassName))
			.`as`("The default excludes should match the generated class $internalClassName")
			.isTrue()
	}

	@ParameterizedTest
	@ValueSource(strings = ["com/x/Foo\$Bar", "com/x/Foo\$1"])
	fun `default excludes do not match ordinary nested or anonymous classes`(internalClassName: String) {
		assertThat(isExcludedByDefault(internalClassName))
			.`as`("The default excludes should not match the ordinary nested or anonymous class $internalClassName")
			.isFalse()
	}

	private fun isExcludedByDefault(internalClassName: String): Boolean {
		val project = ProjectBuilder.builder().build()
		project.pluginManager.apply(TeamscalePlugin::class.java)
		val excludes = project.tasks.named<Test>("test").get().extensions.getByType<JacocoTaskExtension>().excludes

		// JaCoCo's agent replaces the dots of each pattern with slashes and matches it against the JVM internal class name.
		val excludesMatcher = WildcardMatcher(excludes.orEmpty().joinToString(":").replace('.', '/'))
		return excludesMatcher.matches(internalClassName)
	}
}
