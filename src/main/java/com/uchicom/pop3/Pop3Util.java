// (C) 2014 uchicom
package com.uchicom.pop3;

import java.io.PrintStream;
import java.util.regex.Pattern;

public class Pop3Util {

  private static final Pattern PATTERN_USER = Pattern.compile(Constants.REG_EXP_USER);
  private static final Pattern PATTERN_PASS = Pattern.compile(Constants.REG_EXP_PASS);
  private static final Pattern PATTERN_STAT = Pattern.compile(Constants.REG_EXP_STAT);
  private static final Pattern PATTERN_LIST = Pattern.compile(Constants.REG_EXP_LIST);
  private static final Pattern PATTERN_LIST_NUM = Pattern.compile(Constants.REG_EXP_LIST_NUM);
  private static final Pattern PATTERN_RETR = Pattern.compile(Constants.REG_EXP_RETR);
  private static final Pattern PATTERN_RETR_NUM = Pattern.compile(Constants.REG_EXP_RETR_NUM);
  private static final Pattern PATTERN_DELE_NUM = Pattern.compile(Constants.REG_EXP_DELE_NUM);
  private static final Pattern PATTERN_RSET = Pattern.compile(Constants.REG_EXP_RSET);
  private static final Pattern PATTERN_QUIT = Pattern.compile(Constants.REG_EXP_QUIT);
  private static final Pattern PATTERN_TOP_NUM_NUM = Pattern.compile(Constants.REG_EXP_TOP_NUM_NUM);
  private static final Pattern PATTERN_UIDL = Pattern.compile(Constants.REG_EXP_UIDL);
  private static final Pattern PATTERN_UIDL_NUM = Pattern.compile(Constants.REG_EXP_UIDL_NUM);
  private static final Pattern PATTERN_APOP_NAME_DIGEST =
      Pattern.compile(Constants.REG_EXP_APOP_NAME_DIGEST);
  private static final Pattern PATTERN_NOOP = Pattern.compile(Constants.REG_EXP_NOOP);
  private static final Pattern PATTERN_CAPA = Pattern.compile(Constants.REG_EXP_CAPA);
  private static final Pattern PATTERN_AUTH = Pattern.compile(Constants.REG_EXP_AUTH);
  private static final Pattern PATTERN_STLS = Pattern.compile(Constants.REG_EXP_STLS);

  /**
   * コマンドがUSERかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return USERの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isUser(String cmd) {
    return PATTERN_USER.matcher(cmd).matches();
  }

  /**
   * コマンドがPASSかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return PASSの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isPass(String cmd) {
    return PATTERN_PASS.matcher(cmd).matches();
  }

  /**
   * コマンドがSTATかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return STATの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isStat(String cmd) {
    return PATTERN_STAT.matcher(cmd).matches();
  }

  /**
   * コマンドがLISTかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return LISTの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isList(String cmd) {
    return PATTERN_LIST.matcher(cmd).matches();
  }

  /**
   * コマンドがLIST 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return LIST番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isListNum(String cmd) {
    return PATTERN_LIST_NUM.matcher(cmd).matches();
  }

  /**
   * コマンドがRETRかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return RETRの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isRetr(String cmd) {
    return PATTERN_RETR.matcher(cmd).matches();
  }

  /**
   * コマンドがRETR 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return RETR番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isRetrNum(String cmd) {
    return PATTERN_RETR_NUM.matcher(cmd).matches();
  }

  /**
   * コマンドがDELE 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return DELE番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isDeleNum(String cmd) {
    return PATTERN_DELE_NUM.matcher(cmd).matches();
  }

  /**
   * コマンドがRSETかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return RSETの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isRset(String cmd) {
    return PATTERN_RSET.matcher(cmd).matches();
  }

  /**
   * コマンドがQUITかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return QUITの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isQuit(String cmd) {
    return PATTERN_QUIT.matcher(cmd).matches();
  }

  /**
   * コマンドがTOP 番号 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return TOP番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isTopNumNum(String cmd) {
    return PATTERN_TOP_NUM_NUM.matcher(cmd).matches();
  }

  /**
   * コマンドがUIDLかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return UIDLの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isUidl(String cmd) {
    return PATTERN_UIDL.matcher(cmd).matches();
  }

  /**
   * コマンドがUIDL 番号かどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return UIDL番号の場合はtrue,それ以外はfalseを返します
   */
  public static boolean isUidlNum(String cmd) {
    return PATTERN_UIDL_NUM.matcher(cmd).matches();
  }

  /**
   * コマンドがAPOP ユーザー名 ダイジェストかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return APOPの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isApopNameDigest(String cmd) {
    return PATTERN_APOP_NAME_DIGEST.matcher(cmd).matches();
  }

  /**
   * コマンドがNOOPかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return NOOPの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isNoop(String cmd) {
    return PATTERN_NOOP.matcher(cmd).matches();
  }

  /**
   * コマンドがCAPAかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return CAPAの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isCapa(String cmd) {
    return PATTERN_CAPA.matcher(cmd).matches();
  }

  /**
   * コマンドがAUTHかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return AUTHの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isAuth(String cmd) {
    return PATTERN_AUTH.matcher(cmd).matches();
  }

  /**
   * コマンドがSTLSかどうかをチェックする.
   *
   * @param cmd コマンド行
   * @return STLSの場合はtrue,それ以外はfalseを返します
   */
  public static boolean isStls(String cmd) {
    return PATTERN_STLS.matcher(cmd).matches();
  }

  /**
   * コマンド行を空白区切りで分割する.
   *
   * @param cmd コマンド行
   * @return 空白区切りの配列(連続する空白は1つとして扱う)
   */
  public static String[] splitArgs(String cmd) {
    return cmd.trim().split(" +");
  }

  /**
   * コマンド引数の数値を解析する.
   *
   * @param value 数値文字列
   * @return 数値。数値でない、またはintの範囲外の場合は-1を返します
   */
  public static int parseNumber(String value) {
    try {
      int number = Integer.parseInt(value);
      return number < 0 ? -1 : number;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * ステータス行を出力する.
   *
   * @param ps 出力ストリーム
   * @param strings 出力文字列配列
   */
  public static void receiveLine(PrintStream ps, String... strings) {
    for (String string : strings) {
      ps.print(string);
    }
    ps.print(Constants.RECV_LINE_END);
    ps.flush();
  }
}
