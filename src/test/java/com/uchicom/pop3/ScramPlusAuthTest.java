// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** SCRAM-*-PLUS(tls-server-end-point)のテスト. */
class ScramPlusAuthTest {

  static final byte[] BINDING = {10, 20, 30, 40, 50, (byte) 0xff, 0};

  static final String GS2_PLUS = "p=" + ChannelBinding.TYPE + ",,";

  @TempDir File base;

  Pop3Session session;

  @BeforeEach
  void setUp() throws Exception {
    var box = new File(base, "alice");
    assertThat(box.mkdir()).isTrue();
    Files.writeString(new File(box, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    session = new Pop3Session(base, "<1.2@localhost>");
    session.setTlsActive(true);
    session.setChannelBindingData(BINDING);
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

  static String b64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  static String unb64(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  /** クライアント側の処理. 最後のserver-finalまで進め、AUTHの応答を返す. */
  String scram(String mechanism, String gs2Header, byte[] clientBinding) throws Exception {
    var algorithm = ScramAlgorithm.SHA_256;
    var clientFirstBare = "n=alice,r=clientnonce";
    var reply = run("AUTH " + mechanism + " " + b64(gs2Header + clientFirstBare));
    if (!reply.startsWith("+ ")) {
      return reply;
    }
    var serverFirst = unb64(reply.substring(2).trim());
    var attrs = serverFirst.split(",");
    var nonce = attrs[0].substring(2);
    var salt = Base64.getDecoder().decode(attrs[1].substring(2));
    var iterations = Integer.parseInt(attrs[2].substring(2));

    var credential = algorithm.derive("secret", salt, iterations);
    var saltedPassword = algorithm.hi("secret".getBytes(StandardCharsets.UTF_8), salt, iterations);
    var clientKey = algorithm.hmac(saltedPassword, "Client Key".getBytes());
    var header = gs2Header.getBytes(StandardCharsets.ISO_8859_1);
    var channelBinding = new byte[header.length + clientBinding.length];
    System.arraycopy(header, 0, channelBinding, 0, header.length);
    System.arraycopy(clientBinding, 0, channelBinding, header.length, clientBinding.length);
    var withoutProof = "c=" + Base64.getEncoder().encodeToString(channelBinding) + ",r=" + nonce;
    var authMessage = (clientFirstBare + "," + serverFirst + "," + withoutProof).getBytes();
    var signature = algorithm.hmac(credential.storedKey(), authMessage);
    var proof = new byte[clientKey.length];
    for (int i = 0; i < proof.length; i++) {
      proof[i] = (byte) (clientKey[i] ^ signature[i]);
    }
    reply = run(b64(withoutProof + ",p=" + Base64.getEncoder().encodeToString(proof)));
    if (!reply.startsWith("+ ")) {
      return reply;
    }
    return run("");
  }

  @Test
  void capaAndAuthList_advertisePlusAfterTls() throws Exception {
    assertThat(run("CAPA"))
        .contains(
            "SASL SCRAM-SHA-512 SCRAM-SHA-512-PLUS SCRAM-SHA-384 SCRAM-SHA-384-PLUS"
                + " SCRAM-SHA-256 SCRAM-SHA-256-PLUS SCRAM-SHA-224 SCRAM-SHA-224-PLUS"
                + " SCRAM-SHA-1 SCRAM-SHA-1-PLUS PLAIN LOGIN\r\n");
    assertThat(run("AUTH")).contains("SCRAM-SHA-256\r\nSCRAM-SHA-256-PLUS\r\n");
  }

  @Test
  void capaAndAuthList_hidePlusWithoutBindingData() throws Exception {
    session.setChannelBindingData(null);

    assertThat(run("CAPA")).doesNotContain("-PLUS");
    assertThat(run("AUTH")).doesNotContain("-PLUS");
    assertThat(run("AUTH SCRAM-SHA-256-PLUS")).isEqualTo("-ERR\r\n");
  }

  @Test
  void capaAndAuthList_hidePlusWithoutTls() throws Exception {
    session.setTlsActive(false);

    assertThat(run("CAPA")).doesNotContain("-PLUS");
    assertThat(run("AUTH")).doesNotContain("-PLUS");
    assertThat(run("AUTH SCRAM-SHA-256-PLUS")).isEqualTo("-ERR\r\n");
  }

  @Test
  void plus_success() throws Exception {
    assertThat(scram("SCRAM-SHA-256-PLUS", GS2_PLUS, BINDING)).isEqualTo("+OK\r\n");
    assertThat(session.isAuthenticated()).isTrue();
  }

  @Test
  void plus_mechanismNameIsCaseInsensitive() throws Exception {
    assertThat(scram("scram-sha-256-plus", GS2_PLUS, BINDING)).isEqualTo("+OK\r\n");
  }

  @Test
  void plus_bindingMismatch() throws Exception {
    assertThat(scram("SCRAM-SHA-256-PLUS", GS2_PLUS, new byte[] {1, 2, 3})).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void plus_withoutBindingInFinal() throws Exception {
    assertThat(scram("SCRAM-SHA-256-PLUS", GS2_PLUS, new byte[0])).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void plus_requiresChannelBindingRequest() throws Exception {
    assertThat(scram("SCRAM-SHA-256-PLUS", "n,,", BINDING)).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void plus_rejectsYFlag() throws Exception {
    assertThat(scram("SCRAM-SHA-256-PLUS", "y,,", BINDING)).isEqualTo("-ERR\r\n");
  }

  @Test
  void plus_rejectsOtherBindingType() throws Exception {
    assertThat(scram("SCRAM-SHA-256-PLUS", "p=tls-unique,,", BINDING)).isEqualTo("-ERR\r\n");
    assertThat(scram("SCRAM-SHA-256-PLUS", "p=tls-exporter,,", BINDING)).isEqualTo("-ERR\r\n");
  }

  @Test
  void nonPlus_rejectsChannelBindingRequest() throws Exception {
    assertThat(scram("SCRAM-SHA-256", GS2_PLUS, BINDING)).isEqualTo("-ERR\r\n");
  }

  @Test
  void nonPlus_rejectsYWhenPlusIsAdvertised() throws Exception {
    assertThat(scram("SCRAM-SHA-256", "y,,", new byte[0])).isEqualTo("-ERR\r\n");
    assertThat(session.isAuthenticated()).isFalse();
  }

  @Test
  void nonPlus_acceptsNWhenPlusIsAdvertised() throws Exception {
    assertThat(scram("SCRAM-SHA-256", "n,,", new byte[0])).isEqualTo("+OK\r\n");
  }

  @Test
  void nonPlus_acceptsYWhenPlusIsNotAdvertised() throws Exception {
    session.setChannelBindingData(null);

    assertThat(scram("SCRAM-SHA-256", "y,,", new byte[0])).isEqualTo("+OK\r\n");
  }
}
