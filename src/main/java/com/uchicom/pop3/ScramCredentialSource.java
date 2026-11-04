// (C) 2026 uchicom
package com.uchicom.pop3;

/**
 * SCRAMの資格情報の取得元.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public interface ScramCredentialSource {

  /**
   * ユーザーの資格情報を取得する. 存在しないユーザーでも、存在するユーザーと区別できない資格情報を返すこと。
   *
   * @param user ユーザー名
   * @param algorithm SCRAMのアルゴリズム
   * @return 資格情報
   */
  ScramCredential find(String user, ScramAlgorithm algorithm);
}
