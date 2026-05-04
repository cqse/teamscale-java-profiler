import com.teamscale.TeamscaleUpload
import com.teamscale.extension.TeamscaleTaskExtension
import com.teamscale.reporting.testwise.TestwiseCoverageReport

plugins {
    id("java")
    id("com.teamscale") version "${System.getenv("AGENT_VERSION")}"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

teamscale {

    server {
        url = "http://localhost:${System.getenv("TEAMSCALE_PORT")}/"
        userName = "admin"
        userAccessToken = "mrllRGxXJrsyL0UID3gGcJnZuQT4EyWr"
        project = "cucumber-gradle"
    }

    commit {
        revision = "abc1234"
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.cucumber:cucumber-java:7.13.0")
    testImplementation("io.cucumber:cucumber-junit:7.13.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.13.0")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.hamcrest:hamcrest-library:1.3")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
}

tasks.register<Test>("tiaTests") {
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    configure<TeamscaleTaskExtension> {
        partition = "Cucumber Tests"
        collectTestwiseCoverage = true
        runImpacted = true
    }
    configure<JacocoTaskExtension> {
        includes = listOf("*hellocucumber*")
    }
    finalizedBy("tiaTestsReport")
}

tasks.register<TestwiseCoverageReport>("tiaTestsReport") {
    executionData(tasks.named("tiaTests"))
}

tasks.register<TeamscaleUpload>("teamscaleReportUpload") {
    partition = "Cucumber Tests"
    from(tasks.named("tiaTestsReport"))
}
