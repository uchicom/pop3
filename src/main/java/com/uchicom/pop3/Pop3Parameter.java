// (C) 2014 uchicom
package com.uchicom.pop3;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.SelectorServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.util.Parameter;

/** @author uchicom: Shigeki Uchiyama */
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
      put("type", "single");
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
      case "selector":
        server =
            new SelectorServer(
                this,
                () -> {
                  return new Pop3Handler(getFile("dir"), get("host"));
                });
        break;
    }
    return server;
  }
}
