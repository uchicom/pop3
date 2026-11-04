// (C) 2026 uchicom
package com.uchicom.pop3;

/**
 * ユーザー名とパスワードの照合.
 *
 * @author uchicom: Shigeki Uchiyama
 */
@FunctionalInterface
public interface PasswordVerifier {

  /**
   * ユーザー名とパスワードが一致するかを検証する.
   *
   * @param user ユーザー名
   * @param password パスワード
   * @return 一致する場合はtrue
   */
  boolean verify(String user, String password);
}
