import io.github.sgtsilvio.gradle.oci.dsl.OciImageDefinition

plugins {
	com.teamscale.`kotlin-convention`
	com.teamscale.`java-convention`
	application

	// we don't want to cause conflicts between the classes we ship and the target application
	// since the agent will be loaded with the same class loader as the profiled application
	// so we use the shadow plugin to relocate our dependencies and our own classes
	com.teamscale.`shadow-convention`
	com.teamscale.coverage
	com.teamscale.publish
	com.teamscale.`logger-patch`
	alias(libs.plugins.oci)
}

// The jlink runtime image of the installer, which the distribution below ships alongside the agent.
val installerImageDependency = configurations.dependencyScope("installerImage")
val installerImage = configurations.resolvable("installerImagePath") {
	extendsFrom(installerImageDependency.get())
}

dependencies {
	installerImageDependency(project(":installer", JLINK_IMAGE_CONFIGURATION))
}

publishAs {
	artifactId = "teamscale-jacoco-agent"
	readableName = "Teamscale Java Profiler"
	description = "JVM profiler that simplifies various aspects around recording and uploading test coverage"
}

val appVersion = extra["appVersion"].toString()
val jacocoVersion = libs.versions.jacoco.get()
val outputVersion = "$appVersion-jacoco-$jacocoVersion"

dependencies {
	implementation(platform(libs.jersey.bom))
	implementation(libs.jersey.server)
	implementation(libs.jersey.containerJdkHttp)
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

	implementation(libs.retrofit.core)

	implementation(libs.jackson.databind)
	implementation(libs.jetbrains.annotations)
	implementation(libs.coroutines.core)

	testImplementation(project(":tia-client"))
	testImplementation(libs.retrofit.converter.jackson)
	testImplementation(libs.okhttp.mockwebserver)
	testImplementation(libs.mockito.kotlin)
}

application {
	mainClass = "$AGENT_PACKAGE.Main"
}

tasks.shadowJar {
	// since this is used as an agent, we want it to always have the same name
	// otherwise people have to adjust their -javaagent parameters after every
	// update
	archiveFileName = "teamscale-jacoco-agent.jar"

	// The shadow plugin's auto relocation only covers the dependencies, so the agent's own classes are
	// relocated explicitly. The entry points below have to name them by their relocated names, and so do the
	// logback configuration files, cf. ShadowedPackages.kt.
	if (usesShadowedPackages.get()) {
		relocate(AGENT_PACKAGE, "$SHADOW_PACKAGE_PREFIX.$AGENT_PACKAGE")
	}

	manifest {
		attributes["Premain-Class"] = shadowed("$AGENT_PACKAGE.PreMain")
		attributes["Main-Class"] = shadowed("$AGENT_PACKAGE.Main")
	}
}

tasks.startShadowScripts {
	applicationName = "convert"
	mainClass = shadowed("$AGENT_PACKAGE.Main")
}

// Shares the shaded agent jar with the projects that attach the profiler to a JVM, cf. the
// com.teamscale.agent-jar convention plugin.
configurations.consumable(AGENT_JAR_CONFIGURATION)
artifacts.add(AGENT_JAR_CONFIGURATION, tasks.shadowJar)

distributions {
	named("shadow") {
		distributionBaseName = "teamscale-jacoco-agent"
		contents {
			from(installerImage.get()) {
				into("installer")
			}

			// Captured in a local so the copy action does not reference the build script itself,
			// which cannot be stored in the configuration cache.
			val distributionVersion = outputVersion
			filesMatching("**/VERSION.txt") {
				filter {
					it.replace("%APP_VERSION_TOKEN_REPLACED_DURING_BUILD%", distributionVersion)
				}
			}
		}
	}
}

// The logging templates in src/dist are checked in without the shadow prefix so they also work when
// relocation is disabled. Add the prefix while packaging them, since the distribution ships the shaded agent.
listOf(tasks.shadowDistZip, tasks.shadowDistTar, tasks.installShadowDist).forEach { task ->
	task { shadowLoggingPackages(usesShadowedPackages) }
}

verifyShadowedLoggingConfigs(tasks.shadowJar, tasks.shadowDistZip)

tasks.shadowDistZip {
	archiveFileName = "teamscale-jacoco-agent.zip"
	useFileSystemPermissions()
}

oci {
	val ociImageTag = providers.gradleProperty("ociImageTag").orElse(appVersion)
	val configureImage: Action<OciImageDefinition> = Action {
		imageTag = ociImageTag
		allPlatforms {
			dependencies {
				runtime("library:alpine:latest")
			}
			config {
				entryPoint = listOf("/entrypoint.sh")
			}
			layer("agent") {
				contents {
					into("agent") {
						from(tasks.shadowJar)
					}
				}
			}
			layer("entrypoint") {
				contents {
					from("src/docker/entrypoint.sh") {
						filePermissions = "755".toInt(8)
					}
				}
			}
		}
		specificPlatform(platform("linux", "amd64"))
		specificPlatform(platform("linux", "arm64"))
	}

	imageDefinitions.register("main") {
		imageName = "cqse/teamscale-java-profiler"
		configureImage.execute(this)
	}
	imageDefinitions.register("legacy") {
		imageName = "cqse/teamscale-jacoco-agent"
		configureImage.execute(this)
	}
}
