// (C) 2026 uchicom
package com.uchicom.pop3;

import static com.uchicom.pop3.Pop3SessionTest.octets;
import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(greeting).matches("\\+OK <\\d+\\.\\d+@localhost>.*");
  }

  @Test
  void login_success() throws Exception {
    login();
  }

  @Test
  void login_wrongPassword() throws Exception {
    assertThat(send("USER alice")).startsWith("+OK");
    assertThat(send("PASS wrong")).startsWith("-NG");
  }

  @Test
  void login_unknownUser() throws Exception {
    assertThat(send("USER bob")).startsWith("+OK");
    assertThat(send("PASS secret")).startsWith("-NG");
  }

  @Test
  void pass_withoutUser() throws Exception {
    assertThat(send("PASS secret")).startsWith("-NG");
  }

  @Test
  void commands_requireAuthentication() throws Exception {
    for (var command :
        new String[] {"STAT", "LIST", "LIST 1", "RETR 1", "DELE 1", "RSET", "TOP 1 0", "UIDL"}) {
      assertThat(send(command)).as(command).startsWith("-NG");
    }
  }

  @Test
  void unknownCommand() throws Exception {
    assertThat(send("FOO")).isEqualTo(Constants.RECV_NG_CMD_NOT_FOUND);
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
    assertThat(send("LIST 3")).startsWith("-NG");
  }

  @Test
  void retrNum() throws Exception {
    login();

    assertThat(send("RETR 1")).isEqualTo("+OK " + octets(mail1));
    assertThat(readMultiLine()).containsExactly("Subject: first", "", "body1", "body1-2");
    assertThat(send("RETR 9")).startsWith("-NG");
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
    assertThat(readMultiLine()).containsExactly("1 mail1", "2 mail2");
    assertThat(send("UIDL 2")).isEqualTo("+OK 2 mail2");
    assertThat(send("UIDL 3")).startsWith("-NG");
  }

  @Test
  void dele_hidesMailAndQuitDeletesFile() throws Exception {
    login();

    assertThat(send("DELE 1")).startsWith("+OK");
    assertThat(send("STAT")).isEqualTo("+OK 1 " + octets(mail2));
    assertThat(send("RETR 1")).startsWith("-NG");
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

    assertThat(send("DELE 3")).startsWith("-NG");
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

  /** 自己署名のJKSを生成し、TLS有効なサーバを起動する. */
  void startTlsServer() throws Exception {
    tearDown();
    var keyStore = new File(base, "test.jks");
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
                "JKS",
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
    parameter.init();
    startServer(parameter);
  }

  /** STLS成功後にクライアント側のストリームをTLSに切り替える. */
  void upgradeClient() throws Exception {
    var context = SSLContext.getInstance("TLS");
    context.init(
        null,
        new TrustManager[] {
          new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        },
        null);
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
  void stls_rejectsAuthenticationBeforeTls() throws Exception {
    startTlsServer();

    assertThat(send("USER alice")).startsWith("-ERR [AUTH]");
    assertThat(send("PASS secret")).startsWith("-ERR [AUTH]");
    assertThat(send("APOP alice 0123456789abcdef0123456789abcdef")).startsWith("-ERR [AUTH]");
    assertThat(send("AUTH SCRAM-SHA-256")).startsWith("-ERR [AUTH]");
    assertThat(send("STAT")).startsWith("-NG");

    assertThat(send("STLS")).startsWith("+OK");
    upgradeClient();

    // TLS確立後は認証できる
    assertThat(send("PASS secret")).startsWith("-NG");
    login();
  }
}
