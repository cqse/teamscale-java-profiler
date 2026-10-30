import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.internal.os.OperatingSystem

plugins {
	java
	id("org.gradle.test-retry")
}

group = "com.teamscale"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

tasks.compileJava {
	options.release = 11
}

tasks.compileTestJava {
	options.release = 21
}

// --release only restricts our own classes, so check that the dependencies run on the same Java version.
val verifyBytecodeVersion by tasks.registering(VerifyBytecodeVersion::class) {
	// File dependencies like the Gradle API of the Gradle plugin are provided by the environment we run in.
	classpath.from(configurations.runtimeClasspath.map { configuration ->
		configuration.incoming.artifactView {
			componentFilter { it is ModuleComponentIdentifier || it is ProjectComponentIdentifier }
		}.files
	})
	javaVersion = tasks.compileJava.flatMap { it.options.release }
}

tasks.check {
	dependsOn(verifyBytecodeVersion)
}

tasks.test {
	useJUnitPlatform {
		excludeEngines("teamscale-test-impacted")
	}
	testLogging.exceptionFormat = TestExceptionFormat.FULL
	if (OperatingSystem.current().isWindows) {
		maxHeapSize = "1g"
	}
	if (System.getenv("CI") == "true") {
		retry {
			maxRetries = 2
			maxFailures = 5
			failOnPassedAfterRetry = false
		}
	}
}

// Workaround until https://github.com/gradle/gradle/issues/15383 is fixed
val catalogs = extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
	implementation(platform(lib("jackson-bom")))
	testImplementation(platform(lib("junit-bom")))
	testImplementation(lib("junit-jupiter"))
	testImplementation(lib("assertj"))
	testImplementation(lib("mockito-core"))
	testImplementation(lib("mockito-junit"))
	testImplementation(lib("mockito-kotlin"))

	testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.processResources {
	val version = project.version
	inputs.property("version", version)
	filesMatching("**/*.properties", VersionReplacer(version.toString()))
}

class VersionReplacer(val version: String) : Action<FileCopyDetails> {
	override fun execute(t: FileCopyDetails) {
		t.filter {
			it.replace("%VERSION_TOKEN_REPLACED_DURING_BUILD%", version)
		}
	}
}
