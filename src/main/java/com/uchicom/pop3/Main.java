// (C) 2016 uchicom
package com.uchicom.pop3;

import java.util.logging.Logger;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Main {

  private static final Logger logger = Logger.getLogger(Main.class.getCanonicalName());

  /**
   * アドレスとメールユーザーフォルダの格納フォルダを指定する.
   *
   * @param args パラメータ引数
   */
  public static void main(String[] args) {
    logger.info("start");
    Pop3Parameter parameter = new Pop3Parameter(args);
    if (parameter.init()) {
      parameter.createServer().execute();
    }
    logger.info("end");
  }
}
