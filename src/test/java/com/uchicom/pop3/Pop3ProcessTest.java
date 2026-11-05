// (C) 2026 uchicom
package com.uchicom.pop3;

import static com.uchicom.pop3.Pop3SessionTest.octets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ループバックソケットで Pop3Process を実際に動かすテスト. */
class Pop3ProcessTest {

  static final String MAIL1 = "Subject: first\n\nbody1\nbody1-2\n";
  static final String MAIL2 = "Subject: second\n\nbody2\n";

  @TempDir File base;

  File mail1;
  File mail2;
  Socket client;
  Thread serverThread;
  ServerSocket serverSocket;
  BufferedReader in;
  PrintStream out;
  String greeting;

  @BeforeEach
  void setUp() throws Exception {
    var box = new File(base, "alice");
    assertThat(box.mkdir()).isTrue();
    Files.writeString(new File(box, Constants.PASSWORD_FILE_NAME).toPath(), "secret");
    mail1 = new File(box, "mail1");
    mail2 = new File(box, "mail2");
    Files.writeString(mail1.toPath(), MAIL1);
    Files.writeString(mail2.toPath(), MAIL2);
    // 並び順は更新日時の昇順
    assertThat(mail1.setLastModified(1_000_000L)).isTrue();
    assertThat(mail2.setLastModified(2_000_000L)).isTrue();

    var parameter = new Pop3Parameter(new String[0]);
    parameter.put("dir", base.getAbsolutePath());
    // コマンドライン起動時と同じく init() 経由でデフォルト値(host など)を設定する
    parameter.init();
    greeting = startServer(parameter);
  }

  /** サーバを起動し、接続時の挨拶行を返す. */
  String startServer(Pop3Parameter parameter) throws Exception {
    serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    client = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
    var accepted = serverSocket.accept();
    serverThread = new Thread(() -> new Pop3Process(parameter, accepted).execute());
    serverThread.start();

    client.setSoTimeout(5000);
    in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
    out = new PrintStream(client.getOutputStream(), true, StandardCharsets.UTF_8);
    var line = in.readLine();
    assertThat(line).startsWith("+OK");
    return line;
  }

  @AfterEach
  void tearDown() throws Exception {
    client.close();
    serverSocket.close();
    serverThread.join(5000);
  }

  String send(String command) throws IOException {
    out.print(command + "\r\n");
    out.flush();
    return in.readLine();
  }

  /** "." 行までの複数行応答を読む(終端のドットは含まない). */
  List<String> readMultiLine() throws IOException {
    var lines = new ArrayList<String>();
    String line = in.readLine();
    while (line != null && !".".equals(line)) {
      lines.add(line);
      line = in.readLine();
    }
    return lines;
  }

  void login() throws IOException {
    assertThat(send("USER alice")).startsWith("+OK");
    assertThat(send("PASS secret")).startsWith("+OK");
  }

  @Test
  void greeting_containsHostFromInit() {
    assertThat(greeting).matches("\\+OK <[0-9a-f]{32}\\.\\d+@localhost>.*");
  }

  @Test
  void newTimestamp_isUnpredictableAndUnique() {
    var first = Pop3Process.newTimestamp("localhost");
    var second = Pop3Process.newTimestamp("localhost");

    assertThat(first).matches("<[0-9a-f]{32}\\.\\d+@localhost>").isNotEqualTo(second);
  }

  @Test
  void login_success() throws Exception {
    login();
  }

  @Test
  void login_wrongPassword() throws Exception {
    assertThat(send("USER alice")).startsWith("+OK");
    assertThat(send("PASS wrong")).startsWith("-ERR");
  }

  @Test
  void login_unknownUser() throws Exception {
    assertThat(send("USER bob")).startsWith("+OK");
    assertThat(send("PASS secret")).startsWith("-ERR");
  }

  @Test
  void pass_withoutUser() throws Exception {
    assertThat(send("PASS secret")).startsWith("-ERR");
  }

  @Test
  void commands_requireAuthentication() throws Exception {
    for (var command :
        new String[] {"STAT", "LIST", "LIST 1", "RETR 1", "DELE 1", "RSET", "TOP 1 0", "UIDL"}) {
      assertThat(send(command)).as(command).startsWith("-ERR");
    }
  }

  @Test
  void unknownCommand() throws Exception {
    assertThat(send("FOO")).isEqualTo(Constants.RECV_ERR_CMD_NOT_FOUND);
  }

  @Test
  void stat() throws Exception {
    login();

    var total = octets(mail1) + octets(mail2);
    assertThat(send("STAT")).isEqualTo("+OK 2 " + total);
  }

  @Test
  void list() throws Exception {
    login();

    assertThat(send("LIST")).startsWith("+OK");
    assertThat(readMultiLine()).containsExactly("1 " + octets(mail1), "2 " + octets(mail2));
  }

  @Test
  void listNum() throws Exception {
    login();

    assertThat(send("LIST 2")).isEqualTo("+OK 2 " + octets(mail2));
    assertThat(send("LIST 3")).startsWith("-ERR");
  }

  @Test
  void retrNum() throws Exception {
    login();

    assertThat(send("RETR 1")).isEqualTo("+OK " + octets(mail1));
    assertThat(readMultiLine()).containsExactly("Subject: first", "", "body1", "body1-2");
    assertThat(send("RETR 9")).startsWith("-ERR");
  }

  @Test
  void top() throws Exception {
    login();

    assertThat(send("TOP 1 0")).startsWith("+OK");
    assertThat(readMultiLine()).containsExactly("Subject: first", "");
  }

  @Test
  void uidl() throws Exception {
    login();

    assertThat(send("UIDL")).startsWith("+OK");
    assertThat(readMultiLine())
        .containsExactly("1 " + Pop3SessionTest.uid("mail1"), "2 " + Pop3SessionTest.uid("mail2"));
    assertThat(send("UIDL 2")).isEqualTo("+OK 2 " + Pop3SessionTest.uid("mail2"));
    assertThat(send("UIDL 3")).startsWith("-ERR");
  }

  @Test
  void dele_hidesMailAndQuitDeletesFile() throws Exception {
    login();

    assertThat(send("DELE 1")).startsWith("+OK");
    assertThat(send("STAT")).isEqualTo("+OK 1 " + octets(mail2));
    assertThat(send("RETR 1")).startsWith("-ERR");
    assertThat(send("QUIT")).startsWith("+OK");
    serverThread.join(5000);

    assertThat(mail1).doesNotExist();
    assertThat(mail2).exists();
  }

  @Test
  void rset_cancelsDelete() throws Exception {
    login();

    assertThat(send("DELE 1")).startsWith("+OK");
    assertThat(send("RSET")).startsWith("+OK");
    assertThat(send("STAT")).isEqualTo("+OK 2 " + (octets(mail1) + octets(mail2)));
    assertThat(send("QUIT")).startsWith("+OK");
    serverThread.join(5000);

    assertThat(mail1).exists();
    assertThat(mail2).exists();
  }

  @Test
  void dele_outOfRange() throws Exception {
    login();

    assertThat(send("DELE 3")).startsWith("-ERR");
  }

  @Test
  void invalidInput_keepsConnection() throws Exception {
    login();

    for (var command :
        new String[] {
          "USER",
          "PASS",
          "RETR abc",
          "DELE 99999999999",
          "LIST 99999999999",
          "RETR 99999999999",
          "UIDL 99999999999",
          "TOP 1 x",
          "TOP 1 99999999999",
          "TOP 99999999999 1",
          "APOP alice"
        }) {
      assertThat(send(command)).as(command).startsWith("-");
    }
    // 接続が継続していること
    assertThat(send("STAT")).startsWith("+OK");
  }

  @Test
  void invalidInput_beforeLogin() throws Exception {
    assertThat(send("USER")).startsWith("-");
    assertThat(send("PASS")).startsWith("-");
    assertThat(send("USER alice")).startsWith("+OK");
    assertThat(send("PASS secret")).startsWith("+OK");
  }

  @Test
  void forceClose_doesNotThrowFromExecute() throws Exception {
    tearDown();
    var parameter = new Pop3Parameter(new String[0]);
    parameter.put("dir", base.getAbsolutePath());
    parameter.init();
    serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    client = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
    var accepted = serverSocket.accept();
    var process = new Pop3Process(parameter, accepted);
    var thrown = new AtomicReference<Throwable>();
    serverThread = new Thread(process::execute);
    serverThread.setUncaughtExceptionHandler((t, e) -> thrown.set(e));
    serverThread.start();
    client.setSoTimeout(5000);
    in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
    assertThat(in.readLine()).startsWith("+OK");

    process.forceClose();
    serverThread.join(5000);

    assertThat(serverThread.isAlive()).isFalse();
    assertThat(thrown.get()).isNull();
  }

  @Test
  void readLine_stripsCrLfAndLf() throws Exception {
    var reader = new BufferedReader(new java.io.StringReader("abc\r\ndef\nlast"));

    assertThat(Pop3Process.readLine(reader, 10)).isEqualTo("abc");
    assertThat(Pop3Process.readLine(reader, 10)).isEqualTo("def");
    assertThat(Pop3Process.readLine(reader, 10)).isEqualTo("last");
    assertThat(Pop3Process.readLine(reader, 10)).isNull();
  }

  @Test
  void readLine_limit() throws Exception {
    // 上限ちょうどは許可(CRLF 付きでも)、超過は例外
    var ok = new BufferedReader(new java.io.StringReader("12345\r\n"));
    assertThat(Pop3Process.readLine(ok, 5)).isEqualTo("12345");

    var ng = new BufferedReader(new java.io.StringReader("123456\r\n"));
    org.junit.jupiter.api.Assertions.assertThrows(
        Pop3Process.LineTooLongException.class, () -> Pop3Process.readLine(ng, 5));
  }

  @Test
  void longLineWithoutNewline_disconnects() throws Exception {
    tearDown();
    var parameter = new Pop3Parameter(new String[] {"-maxLine", "100"});
    parameter.put("dir", base.getAbsolutePath());
    parameter.init();
    startServer(parameter);

    // 改行を送らずに上限を超える入力
    out.print("A".repeat(200));
    out.flush();

    assertThat(in.readLine()).isNull();
    serverThread.join(5000);
    assertThat(serverThread.isAlive()).isFalse();
  }

  @Test
  void idleConnection_timesOut() throws Exception {
    tearDown();
    var parameter = new Pop3Parameter(new String[] {"-timeout", "300"});
    parameter.put("dir", base.getAbsolutePath());
    parameter.init();
    startServer(parameter);

    // 何も送らなければサーバ側から切断される
    assertThat(in.readLine()).isNull();
    serverThread.join(5000);
    assertThat(serverThread.isAlive()).isFalse();
  }

  /** 自己署名のJKSを生成し、TLS有効なサーバを起動する. */
  void startTlsServer() throws Exception {
    startTlsServer("JKS", Map.of(), false);
  }

  /** 自己署名のキーストアを生成し、TLS有効なサーバを起動する. 暗黙的TLSの場合は挨拶の前にハンドシェイクする. */
  void startTlsServer(String storeType, Map<String, String> options, boolean implicit)
      throws Exception {
    tearDown();
    var keyStore = new File(base, "test.keystore");
    var keytool = new File(System.getProperty("java.home"), "bin/keytool").getAbsolutePath();
    var process =
        new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-alias",
                "test",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-dname",
                "CN=localhost",
                "-validity",
                "2",
                "-storetype",
                storeType,
                "-keystore",
                keyStore.getAbsolutePath(),
                "-storepass",
                "changeit",
                "-keypass",
                "changeit")
            .redirectErrorStream(true)
            .start();
    process.getInputStream().readAllBytes();
    assertThat(process.waitFor()).isZero();

    var parameter = new Pop3Parameter(new String[0]);
    parameter.put("dir", base.getAbsolutePath());
    parameter.put("keyStoreName", keyStore.getAbsolutePath());
    parameter.put("keyStorePass", "changeit");
    options.forEach(parameter::put);
    parameter.init();
    if (implicit) {
      startImplicitTlsServer(parameter);
    } else {
      startServer(parameter);
    }
  }

  /** 接続直後にTLSハンドシェイクを行うサーバを起動し、挨拶行を検証する. */
  void startImplicitTlsServer(Pop3Parameter parameter) throws Exception {
    connectServer(parameter);
    upgradeClient();
    assertThat(in.readLine()).startsWith("+OK");
  }

  /** サーバを起動し、挨拶行を読まずにクライアントを接続する. */
  void connectServer(Pop3Parameter parameter) throws Exception {
    serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    client = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
    var accepted = serverSocket.accept();
    serverThread = new Thread(() -> new Pop3Process(parameter, accepted).execute());
    serverThread.start();
    client.setSoTimeout(5000);
  }

  static final String CIPHER_SUITE = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";

  static final TrustManager[] TRUST_ALL =
      new TrustManager[] {
        new X509TrustManager() {
          public void checkClientTrusted(X509Certificate[] chain, String authType) {}

          public void checkServerTrusted(X509Certificate[] chain, String authType) {}

          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        }
      };

  /** STLS成功後にクライアント側のストリームをTLSに切り替える. */
  void upgradeClient() throws Exception {
    var context = SSLContext.getInstance("TLS");
    context.init(null, TRUST_ALL, null);
    var ssl =
        (SSLSocket)
            context.getSocketFactory().createSocket(client, "localhost", client.getPort(), true);
    ssl.setUseClientMode(true);
    ssl.startHandshake();
    client = ssl;
    in = new BufferedReader(new InputStreamReader(ssl.getInputStream(), StandardCharsets.UTF_8));
    out = new PrintStream(ssl.getOutputStream(), true, StandardCharsets.UTF_8);
  }

  @Test
  void stls_capaAndLoginWorkAfterHandshake() throws Exception {
    startTlsServer();

    assertThat(send("CAPA")).startsWith("+OK");
    assertThat(readMultiLine()).contains("STLS");
    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    assertThat(send("CAPA")).startsWith("+OK");
    assertThat(readMultiLine()).doesNotContain("STLS");
    login();
    assertThat(send("STAT")).startsWith("+OK 2");
  }

  @Test
  void stls_capaAdvertisesScramPlusOnlyAfterTls() throws Exception {
    startTlsServer();

    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    assertThat(send("CAPA")).startsWith("+OK");
    assertThat(readMultiLine())
        .anyMatch(l -> l.startsWith("SASL ") && l.contains(" SCRAM-SHA-256-PLUS "));
  }

  @Test
  void stls_rejectsScramPlusBeforeTls() throws Exception {
    startTlsServer();

    assertThat(send("AUTH SCRAM-SHA-256-PLUS")).startsWith("-ERR [AUTH]");
  }

  @Test
  void stls_authScramPlusWithServerCertificateHash() throws Exception {
    startTlsServer();
    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();
    var certificate = ((SSLSocket) client).getSession().getPeerCertificates()[0];
    var binding = ChannelBinding.serverEndPoint(certificate);
    var gs2Header = ("p=" + ChannelBinding.TYPE + ",,").getBytes(StandardCharsets.US_ASCII);
    var channelBinding = new byte[gs2Header.length + binding.length];
    System.arraycopy(gs2Header, 0, channelBinding, 0, gs2Header.length);
    System.arraycopy(binding, 0, channelBinding, gs2Header.length, binding.length);

    var clientFirstBare = "n=alice,r=clientnonce";
    var encoder = java.util.Base64.getEncoder();
    var reply =
        send(
            "AUTH SCRAM-SHA-256-PLUS "
                + encoder.encodeToString(
                    (new String(gs2Header, StandardCharsets.US_ASCII) + clientFirstBare)
                        .getBytes(StandardCharsets.UTF_8)));
    assertThat(reply).startsWith("+ ");
    var serverFirst =
        new String(
            java.util.Base64.getDecoder().decode(reply.substring(2).trim()),
            StandardCharsets.UTF_8);
    var attrs = serverFirst.split(",");
    var nonce = attrs[0].substring(2);
    var salt = java.util.Base64.getDecoder().decode(attrs[1].substring(2));
    var iterations = Integer.parseInt(attrs[2].substring(2));
    var algorithm = ScramAlgorithm.SHA_256;
    var credential = algorithm.derive("secret", salt, iterations);
    var saltedPassword = algorithm.hi("secret".getBytes(StandardCharsets.UTF_8), salt, iterations);
    var clientKey = algorithm.hmac(saltedPassword, "Client Key".getBytes());
    var withoutProof = "c=" + encoder.encodeToString(channelBinding) + ",r=" + nonce;
    var signature =
        algorithm.hmac(
            credential.storedKey(),
            (clientFirstBare + "," + serverFirst + "," + withoutProof).getBytes());
    var proof = new byte[clientKey.length];
    for (int i = 0; i < proof.length; i++) {
      proof[i] = (byte) (clientKey[i] ^ signature[i]);
    }

    assertThat(
            send(
                encoder.encodeToString(
                    (withoutProof + ",p=" + encoder.encodeToString(proof))
                        .getBytes(StandardCharsets.UTF_8))))
        .startsWith("+ ");
    assertThat(send("")).startsWith("+OK");
    assertThat(send("STAT")).startsWith("+OK 2");
  }

  @Test
  void stls_rejectsAuthenticationBeforeTls() throws Exception {
    startTlsServer();

    assertThat(send("USER alice")).startsWith("-ERR [AUTH]");
    assertThat(send("PASS secret")).startsWith("-ERR [AUTH]");
    assertThat(send("APOP alice 0123456789abcdef0123456789abcdef")).startsWith("-ERR [AUTH]");
    assertThat(send("AUTH SCRAM-SHA-256")).startsWith("-ERR [AUTH]");
    assertThat(send("STAT")).startsWith("-ERR");

    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    // TLS確立後は認証できる
    assertThat(send("PASS secret")).startsWith("-ERR");
    login();
  }

  @Test
  void stls_authPlainAndLoginAfterTlsOnly() throws Exception {
    startTlsServer();

    assertThat(send("AUTH PLAIN AGFsaWNlAHNlY3JldA==")).startsWith("-ERR [AUTH]");
    assertThat(send("AUTH LOGIN")).startsWith("-ERR [AUTH]");
    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    assertThat(send("CAPA")).startsWith("+OK");
    assertThat(readMultiLine()).anyMatch(l -> l.startsWith("SASL ") && l.endsWith(" PLAIN LOGIN"));
    assertThat(send("AUTH PLAIN AGFsaWNlAHNlY3JldA==")).startsWith("+OK");
    assertThat(send("STAT")).startsWith("+OK 2");
  }

  @Test
  void stls_authLoginAfterTls() throws Exception {
    startTlsServer();
    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    assertThat(send("AUTH LOGIN")).isEqualTo("+ VXNlcm5hbWU6");
    assertThat(send("YWxpY2U=")).isEqualTo("+ UGFzc3dvcmQ6");
    assertThat(send("c2VjcmV0")).startsWith("+OK");
    assertThat(send("STAT")).startsWith("+OK 2");
  }

  @Test
  void stls_pkcs12KeyStore() throws Exception {
    startTlsServer("PKCS12", Map.of("keyStoreType", "PKCS12"), false);

    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();
    login();
    assertThat(send("STAT")).startsWith("+OK 2");
  }

  @Test
  void stls_restrictsProtocolsToTls12AndAbove() throws Exception {
    startTlsServer();

    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    assertThat(((SSLSocket) client).getSession().getProtocol()).isIn("TLSv1.3", "TLSv1.2");
  }

  @Test
  void stls_serverRejectsClientWithOnlyOldProtocol() throws Exception {
    startTlsServer();
    assertThat(send("STLS")).startsWith("+OK");

    var context = SSLContext.getInstance("TLS");
    context.init(null, TRUST_ALL, null);
    var ssl =
        (SSLSocket)
            context.getSocketFactory().createSocket(client, "localhost", client.getPort(), true);
    ssl.setUseClientMode(true);
    ssl.setEnabledProtocols(new String[] {"TLSv1.1"});

    assertThrows(IOException.class, ssl::startHandshake);
  }

  @Test
  void stls_cipherSuitesCanBeSpecified() throws Exception {
    startTlsServer("JKS", Map.of("tlsProtocols", "TLSv1.2", "cipherSuites", CIPHER_SUITE), false);

    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    var session = ((SSLSocket) client).getSession();
    assertThat(session.getProtocol()).isEqualTo("TLSv1.2");
    assertThat(session.getCipherSuite()).isEqualTo(CIPHER_SUITE);
  }

  @Test
  void implicitTls_greetsOverTlsAndAcceptsAuth() throws Exception {
    startTlsServer("JKS", Map.of("implicitTls", "true"), true);

    assertThat(send("CAPA")).startsWith("+OK");
    var capa = readMultiLine();
    assertThat(capa).doesNotContain("STLS");
    assertThat(capa).anyMatch(l -> l.startsWith("SASL ") && l.endsWith(" PLAIN LOGIN"));
    assertThat(send("STLS")).startsWith("-ERR");
    assertThat(send("AUTH PLAIN AGFsaWNlAHNlY3JldA==")).startsWith("+OK");
    assertThat(send("STAT")).startsWith("+OK 2");
  }

  @Test
  void implicitTls_plainTextClientIsDisconnected() throws Exception {
    startTlsServer();
    tearDown();
    var parameter = new Pop3Parameter(new String[0]);
    parameter.put("dir", base.getAbsolutePath());
    parameter.put("keyStoreName", new File(base, "test.keystore").getAbsolutePath());
    parameter.put("keyStorePass", "changeit");
    parameter.put("implicitTls", "true");
    parameter.init();
    connectServer(parameter);
    client.getOutputStream().write("USER alice\r\n".getBytes(StandardCharsets.UTF_8));
    client.getOutputStream().flush();

    // ハンドシェイクできないため、平文の+OKは返らず切断される
    try {
      var reply = client.getInputStream().readAllBytes();
      assertThat(new String(reply, StandardCharsets.ISO_8859_1)).doesNotContain("+OK");
    } catch (IOException e) {
      // 切断(リセット)でもよい
    }
    serverThread.join(5000);
    assertThat(serverThread.isAlive()).isFalse();
  }

  @Test
  void sslContext_isCreatedOnceAndShared() throws Exception {
    startTlsServer();
    var parameter = new Pop3Parameter(new String[0]);
    parameter.put("dir", base.getAbsolutePath());
    parameter.put("keyStoreName", new File(base, "test.keystore").getAbsolutePath());
    parameter.put("keyStorePass", "changeit");
    parameter.init();

    assertThat(parameter.getSslContext()).isNotNull().isSameAs(parameter.getSslContext());
  }
}
