// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CAPA、AUTH、SCRAM認証のテスト. */
class ScramAuthTest {

  @TempDir File base;

  Pop3Session session;

  @BeforeEach
  void setUp() throws Exception {
    var box = new File(base, "alice");
    assertThat(box.mkdir()).isTrue();
    Files.writeString(new File(box, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    session = new Pop3Session(base, "<1.2@localhost>");
  }

  String run(String line) throws Exception {
    var out = new StringBuilder();
    session.execute(line, out);
    return out.toString();
  }

  static String b64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  static String unb64(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  /** クライアント側の処理. 最後のserver-finalまで進め、AUTHの応答を返す. */
  String scram(ScramAlgorithm algorithm, String password, boolean initialResponse)
      throws Exception {
    var clientNonce = "clientnonce";
    var clientFirstBare = "n=alice,r=" + clientNonce;
    var clientFirst = "n,," + clientFirstBare;
    String reply;
    if (initialResponse) {
      reply = run("AUTH " + algorithm.getMechanismName() + " " + b64(clientFirst));
    } else {
      assertThat(run("AUTH " + algorithm.getMechanismName())).isEqualTo("+ \r\n");
      reply = run(b64(clientFirst));
    }
    assertThat(reply).startsWith("+ ");
    var serverFirst = unb64(reply.substring(2).trim());
    var attrs = serverFirst.split(",");
    var nonce = attrs[0].substring(2);
    var salt = Base64.getDecoder().decode(attrs[1].substring(2));
    var iterations = Integer.parseInt(attrs[2].substring(2));
    assertThat(nonce).startsWith(clientNonce);

    var credential = algorithm.derive(password, salt, iterations);
    var saltedPassword = algorithm.hi(password.getBytes(StandardCharsets.UTF_8), salt, iterations);
    var clientKey = algorithm.hmac(saltedPassword, "Client Key".getBytes());
    var withoutProof = "c=" + b64("n,,") + ",r=" + nonce;
    var authMessage = (clientFirstBare + "," + serverFirst + "," + withoutProof).getBytes();
    var signature = algorithm.hmac(credential.storedKey(), authMessage);
    var proof = new byte[clientKey.length];
    for (int i = 0; i < proof.length; i++) {
      proof[i] = (byte) (clientKey[i] ^ signature[i]);
    }
    var clientFinal = withoutProof + ",p=" + Base64.getEncoder().encodeToString(proof);
    reply = run(b64(clientFinal));
    if (!reply.startsWith("+ ")) {
      return reply;
    }
    var expected = algorithm.hmac(credential.serverKey(), authMessage);
    assertThat(unb64(reply.substring(2).trim()))
        .isEqualTo("v=" + Base64.getEncoder().encodeToString(expected));
    return run("");
  }

  @Test
  void capa() throws Exception {
    assertThat(run("CAPA"))
        .isEqualTo(
            "+OK Capability list follows\r\nTOP\r\nUSER\r\nUIDL\r\n"
                + "SASL SCRAM-SHA-512 SCRAM-SHA-384 SCRAM-SHA-256 SCRAM-SHA-224 SCRAM-SHA-1\r\n"
                + ".\r\n");

    session.setStlsAvailable(true);
    assertThat(run("CAPA")).contains("STLS\r\n.\r\n");
  }

  @Test
  void auth_list() throws Exception {
    assertThat(run("AUTH"))
        .isEqualTo(
            "+OK\r\nSCRAM-SHA-512\r\nSCRAM-SHA-384\r\nSCRAM-SHA-256\r\nSCRAM-SHA-224\r\nSCRAM-SHA-1\r\n.\r\n");
  }

  @Test
  void scram_success() throws Exception {
    for (var algorithm : ScramAlgorithm.values()) {
      session = new Pop3Session(base, "<1.2@localhost>");
      assertThat(scram(algorithm, "secret", false)).isEqualTo("+OK\r\n");
      assertThat(session.isAuthenticated()).isTrue();
      assertThat(run("STAT")).startsWith("+OK 0 0");
    }
  }

  @Test
  void scram_initialResponse() throws Exception {
    assertThat(scram(ScramAlgorithm.SHA_256, "secret", true)).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
    // 認証後の再認証はエラー
    assertThat(run("AUTH SCRAM-SHA-256")).isEqualTo("-NG\r\n");
  }

  @Test
  void scram_wrongPassword() throws Exception {
    assertThat(scram(ScramAlgorithm.SHA_256, "wrong", false)).isEqualTo("-NG\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void scram_unknownUser_isIndistinguishableUntilProof() throws Exception {
    var reply = run("AUTH SCRAM-SHA-1 " + b64("n,,n=nobody,r=abc"));
    assertThat(reply).startsWith("+ ");
    assertThat(unb64(reply.substring(2).trim())).matches("r=abc.+,s=.+,i=4096");
    assertThat(run(b64("c=biws,r=abc,p=AAAA"))).isEqualTo("-NG\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void auth_errors() throws Exception {
    assertThat(run("AUTH UNKNOWN")).isEqualTo("-NG\r\n");
    // PLUSは未対応
    assertThat(run("AUTH SCRAM-SHA-256-PLUS")).isEqualTo("-NG\r\n");
    // チャネルバインディング要求は拒否
    assertThat(run("AUTH SCRAM-SHA-256 " + b64("p=tls-unique,,n=alice,r=abc")))
        .isEqualTo("-NG\r\n");
    // 不正なbase64
    assertThat(run("AUTH SCRAM-SHA-256 !!!")).isEqualTo("-NG\r\n");
    // 中断
    assertThat(run("AUTH SCRAM-SHA-256")).isEqualTo("+ \r\n");
    assertThat(run("*")).isEqualTo("-NG\r\n");
    // 中断後は通常のコマンドに戻る
    assertThat(run("NOOP")).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  /** RFC 7677 の例(SCRAM-SHA-256)で資格情報の導出を検証する. */
  @Test
  void rfc7677_vector() {
    var algorithm = ScramAlgorithm.SHA_256;
    var salt = Base64.getDecoder().decode("W22ZaJ0SNY7soEsUEjb6gQ==");
    var credential = algorithm.derive("pencil", salt, 4096);
    var authMessage =
        ("n=user,r=rOprNGfwEbeRWgbNEkqO"
                + ",r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096"
                + ",c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0")
            .getBytes(StandardCharsets.UTF_8);

    var serverSignature = algorithm.hmac(credential.serverKey(), authMessage);

    assertThat(Base64.getEncoder().encodeToString(serverSignature))
        .isEqualTo("6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=");
  }
}
