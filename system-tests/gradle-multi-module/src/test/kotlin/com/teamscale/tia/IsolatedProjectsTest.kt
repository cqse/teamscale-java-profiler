package com.teamscale.tia

import com.teamscale.test.commons.SystemTestUtils.runGradle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Checks that the plugins are compatible with [Isolated Projects](https://docs.gradle.org/current/userguide/isolated_projects.html).
 * It is enabled via gradle.properties, so every build of the test project fails on a violation.
 */
class IsolatedProjectsTest {
	@Test
	fun testIsolatedProjects() {
		val result = runGradle(
			"gradle-project", "clean",
			"unitTestAggregateCompactCoverageReport", "unitTestAggregateJUnitReport",
			"systemTestAggregateTestwiseCoverageReport"
		)
		// Guards against the tests of this module silently running without Isolated Projects.
		assertThat(result.stdout).contains("Isolated Projects is an incubating feature")
	}
}
