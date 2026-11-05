// (C) 2014 uchicom
package com.uchicom.pop3;

import com.uchicom.server.ServerProcess;
import com.uchicom.util.Parameter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class Pop3Process implements ServerProcess {

  private static final Logger logger = Logger.getLogger(Pop3Process.class.getCanonicalName());

  private static final SecureRandom RANDOM = new SecureRandom();

  /** ファイルとUIDLで使用する日時フォーマット */
  //	private final SimpleDateFormat format = new SimpleDateFormat(Constants.DATE_TIME_MILI_FORMAT);
  private Parameter parameter;

  private Socket socket;

  /** socket の close/null 化を排他するロック(socket 自体は null になるためロックに使えない) */
  private final Object lock = new Object();

  /** tls-server-end-pointのバインディングデータ(TLS確立後。提示できない場合はnull) */
  private byte[] channelBindingData;

  /** 最終処理時刻 */
  private long lastTime = System.currentTimeMillis();

  /**
   * コンストラクタ.
   *
   * @param parameter パラメータ
   * @param socket 入出力ソケット
   */
  public Pop3Process(Parameter parameter, Socket socket) {
    this.parameter = parameter;
    this.socket = socket;
  }

  /** pop3処理. */
  public void execute() {
    logger.info(String.valueOf(socket.getRemoteSocketAddress()));
    // 0.はプロセスごとに変える番号だけど、とくに複数プロセスを持っていないので。
    String timestamp = newTimestamp(parameter.get("host"));
    BufferedReader br = null;
    PrintStream ps = null;
    Pop3Session session = null;
    try {
      socket.setSoTimeout(readTimeout());
      if (isImplicitTls() && hasKeyStore()) {
        // 暗黙的TLS:挨拶の前にハンドシェイクする
        socket = startTls();
        socket.setSoTimeout(readTimeout());
      }
      br = newReader(socket);
      ps = newPrintStream(socket);
      // 1接続に対する受付開始
      Pop3Util.receiveLine(ps, Constants.RECV_OK, " ", timestamp);
      int maxLine = maxLineLength();
      String line = readLine(br, maxLine);
      session = newSession(timestamp);
      while (line != null) {
        if (Pop3Util.isStls(line) && hasKeyStore() && isNotSslSocket()) {
          if (session.isAuthenticated()) {
            // 認証済みの状態でのSTLSは拒否する
            Pop3Util.receiveLine(ps, Constants.RECV_ERR);
          } else {
            Pop3Util.receiveLine(ps, Constants.RECV_OK);
            socket = startTls();
            socket.setSoTimeout(readTimeout());
            // 差し替え前の br/ps は close しない(下位の平文ソケットごと閉じてしまうため)。
            // 以降の入出力と終了時の close は TLS 側のストリームで行う
            br = newReader(socket);
            ps = newPrintStream(socket);
            // RFC 2595 4: TLS前に得た情報(USERなど)は破棄する
            session = newSession(timestamp);
          }
        } else {
          session.setStlsAvailable(hasKeyStore() && isNotSslSocket());
          session.setTlsActive(!isNotSslSocket());
          session.setChannelBindingData(channelBindingData);
          session.execute(line, ps);
          ps.flush();
          if (session.isFinished()) {
            break;
          }
        }
        lastTime = System.currentTimeMillis();
        line = readLine(br, maxLine);
      }
    } catch (SocketTimeoutException e) {
      logger.warning("read timeout");
    } catch (LineTooLongException e) {
      logger.warning("line too long: " + e.getMessage());
    } catch (IOException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    } catch (Throwable e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    } finally {
      if (session != null) {
        // メールボックスのロックを解放する
        session.close();
      }
      if (ps != null) {
        try {
          ps.close();
        } catch (Exception e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
      }
      if (br != null) {
        try {
          br.close();
        } catch (Exception e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
      }
      synchronized (lock) {
        if (socket != null) {
          try {
            socket.close();
          } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
          }
          socket = null;
        }
      }
    }
    logger.info("end");
  }

  /** 行長が上限を超えたことを表す例外(接続を切断する). */
  static class LineTooLongException extends IOException {
    LineTooLongException(int maxLine) {
      super("exceeds " + maxLine);
    }
  }

  /**
   * APOP用のタイムスタンプを生成する. SecureRandom により推測を困難にし、時刻も含めて一意とする(RFC 1939).
   *
   * @param host ホスト名
   * @return {@code <乱数.時刻@host>} 形式のタイムスタンプ
   */
  static String newTimestamp(String host) {
    byte[] random = new byte[16];
    RANDOM.nextBytes(random);
    StringBuilder sb = new StringBuilder("<");
    for (byte b : random) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.append('.')
        .append(System.currentTimeMillis())
        .append('@')
        .append(host)
        .append('>')
        .toString();
  }

  private Pop3Session newSession(String timestamp) {
    Pop3Session session = new Pop3Session(parameter.getFile("dir"), timestamp);
    session.setMaxAuthFailures(
        intParameter("maxAuthFailures", Constants.DEFAULT_MAX_AUTH_FAILURES));
    session.setAuthFailureDelay(
        intParameter("authFailureDelay", Constants.DEFAULT_AUTH_FAILURE_DELAY));
    session.setRemoteAddress(String.valueOf(socket.getRemoteSocketAddress()));
    return session;
  }

  /** 読み取りタイムアウト(ミリ秒)を取得する. 0 以下は無制限. */
  int readTimeout() {
    return intParameter("timeout", Constants.DEFAULT_TIMEOUT);
  }

  /** 1行の最大長(行終端を除く文字数)を取得する. */
  int maxLineLength() {
    return intParameter("maxLine", Constants.DEFAULT_MAX_LINE);
  }

  private int intParameter(String key, String defaultValue) {
    String value = parameter.get(key);
    return Integer.parseInt(value != null ? value : defaultValue);
  }

  /**
   * 上限付きで1行読み込む. LF までを1行とし、末尾の CR は取り除く.
   *
   * @return 行(行終端を除く)。入力終端なら null
   * @throws LineTooLongException 行終端を除いた長さが maxLine を超えた場合
   */
  static String readLine(BufferedReader br, int maxLine) throws IOException {
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = br.read()) != -1) {
      if (c == '\n') {
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\r') {
          sb.setLength(len - 1);
        }
        return sb.toString();
      }
      // 末尾の CR 1文字分の余裕を持たせる
      if (sb.length() >= maxLine + 1) {
        throw new LineTooLongException(maxLine);
      }
      sb.append((char) c);
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  /** 入出力で使用する文字コード. */
  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private static BufferedReader newReader(Socket socket) throws IOException {
    return new BufferedReader(new InputStreamReader(socket.getInputStream(), CHARSET));
  }

  private static PrintStream newPrintStream(Socket socket) throws IOException {
    // メール本文をバイト透過で送るため、出力は ISO_8859_1 とする
    return new PrintStream(socket.getOutputStream(), false, StandardCharsets.ISO_8859_1);
  }

  /**
   * 最終処理時刻を取得します.
   *
   * @return 最終処理時刻
   */
  public long getLastTime() {
    return lastTime;
  }

  /** 強制終了. */
  public void forceClose() {
    // if (rejectMap.containsKey(senderAddress)) {
    // rejectMap.put(senderAddress, rejectMap.get(senderAddress) + 1);
    // } else {
    // rejectMap.put(senderAddress, 1);
    // }
    logger.warning("forceClose!");
    synchronized (lock) {
      if (socket != null && socket.isConnected()) {
        try {
          socket.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, e.getMessage(), e);
        }
        socket = null;
      }
    }
  }

  /** 共有の SSLContext でソケットを TLS サーバーモードに切り替え、ハンドシェイクを行う. */
  SSLSocket startTls() throws IOException {
    SSLSocket sslSocket =
        (SSLSocket)
            sslContext()
                .getSocketFactory()
                .createSocket(
                    socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);

    sslSocket.setUseClientMode(false);
    sslSocket.setEnabledProtocols(enabledProtocols(sslSocket));
    String cipherSuites = parameter.get("cipherSuites");
    if (cipherSuites != null) {
      sslSocket.setEnabledCipherSuites(cipherSuites.split(" *, *"));
    }
    sslSocket.startHandshake();
    channelBindingData = channelBindingData(sslSocket);

    return sslSocket;
  }

  /** サーバー証明書から tls-server-end-point のバインディングデータを求める. 求められない場合はnull. */
  private byte[] channelBindingData(SSLSocket sslSocket) {
    Certificate[] certificates = sslSocket.getSession().getLocalCertificates();
    if (certificates == null || certificates.length == 0) {
      return null;
    }
    return ChannelBinding.serverEndPoint(certificates[0]);
  }

  /** 指定プロトコルのうち、実行環境が対応するものを返す. 1つも対応しない場合は例外. */
  private String[] enabledProtocols(SSLSocket sslSocket) throws IOException {
    String value = parameter.get("tlsProtocols");
    List<String> supported = Arrays.asList(sslSocket.getSupportedProtocols());
    List<String> enabled = new ArrayList<>();
    for (String protocol :
        (value != null ? value : Constants.DEFAULT_TLS_PROTOCOLS).split(" *, *")) {
      if (supported.contains(protocol)) {
        enabled.add(protocol);
      }
    }
    if (enabled.isEmpty()) {
      throw new IOException("no supported tls protocol: " + value);
    }
    return enabled.toArray(new String[0]);
  }

  private SSLContext sslContext() throws IOException {
    SSLContext sslContext =
        parameter instanceof Pop3Parameter ? ((Pop3Parameter) parameter).getSslContext() : null;
    if (sslContext == null) {
      throw new IOException("ssl context is not initialized");
    }
    return sslContext;
  }

  boolean hasKeyStore() {
    return parameter.get("keyStoreName") != null && parameter.get("keyStorePass") != null;
  }

  /** 暗黙的TLS(接続直後からTLS)で待ち受けるかどうか. */
  boolean isImplicitTls() {
    return "true".equalsIgnoreCase(parameter.get("implicitTls"));
  }

  boolean isNotSslSocket() {
    return !(socket instanceof SSLSocket);
  }
}
