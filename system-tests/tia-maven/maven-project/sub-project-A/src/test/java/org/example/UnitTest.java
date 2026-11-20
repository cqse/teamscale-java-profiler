package org.example;

import org.junit.jupiter.api.Test;
import org.example.SUTA;

public class UnitTest {

    @Test
    public void utBla() {
        new SUTA().bla();
    }

    @Test
    public void utFoo() {
        new SUTA().foo();
        TestUtils.log("foo");
    }
}
