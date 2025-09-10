plugins {
	com.teamscale.`java-convention`
	application

	// we don't want to cause conflicts between our dependencies and the target application
	// since the agent will be loaded with the same class loader as the profiled application
	// so we use the shadow plugin to relocate our dependencies
	com.teamscale.`shadow-convention`
	com.teamscale.coverage
	com.teamscale.publish
	com.teamscale.`logger-patch`
}

evaluationDependsOn(":installer")

publishAs {
	artifactId = "teamscale-jacoco-agent"
	readableName = "Teamscale Java Profiler"
	description = "JVM profiler that simplifies various aspects around recording and uploading test coverage"
}

val appVersion = rootProject.extra["appVersion"].toString()
val jacocoVersion = libs.versions.jacoco.get()
val outputVersion = "$appVersion-jacoco-$jacocoVersion"

dependencies {
	implementation(platform(libs.jetty.bom))
	implementation(libs.jetty.server)
	implementation(libs.jetty.servlet)

	implementation(platform(libs.jersey.bom))
	implementation(libs.jersey.server)
	implementation(libs.jersey.containerServletCore)
	implementation(libs.jersey.containerJettyHttp)
	implementation(libs.jersey.mediaJsonJackson)
	implementation(libs.jersey.hk2)
	runtimeOnly(libs.jakarta.activation.api)

	implementation(project(":teamscale-client"))
	implementation(project(":report-generator"))

	implementation(libs.jacoco.core)
	implementation(libs.jacoco.report)
	implementation(libs.jacoco.agent) {
		artifact {
			classifier = "runtime"
		}
	}

	implementation(libs.logback.core)
	implementation(libs.logback.classic)

	implementation(libs.jcommander)
	implementation(libs.teamscaleLibCommons)

	implementation(libs.retrofit.core)

	implementation(libs.jackson.databind)

	testImplementation(project(":tia-client"))
	testImplementation(libs.retrofit.converter.jackson)
	testImplementation(libs.okhttp.mockwebserver)
}

application {
	mainClass = "com.teamscale.jacoco.agent.Main"
}

tasks.shadowJar {
	// since this is used as an agent, we want it to always have the same name
	// otherwise people have to adjust their -javaagent parameters after every
	// update
	archiveFileName = "teamscale-jacoco-agent.jar"

	manifest {
		attributes["Premain-Class"] = "com.teamscale.jacoco.agent.PreMain"
	}
}

tasks.startShadowScripts {
	applicationName = "convert"
}

distributions {
	named("shadow") {
		distributionBaseName = "teamscale-jacoco-agent"
		contents {
			from(project(":installer").tasks["jlink"]) {
				into("installer")
			}

			filesMatching("**/VERSION.txt") {
				filter {
					it.replace("%APP_VERSION_TOKEN_REPLACED_DURING_BUILD%", outputVersion)
				}
			}
		}
	}
}

tasks.shadowDistZip {
	archiveFileName = "teamscale-jacoco-agent.zip"
}
