import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	`java-library`
	com.teamscale.`kotlin-convention`
	com.teamscale.coverage
	com.teamscale.publish
}

tasks.compileJava {
	options.release = 17
}

tasks.withType<KotlinCompile> {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

publishAs {
	readableName = "Teamscale TIA JUnit Run Listeners"
	description = "JUnit 4 RunListener and JUnit 5 TestExecutionListener to record testwise coverage via the tia-client"
}

tasks.jar {
	manifest {
		attributes["Main-Class"] = "com.teamscale.tia.CommandLineInterface"
	}
}

dependencies {
	implementation(project(":tia-client"))
	implementation(platform(libs.junit.bom))
	api(libs.junit4)
	api(libs.junit.platform.launcher)
}
