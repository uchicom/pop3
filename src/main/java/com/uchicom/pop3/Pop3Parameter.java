// (C) 2014 uchicom
package com.uchicom.pop3;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.server.VirtualSocketServer;
import com.uchicom.util.Parameter;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * @author uchicom: Shigeki Uchiyama
 */
public class Pop3Parameter extends Parameter {

  /** 全接続で共有する SSLContext(キーストア未指定ならnull) */
  private SSLContext sslContext;

  public Pop3Parameter(String[] args) {
    super(args);
  }

  /**
   * 初期化
   *
   * @return 必ずtrueを返却します.
   */
  public boolean init() {
    // メールボックスの基準フォルダ
    if (!is("dir")) {
      put("dir", Constants.DEFAULT_MAILBOX);
    }
    // 実行するサーバのタイプ
    if (!is("type")) {
      put("type", "pool");
    }
    // ホスト名
    if (!is("host")) {
      put("host", "localhost");
    }
    // 待ち受けポート
    if (!is("port")) {
      put("port", Constants.DEFAULT_PORT);
    }
    // 受信する接続 (接続要求) のキューの最大長
    if (!is("back")) {
      put("back", Constants.DEFAULT_BACK);
    }
    // プールするスレッド数
    if (!is("pool")) {
      put("pool", Constants.DEFAULT_POOL);
    }
    // 読み取りタイムアウト(ミリ秒)。0 以下は無制限
    if (!is("timeout")) {
      put("timeout", Constants.DEFAULT_TIMEOUT);
    }
    // 1行の最大長(行終端を除く文字数)
    if (!is("maxLine")) {
      put("maxLine", Constants.DEFAULT_MAX_LINE);
    }
    // キーストアの種別(JKS, PKCS12 など)
    if (!is("keyStoreType")) {
      put("keyStoreType", Constants.DEFAULT_KEY_STORE_TYPE);
    }
    // 有効にするプロトコル(カンマ区切り)
    if (!is("tlsProtocols")) {
      put("tlsProtocols", Constants.DEFAULT_TLS_PROTOCOLS);
    }
    if (isImplicitTls() && !hasKeyStore()) {
      throw new IllegalArgumentException("implicitTls requires keyStoreName and keyStorePass");
    }
    if (hasKeyStore()) {
      // 設定の誤りを起動時に検出するため、SSLContext はここで1回だけ作る
      sslContext = createSslContext();
    }

    return true;
  }

  /** キーストアが指定されているかどうか. */
  public boolean hasKeyStore() {
    return get("keyStoreName") != null && get("keyStorePass") != null;
  }

  /** 暗黙的TLS(接続直後からTLS)で待ち受けるかどうか. */
  public boolean isImplicitTls() {
    return "true".equalsIgnoreCase(get("implicitTls"));
  }

  /**
   * 全接続で共有する SSLContext を取得する.
   *
   * @return SSLContext。キーストアが指定されていない場合はnull
   */
  public SSLContext getSslContext() {
    return sslContext;
  }

  private SSLContext createSslContext() {
    char[] password = get("keyStorePass").toCharArray();
    try {
      KeyStore ks = KeyStore.getInstance(get("keyStoreType"));
      try (FileInputStream fis = new FileInputStream(get("keyStoreName"))) {
        ks.load(fis, password);
      }
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks, password);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(kmf.getKeyManagers(), null, null);
      return context;
    } catch (GeneralSecurityException | IOException e) {
      throw new IllegalArgumentException("failed to load keystore: " + e.getMessage(), e);
    }
  }

  public Server createServer() {
    Server server = null;
    switch (get("type")) {
      case "multi":
        server =
            new MultiSocketServer(
                this,
                (a, b) -> {
                  return new Pop3Process(a, b);
                });
        break;
      case "pool":
        server =
            new PoolSocketServer(
                this,
                (a, b) -> {
                  return new Pop3Process(a, b);
                });
        break;
      case "virtual":
        server =
            new VirtualSocketServer(
                this,
                (a, b) -> {
                  return new Pop3Process(a, b);
                });
        break;
      case "single":
        server =
            new SingleSocketServer(
                this,
                (a, b) -> {
                  return new Pop3Process(a, b);
                });
        break;
      default:
        throw new IllegalArgumentException("unknown type: " + get("type"));
    }
    return server;
  }
}
