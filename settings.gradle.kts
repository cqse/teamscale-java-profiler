pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.10"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
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
