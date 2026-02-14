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
    logger.info("start");
    Pop3Parameter parameter = new Pop3Parameter(args);
    if (parameter.init()) {
      server = parameter.createServer();
      server.execute();
    }
    logger.info("end");
  }

  public static void shutdown() {
    if (server != null) {
      server.stop();
    }
  }
}
