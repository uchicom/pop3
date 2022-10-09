// (C) 2014 uchicom
package com.uchicom.pop3;

import java.io.PrintStream;

public class Pop3Util {

  /**
   * コマンドがUSERかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return USERの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isUser(String cmd) {
    return cmd.matches(Constants.REG_EXP_USER);
  }

  /**
   * コマンドがPASSかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return PASSの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isPass(String cmd) {
    return cmd.matches(Constants.REG_EXP_PASS);
  }

  /**
   * コマンドがSTATかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return STATの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isStat(String cmd) {
    return cmd.matches(Constants.REG_EXP_STAT);
  }

  /**
   * コマンドがLISTかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return LISTの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isList(String cmd) {
    return cmd.matches(Constants.REG_EXP_LIST);
  }

  /**
   * コマンドがLIST 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return LIST番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isListNum(String cmd) {
    return cmd.matches(Constants.REG_EXP_LIST_NUM);
  }

  /**
   * コマンドがRETRかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return RETRの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isRetr(String cmd) {
    return cmd.matches(Constants.REG_EXP_RETR);
  }

  /**
   * コマンドがRETR 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return RETR番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isRetrNum(String cmd) {
    return cmd.matches(Constants.REG_EXP_RETR_NUM);
  }

  /**
   * コマンドがDELE 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return DELE番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isDeleNum(String cmd) {
    return cmd.matches(Constants.REG_EXP_DELE_NUM);
  }

  /**
   * コマンドがRSETかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return RSETの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isRset(String cmd) {
    return cmd.matches(Constants.REG_EXP_RSET);
  }

  /**
   * コマンドがQUITかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return QUITの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isQuit(String cmd) {
    return cmd.matches(Constants.REG_EXP_QUIT);
  }

  /**
   * コマンドがTOP 番号 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return TOP番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isTopNumNum(String cmd) {
    return cmd.matches(Constants.REG_EXP_TOP_NUM_NUM);
  }

  /**
   * コマンドがUIDLかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return UIDLの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isUidl(String cmd) {
    return cmd.matches(Constants.REG_EXP_UIDL);
  }

  /**
   * コマンドがUIDL 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return UIDL番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isUidlNum(String cmd) {
    return cmd.matches(Constants.REG_EXP_UIDL_NUM);
  }

  /**
   * コマンドがAPOP ユーザー名 ダイジェストかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return APOPの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isApopNameDigest(String cmd) {
    return cmd.matches(Constants.REG_EXP_APOP_NAME_DIGEST);
  }

  /**
   * コマンドがNOOPかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return NOOPの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isNoop(String cmd) {
    return cmd.matches(Constants.REG_EXP_NOOP);
  }

  /**
   * コマンドがCAPAかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return CAPAの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isCapa(String cmd) {
    return cmd.matches(Constants.REG_EXP_CAPA);
  }

  /**
   * コマンドがSTLSかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return STLSの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isStls(String cmd) {
    return cmd.matches(Constants.REG_EXP_STLS);
  }

  /**
   * ステータス行を出力する.
   *
   * @param ps 出力ストリーム
   * @param strings 出力文字列配列
   */
  public static void recieveLine(PrintStream ps, String... strings) {
    for (String string : strings) {
      ps.print(string);
    }
    ps.print(Constants.RECV_LINE_END);
    ps.flush();
  }
}
