plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
    id("io.github.sgtsilvio.gradle.oci") version("0.30.0")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()

        // The JDKs that the installer's runtime images are linked against. Declaring them as dependencies
        // instead of letting the jlink plugin download them keeps the download out of the configuration
        // phase and makes it cacheable, cf. installer/build.gradle.kts.
        ivy {
            val jdkVersion = providers.gradleProperty("runtimeJdkVersion").get()
            val repository = "temurin${jdkVersion.substringBefore(".")}-binaries"
            url = uri(
                "https://github.com/adoptium/$repository/releases/download/jdk-${jdkVersion.replace("+", "%2B")}/"
            )
            patternLayout {
                artifact("[artifact].[ext]")
            }
            // The release only contains the archives themselves, there is no module metadata to fetch.
            metadataSources {
                artifact()
            }
            content {
                includeGroup("net.adoptium.cdn")
            }
        }
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
    val appVersion = "38.0.0"
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
include(":sample-app")
include(":teamscale-maven-plugin")
include(":installer")

file("system-tests").listFiles { file -> !file.isHidden && file.isDirectory }?.forEach { folder ->
    include(":system-tests:${folder.name}")
}
