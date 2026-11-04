// (C) 2014 uchicom
package com.uchicom.pop3;

import com.uchicom.server.ServerProcess;
import com.uchicom.util.Parameter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class Pop3Process implements ServerProcess {

  private static final Logger logger = Logger.getLogger(Pop3Process.class.getCanonicalName());

  /** ファイルとUIDLで使用する日時フォーマット */
  //	private final SimpleDateFormat format = new SimpleDateFormat(Constants.DATE_TIME_MILI_FORMAT);
  private Parameter parameter;

  private Socket socket;

  /** socket の close/null 化を排他するロック(socket 自体は null になるためロックに使えない) */
  private final Object lock = new Object();

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
    String timestamp =
        "<"
            + Thread.currentThread().getId()
            + "."
            + System.currentTimeMillis()
            + "@"
            + parameter.get("host")
            + ">";
    BufferedReader br = null;
    PrintStream ps = null;
    Pop3Session session = null;
    try {
      socket.setSoTimeout(readTimeout());
      br = newReader(socket);
      ps = newPrintStream(socket);
      // 1接続に対する受付開始
      Pop3Util.recieveLine(ps, Constants.RECV_OK, " ", timestamp);
      int maxLine = maxLineLength();
      String line = readLine(br, maxLine);
      session = new Pop3Session(parameter.getFile("dir"), timestamp);
      while (line != null) {
        if (Pop3Util.isStls(line) && hasKeyStore() && isNotSslSocket()) {
          if (session.isAuthenticated()) {
            // 認証済みの状態でのSTLSは拒否する
            Pop3Util.recieveLine(ps, Constants.RECV_NG);
          } else {
            Pop3Util.recieveLine(ps, Constants.RECV_OK);
            socket = startTls();
            socket.setSoTimeout(readTimeout());
            // 差し替え前の br/ps は close しない(下位の平文ソケットごと閉じてしまうため)。
            // 以降の入出力と終了時の close は TLS 側のストリームで行う
            br = newReader(socket);
            ps = newPrintStream(socket);
            // RFC 2595 4: TLS前に得た情報(USERなど)は破棄する
            session = new Pop3Session(parameter.getFile("dir"), timestamp);
          }
        } else {
          session.setStlsAvailable(hasKeyStore() && isNotSslSocket());
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
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      if (session != null) {
        // メールボックスのロックを解放する
        session.close();
      }
      if (ps != null) {
        try {
          ps.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      if (br != null) {
        try {
          br.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      synchronized (lock) {
        if (socket != null) {
          try {
            socket.close();
          } catch (Exception e) {
            e.printStackTrace();
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
    System.out.println("forceClose!");
    synchronized (lock) {
      if (socket != null && socket.isConnected()) {
        try {
          socket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
        socket = null;
      }
    }
  }

  SSLSocket startTls()
      throws KeyStoreException,
          NoSuchAlgorithmException,
          CertificateException,
          FileNotFoundException,
          IOException,
          UnrecoverableKeyException,
          KeyManagementException {
    String password = parameter.get("keyStorePass");
    KeyStore ks = KeyStore.getInstance("JKS");
    try (FileInputStream fis = new FileInputStream(parameter.get("keyStoreName"))) {
      ks.load(fis, password.toCharArray());
    }
    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, password.toCharArray());
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), null, null);

    SSLSocket sslSocket =
        (SSLSocket)
            ((SSLSocketFactory) sslContext.getSocketFactory())
                .createSocket(
                    socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);

    sslSocket.setUseClientMode(false);
    sslSocket.startHandshake();

    return sslSocket;
  }

  boolean hasKeyStore() {
    return parameter.get("keyStoreName") != null && parameter.get("keyStorePass") != null;
  }

  boolean isNotSslSocket() {
    return !(socket instanceof SSLSocket);
  }
}
