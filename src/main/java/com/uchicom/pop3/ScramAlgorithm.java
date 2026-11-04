// (C) 2026 uchicom
package com.uchicom.pop3;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.Normalizer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * SCRAMで使用するハッシュ関数ごとの定義と計算.
 *
 * <p>優先度の高い順(強いハッシュ関数が先)に並べている。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public enum ScramAlgorithm {
  SHA_512("SCRAM-SHA-512", "SHA-512", "HmacSHA512"),
  SHA_384("SCRAM-SHA-384", "SHA-384", "HmacSHA384"),
  SHA_256("SCRAM-SHA-256", "SHA-256", "HmacSHA256"),
  SHA_224("SCRAM-SHA-224", "SHA-224", "HmacSHA224"),
  SHA_1("SCRAM-SHA-1", "SHA-1", "HmacSHA1");

  private final String mechanismName;
  private final String digestName;
  private final String macName;

  ScramAlgorithm(String mechanismName, String digestName, String macName) {
    this.mechanismName = mechanismName;
    this.digestName = digestName;
    this.macName = macName;
  }

  public String getMechanismName() {
    return mechanismName;
  }

  /**
   * 機構名からアルゴリズムを探す.
   *
   * @param name SASL機構名(大文字小文字を区別しない)
   * @return アルゴリズム。ない場合はnull
   */
  public static ScramAlgorithm find(String name) {
    for (ScramAlgorithm algorithm : values()) {
      if (algorithm.mechanismName.equalsIgnoreCase(name)) {
        return algorithm;
      }
    }
    return null;
  }

  /** H(str). */
  byte[] hash(byte[] value) {
    try {
      return MessageDigest.getInstance(digestName).digest(value);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** HMAC(key, str). */
  byte[] hmac(byte[] key, byte[] value) {
    try {
      Mac mac = Mac.getInstance(macName);
      mac.init(new SecretKeySpec(key, macName));
      return mac.doFinal(value);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Hi(str, salt, i). PBKDF2相当. */
  byte[] hi(byte[] password, byte[] salt, int iterations) {
    byte[] input = new byte[salt.length + 4];
    System.arraycopy(salt, 0, input, 0, salt.length);
    input[input.length - 1] = 1;
    byte[] u = hmac(password, input);
    byte[] result = u.clone();
    for (int i = 1; i < iterations; i++) {
      u = hmac(password, u);
      for (int j = 0; j < result.length; j++) {
        result[j] ^= u[j];
      }
    }
    return result;
  }

  /**
   * パスワードから資格情報を導出する.
   *
   * @param password パスワード(平文)
   * @param salt ソルト
   * @param iterations 反復回数
   * @return 資格情報
   */
  public ScramCredential derive(String password, byte[] salt, int iterations) {
    // SASLprepの簡易実装(NFKC正規化)
    byte[] normalized =
        Normalizer.normalize(password, Normalizer.Form.NFKC).getBytes(StandardCharsets.UTF_8);
    byte[] saltedPassword = hi(normalized, salt, iterations);
    byte[] clientKey = hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.US_ASCII));
    byte[] serverKey = hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.US_ASCII));
    return new ScramCredential(salt, iterations, hash(clientKey), serverKey);
  }
}
