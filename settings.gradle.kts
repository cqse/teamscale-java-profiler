pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.21"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
    id("io.github.sgtsilvio.gradle.oci") version("0.26.0")
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

include(":agent")
include(":report-generator")
include(":teamscale-gradle-plugin")
include(":teamscale-client")
include(":sample-app")
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
