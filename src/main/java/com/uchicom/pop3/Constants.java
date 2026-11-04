// (C) 2012 uchicom
package com.uchicom.pop3;

/**
 * POP3に関する定数クラス
 *
 * @author Uchiyama Shigeki
 */
public class Constants {

  /** デバッグフラグ */
  public static final boolean DEBUG = true;

  // POP3返却メッセージ
  /** 返却メッセージ(+OK) */
  public static String RECV_OK = "+OK";

  /** 返却メッセージ(行終端文字列) */
  public static String RECV_LINE_END = "\r\n";

  /** 返却メッセージ(+OK(行終端文字列あり)) */
  public static String RECV_OK_LINE_END = "+OK" + RECV_LINE_END;

  /** 返却メッセージ(-ERR) */
  public static String RECV_ERR = "-ERR";

  /** 返却メッセージ(-ERR(コマンド未対応)) */
  public static String RECV_ERR_CMD_NOT_FOUND = "-ERR Command not support";

  /** 返却メッセージ(TLS確立前の認証拒否:RFC 3206 の [AUTH] 応答コード) */
  public static String RECV_ERR_AUTH_TLS_REQUIRED =
      "-ERR [AUTH] STLS required before authentication";

  /** 返却メッセージ(メールボックス使用中:RFC 3206 の [IN-USE] 応答コード) */
  public static String RECV_ERR_IN_USE = "-ERR [IN-USE] Mailbox is locked by another session";

  /** 返却メッセージ(-ERR(行終端文字列あり) */
  public static String RECV_ERR_LINE_END = "-ERR" + RECV_LINE_END;

  /** 返却メッセージ(.(行終端文字列あり)) */
  public static String RECV_DATA = ".";

  // POP3コマンド正規表現
  /** USER 文字の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_USER = "^[Uu][Ss][Ee][Rr] +[^ ]+ *$";

  /** PASS 文字の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_PASS = "^[Pp][Aa][Ss][Ss] +[^ ]+ *$";

  /** STATの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_STAT = "^[Ss][Tt][Aa][Tt] *$";

  /** LISTの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_LIST = "^[Ll][Ii][Ss][Tt] *$";

  /** LIST 数値の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_LIST_NUM = "^[Ll][Ii][Ss][Tt] +[1-9][0-9]* *$";

  /** RETRの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_RETR = "^[Rr][Ee][Tt][Rr] *$";

  /** GRETの正規表現(大文字小文字後続スペース):独自拡張gz圧縮して転送する */
  public static String REG_EXP_GRET = "^[Gr][Rr][Ee][Tt] *$";

  /** RETR 数値の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_RETR_NUM = "^[Rr][Ee][Tt][Rr] +[1-9][0-9]* *$";

  /** DELE 数値の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_DELE_NUM = "^[Dd][Ee][Ll][Ee] +[1-9][0-9]* *$";

  /** RSETの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_RSET = "^[Rr][Ss][Ee][Tt] *$";

  /** QUITの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_QUIT = "^[Qq][Uu][Ii][Tt] *$";

  /** NOOPの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_NOOP = "^[Nn][Oo][Oo][Pp] *$";

  /** TOP 数値 数値の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_TOP_NUM_NUM = "^[Tt][Oo][Pp] +[1-9][0-9]* +[0-9]+ *$";

  /** UIDLの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_UIDL = "^[Uu][Ii][Dd][Ll] *$";

  /** UIDL 数値の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_UIDL_NUM = "^[Uu][Ii][Dd][Ll] +[1-9][0-9]* *$";

  /** APOP 文字 文字の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_APOP_NAME_DIGEST = "^[Aa][Pp][Oo][Pp] +[^ ]+ +[^ ]+ *$";

  /** CAPAの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_CAPA = "^[Cc][Aa][Pp][Aa] *$";

  /** AUTH [機構名 [初期応答]] の正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_AUTH = "^[Aa][Uu][Tt][Hh]( +[^ ]+){0,2} *$";

  // POP3 STARTTLS拡張コマンド
  /** STLSの正規表現(大文字小文字後続スペース) */
  public static String REG_EXP_STLS = "^[Ss][Tt][Ll][Ss] *$";

  /** 日時書式 */
  public static String DATE_TIME_MILI_FORMAT = "yyyyMMdd_HHmmss.SSS";

  /** パスワードファイルのパス */
  public static String PASSWORD_FILE_NAME = ".pop3";

  /** メールボックスの排他ロックファイルのパス */
  public static String LOCK_FILE_NAME = ".lock";

  // 初期設定
  /** デフォルトメールボックスディレクトリ */
  public static String DEFAULT_MAILBOX = "mailbox";

  /** デフォルト待ち受けポート番号 */
  public static String DEFAULT_PORT = "8110";

  /** デフォルト接続待ち数 */
  public static String DEFAULT_BACK = "10";

  /** デフォルトスレッドプール数 */
  public static String DEFAULT_POOL = "10";

  /** デフォルト読み取りタイムアウト(ミリ秒) */
  public static String DEFAULT_TIMEOUT = "60000";

  /** デフォルト1行の最大長(RFC 1939 は 255 オクテット以上を推奨) */
  public static String DEFAULT_MAX_LINE = "1024";

  /** デフォルトキーストア種別 */
  public static String DEFAULT_KEY_STORE_TYPE = "JKS";

  /** デフォルト有効プロトコル(カンマ区切り。TLS 1.2 以上) */
  public static String DEFAULT_TLS_PROTOCOLS = "TLSv1.3,TLSv1.2";

  /** デフォルト認証失敗の上限回数(超過で切断) */
  public static String DEFAULT_MAX_AUTH_FAILURES = "3";

  /** デフォルト認証失敗時の応答遅延(ミリ秒) */
  public static String DEFAULT_AUTH_FAILURE_DELAY = "1000";
}
