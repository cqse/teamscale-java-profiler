package com.example;

/**
 * A tiny application to attach the profiler to, see docs/DEBUGGING.md. It sleeps for ten seconds, which leaves room to
 * attach a debugger or to watch coverage being uploaded. Pass a different runtime in seconds as the first argument,
 * e.g. {@code ./gradlew :sample-app:run -PruntimeSeconds=300}.
 */
public class Main {

	private static final int DEFAULT_RUNTIME_SECONDS = 10;
	private static final int TICK_SECONDS = 5;

	public static void main(String[] args) throws InterruptedException {
		int runtime = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_RUNTIME_SECONDS;
		System.out.println("Hello Java Profiler! Staying alive for " + runtime + "s.");
		for (int elapsed = 0; elapsed < runtime; elapsed += TICK_SECONDS) {
			System.out.println("Still running: " + elapsed + "s of " + runtime + "s");
			Thread.sleep(Math.min(TICK_SECONDS, runtime - elapsed) * 1000L);
		}
		System.out.println("Done. Coverage is dumped while the JVM shuts down.");
	}
}
