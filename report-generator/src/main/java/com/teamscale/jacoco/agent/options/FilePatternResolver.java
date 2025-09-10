package com.teamscale.jacoco.agent.options;

import com.teamscale.client.FileSystemUtils;
import com.teamscale.report.util.ILogger;
import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.collections.Pair;
import org.conqat.lib.commons.filesystem.AntPatternUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/** Helper class to support resolving file paths which may contain Ant patterns. */
public class FilePatternResolver {

	/** Stand-in for the question mark operator. */
	private static final String QUESTION_REPLACEMENT = "!@";

	/** Stand-in for the asterisk operator. */
	private static final String ASTERISK_REPLACEMENT = "#@";

	private final ILogger logger;

	public FilePatternResolver(ILogger logger) {
		this.logger = logger;
	}

	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one existing {@link Path}. If the given path is
	 * relative, it is resolved relative to the current working directory. If more than one file matches the pattern,
	 * one of the matching files is used without any guarantees as to which. The selection is, however, guaranteed to be
	 * deterministic, i.e. if you run the pattern twice and get the same set of files, the same file will be picked each
	 * time.
	 */
	public Path parsePath(String optionName, String pattern) throws IOException {
		return parsePath(optionName, pattern, new File("."));
	}

	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one or multiple existing {@link File}s. If the
	 * given path is relative, it is resolved relative to the current working directory.
	 */
	public List<File> resolveToMultipleFiles(String optionName, String pattern) throws IOException {
		return resolveToMultipleFiles(optionName, pattern, new File("."));
	}

	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one or multiple existing {@link File}s. If the
	 * given path is relative, it is resolved relative to the current working directory.
	 * <p>
	 * Visible for testing only.
	 */
	/* package */ List<File> resolveToMultipleFiles(String optionName, String pattern,
			File workingDirectory) throws IOException {
		if (isPathWithPattern(pattern)) {
			return CollectionUtils
					.map(parseFileFromPattern(optionName, pattern, workingDirectory).getAllMatchingPaths(),
							Path::toFile);
		}
		try {
			return Collections.singletonList(workingDirectory.toPath().resolve(Paths.get(pattern)).toFile());
		} catch (InvalidPathException e) {
			throw new IOException("Invalid path given for option " + optionName + ": " + pattern, e);
		}
	}

	/**
	 * Interprets the given pattern as an Ant pattern and resolves it to one existing {@link Path}. If the given path is
	 * relative, it is resolved relative to the given working directory. If more than one file matches the pattern, one
	 * of the matching files is used without any guarantees as to which. The selection is, however, guaranteed to be
	 * deterministic, i.e. if you run the pattern twice and get the same set of files, the same file will be picked each
	 * time.
	 */
	/* package */ Path parsePath(String optionName, String pattern,
			File workingDirectory) throws IOException {
		if (isPathWithPattern(pattern)) {
			return parseFileFromPattern(optionName, pattern, workingDirectory).getSinglePath();
		}
		try {
			return workingDirectory.toPath().resolve(Paths.get(pattern));
		} catch (InvalidPathException e) {
			throw new IOException("Invalid path given for option " + optionName + ": " + pattern, e);
		}
	}

	/** Parses the pattern as a Ant pattern to one or multiple files or directories. */
	private FilePatternResolverRun parseFileFromPattern(String optionName,
			String pattern,
			File workingDirectory) throws IOException {
		return new FilePatternResolverRun(logger, optionName, pattern, workingDirectory).resolve();
	}

	/** Returns whether the given path contains Ant pattern characters (?,*). */
	private static boolean isPathWithPattern(String path) {
		return path.contains("?") || path.contains("*");
	}

	/**
	 * Returns whether the given path contains artificial pattern characters ({@link #QUESTION_REPLACEMENT},
	 * {@link #ASTERISK_REPLACEMENT}).
	 */
	private static boolean isPathWithArtificialPattern(String path) {
		return path.contains(QUESTION_REPLACEMENT) || path.contains(ASTERISK_REPLACEMENT);
	}

	private static class FilePatternResolverRun {
		private final File workingDirectory;
		private final String optionName;
		private final String pattern;
		private String suffixPattern;
		private Path basePath;
		private List<Path> matchingPaths;
		private final ILogger logger;

		private FilePatternResolverRun(ILogger logger, String optionName, String pattern, File workingDirectory) {
			this.logger = logger;
			this.optionName = optionName;
			this.pattern = pattern;
			this.workingDirectory = workingDirectory.getAbsoluteFile();
			splitIntoBasePathAndPattern(pattern);
		}

		/**
		 * Resolves the pattern. The results can be retrieved via {@link #getSinglePath()} or
		 * {@link #getAllMatchingPaths()}.
		 */
		private FilePatternResolverRun resolve() throws IOException {
			Pattern pathRegex = AntPatternUtils.convertPattern(suffixPattern, false);
			Predicate<Path> filter = path -> pathRegex
					.matcher(FileSystemUtils.normalizeSeparators(basePath.relativize(path).toString())).matches();

			try {
				matchingPaths = Files.walk(basePath).filter(filter).sorted().collect(toList());
			} catch (IOException e) {
				throw new IOException(
						"Could not recursively list files in directory " + basePath + " in order to resolve pattern " + suffixPattern + " given for option " + optionName,
						e);
			}
			return this;
		}

		/**
		 * Splits the path into a base dir, i.e. the directory-prefix of the path that does not contain any ? or *
		 * placeholders, and a pattern suffix. We need to replace the pattern characters with stand-ins, because ? and *
		 * are not allowed as path characters on windows.
		 */
		private void splitIntoBasePathAndPattern(String value) {
			String pathWithArtificialPattern = value.replace("?", QUESTION_REPLACEMENT)
					.replace("*", ASTERISK_REPLACEMENT);
			Path pathWithPattern = Paths.get(pathWithArtificialPattern);
			Path baseDir = pathWithPattern;
			while (isPathWithArtificialPattern(baseDir.toString())) {
				baseDir = baseDir.getParent();
				if (baseDir == null) {
					suffixPattern = value;
					basePath = workingDirectory.toPath().resolve("").normalize().toAbsolutePath();
					return;
				}
			}
			String pattern = baseDir.relativize(pathWithPattern).toString().replace(QUESTION_REPLACEMENT, "?")
					.replace(ASTERISK_REPLACEMENT, "*");
			Pair<String, String> baseDirAndPattern = new Pair<>(baseDir.toString(), pattern);

			suffixPattern = baseDirAndPattern.getSecond();
			basePath = workingDirectory.toPath().resolve(baseDir).normalize().toAbsolutePath();
		}

		/** Returns the result of a resolution as a single Path and warns when multiple paths match. */
		private Path getSinglePath() throws IOException {
			if (this.matchingPaths.isEmpty()) {
				throw new IOException(
						"Invalid path given for option " + optionName + ": " + this.pattern + ". The pattern " + this.suffixPattern +
								" did not match any files in " + this.basePath.toAbsolutePath());
			} else if (this.matchingPaths.size() > 1) {
				logger.warn(
						"Multiple files match the pattern " + this.suffixPattern + " in " + this.basePath
								.toString() + " for option " + optionName + "! " +
								"The first one is used, but consider to adjust the " +
								"pattern to match only one file. Candidates are: " + this.matchingPaths.stream()
								.map(this.basePath::relativize).map(Path::toString).collect(joining(", ")));
			}
			Path path = this.matchingPaths.get(0).normalize();
			logger.info("Using file " + path + " for option " + optionName);
			return path;
		}

		/** Returns all matched paths after the resolution. */
		private List<Path> getAllMatchingPaths() {
			if (this.matchingPaths.isEmpty()) {
				logger.warn(
						"The pattern " + this.suffixPattern + " in " + this.basePath
								.toString() + " for option " + optionName + " did not match any file!");
			}
			logger.info("Resolved " + pattern + " to " + this.matchingPaths.size() + " for option " + optionName);
			return this.matchingPaths;
		}
	}
}
