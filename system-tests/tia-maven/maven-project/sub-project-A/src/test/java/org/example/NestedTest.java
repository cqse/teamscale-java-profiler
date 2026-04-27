package org.example;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class NestedTest {

	@Nested
	class Child1 {
		@Test
		void getDefaultParameter() {
			new SUTA().bla();
		}
	}

	@Nested
	class Child2 {
		@Test
		void getDefaultParameter() {
			new SUTA().foo();
		}
	}
}
