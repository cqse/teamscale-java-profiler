package com.teamscale.jacoco.agent.commit_resolution.git_properties;

import com.teamscale.jacoco.agent.logging.LoggingUtils;
import com.teamscale.jacoco.agent.upload.delay.DelayedUploader;
import com.teamscale.jacoco.agent.util.DaemonThreadFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Searches a Jar/War/Ear/... file for a git.properties file in order to enable upload for the commit described therein,
 * e.g. to Teamscale, via a {@link DelayedUploader}.
 */
public class GitSingleProjectPropertiesLocator<T> implements IGitPropertiesLocator {

	private final Logger logger = LoggingUtils.getLogger(this);
	private final Executor executor;
	private T foundData = null;
	private File jarFileWithGitProperties = null;

	private final DelayedUploader<T> uploader;
	private final DataExtractor<T> dataExtractor;

	private final boolean recursiveSearch;
	private final @Nullable DateTimeFormatter gitPropertiesCommitTimeFormat;

	public GitSingleProjectPropertiesLocator(DelayedUploader<T> uploader, DataExtractor<T> dataExtractor,
			boolean recursiveSearch,
			@Nullable DateTimeFormatter gitPropertiesCommitTimeFormat) {
		// using a single threaded executor allows this class to be lock-free
		this(uploader, dataExtractor, Executors
						.newSingleThreadExecutor(
								new DaemonThreadFactory(GitSingleProjectPropertiesLocator.class,
										"git.properties Jar scanner thread")),
				recursiveSearch, gitPropertiesCommitTimeFormat);
	}

	/**
	 * Visible for testing. Allows tests to control the {@link Executor} in order to test the asynchronous functionality
	 * of this class.
	 */
	public GitSingleProjectPropertiesLocator(DelayedUploader<T> uploader, DataExtractor<T> dataExtractor,
			Executor executor,
			boolean recursiveSearch,
			@Nullable DateTimeFormatter gitPropertiesCommitTimeFormat) {
		this.uploader = uploader;
		this.dataExtractor = dataExtractor;
		this.executor = executor;
		this.recursiveSearch = recursiveSearch;
		this.gitPropertiesCommitTimeFormat = gitPropertiesCommitTimeFormat;
	}

	/**
	 * Asynchronously searches the given jar file for a git.properties file.
	 */
	@Override
	public void searchFileForGitPropertiesAsync(File file, boolean isJarFile) {
		executor.execute(() -> searchFile(file, isJarFile));
	}

	private void searchFile(File file, boolean isJarFile) {
		logger.debug("Searching jar file {} for a single git.properties", file);
		try {
			List<T> data = dataExtractor.extractData(file, isJarFile, recursiveSearch, gitPropertiesCommitTimeFormat);
			if (data.isEmpty()) {
				logger.debug("No git.properties files found in {}", file.toString());
				return;
			}
			if (data.size() > 1) {
				logger.warn("Multiple git.properties files found in {}", file.toString() +
						". Using the first one: " + data.get(0));

			}
			T dataEntry = data.get(0);

			if (foundData != null) {
				if (!foundData.equals(dataEntry)) {
					logger.warn(
							"Found inconsistent git.properties files: {} contained data {} while {} contained {}." +
									" Please ensure that all git.properties files of your application are consistent." +
									" Otherwise, you may" +
									" be uploading to the wrong project/commit which will result in incorrect coverage data" +
									" displayed in Teamscale. If you cannot fix the inconsistency, you can manually" +
									" specify a Jar/War/Ear/... file from which to read the correct git.properties" +
									" file with the agent's teamscale-git-properties-jar parameter.",
							jarFileWithGitProperties, foundData, file, data);
				}
				return;
			}

			logger.debug("Found git.properties file in {} and found commit descriptor {}", file.toString(),
					dataEntry);
			foundData = dataEntry;
			jarFileWithGitProperties = file;
			uploader.setCommitAndTriggerAsynchronousUpload(dataEntry);
		} catch (IOException | InvalidGitPropertiesException e) {
			logger.error("Error during asynchronous search for git.properties in {}", file.toString(), e);
		}
	}

	/** Functional interface for data extraction from a jar file. */
	@FunctionalInterface
	public interface DataExtractor<T> {
		/** Extracts data from the JAR. */
		List<T> extractData(File file, boolean isJarFile,
				boolean recursiveSearch,
				@Nullable DateTimeFormatter gitPropertiesCommitTimeFormat) throws IOException, InvalidGitPropertiesException;
	}
}
