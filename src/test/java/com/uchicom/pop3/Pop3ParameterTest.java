// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.server.VirtualSocketServer;
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
  void init_tlsDefaults() {
    var parameter = new Pop3Parameter(new String[0]);
    parameter.init();

    assertEquals(Constants.DEFAULT_KEY_STORE_TYPE, parameter.get("keyStoreType"));
    assertEquals("TLSv1.3,TLSv1.2", parameter.get("tlsProtocols"));
    assertNull(parameter.getSslContext());
    assertFalse(parameter.isImplicitTls());
  }

  @Test
  void init_implicitTlsRequiresKeyStore() {
    var parameter = new Pop3Parameter(new String[] {"-implicitTls", "true"});

    assertThrows(IllegalArgumentException.class, parameter::init);
  }

  @Test
  void init_invalidKeyStoreFailsAtStartup() {
    var parameter =
        new Pop3Parameter(new String[] {"-keyStoreName", "no-such-file", "-keyStorePass", "x"});

    assertThrows(IllegalArgumentException.class, parameter::init);
  }

  @Test
  void createServer_virtual() {
    var parameter = new Pop3Parameter(new String[] {"-type", "virtual"});
    parameter.init();

    assertInstanceOf(VirtualSocketServer.class, parameter.createServer());
  }

  @Test
  void createServer_pool() {
    var parameter = new Pop3Parameter(new String[] {"-type", "pool"});
    parameter.init();

    assertInstanceOf(PoolSocketServer.class, parameter.createServer());
  }

  @Test
  void createServer_multi() {
    var parameter = new Pop3Parameter(new String[] {"-type", "multi"});
    parameter.init();

    assertInstanceOf(MultiSocketServer.class, parameter.createServer());
  }

  @Test
  void createServer_single() {
    var parameter = new Pop3Parameter(new String[] {"-type", "single"});
    parameter.init();

    assertInstanceOf(SingleSocketServer.class, parameter.createServer());
  }

  @Test
  void createServer_unknownType() {
    var parameter = new Pop3Parameter(new String[] {"-type", "selector"});
    parameter.init();

    assertThrows(IllegalArgumentException.class, parameter::createServer);
  }
}
