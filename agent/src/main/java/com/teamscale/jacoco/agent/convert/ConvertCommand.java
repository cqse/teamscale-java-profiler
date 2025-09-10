/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2017 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.convert;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.teamscale.client.FileSystemUtils;
import com.teamscale.client.StringUtils;
import com.teamscale.jacoco.agent.commandline.ICommand;
import com.teamscale.jacoco.agent.commandline.Validator;
import com.teamscale.jacoco.agent.options.ClasspathUtils;
import com.teamscale.jacoco.agent.options.FilePatternResolver;
import com.teamscale.report.EDuplicateClassFileBehavior;
import com.teamscale.report.util.CommandLineLogger;
import org.conqat.lib.commons.assertion.CCSMAssert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Encapsulates all command line options for the convert command for parsing with {@link JCommander}.
 */
@Parameters(commandNames = "convert", commandDescription = "Converts a binary .exec coverage file to XML. " +
		"Note that the XML report will only contain source file coverage information, but no class coverage.")
public class ConvertCommand implements ICommand {

	/** The directories and/or zips that contain all class files being profiled. */
	@Parameter(names = {"--class-dir", "--jar", "-c"}, required = true, description = ""
			+ "The directories or zip/ear/jar/war/... files that contain the compiled Java classes being profiled."
			+ " Searches recursively, including inside zips. You may also supply a *.txt file with one path per line.")
	/* package */ List<String> classDirectoriesOrZips = new ArrayList<>();

	/**
	 * Wildcard include patterns to apply during JaCoCo's traversal of class files.
	 */
	@Parameter(names = {"--includes"}, description = ""
			+ "Wildcard include patterns to apply to all found class file locations during JaCoCo's traversal of class files."
			+ " Note that zip contents are separated from zip files with @ and that you can filter only"
			+ " class files, not intermediate folders/zips. Use with great care as missing class files"
			+ " lead to broken coverage files! Turn on debug logging to see which locations are being filtered."
			+ " Defaults to no filtering. Excludes overrule includes.")
	/* package */ List<String> locationIncludeFilters = new ArrayList<>();

	/**
	 * Wildcard exclude patterns to apply during JaCoCo's traversal of class files.
	 */
	@Parameter(names = {"--excludes", "-e"}, description = ""
			+ "Wildcard exclude patterns to apply to all found class file locations during JaCoCo's traversal of class files."
			+ " Note that zip contents are separated from zip files with @ and that you can filter only"
			+ " class files, not intermediate folders/zips. Use with great care as missing class files"
			+ " lead to broken coverage files! Turn on debug logging to see which locations are being filtered."
			+ " Defaults to no filtering. Excludes overrule includes.")
	/* package */ List<String> locationExcludeFilters = new ArrayList<>();

	/** The directory to write the XML traces to. */
	@Parameter(names = {"--in", "-i"}, required = true, description = "" + "The binary .exec file(s), test details and " +
			"test executions to read. Can be a single file or a directory that is recursively scanned for relevant files.")
	/* package */ List<String> inputFiles = new ArrayList<>();

	/** The directory to write the XML traces to. */
	@Parameter(names = {"--out", "-o"}, required = true, description = ""
			+ "The file to write the generated XML report to.")
	/* package */ String outputFile = "";

	/** Whether to ignore duplicate, non-identical class files. */
	@Parameter(names = {"--duplicates", "-d"}, arity = 1, description = ""
			+ "Whether to ignore duplicate, non-identical class files."
			+ " This is discouraged and may result in incorrect coverage files. Defaults to WARN. " +
			"Options are FAIL, WARN and IGNORE.")
	/* package */ EDuplicateClassFileBehavior duplicateClassFileBehavior = EDuplicateClassFileBehavior.WARN;

	/** Whether to ignore uncovered class files. */
	@Parameter(names = {"--ignore-uncovered-classes"}, required = false, arity = 1, description = ""
			+ "Whether to ignore uncovered classes."
			+ " These classes will not be part of the XML report at all, making it considerably smaller in some cases. Defaults to false.")
	/* package */ boolean shouldIgnoreUncoveredClasses = false;

	/** Whether testwise coverage or jacoco coverage should be generated. */
	@Parameter(names = {"--testwise-coverage", "-t"}, required = false, arity = 0, description = "Whether testwise " +
			"coverage or jacoco coverage should be generated.")
	/* package */ boolean shouldGenerateTestwiseCoverage = false;

	/** After how many tests testwise coverage should be split into multiple reports. */
	@Parameter(names = {"--split-after", "-s"}, required = false, arity = 1, description = "After how many tests " +
			"testwise coverage should be split into multiple reports (Default is 5000).")
	private int splitAfter = 5000;

	/** @see #classDirectoriesOrZips */
	public List<File> getClassDirectoriesOrZips() throws IOException {
		return ClasspathUtils
				.resolveClasspathTextFiles("class-dir", new FilePatternResolver(new CommandLineLogger()),
						classDirectoriesOrZips);
	}

	/** @see #locationIncludeFilters */
	public List<String> getLocationIncludeFilters() {
		return locationIncludeFilters;
	}

	/** @see #locationExcludeFilters */
	public List<String> getLocationExcludeFilters() {
		return locationExcludeFilters;
	}

	/** @see #inputFiles */
	public List<File> getInputFiles() {
		return inputFiles.stream().map(File::new).collect(Collectors.toList());
	}

	/** @see #outputFile */
	public File getOutputFile() {
		return new File(outputFile);
	}

	/** @see #splitAfter */
	public int getSplitAfter() {
		return splitAfter;
	}

	/** @see #duplicateClassFileBehavior */
	public EDuplicateClassFileBehavior getDuplicateClassFileBehavior() {
		return duplicateClassFileBehavior;
	}

	/** Makes sure the arguments are valid. */
	@Override
	public Validator validate() {
		Validator validator = new Validator();

		List<File> classDirectoriesOrZips = new ArrayList<>();
		validator.ensure(() -> classDirectoriesOrZips.addAll(getClassDirectoriesOrZips()));
		validator.isFalse(classDirectoriesOrZips.isEmpty(),
				"You must specify at least one directory or zip that contains class files");
		for (File path : classDirectoriesOrZips) {
			validator.isTrue(path.exists(), "Path '" + path + "' does not exist");
			validator.isTrue(path.canRead(), "Path '" + path + "' is not readable");
		}

		for (File inputFile : getInputFiles()) {
			validator.isTrue(inputFile.exists() && inputFile.canRead(),
					"Cannot read the input file " + inputFile);
		}

		validator.ensure(() -> {
			CCSMAssert.isFalse(StringUtils.isEmpty(outputFile), "You must specify an output file");
			File outputDir = getOutputFile().getAbsoluteFile().getParentFile();
			FileSystemUtils.ensureDirectoryExists(outputDir);
			CCSMAssert.isTrue(outputDir.canWrite(), "Path '" + outputDir + "' is not writable");
		});

		return validator;
	}

	/** {@inheritDoc} */
	@Override
	public void run() throws Exception {
		Converter converter = new Converter(this);
		if (this.shouldGenerateTestwiseCoverage) {
			converter.runTestwiseCoverageReportGeneration();
		} else {
			converter.runJaCoCoReportGeneration();
		}
	}
}
