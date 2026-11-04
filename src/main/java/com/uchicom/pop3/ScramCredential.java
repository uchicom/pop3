// (C) 2026 uchicom
package com.uchicom.pop3;

/**
 * SCRAMの検証に必要な資格情報.
 *
 * @param salt ソルト
 * @param iterations 反復回数
 * @param storedKey StoredKey
 * @param serverKey ServerKey
 * @author uchicom: Shigeki Uchiyama
 */
public record ScramCredential(byte[] salt, int iterations, byte[] storedKey, byte[] serverKey) {}
