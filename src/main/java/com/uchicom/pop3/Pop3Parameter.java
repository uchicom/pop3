// (C) 2014 uchicom
package com.uchicom.pop3;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.util.Parameter;

/**
 * @author uchicom: Shigeki Uchiyama
 */
public class Pop3Parameter extends Parameter {

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

    return true;
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
