// (C) 2026 uchicom
package com.uchicom.pop3;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PSSParameterSpec;
import java.util.Locale;

/**
 * SCRAMのチャネルバインディングデータ(RFC 5929 4 tls-server-end-point).
 *
 * @author uchicom: Shigeki Uchiyama
 */
public final class ChannelBinding {

  /** チャネルバインディング種別名 */
  public static final String TYPE = "tls-server-end-point";

  private ChannelBinding() {}

  /**
   * サーバー証明書のハッシュを求める.
   *
   * @param certificate サーバー証明書
   * @return 証明書(DER)のハッシュ。ハッシュが定義されない署名アルゴリズムの場合はnull
   */
  public static byte[] serverEndPoint(Certificate certificate) {
    if (!(certificate instanceof X509Certificate)) {
      return null;
    }
    X509Certificate x509 = (X509Certificate) certificate;
    String digestName = digestName(x509);
    if (digestName == null) {
      return null;
    }
    try {
      return MessageDigest.getInstance(digestName).digest(x509.getEncoded());
    } catch (GeneralSecurityException e) {
      // ハッシュを求められなければPLUSを提示しない
      return null;
    }
  }

  /** 署名アルゴリズムに対応するハッシュ関数名. MD5とSHA-1はSHA-256にする. */
  static String digestName(X509Certificate certificate) {
    String sigAlgName = certificate.getSigAlgName().toUpperCase(Locale.ROOT);
    if (sigAlgName.startsWith("RSASSA-PSS") || sigAlgName.equals("PSS")) {
      return pssDigestName(certificate);
    }
    int index = sigAlgName.indexOf("WITH");
    if (index < 0) {
      // Ed25519 / Ed448 などハッシュが定義されないもの
      return null;
    }
    switch (sigAlgName.substring(0, index)) {
      case "MD2":
      case "MD5":
      case "SHA1":
      case "SHA256":
        return "SHA-256";
      case "SHA224":
        return "SHA-224";
      case "SHA384":
        return "SHA-384";
      case "SHA512":
        return "SHA-512";
      case "SHA3-224":
      case "SHA3-256":
      case "SHA3-384":
      case "SHA3-512":
        return sigAlgName.substring(0, index);
      default:
        return null;
    }
  }

  private static String pssDigestName(X509Certificate certificate) {
    byte[] params = certificate.getSigAlgParams();
    if (params == null) {
      return null;
    }
    try {
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("RSASSA-PSS");
      parameters.init(params);
      String digest = parameters.getParameterSpec(PSSParameterSpec.class).getDigestAlgorithm();
      return "SHA-1".equals(digest) || "MD5".equals(digest) ? "SHA-256" : digest;
    } catch (GeneralSecurityException | IOException e) {
      return null;
    }
  }
}
