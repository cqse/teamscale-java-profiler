import org.beryx.jlink.BaseTask
import org.beryx.jlink.CreateMergedModuleTask
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

tasks.withType<JavaCompile> {
	options.release = 21
}

tasks.withType<BaseTask> {
	notCompatibleWithConfigurationCache("https://github.com/beryx/badass-jlink-plugin/issues/304")
}

tasks.withType<CreateMergedModuleTask> {
	notCompatibleWithConfigurationCache("https://github.com/beryx/badass-jlink-plugin/issues/304")
}

tasks.withType<KotlinCompile> {
	compilerOptions.jvmTarget = JvmTarget.JVM_21
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
		"--compress", "2",
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
	implementation(libs.picocli.core)
	annotationProcessor(libs.picocli.codegen)
	implementation(libs.jna.platform)

	testImplementation(libs.spark)
	testImplementation(project(":common-system-test"))
}
