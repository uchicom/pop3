// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pop3Parameter のテスト. */
class Pop3ParameterTest {

  @Test
  void init_defaults() {
    var parameter = new Pop3Parameter(new String[0]);
    parameter.init();

    assertEquals("pool", parameter.get("type"));
    assertEquals(Constants.DEFAULT_TIMEOUT, parameter.get("timeout"));
    assertEquals(Constants.DEFAULT_MAX_LINE, parameter.get("maxLine"));
  }

  @Test
  void init_keepsSpecifiedValues() {
    var parameter =
        new Pop3Parameter(new String[] {"-type", "single", "-timeout", "1000", "-maxLine", "300"});
    parameter.init();

    assertEquals("single", parameter.get("type"));
    assertEquals("1000", parameter.get("timeout"));
    assertEquals("300", parameter.get("maxLine"));
  }

  @Test
  void createServer_unknownType() {
    var parameter = new Pop3Parameter(new String[] {"-type", "selector"});
    parameter.init();

    assertThrows(IllegalArgumentException.class, parameter::createServer);
  }
}
