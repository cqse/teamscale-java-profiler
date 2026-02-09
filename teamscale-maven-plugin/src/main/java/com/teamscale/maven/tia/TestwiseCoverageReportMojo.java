package com.teamscale.maven.tia;

import com.teamscale.client.TestDetails;
import com.teamscale.maven.DependencyUtils;
import com.teamscale.report.EDuplicateClassFileBehavior;
import com.teamscale.report.ReportUtils;
import com.teamscale.report.testwise.ETestArtifactFormat;
import com.teamscale.report.testwise.TestwiseCoverageReportWriter;
import com.teamscale.report.testwise.jacoco.JaCoCoTestwiseReportGenerator;
import com.teamscale.report.testwise.model.TestExecution;
import com.teamscale.report.testwise.model.factory.TestInfoFactory;
import com.teamscale.report.util.ClasspathWildcardIncludeFilter;
import com.teamscale.report.util.CommandLineLogger;
import com.teamscale.report.util.ILogger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Batch converts all binary data files of the current and dependent projects into a Testwise Coverage report. The goal
 * should only be run in the aggregator module.
 */
@Mojo(name = TestwiseCoverageReportMojo.NAME, defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class TestwiseCoverageReportMojo extends AbstractMojo {

	/** The name of the {@link TestwiseCoverageReportMojo}. */
	protected static final String NAME = "testwise-coverage-report";

	/**
	 * Wildcard include patterns to apply during JaCoCo's traversal of class files.
	 */
	@Parameter(defaultValue = "*")
	public String[] includes;
	/**
	 * Wildcard exclude patterns to apply during JaCoCo's traversal of class files.
	 */
	@Parameter()
	public String[] excludes;

	/**
	 * After how many tests the testwise coverage should be split into multiple reports (Default is 5000).
	 */
	@Parameter(defaultValue = "5000")
	public int splitAfter;

	/**
	 * Executes all tests, not only impacted ones if set. Defaults to false.
	 */
	@Parameter(defaultValue = "false")
	public boolean runAllTests;

	/**
	 * Executes only impacted tests, not all ones if set. Defaults to true.
	 */
	@Parameter(defaultValue = "true")
	public boolean runImpacted;

	/**
	 * Maven project. Provided automatically by Maven.
	 */
	@Parameter(property = "project", readonly = true)
	MavenProject project;

	/**
	 * The projects in the reactor. Provided automatically by Maven.
	 */
	@Parameter(property = "reactorProjects", readonly = true)
	private List<MavenProject> reactorProjects;

	private final ILogger logger = new CommandLineLogger();

	@Override
	public void execute() throws MojoFailureException {
		List<File> projectBuildDirectories = collectProjectBuildDirectories();
		logger.info("Generating the testwise coverage report");
		JaCoCoTestwiseReportGenerator generator = createJaCoCoTestwiseReportGenerator(projectBuildDirectories);

		generateTestwiseCoverageReport(generator, projectBuildDirectories, TiaUnitTestMojo.OUTPUT_DIR_NAME);
		generateTestwiseCoverageReport(generator, projectBuildDirectories, TiaIntegrationTestMojo.OUTPUT_DIR_NAME);
	}

	private @NotNull List<File> collectProjectBuildDirectories() {
		List<File> projectBuildDirectories = new ArrayList<>();
		List<MavenProject> dependantProjects = DependencyUtils.findDependencies(reactorProjects, project,
				Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME, Artifact.SCOPE_PROVIDED);
		for (MavenProject dependantProject : dependantProjects) {
			projectBuildDirectories.add(new File(dependantProject.getBuild().getDirectory()));
		}
		return projectBuildDirectories;
	}

	private void generateTestwiseCoverageReport(JaCoCoTestwiseReportGenerator generator,
			List<File> projectBuildDirectories, String folderName) throws MojoFailureException {
		Path outputsFolder = Paths.get(project.getBuild().getDirectory(), folderName);
		List<File> reportFileDirectories = getReportFileDirectories(projectBuildDirectories, folderName);
		TestInfoFactory testInfoFactory = createTestInfoFactory(reportFileDirectories);
		if (testInfoFactory.isEmpty()) {
			logger.debug(
					String.format("Skipping testwise coverage generation for %s as details are empty.", outputsFolder));
			return;
		}
		Path reportsFolder = outputsFolder.resolve("reports");
		try {
			Files.createDirectories(reportsFolder);
		} catch (IOException e) {
			logger.error("Could not create folder " + reportsFolder + ". Aborting.", e);
			throw new MojoFailureException(e);
		}
		List<File> jacocoExecutionDataList = ReportUtils.listFiles(ETestArtifactFormat.JACOCO, reportFileDirectories);
		String reportFilePath = reportsFolder.resolve("testwise-coverage.json").toString();

		Boolean partial = runImpacted && !runAllTests;
		try (TestwiseCoverageReportWriter coverageWriter = new TestwiseCoverageReportWriter(testInfoFactory,
				new File(reportFilePath), splitAfter, partial)) {
			for (File executionDataFile : jacocoExecutionDataList) {
				logger.info("Writing execution data for file: " + executionDataFile.getName());
				generator.convertAndConsume(executionDataFile, coverageWriter);
			}
		} catch (IOException e) {
			logger.error("Could not create testwise report. Aborting.", e);
			throw new MojoFailureException(e);
		}
	}

	/** Appends the given folderName to all given project build directories. */
	private static @NotNull List<File> getReportFileDirectories(List<File> projectBuildDirectories, String folderName) {
		return projectBuildDirectories.stream()
				.map(buildDir -> new File(buildDir, folderName))
				.collect(Collectors.toList());
	}

	private TestInfoFactory createTestInfoFactory(List<File> reportFiles) throws MojoFailureException {
		try {
			List<TestDetails> testDetails = ReportUtils.readObjects(ETestArtifactFormat.TEST_LIST, TestDetails[].class,
					reportFiles);
			List<TestExecution> testExecutions = ReportUtils.readObjects(ETestArtifactFormat.TEST_EXECUTION,
					TestExecution[].class, reportFiles);
			logger.info("Writing report with " + testDetails.size() + " Details/" + testExecutions.size() + " Results");
			return new TestInfoFactory(testDetails, testExecutions);
		} catch (IOException e) {
			logger.error("Could not read test details from reports. Aborting.", e);
			throw new MojoFailureException(e);
		}
	}

	private JaCoCoTestwiseReportGenerator createJaCoCoTestwiseReportGenerator(List<File> classFiles) {
		String includes = null;
		if (this.includes != null) {
			includes = String.join(":", this.includes);
		}
		String excludes = null;
		if (this.excludes != null) {
			excludes = String.join(":", this.excludes);
		}
		return new JaCoCoTestwiseReportGenerator(classFiles,
				new ClasspathWildcardIncludeFilter(includes, excludes), EDuplicateClassFileBehavior.WARN, logger);
	}
}
