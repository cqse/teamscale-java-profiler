package com.teamscale.maven;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utils for working with a Git repository.
 */
public class GitCommitUtils {

	/**
	 * Determines the current HEAD commit in the Git repository located in the or above the given search directory.
	 *
	 * @throws IOException if reading from the Git repository fails or the current directory is not a Git repository.
	 */
	public static String getGitHeadRevision(Path searchDirectory) throws IOException {
		Path gitDirectory = findGitBaseDirectory(searchDirectory);
		if (gitDirectory == null) {
			throw new IOException(
					"Could not find a Git directory in " + searchDirectory + " or any parent directory."
							+ " Configure <revision> or <commit> in the plugin configuration,"
							+ " or run Maven from inside a Git checkout.");
		}
		Repository repository;
		try (Git git = Git.open(gitDirectory.toFile())) {
			repository = git.getRepository();
			return repository.getRefDatabase().findRef("HEAD").getObjectId().getName();
		}
	}

	/**
	 * Traverses the directory tree upwards until it finds a .git directory. Returns null if no .git directory is
	 * found.
	 */
	private static Path findGitBaseDirectory(Path searchDirectory) {
		while (searchDirectory != null) {
			if (Files.exists(searchDirectory.resolve(".git"))) {
				return searchDirectory;
			}
			searchDirectory = searchDirectory.getParent();
		}
		return null;
	}
}
