// (C) 2013 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pop3Handler のテスト. */
class Pop3HandlerTest {

  @TempDir File base;

  @Test
  void greeting_containsTimestamp() {
    var handler = new Pop3Handler(base, "localhost");

    assertThat(handler.strBuff.toString()).matches("\\+OK <\\d+\\.\\d+@localhost>\r\n");
  }
}
