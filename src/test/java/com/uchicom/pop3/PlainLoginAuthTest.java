// (C) 2026 uchicom
package com.uchicom.pop3;

import static com.uchicom.pop3.ScramAuthTest.b64;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AUTH PLAIN / LOGIN のテスト. */
class PlainLoginAuthTest {

  @TempDir File base;

  Pop3Session session;

  @BeforeEach
  void setUp() throws Exception {
    var box = new File(base, "alice");
    assertThat(box.mkdir()).isTrue();
    Files.writeString(new File(box, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    session = new Pop3Session(base, "<1.2@localhost>");
    session.setTlsActive(true);
  }

  @AfterEach
  void tearDown() {
    session.close();
  }

  String run(String line) throws Exception {
    var out = new StringBuilder();
    session.execute(line, out);
    return out.toString();
  }

  @Test
  void capaAndAuthList_includePlainAndLoginOnlyOverTls() throws Exception {
    assertThat(run("CAPA")).contains(" PLAIN LOGIN\r\n");
    assertThat(run("AUTH")).contains("PLAIN\r\nLOGIN\r\n");

    session.setTlsActive(false);
    assertThat(run("CAPA")).doesNotContain("PLAIN").doesNotContain("LOGIN");
    assertThat(run("AUTH")).doesNotContain("PLAIN").doesNotContain("LOGIN");
  }

  @Test
  void plain_initialResponse() throws Exception {
    assertThat(run("AUTH PLAIN " + b64("\0alice\0secret"))).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
  }

  @Test
  void plain_withoutInitialResponse() throws Exception {
    assertThat(run("AUTH plain")).isEqualTo("+ \r\n");
    assertThat(run(b64("alice\0alice\0secret"))).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
  }

  @Test
  void plain_rejectsInvalid() throws Exception {
    for (var message :
        new String[] {
          "\0alice\0wrong",
          "\0bob\0secret",
          "\0alice\0",
          "\0\0secret",
          "bob\0alice\0secret",
          "alice\0secret",
          "\0alice\0sec\0ret"
        }) {
      assertThat(run("AUTH PLAIN " + b64(message))).as(message).isEqualTo("-ERR\r\n");
      assertThat(session.isAuthenticated()).isFalse();
    }
  }

  @Test
  void plain_cancelAndInvalidBase64() throws Exception {
    assertThat(run("AUTH PLAIN")).isEqualTo("+ \r\n");
    assertThat(run("*")).isEqualTo("-ERR\r\n");
    assertThat(run("AUTH PLAIN !!!")).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void login_stepByStep() throws Exception {
    assertThat(run("AUTH LOGIN")).isEqualTo("+ " + b64("Username:") + "\r\n");
    assertThat(run(b64("alice"))).isEqualTo("+ " + b64("Password:") + "\r\n");
    assertThat(run(b64("secret"))).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
  }

  @Test
  void login_initialResponseIsUsername() throws Exception {
    assertThat(run("AUTH LOGIN " + b64("alice"))).isEqualTo("+ " + b64("Password:") + "\r\n");
    assertThat(run(b64("secret"))).isEqualTo("+OK\r\n");
  }

  @Test
  void login_rejectsWrongPasswordAndUnknownUser() throws Exception {
    run("AUTH LOGIN");
    run(b64("alice"));
    assertThat(run(b64("wrong"))).isEqualTo("-ERR\r\n");

    run("AUTH LOGIN");
    run(b64("bob"));
    assertThat(run(b64("secret"))).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void plainAndLogin_rejectedWithoutTls() throws Exception {
    session.setTlsActive(false);

    assertThat(run("AUTH PLAIN " + b64("\0alice\0secret"))).isEqualTo("-ERR\r\n");
    assertThat(run("AUTH LOGIN")).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void failures_countTowardLimit() throws Exception {
    session.setMaxAuthFailures(2);

    run("AUTH PLAIN " + b64("\0alice\0wrong"));
    assertThat(session.isFinished()).isFalse();
    run("AUTH PLAIN " + b64("\0alice\0wrong"));
    assertThat(session.isFinished()).isTrue();
  }

  @Test
  void authAfterSuccess_isRejected() throws Exception {
    run("AUTH PLAIN " + b64("\0alice\0secret"));

    assertThat(run("AUTH PLAIN " + b64("\0alice\0secret"))).isEqualTo("-ERR\r\n");
  }
}
