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
    try {
      br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      ps = new PrintStream(socket.getOutputStream());
      // 1接続に対する受付開始
      Pop3Util.recieveLine(ps, Constants.RECV_OK, " ", timestamp);
      String line = br.readLine();
      Pop3Session session = new Pop3Session(parameter.getFile("dir"), timestamp);
      while (line != null) {
        if (Pop3Util.isStls(line) && hasKeyStore() && isNotSslSocket()) {
          Pop3Util.recieveLine(ps, Constants.RECV_OK);
          socket = startTls();
          br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
          ps = new PrintStream(socket.getOutputStream());
        } else {
          session.execute(line, ps);
          ps.flush();
          if (session.isFinished()) {
            break;
          }
        }
        lastTime = System.currentTimeMillis();
        line = br.readLine();
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
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
      synchronized (socket) {
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
    if (socket != null && socket.isConnected()) {
      try {
        socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      socket = null;
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
