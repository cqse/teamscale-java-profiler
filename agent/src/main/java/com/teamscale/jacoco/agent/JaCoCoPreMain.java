/*******************************************************************************
 * Copyright (c) 2009, 2023 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/

package com.teamscale.jacoco.agent;

import org.jacoco.agent.rt.internal_29a6edd.Agent;
import org.jacoco.agent.rt.internal_29a6edd.AgentModule;
import org.jacoco.agent.rt.internal_29a6edd.CoverageTransformer;
import org.jacoco.agent.rt.internal_29a6edd.IExceptionLogger;
import org.jacoco.agent.rt.internal_29a6edd.PreMain;
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.AgentOptions;
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.IRuntime;
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.InjectedClassRuntime;
import org.jacoco.agent.rt.internal_29a6edd.core.runtime.ModifiedSystemClassRuntime;
import org.slf4j.Logger;

import java.lang.instrument.Instrumentation;

/**
 * This is a copy of the {@link PreMain} class from the JaCoCo agent. The only changes are that we:
 * <ul>
 * <li>replaced the {@link CoverageTransformer} with our {@link LenientCoverageTransformer}</li>
 * <li>pass a {@link Logger} to {@link #premain(String, Instrumentation, Logger)} which is passed to the
 * {@link LenientCoverageTransformer} instead of {@link IExceptionLogger}</li>
 * <li>Replaced "$JaCoCo" suffix with "$TeamscaleJaCoCo" to avoid conflicts in cases where jacoco is on the classpath as well.</li>
 * </ul>
 */
public final class JaCoCoPreMain {

	private JaCoCoPreMain() {
		// no instances
	}

	/**
	 * This method is called by the JVM to initialize Java agents.
	 *
	 * @param options agent options
	 * @param inst    instrumentation callback provided by the JVM
	 * @throws Exception in case initialization fails
	 */
	public static void premain(final String options, final Instrumentation inst, Logger logger)
			throws Exception {

		final AgentOptions agentOptions = new AgentOptions(options);

		final Agent agent = Agent.getInstance(agentOptions);

		final IRuntime runtime = createRuntime(inst);
		runtime.startup(agent.getData());
		inst.addTransformer(new LenientCoverageTransformer(runtime, agentOptions,
				logger));
	}

	private static IRuntime createRuntime(final Instrumentation inst)
			throws Exception {

		if (AgentModule.isSupported()) {
			final AgentModule module = new AgentModule();
			module.openPackage(inst, Object.class);
			final Class<InjectedClassRuntime> clazz = module
					.loadClassInModule(InjectedClassRuntime.class);
			return clazz.getConstructor(Class.class, String.class)
					.newInstance(Object.class, "$TeamscaleJaCoCo");
		}

		return ModifiedSystemClassRuntime.createFor(inst,
				"java/lang/UnknownError");
	}
}
