import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	kotlin("jvm")
	id("com.teamscale.java-convention")
}

tasks.compileKotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_11
		// The counterpart of javac's --release: jvmTarget only sets the bytecode version, this also rejects calls
		// to JDK APIs that are newer than the target. Derived from jvmTarget, so projects that raise it only set that.
		freeCompilerArgs.add(jvmTarget.map { "-Xjdk-release=${it.target}" })
	}
}
