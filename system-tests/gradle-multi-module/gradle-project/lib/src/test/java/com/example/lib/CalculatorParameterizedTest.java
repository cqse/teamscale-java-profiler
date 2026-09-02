package com.example.lib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Uses @ParameterizedClass. The JUnit platform prunes the tests of such a class from the discovered test tree and only
 * registers them once per parameter set while the class is being executed, so the profiler reports each of its test
 * methods as one test with the executions of all parameter sets collapsed into it. Only calls methods that are already
 * covered by {@link CalculatorTest} to keep the expected line coverage stable.
 */
@ParameterizedClass
@ValueSource(ints = {1, 2})
class CalculatorParameterizedTest {

	/** The summand that the test methods of this class are executed with. */
	@Parameter
	int summand;

	@Test
	public void testAddIsCommutative() {
		Calculator calculator = new Calculator();
		assertEquals(calculator.add(summand, 3), calculator.add(3, summand));
	}
}
