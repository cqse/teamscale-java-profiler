We use [semantic versioning](http://semver.org/):

- MAJOR version when you make incompatible API changes,
- MINOR version when you add functionality in a backwards-compatible manner, and
- PATCH version when you make backwards compatible bug fixes.

# Next version
- _teamscale-client_: User-Agent header now includes the specific component performing the request (e.g., "Teamscale Gradle Plugin", "Teamscale Maven Plugin") and version number
- _maven-plugin_: Added maven properties for `runImpacted` and `runAllTests` configuration parameters
- _maven-plugin_: The warning "Both baselineRevision and baselineCommit are set but only one of them is needed" was displayed incorrectly
- [fix] _maven-plugin_: Coverage upload used "Now" instead of auto-resolved Git revision when no commit/revision was configured

# 36.2.0
- _agent_: improved logging when multiple agents are attached
- [feature] _teamscale-gradle-plugin_: Added `debugLogging` option to enable debug logging for the java profiler and the impacted test engine
- [feature] _teamscale-maven-plugin_: Enhanced `debugLogging` option to also enable debug logging for the impacted test engine

# 36.1.0
- [feature] _impacted-test-engine_: Added support for JUnit 5.13 and 6.0
- [feature] _agent_: Added support for Java 25 and experimental support for Java 26

# 36.0.0
- [breaking fix] _teamscale-gradle-plugin_: Adjusted to breaking API changes in Gradle 9.0
- [feature] Reduced dependencies and hence the size of all artifacts
- [fix] _installer_: `InaccessibleObjectException` at startup

# 35.2.2
- [fix] _teamscale-gradle-plugin_: TeamscaleUpload task did not properly validate `revision` argument
- [fix] _teamscale-gradle-plugin_: Removed usage of deprecated `movetolastcommit` option

# 35.2.1
- [fix] _agent_: Bad request error when starting a test run in some cases

# 35.2.0
- [feature] _agent_: Access token can be passed via environment variable `TEAMSCALE_ACCESS_TOKEN`

# 35.1.0
- [feature] _agent_: Experimental support for Java 25 class files
- [fix] _agent_: Reported wrong version number in docker container
- [fix] _agent_: Erroneous log messages were printed about unmatched probes
- [fix] _agent_: Couldn't receive impacted tests when revision is provided instead of commit

# 35.0.3
- [fix] _teamscale-gradle-plugin_: `IllegalArgumentException`: Can't execute request without discovering it first

# 35.0.2
- [fix] _teamscale-gradle-plugin_: `IllegalArgumentException`: End commit must be set via endCommit or endRevision
- [fix] _teamscale-gradle-plugin_: `TestwiseCoverageReport#executionData` Used test classpath as default which could lead to OOM

# 35.0.1
- [fix] _teamscale-gradle-plugin_: Running tests via IntelliJ did no longer work 

# 35.0.0
- [breaking] Dropped support for Teamscale 2024.6 and older
- [feature] _teamscale-gradle-plugin_: Added support for generating Compact Coverage reports from JaCoCo execution data
- [feature] _teamscale-gradle-plugin_: Added support for aggregating Testwise Coverage across projects
- [feature] _teamscale-gradle-plugin_: Added support for aggregating Compact Coverage across projects
- [feature] _teamscale-gradle-plugin_: Made compatible with Gradle 9.0
- [breaking] _teamscale-gradle-plugin_: Support for Gradle configuration cache (see `teamscale-gradle-plugin/MIGRATION_35.md` for how to update)
- [breaking] _teamscale-gradle-plugin_: Bumped up minimum required Gradle version to 8.10
- [breaking] _teamscale-maven-plugin_: Renamed `testwise-coverage-converter` goal to `testwise-coverage-report`.
- [breaking] _teamscale-maven-plugin_: Removed `outputFolder` option from `testwise-coverage-report`
- [breaking] _teamscale-maven-plugin_: Removed `tiaMode` option from the plugin. `exec-file` is the new default. All setups that did not use `exec-file` before now need to add the `testwise-coverage-report` and `upload-coverage` goals to their builds. They should be configured only for a single coverage aggregation module.
- [breaking] _teamscale-maven-plugin_: Removed `testwisePartition` option from the plugin. Uploads now use the `unitTestPartition` and `integrationTestPartition` respectively.
- [breaking] _teamscale-maven-plugin_: Replaced `partition` option from `prepare-tia-unit-test` and `prepare-tia-integration-test` in favor of `unitTestPartition` and `integrationTestPartition` options to allow consistently querying and upload impacted tests.

# 34.2.6
- [fix] _agent_: Make Teamscale connection more robust in K8s environment
- 
# 34.2.5
- [fix] Improved error messages when configuration can't be read from Teamscale 

# 34.2.4
- [fix] Convert tool could fail with `IOException` when providing nested jars

# 34.2.3
- [fix] _agent_: Delayed initialization of the project via a git properties file did not work.
- [fix] _agent_: Only logs with a stack trace (errors) were sent to Teamscale.
- [fix] _agent_: Fix wrong class description of GitMultiProjectPropertiesLocator in logs.
- [fix] _agent_: Specifying `config-id` before Teamscale credentials led to option parsing errors. 

# 34.2.2
- [fix] _teamscale-gradle-plugin_: 401 Unauthorized error when trying to upload reports to Teamscale 

# 34.2.1
- [fix] _agent_: Warning about multiple agents being present is misleading
- [fix] _agent_: CVE-2024-26308

# 34.2.0
- [fix] _agent_: `search-git-properties-recursively` was not considered when jar was given via `artifactory-git-properties-jar`
- [deprecation] _agent_: `artifactory-git-properties-jar` is deprecated now. Replace the option with `git-properties-jar`. The old option still works but is an alias now for `git-properties-jar`.
- [feature] _agent_: Added `git-properties-commit-date-format` (replaces `artifactory-git-properties-commit-date-format`), which now also can be used in non-artifactory cases.
- [fix] _agent_: `search-git-properties-recursively` did only consider nested jar files, but no `war`, `ear`, `aar`

# 34.1.1
- [fix] _agent_: Loading a profiler configuration from Teamscale was not possible if the potentially necessary proxy settings were not set yet.

# 34.1.0
- [feature] _agent_: New options `proxy-http(s)-host`/`-port`/`-user`/`-password` allow user to specify teamscale-specific proxy settings.
- [fix] _teamscale-maven-plugin_: NPE when no commit, nor revision was configured
- [fix] _teamscale-maven-plugin_: Fixed commit was ignored in commit resolution

# 34.0.2
- [feature] _agent_: Prevent uploading coverage to the same project + revision or branch@timestamp when doing multi project upload via git.properties
- [fix] _impacted-test-engine_: Remove duplicated slashes in uniform paths and cluster ids for Cucumber Tests
- [fix] _impacted-test-engine_: Ensure that the config file specified via `java.util.logging.config.file` is used when specified

# 34.0.1
- [fix] _agent_: Error was reported when the system under test used logback
- [fix] _teamscale-gradle-plugin_: Improved error message regarding minimum supported Gradle version

# 34.0.0
- [breaking] _teamscale-client_, _teamscale-maven-plugin_, _teamscale-gradle-plugin_: Support using revision for fetching impacted tests for both the baseline and the endCommit. This change is breaking because **this changes the minimum required Teamscale Server version from 8.0.0 to 9.4.0** and because the Teamscale client API was changed. For the Maven and Gradle plugins, this change is non-breaking.
- [breaking] _teamscale-client_, _teamscale-maven-plugin_, _teamscale-gradle-plugin_, _teamscale-jacoco-agent_: New parameter: `teamscale-repository` (agent), `repository` (Teamscale Client, Gradle and Maven Plugins). This parameter can be used to specify in which repository Teamscale should look up revisions in case there is more than one repository in a given Teamscale project. If no repository is given, Teamscale will look in all connected repositories. This change is breaking because the Teamscale client API was changed. For the JaCoCo agent and the Maven and Gradle plugins, this change is non-breaking.
- [breaking] _tia-maven-plugin_: The `endCommit` option was renamed to `commit` in order to make it more understandable when using the upload task
- [breaking] _agent_: removed currently unused `test-env` option. Use one of the other testwise coverage modes instead, please.

# 33.3.3
- [fix] _agent_: Coverage for Kotlin code did sometimes produce incorrect line numbers

# 33.3.2
- Re-release of 33.3.1

# 33.3.1
- [fix] _agent_: Dump coverage when profiler settings are changed via the API.
- [fix] _agent_: NullPointerException when agent jar was located in the file system root.
- [fix] _installer_: Did not support SSL certificates that use elliptic curve ciphers.

# 33.3.0
- [feature] _agent_: Support overwriting the git commit inside `git.properties` files with `teamscale.commit.branch` and `teamscale.commit.time` (see [docs](agent/README.md#overwriting-the-git-commit-inside-gitproperties-files-with-teamscalecommitbranch-and-teamscalecommittime))
- [fix] _agent_: New default date format of the [git-commit-id-maven-plugin](https://github.com/git-commit-id/git-commit-id-maven-plugin) could not be parsed (see [GitHub Issue](https://github.com/cqse/teamscale-jacoco-agent/issues/464))
- [fix] _Gradle Plugin_: Testwise Coverage reports did override each other when multiple modules upload to the same partition

# 33.2.0
- [feature] _agent_: Add support for git.properties in Spring Boot 3.2
- [feature] _agent_: Read configuration file path from `TEAMSCALE_JAVA_PROFILER_CONFIG_FILE` environment variable
- [feature] add installer for Windows
- [feature] _Docker_: agent copies itself to `/transfer` if this is mounted into the container
- [fix] _agent_: Disable warning about proxy port not being correct when no proxy port was set at all
- [fix] _agent_: `GET /commit` and `GET /revision` endpoints did return 500 error
- [feature] _agent_: Added stable support for Java 22 and experimental support for Java 23

# 33.1.2
- [fix] _teamscale-maven-plugin_: Revision and end commit could not be set via command line (user property)

# 33.1.1
- [fix] _agent_: NPE during agent startup probably due to missing read permissions in a shared folder

# 33.1.0
- [feature] _teamscale-maven-plugin_: Add new execution goal to batch convert .exec files into testwise coverage report.
- [feature] _agent_: Extended list of packages excluded by default
- [maintenance] _agent_: Removed HTTP upload (i.e. `upload-url` option)
- [feature] _agent_: New option `proxy-password-file` allows users to provide a file containing a proxy password

# 33.0.0
- [feature] add installer for system-wide installation (see agent/MIGRATION.md for a migration guide)
- [feature] allow specifying configuration ID from Teamscale via environment variable `TEAMSCALE_JAVA_PROFILER_CONFIG_ID`
- [breaking change] default log and coverage file directory changed to `/tmp` which works in more situations
- [feature] _agent_: Added `config-id` option to allow retrieving the agent configuration from Teamscale. 

# 32.6.3
- Re-Release 32.6.2

# 32.6.2
- Re-Release 32.6.1
- [fix] _teamscale-maven-plugin_ Test names containing slashes could not be uploaded
- [fix] _tia-client_ Impacted test retrieval failed due to JSON parsing error

# 32.6.1
- [fix] _teamscale-maven-plugin_ Test names containing slashes could not be uploaded
- [fix] _tia-client_ Impacted test retrieval failed due to JSON parsing error

# 32.6.0
- [feature] Profiler logs its version on startup
- [fix] _agent_: The agent crashed while starting on some machines with "cannot construct instances of" errors

# 32.5.0
- [feature] _teamscale-maven-plugin_: Added the `upload-coverage` goal which automatically uploads JaCoCo reports to Teamscale.

# 32.4.2
- [fix] _agent_: The agent crashed while starting on some machines with "cannot construct instances of" errors 

# 32.4.1
- [fix] _agent_: The agent crashed while starting on some machines with "cannot construct instances of" errors 

# 32.4.0
- [feature] _agent_: Added stable support for Java 21 and experimental support for Java 22
- [fix] _agent_: Produce more helpful error results in case a service call to Teamscale fails while handling an HTTP call 
- [fix] _teamscale-maven-plugin_: The same uniform paths were extracted for tests that have the same name in a Cucumber feature file

# 32.3.1
- Re-release because of failing publishing process
- - _impacted-test-engine_: Failed requests to Teamscale did result in unreadable error message
  - [feature] _teamscale-maven-plugin_: Automatically find open port for coverage collection.
  - [fix] _teamscale-maven-plugin_: Same uniform path was extracted for Cucumber scenario outlines which do not have their parameters in the title

# 32.3.0
- _impacted-test-engine_: Failed requests to Teamscale did result in unreadable error message
- [feature] _teamscale-maven-plugin_: Automatically find open port for coverage collection.
- [fix] _teamscale-maven-plugin_: Same uniform path was extracted for Cucumber scenario outlines which do not have their parameters in the title

# 32.2.0
- [feature] _agent_: Previously unsuccessful coverage uploads are now automatically retried upon agent restart
- [fix] _teamscale-maven-plugin_: Fix uniform path and cluster ID extraction for cucumber pre 7.11.2

# 32.1.0
- [feature] _teamscale-maven-plugin_: Support for cucumber tests
- [feature] _teamscale-maven-plugin_, _impacted-test-engine_: Support for junit platform suite tests

# 32.0.0
- [breaking] _teamscale-gradle-plugin_: Removed usage of deprecated Gradle APIs
  - `teamscale.agent.destinationProperty` type has changed from `Property<File>` to `DirectoryProperty`
  - `teamscale.agent.setDestination(destination)` parameter type has changed from `Property<File>` to `Provider<Directory>`
- [fix] _agent_: Discovery of unsupported class file versions will no longer lead to a crash, but will be logged instead
- [breaking] _tia-runlisteners_: Requires JUnit 5.10.0 now
- [feature] _agent_: Added support for Java 19, 20 and experimental support for 21
- [fix] _agent_: Attaching the agent in testwise mode to a JBoss Wildfly server caused a crash

# 31.0.0
- [breaking change] Replaced `teamscale-git-properties-jar` with `git-properties-jar`. Jars/Wars/Ears/Aars provided with this option will now also be searched recursively for git.properties files except you set `search-git-properties-recursively=false`.
- [feature] support `full` mode of the Maven git-commit-id plugin.
- [fix] Providing multiple include pattern in the maven plugin resulted in no coverage being collected

# 30.1.1
- [fix] _teamscale-gradle-plugin_: Warnings were logged during test execution (WARNING: JAXBContext implementation could not be found. WADL feature is disabled., WARNING: A class javax.activation.DataSource for a default provider)

# 30.1.0
- [feature] The option `tiamode` has now an additional choice `disk` to dump testwise coverage as JSON to the output folder.
- [feature] _teamscale-maven-plugin_: The configuration options `runAllTests`, `runImpacted`, and `tiamode` are now available
- [fix] _impacted-test-engine_: Mixed test results for dynamically generated tests were incorrectly summarized
- [fix] The option `ignore-uncovered-classes` did not filter out empty interface nodes from the XML report 
- [fix] _teamscale-gradle-plugin_: The plugin failed to log the `No class files found in the given directories!` error message

# 30.0.2
- [fix] _teamscale-gradle-plugin_: Reports uploaded by `teamscaleReportUpload` ended up in wrong partition
- [fix] _impacted-test-engine_: Failure when no tests were impacted

# 30.0.1
- [fix] _report-generator_: Fixed Gradle module metadata which resulted in `Could not find org.jacoco.agent-0.8.8-runtime`

# 30.0.0
- [breaking change] _teamscale-maven-plugin_: Made plugin compatible with surefire 3.0.0. Replace the `teamscale-surefire-provider` dependency with `impacted-test-engine` in your pom.xml files.
- [feature] _teamscale-gradle-plugin_, _teamscale-maven-plugin_: Added ability to pass excluded test engines to impacted test engine
- [fix] _teamscale-maven-plugin_: Testwise coverage uploads were performed per test cluster instead of one upload after all tests
- [fix] _teamscale-gradle-plugin_, _teamscale-maven-plugin_: Impacted tests were requested once for each test engine
- [fix] _teamscale-gradle-plugin_, _teamscale-maven-plugin_: Execution of impacted tests failed when test names were unstable i.e. parameterized tests with parameters that are not serializeable

# 29.1.3
- [fix] http control server was not correctly shut down after the tests ended

# 29.1.2
- [fix] _teamscale-maven-plugin_: Partition was not correctly provided to the impacted test engine
- [fix] _tia-client_: Semicolons in test names were not correctly sent to the Teamscale JaCoCo Agent

# 29.1.1
- [fix] _teamscale-gradle-plugin_, _teamscale-maven-plugin_, _teamscale-jacoco-agent_: Tooling did not provide a way to set the partial flag

# 29.1.0
- [feature] _teamscale-gradle-plugin_: Allow parallel test execution with testwise coverage collection
- [fix] _teamscale-gradle-plugin_: Verify that maxParallelForks is 1
- [fix] _impacted-test-engine_: Provide sane fallback for non-supported test engines

# 29.0.0
- [fix] Fixed the prefix extraction pattern and the partition pattern for Artifactory in the agent's documentation
- [fix/breaking change] _teamscale-client_, _teamscale-gradle-plugin_, _teamscale-maven-plugin_, _teamscale-jacoco-agent_: Teamscale 8.0 introduced the concept of execution units. To distinguish them from normal, singular test executions, the `-test-exection-` and `-execution-unit-` uniform path prefixes were introduced. This broke gradle and maven test runner plugins because the actual test paths and Teamscale's uniform paths did not match anymore. To prevent this, `testName` is now exposed by the teamscale client and used by the plugins which corresponds to the uniform path without the prefixes. **If you're using the client, or listen to the `/testrun/start` API from the Teamscale JaCoCo agent, this is a breaking change.** You now need to use `testName` in your runner, not the `uniformPath`.

# 28.0.0
- [breaking change] removed support for JavaWS
- [fix] Fixed git.properties detection when directories are on the classpath

# 27.0.2
- [fix] Updated dependencies (Fixes #225, CVE-2022-42889)

# 27.0.1
- [fix] _teamscale-gradle-plugin_: Property 'outputLocation' is declared as an output property of Report xml (type TaskGeneratedSingleFileReport) but does not have a task associated with it.

# 27.0.0

- [fix] _teamscale-gradle-plugin_: Failed to upload JUnit reports
- [breaking change] _teamscale-gradle-plugin_: Setting the destination within test.teamscale.reports.jacoco/junit transparently sets the report destination in the corresponding jacoco/junit plugins
- [breaking change] _teamscale-gradle-plugin_: TestwiseCoverageReportTask no longer clears the parent folder of the written testwise coverage report

# 26.0.2

- [fix] Testwise coverage conversion was slow when many .exec files are included.

# 26.0.1

- [fix] The agent could not be started with the `debug=true` command line option

# 26.0.0

- [fix] _impacted-test-engine_: Handle tests with trailing whitespaces correctly
- [breaking] _impacted-test-engine_: Now requires Teamscale 8.0 as minimum version

# 25.0.0

 - [breaking] Find git.properties files recursively in folders, all types of archive files (jar, war, ear, aar, ...) and arbitrary depth. This was only possible for nested jar and war files and up to nesting depth 1. If you'd like to disable recursive search, e.g. due to performance issues, please use `search-git-properties-recursively=false`.  
  Note: This is not actually a breaking change but if you profile large projects and don't use the multi-project upload, you might want to disable recursive search.

# 24.1.1

- [fix] Maven plugin for TIA: sometimes the agent was not attached to a Spring Boot application during integration tests

# 24.1.0

- [feature] The Maven plugin now writes temporary reports to `target/tia/reports` in case of upload errors so they can be inspected manually.
- [feature] The agent logs a warning when multiple java agents are used and recommends registering the Teamscale JaCoCo Agent first.

# 24.0.1

- [fix] fix POM metadata of Maven plugin to allow publishing it

# 24.0.0

- [feature] add Maven plugin for TIA
- [feature] added official support for Java 17 and 18 and experimental support for Java 19.
- [feature] New command line option: `debug`. Simplifies debugging by avoiding the process of providing an XML logging
  configuration file. In debug mode logs are written to console and a configurable directory. For more details, see
  [here](agent/README.md#general-options).
- [breaking change] Artifactory uploader: use of new default upload schema for easy integration with Teamscale. To keep the old behavior add the `artifactory-legacy-path=true` option. For more details see 
  [here](agent/README.md#options-for-the-artifactory-upload).

# 23.1.1

- [fix] _teamscale-gradle-plugin_: The `TestImpacted` task did not execute any tests
  when `collectTestwiseCoverage = false` was set

# 23.1.0

- [feature] add support for git properties files in jar files nested in jar or war files

# 23.0.0

- [feature] The agent logs now an error with further information when dumped coverage is empty
- [feature] add JUnit 5 TestExecutionListener for testwise coverage recording
- [breaking change] JUnit 4 RunListener renamed to com.teamscale.tia.runlistener.JUnit4TestwiseCoverageRunListener and
  published via new artifact com.teamscale:tia-runlisteners
- [fix] Not specifying certain options for the JUnit 5 impacted test engine caused an NPE
- [breaking change] _teamscale-gradle-plugin_: Removed automatic registration of mavenCentral repository (Makes the
  plugin compatible with `dependencyResolutionManagement`)
- [breaking change] _teamscale-gradle-plugin_: The `TestImpacted` task now collects testwise coverage by default even
  without the `--impacted` option being set. Can be disabled programmatically with `collectTestwiseCoverage = false`.
- [feature] The Teamscale server configuration for the `TestImpacted` task is only needed when `--impacted` is used.

# 22.2.0

- [feature] Add REST endpoint to change revision and commit while agent is running.

# 22.1.2

- [fix] Restored upload to DockerHub
- [fix] The agent now uses HTTPS when port 443 is specified in a URL but no scheme is provided

# 22.1.1

- [fix] The Docker image is available again for the latest version of the JaCoCo agent

# 22.1.0

- [feature] The agent now follows HTTP 307/308 redirects for Teamscale uploads
- [fix] Updated to latest Log4J 2.17.1

# 22.0.0

- [feature] Option `teamscale-revision-manifest-jar` which can be used to read the git commit revision from
  the `META-INF/MANIFEST.MF` in the Jar file. The field must be named `Revision` and must be provided as a main
  attribute. Alternatively the field can be called `Git_Commit` and must be an attribute of an entry called `Git`.
- [breaking change] SSL validation (option `validate-ssl`) is now enabled by default

# 21.9.0

- [feature] _teamscale-gradle-plugin_: Improve compatibility with Groovy DSL
- [feature] _teamscale-client_: Added support for jQAssistant report format
- [feature] _teamscale-client_: Added upload endpoint that accepts a string instead of EReportFormat

# 21.8.0

- [feature] Option `artifactory-api-key` that can be used instead of `artifactory-user` and `artifactory-password` as
  authentication mechanism for artifactory.
- [feature] _tia-client_: Added all available report formats

# 21.7.1

- [feature] Option `obfuscate-security-related-outputs` to enable or disable obfuscating of security related outputs
  such as the access key.
- [fix] Obfuscating the access key when logging it or printing it to the console.

# 21.6.1

- [fix] _teamscale-gradle-plugin_: Deprecation warnings under Gradle 7.1
- [fix] _tia-client_: Increased timout for connection to Teamscale JaCoCo agent

# 21.6.0

- [feature] Support for Java 16 (and experimental support for Java 17)

# 21.5.0

- [feature] Support for VFS (Virtual File System) URL format used in JBoss and Wildfly
- [fix] Upload to Teamscale mode for testwise coverage did report tests multiple times when multiple uploads were
  triggered
- [fix] Setting partition or message via REST did not strip JSON encoding quotes

# 21.4.0

- [feature] _tia-client_: dynamically set partition and message
- [fix] Upload to Teamscale mode for testwise coverage did miss some class files when class-dir was not explicitly given

# 21.3.0

- [feature] _tia-client_: expose rank of tests

# 21.2.1

- [fix] Spring Boot applications could not be profiled due to changes in their code location format

# 21.2.0

- [feature] _teamscale-client_: Impacted tests now allows to specify multiple partitions
- [feature] _teamscale-client_: Impacted tests baseline can now be a string
- [feature] _teamscale-client_: Impacted tests are queried with CHEAP_ADDITIONAL_COVERAGE_PER_TIME

# 21.1.0

- [feature] Support uploading to multiple Teamscale projects

# 21.0.0

- [feature] _tia-client_: add API to hash test data when creating ClusteredTestDetails
- [feature] support for SAP NWDI application profiling
- [feature] _teamscale-gradle-plugin_: support jacoco and junit report uploads to Teamscale
- [feature] _teamscale-gradle-plugin_: available on Gradle Plugin Portal
- [breaking change] _teamscale-gradle-plugin_: teamscale.report.partition is no longer available

# 20.0.0

- [breaking change] This release requires Teamscale 5.9 or higher
- [feature] Made ensure-processed and include-failed-and-skipped options available in teamscale-client

# 19.0.0

- [fix] option parsing errors were not logged in rare cases
- [breaking change] changed default message to include partition and upload date
- [breaking change] Bumped minimum supported Gradle version for the Gradle plugin to 6.5 so we can support Java 11
- [feature] Made score and duration of test selected by TIA available via the teamscale-client

# 18.0.0

- [breaking change] removed options `coverage-via-http` and `teamscale-testwise-upload`. Use `tia-mode=http`
  or `tia-mode=teamscale-upload` instead.
- [breaking change] _tia-client_: changed Java API
- [feature] always exclude common libraries from profiling to shrink coverage files
- [feature] enable changing and reading the commit message via a new `/message` REST endpoint
- [breaking change] changing the partition is now a PUT request to `/partition` (formerly POST
  to `/partition/{partitionName}`)

# 17.0.0

- [breaking change] default for `--interval` changed from 60 to 480 minutes
- [feature] Support for uploading XML reports to artifactory
- [fix] Docker image did not react to SIGTERM
- [feature] All artifacts are now also available on Maven Central
- [fix] `test-env` option was ignored

# 16.0.1

- [fix] revision is ignored during validation for automatic upload to Teamscale

# 16.0.0

- [fix] Prevent "out of memory" in small JVMs: Don't cache test executions in memory
- [breaking change] `--ignore-duplicates` (and `-d` option in convert tool) have been replaced with `--duplicates`
  option
- [breaking change] `--filter` option in convert tool has been renamed to `--includes`
- [breaking change] `--exclude` option in convert tool has been renamed to `--excludes`
- [fix] Remove retry logic for impacted tests request
- [fix] Resolve possible memory leak during report generation
- [feature] Use git.commit.id from git.properties instead of branch and timestamp
- [breaking change] Reduce XML report size by only including source file coverage, no class coverage
- [feature] New option `--ignore-uncovered-classes` to further reduce size of XML reports
- [fix] converter produces duplicate test entries for testwise coverage
- [breaking change] `--classDir` option in convert tool has been renamed to `--class-dir`
- [feature] `--class-dir` option allows to pass in a `.txt` file with the class file directories/jars separated by
  newlines

# 15.5.0

- [feature] add TIA client library for integrating TIA in your custom test framework
- [fix] Delete empty coverage directories
- [fix] Don't upload empty coverage reports

# 15.4.0

- [fix] `git.properties` commit was not used for upload
- [feature] Upload ignored `origin/` prefix in `git.properties`'s branch name
- [fix] `http-server-port` option does not pass validation in normal mode
- [fix] Executable spring boot jar produces no coverage

# 15.3.0

- [feature] Added `coverage-via-http` option for testwise mode
- [feature] Added `teamscale-revision` option to supply VCS revision instead of branch and timestamp
- [feature] Added `/revision` HTTP endpoint for testwise mode
- [feature] Updated JaCoCo to 0.8.5
- [fix] Significantly reduced memory footprint
- [fix] `--run-all-tests` doesn't run any tests at all
- [fix] test-wise coverage report incorrect for classes in default package

# 15.2.0

- [fix] WildFly won't start with agent attached
- [feature] make `out` parameter optional with sensible fallback (subdirectory `coverage` in agent installation
  directory)
- [feature] if no `teamscale-commit`, `teamscale-git-properties-jar` or `teamscale-commit-manifest-jar` is configured,
  all loaded Jar/War/Ear/... files that contain profiled code are checked for a `git.properties` file. This allows
  auto-detecting a
  `git.properties` file without any additional configuration.

# 15.1.1

- [documentation] Configuration for SAP NetWeaver Java (>= 7.50) is now documented

# 15.1.0

- [feature] supplying a `class-dir` option is no longer mandatory
- [feature] agent logs errors in case of empty coverage dumps (i.e. misconfigured agent)
- [fix] prevent NPE when trying to read manifest from Jar file

# 15.0.0

- [feature] support for git.properties to supply commit information
- [breaking change] Agent now ignores SSL certificates by default and only turns on validation if
  `validate-ssl=true` is passed in the agent arguments. Existing setups will continue to work but validation will be
  disabled from this version on
- [fix] Agent uses higher timeouts (20s) for all HTTP connections to account for slow networks

# 14.0.0

- [fix] Reduced memory requirements for generating testwise coverage.
- [breaking change] When using the `convert` tool in `--testwise-coverage` mode the new `--split-after 5000` option will
  break up testwise coverage files automatically after the specified number of tests written. This ensures that
  generated reports are small enough to be uploaded to Teamscale. Default is `5000`. The given output file will now be
  appended suffixed `-1`, `-2` etc.. If the specified output file is named `testwise_coverage.json` the actually written
  file will be called `testwise_coverage-1.json` for the first 5000 tests. For uploading multiple files use an upload
  session.

# 13.0.1

- [fix] Prevent `-1` to show up as covered line in Testwise Coverage report

# 13.0.0

- [feature] added `dump-on-exit` option
- [breaking change] added `mode` option (Must be set for Testwise Coverage mode. `http-server-port` or `test-env` alone
  is no longer sufficient)
- [feature] The agent now optionally accepts test execution results via /test/end
- [feature] Support for Java 12

# 12.0.0

- [breaking change] The convert tool now uses wildcard patterns for the class matching (was ant pattern before)
- [breaking change] The agent returns the correct 204 and 400 status codes in Testwise Coverage mode

# 11.2.0

- [feature] The agent now also supports Java 11 code.

# 11.1.0

- [feature] The agent now also supports line coverage in Testwise Coverage mode

# 11.0.4

- [breaking change] Test impact mode no longer uploads reports to teamscale and does no longer generate reports on its
  own (see TEST_IMPACT_ANALYSIS_DOC -> How to get testwise coverage)
- [feature] Added `test-env` option

# 10.2.0

- [feature] Added option to upload to azure file storage

# 10.1.0

- [feature] Paths passed to the agent can now be relative and contain ant patterns

# 10.0.0

- [breaking change] switched to logback for logging. All logging configurations must be replaced with logback XMLs. This
  fixes Java 10 compatibility issues with Log4j by removing Log4j from the agent
- [feature] make agent log INFO and above to `agentdir/logs` by default
- [fix] isolate agent further from the application to prevent errors from conflicting library versions

# 9.1.0

- [feature] dump interval of 0 only dumps at the end

# 9.0.3

- [fix] Test Impact Mode: Empty reports are no longer dumped
- [fix] Test Impact Mode: JUnit is uploaded before testwise coverage

# 9.0.2

- [fix] prevent log files from flooding disk with default log4j config files

# 9.0.1

- [fix] NativeWebSocketServletContainerInitializer not found

# 9.0.0

- [breaking change] added `teamscale-commit-manifest-jar` option. Automatic fallback to MANIFEST.MF on classpath is no
  longer supported

# 8.4.0

- [feature] added `config-file` option

# 8.3.1

- [fix] fixed class conflict with JAXB in WebSphere

# 8.3.0

- [feature] added Test Impact mode via `http-server-port`

# 8.2.0

- [feature] added ability to read commit branch and timestamp from MANIFEST.MF

# 8.1.0

- [feature] added options to configure coverage upload to Teamscale

# 8.0.2

- [fix] remove version number from jar file name

# 8.0.1

- [fix] prefill the `javaws.properties` correctly on Windows

# 8.0.0

- [breaking change] removed `watch` mode and made `convert` mode the default. Only the agent is now supported

# 7.0.0

- [feature] prevent clashes between our dependencies and the profile app's
- [breaking change] logging for the agent must now be configured via the `logging-config` parameter

# 6.0.0

- [breaking change] Docker image is now designed to use the agent instead of the client
- [feature] add wrapper around `javaws` to allow profiling Java Web Start applications

# 5.0.0

- [breaking change] ignore-duplicates is now the default for the agent to simplify the initial setup

# 4.0.3

- [fix] fix handling of Windows paths

# 4.0.2

- [fix] using `upload-url` in conjunction with `upload-metadata` caused a crash

# 4.0.1

_This version was never checked in, thus there is no tag for it in the Git_

- [fix] using `upload-url` in conjunction with `upload-metadata` caused corrupt files

# 4.0.0

- [breaking change] agent option separator is now a semicolon, no longer a colon (to support Windows paths)
- [breaking change] merged `include` and `jacoco-include` parameters. Both use the JaCoCo pattern syntax
- [feature] allow dumping via HTTP file upload

# 3.1.0

- [feature] add agent for regular dumping without separate process

# 3.0.0

- [breaking change] you must pass `true` or `false` explicitly to the `-d` parameter
- [feature] allow setting explicit host name via `--host`
- [feature] add Docker image
- [tooling] add Docker image build and Docker Hub project

# 2.0.1

- [fix] working directory was missing from systemd service file

# 2.0.0

- [feature] added `convert` mode to do one-time conversion. The old behavior is now reachable via the `watch` command (
  which is also the default if no command is provided).

# 1.4.1

- [tooling] add GitLab build and `dist` task. Update documentation

# 1.4.0

- [feature] added `-d` switch to ignore non-identical, duplicate class files during report generation
