/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright (c) 2009-2017 CQSE GmbH                                        |
|                                                                          |
+-------------------------------------------------------------------------*/
package com.teamscale.jacoco.agent.convert

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.teamscale.client.FileSystemUtils.ensureDirectoryExists
import com.teamscale.client.StringUtils.isEmpty
import com.teamscale.jacoco.agent.commandline.ICommand
import com.teamscale.jacoco.agent.commandline.Validator
import com.teamscale.jacoco.agent.commandline.Validator.ExceptionBasedValidation
import com.teamscale.jacoco.agent.options.ClasspathUtils
import com.teamscale.jacoco.agent.options.FilePatternResolver
import com.teamscale.jacoco.agent.util.Assertions
import com.teamscale.report.EDuplicateClassFileBehavior
import com.teamscale.report.util.CommandLineLogger
import java.io.File
import java.io.IOException
import java.util.stream.Collectors

/**
 * Encapsulates all command line options for the convert command for parsing with [JCommander].
 */
@Parameters(
	commandNames = ["convert"], commandDescription = "Converts a binary .exec coverage file to XML. " +
			"Note that the XML report will only contain source file coverage information, but no class coverage."
)
class ConvertCommand : ICommand {
	/** The directories and/or zips that contain all class files being profiled.  */
	@JvmField
	@Parameter(
		names = ["--class-dir", "--jar", "-c"], required = true, description = (""
				+ "The directories or zip/ear/jar/war/... files that contain the compiled Java classes being profiled."
				+ " Searches recursively, including inside zips. You may also supply a *.txt file with one path per line.")
	)
	var classDirectoriesOrZips = mutableListOf<String>()

	/**
	 * Wildcard include patterns to apply during JaCoCo's traversal of class files.
	 */
	@Parameter(
		names = ["--includes"], description = (""
				+ "Wildcard include patterns to apply to all found class file locations during JaCoCo's traversal of class files."
				+ " Note that zip contents are separated from zip files with @ and that you can filter only"
				+ " class files, not intermediate folders/zips. Use with great care as missing class files"
				+ " lead to broken coverage files! Turn on debug logging to see which locations are being filtered."
				+ " Defaults to no filtering. Excludes overrule includes.")
	)
	var locationIncludeFilters = mutableListOf<String>()

	/**
	 * Wildcard exclude patterns to apply during JaCoCo's traversal of class files.
	 */
	@Parameter(
		names = ["--excludes", "-e"], description = (""
				+ "Wildcard exclude patterns to apply to all found class file locations during JaCoCo's traversal of class files."
				+ " Note that zip contents are separated from zip files with @ and that you can filter only"
				+ " class files, not intermediate folders/zips. Use with great care as missing class files"
				+ " lead to broken coverage files! Turn on debug logging to see which locations are being filtered."
				+ " Defaults to no filtering. Excludes overrule includes.")
	)
	var locationExcludeFilters = mutableListOf<String>()

	/** The directory to write the XML traces to.  */
	@JvmField
	@Parameter(
		names = ["--in", "-i"], required = true, description = ("" + "The binary .exec file(s), test details and " +
				"test executions to read. Can be a single file or a directory that is recursively scanned for relevant files.")
	)
	var inputFiles = mutableListOf<String>()

	/** The directory to write the XML traces to.  */
	@JvmField
	@Parameter(
		names = ["--out", "-o"], required = true, description = (""
				+ "The file to write the generated XML report to.")
	)
	var outputFile = ""

	/** Whether to ignore duplicate, non-identical class files.  */
	@Parameter(
		names = ["--duplicates", "-d"], arity = 1, description = (""
				+ "Whether to ignore duplicate, non-identical class files."
				+ " This is discouraged and may result in incorrect coverage files. Defaults to WARN. " +
				"Options are FAIL, WARN and IGNORE.")
	)
	var duplicateClassFileBehavior = EDuplicateClassFileBehavior.WARN

	/** Whether to ignore uncovered class files.  */
	@Parameter(
		names = ["--ignore-uncovered-classes"], required = false, arity = 1, description = (""
				+ "Whether to ignore uncovered classes."
				+ " These classes will not be part of the XML report at all, making it considerably smaller in some cases. Defaults to false.")
	)
	var shouldIgnoreUncoveredClasses = false

	/** Whether testwise coverage or jacoco coverage should be generated.  */
	@Parameter(
		names = ["--testwise-coverage", "-t"], required = false, arity = 0, description = "Whether testwise " +
				"coverage or jacoco coverage should be generated."
	)
	var shouldGenerateTestwiseCoverage = false

	/** After how many tests testwise coverage should be split into multiple reports.  */
	@Parameter(
		names = ["--split-after", "-s"], required = false, arity = 1, description = "After how many tests " +
				"testwise coverage should be split into multiple reports (Default is 5000)."
	)
	val splitAfter = 5000

	@Throws(IOException::class)
	fun getClassDirectoriesOrZips(): List<File> = ClasspathUtils
		.resolveClasspathTextFiles(
			"class-dir", FilePatternResolver(CommandLineLogger()),
			classDirectoriesOrZips
		)

	fun getInputFiles() = inputFiles.map { File(it) }
	fun getOutputFile() = File(outputFile)

	/** Makes sure the arguments are valid.  */
	override fun validate() = Validator().apply {
		val classDirectoriesOrZips = mutableListOf<File>()
		ensure { classDirectoriesOrZips.addAll(getClassDirectoriesOrZips()) }
		isFalse(
			classDirectoriesOrZips.isEmpty(),
			"You must specify at least one directory or zip that contains class files"
		)
		classDirectoriesOrZips.forEach { path ->
			isTrue(path.exists(), "Path '$path' does not exist")
			isTrue(path.canRead(), "Path '$path' is not readable")
		}
		getInputFiles().forEach { inputFile ->
			isTrue(inputFile.exists() && inputFile.canRead(), "Cannot read the input file $inputFile")
		}
		ensure {
			Assertions.isFalse(isEmpty(outputFile), "You must specify an output file")
			val outputDir = getOutputFile().getAbsoluteFile().getParentFile()
			ensureDirectoryExists(outputDir)
			Assertions.isTrue(outputDir.canWrite(), "Path '$outputDir' is not writable")
		}
	}

	/** {@inheritDoc}  */
	@Throws(Exception::class)
	override fun run() {
		Converter(this).apply {
			if (shouldGenerateTestwiseCoverage) {
				runTestwiseCoverageReportGeneration()
			} else {
				runJaCoCoReportGeneration()
			}
		}
	}
}
