import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	`java-library`
	com.teamscale.`kotlin-convention`
	com.teamscale.coverage
	com.teamscale.`shadow-convention`
	com.teamscale.publish
}

publishAs {
	readableName = "Impacted Test Engine"
	description = "A JUnit 5 engine that handles retrieving impacted tests from Teamscale and organizes their execution"
}

tasks.compileJava {
	options.release = 17
}

tasks.compileKotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_17
	}
}

dependencies {
	implementation(platform(libs.junit.bom))
	implementation(project(":teamscale-client"))
	implementation(project(":report-generator"))
	implementation(project(":tia-client"))

	compileOnly(libs.junit.platform.engine)
	compileOnly(libs.junit.platform.commons)
	testImplementation(libs.junit.platform.engine)
	testImplementation(libs.junit.jupiter.params)
	testImplementation(libs.mockito.kotlin)
}
