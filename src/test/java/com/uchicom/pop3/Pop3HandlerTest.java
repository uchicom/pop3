// (C) 2013 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pop3Handler のコマンド処理テスト. */
class Pop3HandlerTest {

  @TempDir File base;

  Pop3Handler handler;

  @BeforeEach
  void setUp() throws Exception {
    var box = new File(base, "alice");
    assertThat(box.mkdir()).isTrue();
    Files.writeString(new File(box, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    Files.writeString(new File(box, "mail1").toPath(), "Subject: first\n\nbody1\n");

    handler = new Pop3Handler(base, "localhost");
    handler.strBuff.setLength(0);
    handler.user("USER alice");
    handler.pass("PASS secret");
    assertThat(handler.bPass).isTrue();
    handler.strBuff.setLength(0);
  }

  String output() {
    var result = handler.strBuff.toString();
    handler.strBuff.setLength(0);
    return result;
  }

  @Test
  void invalidNumber_returnsNg() throws Exception {
    assertThatCode(
            () -> {
              handler.listNum("LIST 99999999999");
              handler.retrNum("RETR abc");
              handler.deleNum("DELE 99999999999");
              handler.uidlNum("UIDL 99999999999");
              handler.topNumNum("TOP 1 x");
              handler.topNumNum("TOP 1 99999999999");
            })
        .doesNotThrowAnyException();

    assertThat(output()).isEqualTo(String.valueOf(Constants.RECV_NG_LINE_END).repeat(6));
  }

  @Test
  void missingArgument_doesNotThrow() throws Exception {
    var freshHandler = new Pop3Handler(base, "localhost");
    freshHandler.strBuff.setLength(0);

    assertThatCode(
            () -> {
              freshHandler.user("USER");
              freshHandler.pass("PASS");
              freshHandler.apopNameDigest("APOP alice");
            })
        .doesNotThrowAnyException();

    assertThat(freshHandler.bPass).isFalse();
    assertThat(freshHandler.strBuff.toString()).doesNotContain("+OK");
  }

  @Test
  void validNumber_stillWorks() throws Exception {
    handler.listNum("LIST 1");

    assertThat(output()).startsWith("+OK 1 ");
  }
}
