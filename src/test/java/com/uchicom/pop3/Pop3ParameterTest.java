// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pop3Parameter のテスト. */
class Pop3ParameterTest {

  @Test
  void createServer_unknownType() {
    var parameter = new Pop3Parameter(new String[] {"-type", "selector"});
    parameter.init();

    assertThrows(IllegalArgumentException.class, parameter::createServer);
  }
}
