// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pop3Session のコマンド処理テスト. */
class Pop3SessionTest {

  static final String TIMESTAMP = "<1.2@localhost>";
  static final String MAIL1 = "Subject: first\n\nbody1\nbody1-2\n";
  static final String MAIL2 = "Subject: second\n\nbody2\n";

  @TempDir File base;

  File mail1;
  File mail2;
  Pop3Session session;

  @BeforeEach
  void setUp() throws Exception {
    var box = new File(base, "alice");
    assertThat(box.mkdir()).isTrue();
    Files.writeString(new File(box, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    mail1 = new File(box, "mail1");
    mail2 = new File(box, "mail2");
    Files.writeString(mail1.toPath(), MAIL1);
    Files.writeString(mail2.toPath(), MAIL2);
    assertThat(mail1.setLastModified(1_000_000L)).isTrue();
    assertThat(mail2.setLastModified(2_000_000L)).isTrue();
    session = new Pop3Session(base, TIMESTAMP);
  }

  String run(String... lines) throws Exception {
    var out = new StringBuilder();
    for (var line : lines) {
      session.execute(line, out);
    }
    return out.toString();
  }

  void login() throws Exception {
    assertThat(run("USER alice", "PASS secret")).isEqualTo("+OK\r\n+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
  }

  @Test
  void login_failures() throws Exception {
    assertThat(run("PASS secret")).isEqualTo("-NG\r\n");
    assertThat(run("USER bob", "PASS secret")).isEqualTo("+OK\r\n-NG\r\n");
    assertThat(run("USER alice", "PASS wrong")).isEqualTo("+OK\r\n-NG\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void login_withoutPasswordFile() throws Exception {
    assertThat(new File(base, "bob").mkdir()).isTrue();

    assertThat(run("USER bob", "PASS secret")).isEqualTo("+OK\r\n-NG\r\n");
  }

  @Test
  void apop_success() throws Exception {
    var md = MessageDigest.getInstance("MD5").digest((TIMESTAMP + "secret").getBytes());
    var digest = new StringBuilder();
    for (var b : md) {
      digest.append(String.format("%02x", b & 0xFF));
    }

    assertThat(run("APOP alice " + digest)).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
    // 認証後の再認証はエラー
    assertThat(run("APOP alice " + digest)).isEqualTo("-NG\r\n");
  }

  @Test
  void apop_failure() throws Exception {
    assertThat(run("APOP alice 00", "APOP bob 00")).isEqualTo("-NG\r\n-NG\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void commands_requireAuthentication() throws Exception {
    for (var command :
        new String[] {"STAT", "LIST", "LIST 1", "RETR", "RETR 1", "DELE 1", "RSET", "TOP 1 0"}) {
      assertThat(run(command)).as(command).isEqualTo("-NG\r\n");
    }
    assertThat(run("UIDL", "UIDL 1")).isEqualTo("-NG\r\n-NG\r\n");
  }

  @Test
  void stat() throws Exception {
    login();

    assertThat(run("STAT")).isEqualTo("+OK 2 " + (octets(mail1) + octets(mail2)) + "\r\n");
  }

  @Test
  void list() throws Exception {
    login();

    assertThat(run("LIST"))
        .isEqualTo("+OK\r\n1 " + octets(mail1) + "\r\n2 " + octets(mail2) + "\r\n.\r\n");
    assertThat(run("LIST 2")).isEqualTo("+OK 2 " + octets(mail2) + "\r\n");
    assertThat(run("LIST 3")).isEqualTo("-NG\r\n");
  }

  @Test
  void retr() throws Exception {
    login();

    assertThat(run("RETR 1"))
        .isEqualTo("+OK " + octets(mail1) + "\r\nSubject: first\r\n\r\nbody1\r\nbody1-2\r\n.\r\n");
    assertThat(run("RETR 9")).isEqualTo("-NG\r\n");
    assertThat(run("RETR"))
        .isEqualTo(
            "+OK\r\nSubject: first\r\n\r\nbody1\r\nbody1-2\r\nSubject: second\r\n\r\nbody2\r\n.\r\n");
  }

  @Test
  void top() throws Exception {
    login();

    assertThat(run("TOP 1 0")).isEqualTo("+OK\r\nSubject: first\r\n\r\n.\r\n");
    assertThat(run("TOP 1 1")).isEqualTo("+OK\r\nSubject: first\r\n\r\nbody1\r\n.\r\n");
  }

  @Test
  void uidl() throws Exception {
    login();

    assertThat(run("UIDL")).isEqualTo("+OK\r\n1 mail1\r\n2 mail2\r\n.\r\n");
    assertThat(run("UIDL 2")).isEqualTo("+OK 2 mail2\r\n");
    assertThat(run("UIDL 3")).isEqualTo("-NG\r\n");
  }

  @Test
  void uidl_longNameIsTruncatedToLast70() throws Exception {
    var longName = "a".repeat(10) + "b".repeat(70);
    var longMail = new File(mail1.getParentFile(), longName);
    Files.writeString(longMail.toPath(), "x");
    assertThat(longMail.setLastModified(3_000_000L)).isTrue();
    login();

    assertThat(run("UIDL 3")).isEqualTo("+OK 3 " + "b".repeat(70) + "\r\n");
  }

  @Test
  void dele_rset_quit() throws Exception {
    login();

    assertThat(run("DELE 1")).isEqualTo("+OK\r\n");
    assertThat(run("STAT")).isEqualTo("+OK 1 " + octets(mail2) + "\r\n");
    assertThat(run("RETR 1", "LIST 1", "UIDL 1")).isEqualTo("-NG\r\n-NG\r\n-NG\r\n");
    assertThat(run("RSET")).isEqualTo("+OK\r\n");
    assertThat(run("STAT")).isEqualTo("+OK 2 " + (octets(mail1) + octets(mail2)) + "\r\n");

    assertThat(run("DELE 1")).isEqualTo("+OK\r\n");
    assertThat(run("DELE 3")).isEqualTo("-NG\r\n");
    assertThat(session.isFinished()).isFalse();
    assertThat(run("QUIT")).isEqualTo("+OK\r\n");
    assertThat(session.isFinished()).isTrue();
    assertThat(mail1).doesNotExist();
    assertThat(mail2).exists();
  }

  @Test
  void invalidNumber_returnsNg() throws Exception {
    login();
    var count = 0;
    for (var command :
        new String[] {
          "LIST 99999999999",
          "RETR 99999999999",
          "DELE 99999999999",
          "UIDL 99999999999",
          "TOP 1 99999999999",
          "TOP 99999999999 1"
        }) {
      assertThat(run(command)).as(command).isEqualTo("-NG\r\n");
      count++;
    }
    assertThat(count).isEqualTo(6);
  }

  @Test
  void missingArgument_isRejected() throws Exception {
    assertThat(run("USER", "PASS", "APOP alice"))
        .isEqualTo(Constants.RECV_NG_CMD_NOT_FOUND.concat("\r\n").repeat(3));
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void noop_emptyLine_unknownCommand() throws Exception {
    assertThat(run("NOOP")).isEqualTo("+OK\r\n");
    assertThat(run("")).isEmpty();
    assertThat(run("FOO")).isEqualTo(Constants.RECV_NG_CMD_NOT_FOUND + "\r\n");
  }

  /** 送信されるオクテット数(改行はCRLF換算). */
  static long octets(File file) throws Exception {
    long length = 0;
    for (var line :
        Files.readString(file.toPath(), StandardCharsets.ISO_8859_1).split("\r\n|\n|\r")) {
      length += line.length() + 2;
    }
    return length;
  }

  void writeMail(String content) throws Exception {
    Files.write(mail1.toPath(), content.getBytes(StandardCharsets.ISO_8859_1));
    assertThat(mail1.setLastModified(1_000_000L)).isTrue();
  }

  @Test
  void retr_dotStuffing() throws Exception {
    writeMail("Subject: dot\n\n.\n.hidden\n..twice\nend\n");
    login();

    assertThat(run("RETR 1"))
        .isEqualTo(
            "+OK "
                + octets(mail1)
                + "\r\nSubject: dot\r\n\r\n..\r\n..hidden\r\n...twice\r\nend\r\n.\r\n");
  }

  @Test
  void top_dotStuffing() throws Exception {
    writeMail("Subject: dot\n\n.\n.x\n");
    login();

    assertThat(run("TOP 1 2")).isEqualTo("+OK\r\nSubject: dot\r\n\r\n..\r\n..x\r\n.\r\n");
  }

  @Test
  void retr_preservesBinaryBytes() throws Exception {
    // 8bit と不正な UTF-8 のバイト列がそのまま通ること
    writeMail("Subject: bin\n\néÿ\u0080\n");
    login();

    assertThat(run("RETR 1")).isEqualTo("+OK 21\r\nSubject: bin\r\n\r\néÿ\u0080\r\n.\r\n");
  }

  @Test
  void retr_normalizesLineEndingsAndAddsFinalNewline() throws Exception {
    writeMail("a\r\nb\nc");
    login();

    assertThat(run("RETR 1")).isEqualTo("+OK 9\r\na\r\nb\r\nc\r\n.\r\n");
    assertThat(run("LIST 1")).isEqualTo("+OK 1 9\r\n");
    assertThat(run("STAT")).isEqualTo("+OK 2 " + (9 + octets(mail2)) + "\r\n");
  }
}
