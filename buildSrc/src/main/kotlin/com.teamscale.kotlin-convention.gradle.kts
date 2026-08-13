import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm")
	id("com.teamscale.java-convention")
}

tasks.compileKotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_1_8
	}
}
