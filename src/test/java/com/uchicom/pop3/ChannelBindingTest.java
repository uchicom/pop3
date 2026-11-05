// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;

/** tls-server-end-point のハッシュ選択のテスト. */
class ChannelBindingTest {

  static X509Certificate certificate(String sigAlgName, byte[] encoded) throws Exception {
    var certificate = mock(X509Certificate.class);
    when(certificate.getSigAlgName()).thenReturn(sigAlgName);
    when(certificate.getEncoded()).thenReturn(encoded);
    return certificate;
  }

  @Test
  void digestName_weakHashUsesSha256() throws Exception {
    assertThat(ChannelBinding.digestName(certificate("SHA1withRSA", new byte[0])))
        .isEqualTo("SHA-256");
    assertThat(ChannelBinding.digestName(certificate("MD5withRSA", new byte[0])))
        .isEqualTo("SHA-256");
  }

  @Test
  void digestName_followsSignatureHash() throws Exception {
    assertThat(ChannelBinding.digestName(certificate("SHA256withRSA", new byte[0])))
        .isEqualTo("SHA-256");
    assertThat(ChannelBinding.digestName(certificate("SHA384withECDSA", new byte[0])))
        .isEqualTo("SHA-384");
    assertThat(ChannelBinding.digestName(certificate("SHA512withRSA", new byte[0])))
        .isEqualTo("SHA-512");
  }

  @Test
  void digestName_undefined() throws Exception {
    assertThat(ChannelBinding.digestName(certificate("Ed25519", new byte[0]))).isNull();
    assertThat(ChannelBinding.digestName(certificate("Ed448", new byte[0]))).isNull();
    // パラメータを取得できないRSASSA-PSS
    assertThat(ChannelBinding.digestName(certificate("RSASSA-PSS", new byte[0]))).isNull();
  }

  @Test
  void serverEndPoint_hashesDerEncoding() throws Exception {
    var encoded = new byte[] {1, 2, 3};

    assertThat(ChannelBinding.serverEndPoint(certificate("SHA384withECDSA", encoded)))
        .isEqualTo(MessageDigest.getInstance("SHA-384").digest(encoded));
    assertThat(ChannelBinding.serverEndPoint(certificate("SHA1withRSA", encoded)))
        .isEqualTo(MessageDigest.getInstance("SHA-256").digest(encoded));
  }

  @Test
  void serverEndPoint_unsupported() throws Exception {
    assertThat(ChannelBinding.serverEndPoint(certificate("Ed25519", new byte[] {1}))).isNull();
    assertThat(ChannelBinding.serverEndPoint(mock(Certificate.class))).isNull();
    assertThat(ChannelBinding.serverEndPoint(null)).isNull();
  }
}
