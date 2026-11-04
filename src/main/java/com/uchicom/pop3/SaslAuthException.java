// (C) 2026 uchicom
package com.uchicom.pop3;

/**
 * SASL認証の失敗を表す例外.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class SaslAuthException extends Exception {

  private static final long serialVersionUID = 1L;

  public SaslAuthException(String message) {
    super(message);
  }
}
