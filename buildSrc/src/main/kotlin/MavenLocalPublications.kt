import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

/**
 * Installs what the projects with the given paths publish into the local Maven repository before this test runs, and
 * declares the installed files as inputs of the test. The Maven and Gradle builds that the test starts resolve the
 * artifacts from there, so the paths must include every project whose artifacts they resolve, directly or
 * transitively.
 *
 * A dependency on the publishToMavenLocal tasks alone is not enough: Gradle would consider the test up to date
 * after a change to the installed artifacts, so the test would not run against them.
 */
fun Test.usesMavenLocalPublicationsOf(vararg projectPaths: String) {
	val dependencies = projectPaths.map {
		project.dependencies.project(mapOf("path" to it, "configuration" to MAVEN_LOCAL_CONFIGURATION))
	}
	inputs.files(project.configurations.detachedConfiguration(*dependencies.toTypedArray()))
		.withPropertyName("mavenLocalPublications")
		.withPathSensitivity(PathSensitivity.NAME_ONLY)
}
