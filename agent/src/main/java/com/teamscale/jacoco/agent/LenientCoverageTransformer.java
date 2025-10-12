package com.teamscale.jacoco.agent;

import org.jacoco.agent.rt.internal_29a6edd.CoverageTransformer;
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.AgentOptions;
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.IRuntime;
import org.slf4j.Logger;

import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * A class file transformer which delegates to the JaCoCo {@link CoverageTransformer} to do the actual instrumentation,
 * but treats instrumentation errors e.g. due to unsupported class file versions more lenient by only logging them, but
 * not bailing out completely. Those unsupported classes will not be instrumented and will therefore not be contained in
 * the collected coverage report.
 */
public class LenientCoverageTransformer extends CoverageTransformer {

	private final Logger logger;

	public LenientCoverageTransformer(IRuntime runtime, AgentOptions options, Logger logger) {
		// The coverage transformer only uses the logger to print an error when the instrumentation fails.
		// We want to show our more specific error message instead, so we only log this for debugging at trace.
		super(runtime, options, e -> logger.trace(e.getMessage(), e));
		this.logger = logger;
	}

	@Override
	public byte[] transform(ClassLoader loader, String classname, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain,
			byte[] classfileBuffer) {
		try {
			return super.transform(loader, classname, classBeingRedefined, protectionDomain, classfileBuffer);
		} catch (IllegalClassFormatException e) {
			logger.error(
					"Failed to instrument " + classname + ". File will be skipped from instrumentation. " +
							"No coverage will be collected for it. Exclude the file from the instrumentation or try " +
							"updating the Teamscale Java Profiler if the file should actually be instrumented. (Cause: {})",
					getRootCauseMessage(e));
			return null;
		}
	}

	private static String getRootCauseMessage(Throwable e) {
		if (e.getCause() != null) {
			return getRootCauseMessage(e.getCause());
		}
		return e.getMessage();
	}
}
