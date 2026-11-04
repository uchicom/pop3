// (C) 2026 uchicom
package com.uchicom.pop3;

/**
 * SASL認証機構.
 *
 * <p>クライアントからの応答を受け取り、サーバーからのチャレンジを返す。 入出力には依存しない。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public interface SaslMechanism {

  /**
   * クライアントの応答を処理する.
   *
   * @param response クライアントの応答(デコード済み)
   * @return サーバーのチャレンジ。認証が完了した場合は空配列
   * @throws SaslAuthException 認証失敗、または不正な応答
   */
  byte[] step(byte[] response) throws SaslAuthException;

  /**
   * 認証が成功して完了したかどうか.
   *
   * @return 完了した場合はtrue
   */
  boolean isComplete();

  /**
   * 認証されたユーザー名.
   *
   * @return ユーザー名。完了前はnull
   */
  String getUser();
}
