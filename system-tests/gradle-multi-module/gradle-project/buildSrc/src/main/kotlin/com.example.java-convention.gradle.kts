import com.teamscale.aggregation.TestSuiteCompatibilityUtil
import com.teamscale.extension.TeamscaleTaskExtension

plugins {
	java
	id("com.teamscale")
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

testing {
	suites {
		val test by getting(JvmTestSuite::class) {
			useJUnitJupiter()
		}

		register<JvmTestSuite>("integrationTest") {
			dependencies {
				implementation(project())
			}
		}
	}
}

dependencies {
	testImplementation(platform("org.junit:junit-bom:5.12.1"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

teamscale {
	server {
		url = "http://localhost:${System.getenv("TEAMSCALE_PORT")}/"
		userName = "admin"
		userAccessToken = "mrllRGxXJrsyL0UID3gGcJnZuQT4EyWr"
		project = "submodules"
	}

	commit {
		branchName = "branch1"
		timestamp = "123456"
	}
}

val test by testing.suites.existing(JvmTestSuite::class)

val unitTest by tasks.registering(Test::class) {
	useJUnitPlatform()
	configure<JacocoTaskExtension> {
		includes = listOf("com.example.*")
	}
	testClassesDirs = files(test.map { it.sources.output.classesDirs })
	classpath = files(test.map { it.sources.runtimeClasspath })
}

val systemTest by tasks.registering(Test::class) {
	useJUnitPlatform()
	configure<JacocoTaskExtension> {
		includes = listOf("com.example.*")
	}
	configure<TeamscaleTaskExtension> {
		collectTestwiseCoverage = true
		runImpacted = System.getProperty("impacted") != null
		debugLogging = System.getProperty("debugLogging")?.toBoolean() ?: false
		partition = "System Tests"
	}
	testClassesDirs = files(test.map { it.sources.output.classesDirs })
	classpath = files(test.map { it.sources.runtimeClasspath })
}

TestSuiteCompatibilityUtil.exposeTestForAggregation(unitTest, SuiteNames.UNIT_TEST)
TestSuiteCompatibilityUtil.exposeTestForAggregation(systemTest, SuiteNames.SYSTEM_TEST)
