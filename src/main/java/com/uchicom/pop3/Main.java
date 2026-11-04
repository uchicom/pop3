// (C) 2016 uchicom
package com.uchicom.pop3;

import com.uchicom.server.Server;
import java.util.logging.Logger;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Main {

  private static final Logger logger = Logger.getLogger(Main.class.getCanonicalName());

  static Server server;

  /**
   * アドレスとメールユーザーフォルダの格納フォルダを指定する.
   *
   * @param args パラメータ引数
   */
  public static void main(String[] args) {
    run(args);
  }

  /**
   * サーバを起動する. サーバが停止するまで戻らない.
   *
   * @param args パラメータ引数
   * @return 終了コード(正常終了は0、設定不正は1)
   */
  static int run(String[] args) {
    logger.info("start");
    try {
      Pop3Parameter parameter = new Pop3Parameter(args);
      if (parameter.init()) {
        server = parameter.createServer();
        server.execute();
      }
    } catch (IllegalArgumentException e) {
      logger.severe(e.getMessage());
      System.err.println(e.getMessage());
      return 1;
    }
    logger.info("end");
    return 0;
  }

  public static void shutdown() {
    if (server != null) {
      server.stop();
    }
  }
}
