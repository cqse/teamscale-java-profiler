import org.beryx.jlink.BaseTask
import org.beryx.jlink.CreateMergedModuleTask
import org.beryx.jlink.JlinkTask
import org.beryx.jlink.util.JdkUtil
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm")
	application
	com.teamscale.`java-convention`
	com.teamscale.coverage
	com.teamscale.`system-test-convention`
	alias(libs.plugins.jlink)
}

tasks.jar {
	manifest {
		attributes(
			"Main-Class" to "com.teamscale.profiler.installer.RootCommand",
		)
	}
}

// Workaround for https://youtrack.jetbrains.com/issue/KT-55389
tasks.compileJava {
	// Typed as FileCollection because the configuration cache cannot restore the captured value
	// into a field declared as the more specific SourceSetOutput.
	val mainSourceSetOutput: FileCollection = sourceSets.main.get().output
	options.compilerArgumentProviders.add(CommandLineArgumentProvider {
		listOf(
			"--patch-module",
			"com.teamscale.profiler.installer=${mainSourceSetOutput.asPath}"
		)
	})
}

tasks.withType<JavaCompile> {
	options.release = 21
}

// The jlink tasks expose `targetPlatforms` as an @Input. `jdkDownload` below stores the JDK home as a
// lazily evaluated Groovy closure in there. The configuration cache replaces that closure's owner with a
// non-serializable BrokenObject, so fingerprinting the input fails once the task graph is restored:
//   java.io.NotSerializableException: ...ClosureCodec$BrokenObject
// Removing these opt-outs therefore requires provisioning the target JDKs ourselves and passing plain
// paths to `setJdkHome`. Until then, any build that runs jlink falls back to no configuration cache.
tasks.withType<BaseTask> {
	notCompatibleWithConfigurationCache("jdkDownload stores a Groovy closure in the targetPlatforms input")
}

tasks.withType<CreateMergedModuleTask> {
	notCompatibleWithConfigurationCache("jdkDownload stores a Groovy closure in the targetPlatforms input")
}

tasks.withType<KotlinCompile> {
	compilerOptions.jvmTarget = JvmTarget.JVM_21
}

// Shares the jlink runtime image with the :agent project, which ships it in its distribution.
configurations.consumable(JLINK_IMAGE_CONFIGURATION)
artifacts.add(JLINK_IMAGE_CONFIGURATION, tasks.named<JlinkTask>("jlink").map { it.imageDir }) {
	type = "directory"
	builtBy(tasks.named("jlink"))
}

application {
	applicationName = "installer"
	mainClass = "com.teamscale.profiler.installer.RootCommand"
	mainModule = "com.teamscale.profiler.installer"
	applicationDefaultJvmArgs = listOf(
		// Ensure that no stack traces are lost.
		// See <https://stackoverflow.com/questions/2411487/nullpointerexception-in-java-with-no-stacktrace>
		"-XX:-OmitStackTraceInFastThrow",
	)
}

val ADOPTIUM_BINARY_REPOSITORY = "https://api.adoptium.net/v3/binary"
val RUNTIME_JDK_VERSION = "21.0.6+7"
jlink {
	forceMerge("kotlin")
	options = listOf(
		"--no-header-files",
		"--no-man-pages",
		"--dedup-legal-notices", "error-if-not-same-content"
	)
	launcher {
		name = "installer"
	}

	targetPlatform("linux-x86_64") {
		setJdkHome(
			jdkDownload(
				"$ADOPTIUM_BINARY_REPOSITORY/version/jdk-${RUNTIME_JDK_VERSION}/linux/x64/jdk/hotspot/normal/eclipse",
				closureOf<JdkUtil.JdkDownloadOptions> {
					archiveExtension = "tar.gz"
				})
		)
	}
	targetPlatform("windows-x86_64") {
		setJdkHome(
			jdkDownload(
				"$ADOPTIUM_BINARY_REPOSITORY/version/jdk-${RUNTIME_JDK_VERSION}/windows/x64/jdk/hotspot/normal/eclipse",
				closureOf<JdkUtil.JdkDownloadOptions> {
					archiveExtension = "zip"
				})
		)
	}
}

dependencies {
	implementation(libs.okhttp.core)
	implementation(libs.commonsLang)
	implementation(libs.commonsIo)
	implementation(libs.picocli.core)
	annotationProcessor(libs.picocli.codegen)
	implementation(libs.jna.platform)

	testImplementation(libs.spark)
	testImplementation(project(":common-system-test"))
}
