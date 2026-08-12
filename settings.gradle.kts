plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
    id("io.github.sgtsilvio.gradle.oci") version("0.30.0")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    oci {
        registries {
            dockerHub {
                optionalCredentials()
            }
        }
    }
}

// Coordinates are configured from the settings file rather than via allprojects {} in the root build
// file, because cross-configuring projects from the root is incompatible with project isolation.
// Everything the action below reads must be a local, since values it captures are isolated from the
// settings script and script object references cannot be serialized.
run {
    /** The version of the profiler. Released builds use it as is, all others get a snapshot suffix. */
    val appVersion = "37.0.2"
    val isTaggedRelease = providers.environmentVariable("GITHUB_REF").map { it.contains("/tags/") }
    val projectVersion = appVersion + if (isTaggedRelease.getOrElse(false)) "" else "-SNAPSHOT"

    gradle.lifecycle.beforeProject {
        group = "com.teamscale"
        version = projectVersion
        // The plain version without the snapshot suffix, e.g. for naming the distribution.
        extra.set("appVersion", appVersion)
    }
}

include(":agent")
include(":report-generator")
include(":teamscale-gradle-plugin")
include(":teamscale-client")
include(":impacted-test-engine")
include(":tia-client")
include(":tia-runlisteners")
include(":common-system-test")
include(":sample-debugging-app")
include(":teamscale-maven-plugin")
include(":installer")

file("system-tests").listFiles { file -> !file.isHidden && file.isDirectory }?.forEach { folder ->
    include(":system-tests:${folder.name}")
}
