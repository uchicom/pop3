// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Main のテスト. */
class MainTest {

  @Test
  void run_unknownType() {
    assertEquals(1, Main.run(new String[] {"-type", "unknown"}));
  }

  @Test
  void run_implicitTlsWithoutKeyStore() {
    assertEquals(1, Main.run(new String[] {"-implicitTls", "true"}));
  }

  @Test
  void shutdown_withoutServer() {
    Main.server = null;
    Main.shutdown();
  }
}
