plugins {
    com.teamscale.`kotlin-convention`
    com.teamscale.`system-test-convention`
    com.teamscale.`spawned-jvm-coverage`
}

dependencies {
    spawnedJvmCode(project(":teamscale-maven-plugin"))
    spawnedJvmCode(project(":report-generator"))
    spawnedJvmCode(project(":teamscale-client"))
}

tasks.test {
    // install dependencies needed by the Maven test project
    dependsOn(":publishToMavenLocal")
}
