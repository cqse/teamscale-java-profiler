package com.teamscale.maven;

import java.util.ResourceBundle;

/** Provides access to the Maven plugin version at runtime. */
public class BuildVersion {

	private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("com.teamscale.maven.app");

	/** The version of the Teamscale Maven plugin. */
	public static final String VERSION = BUNDLE.getString("version");
}
