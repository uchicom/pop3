// (C) 2026 uchicom
package com.uchicom.pop3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SCRAM認証機構(RFC 5802)のサーバー側. チャネルバインディング(-PLUS)は tls-server-end-point のみ対応.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class ScramMechanism implements SaslMechanism {

  private static final SecureRandom RANDOM = new SecureRandom();

  private enum State {
    CLIENT_FIRST,
    CLIENT_FINAL,
    CONFIRM,
    DONE
  }

  private final ScramAlgorithm algorithm;
  private final ScramCredentialSource source;
  private final boolean plus;
  private final byte[] bindingData;
  private State state = State.CLIENT_FIRST;
  private String user;
  private String gs2Header;
  private String clientFirstBare;
  private String serverFirst;
  private String nonce;
  private ScramCredential credential;

  public ScramMechanism(ScramAlgorithm algorithm, ScramCredentialSource source) {
    this(algorithm, source, false, null);
  }

  /**
   * コンストラクタ.
   *
   * @param algorithm アルゴリズム
   * @param source 資格情報の取得元
   * @param plus -PLUS機構かどうか
   * @param bindingData tls-server-end-pointのバインディングデータ。PLUSを提示できない場合はnull
   */
  public ScramMechanism(
      ScramAlgorithm algorithm, ScramCredentialSource source, boolean plus, byte[] bindingData) {
    this.algorithm = algorithm;
    this.source = source;
    this.plus = plus;
    this.bindingData = bindingData;
  }

  @Override
  public byte[] step(byte[] response) throws SaslAuthException {
    String message = new String(response, StandardCharsets.UTF_8);
    switch (state) {
      case CLIENT_FIRST:
        return clientFirst(message);
      case CLIENT_FINAL:
        return clientFinal(message);
      case CONFIRM:
        // server-finalに対するクライアントの空応答
        if (response.length != 0) {
          throw new SaslAuthException("unexpected response");
        }
        state = State.DONE;
        return new byte[0];
      default:
        throw new SaslAuthException("already completed");
    }
  }

  @Override
  public boolean isComplete() {
    return state == State.DONE;
  }

  @Override
  public String getUser() {
    return isComplete() ? user : null;
  }

  private byte[] clientFirst(String message) throws SaslAuthException {
    // gs2-header = gs2-cbind-flag "," [authzid] ","
    String[] parts = message.split(",", 3);
    if (parts.length < 3) {
      throw new SaslAuthException("invalid client-first");
    }
    String flag = parts[0];
    if (flag.startsWith("p=")) {
      // チャネルバインディング要求はPLUS機構の tls-server-end-point のみ受理する
      if (!plus || !flag.equals("p=" + ChannelBinding.TYPE)) {
        throw new SaslAuthException("channel binding not supported");
      }
    } else if (plus) {
      throw new SaslAuthException("channel binding required");
    } else if ("y".equals(flag)) {
      // PLUSを提示しているのにyを送るのはダウングレード
      if (bindingData != null) {
        throw new SaslAuthException("channel binding downgrade");
      }
    } else if (!"n".equals(flag)) {
      throw new SaslAuthException("invalid client-first");
    }
    if (!parts[1].isEmpty()) {
      throw new SaslAuthException("authzid not supported");
    }
    gs2Header = flag + ",,";
    clientFirstBare = parts[2];
    String[] attrs = clientFirstBare.split(",");
    if (attrs.length < 2 || !attrs[0].startsWith("n=") || !attrs[1].startsWith("r=")) {
      throw new SaslAuthException("invalid client-first-bare");
    }
    user = attrs[0].substring(2).replace("=2C", ",").replace("=3D", "=");
    String clientNonce = attrs[1].substring(2);
    if (user.isEmpty() || clientNonce.isEmpty()) {
      throw new SaslAuthException("invalid client-first-bare");
    }
    credential = source.find(user, algorithm);
    byte[] serverNonce = new byte[18];
    RANDOM.nextBytes(serverNonce);
    nonce = clientNonce + Base64.getEncoder().encodeToString(serverNonce);
    serverFirst =
        "r="
            + nonce
            + ",s="
            + Base64.getEncoder().encodeToString(credential.salt())
            + ",i="
            + credential.iterations();
    state = State.CLIENT_FINAL;
    return serverFirst.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] clientFinal(String message) throws SaslAuthException {
    int proofIndex = message.lastIndexOf(",p=");
    if (proofIndex < 0) {
      throw new SaslAuthException("invalid client-final");
    }
    String withoutProof = message.substring(0, proofIndex);
    String[] attrs = withoutProof.split(",");
    if (attrs.length != 2 || !attrs[0].startsWith("c=") || !attrs[1].startsWith("r=")) {
      throw new SaslAuthException("invalid client-final");
    }
    byte[] proof;
    try {
      byte[] channelBinding = Base64.getDecoder().decode(attrs[0].substring(2));
      byte[] expected = gs2Header.getBytes(StandardCharsets.ISO_8859_1);
      if (plus) {
        byte[] header = expected;
        expected = new byte[header.length + bindingData.length];
        System.arraycopy(header, 0, expected, 0, header.length);
        System.arraycopy(bindingData, 0, expected, header.length, bindingData.length);
      }
      if (!MessageDigest.isEqual(expected, channelBinding)) {
        throw new SaslAuthException("channel binding mismatch");
      }
      proof = Base64.getDecoder().decode(message.substring(proofIndex + 3));
    } catch (IllegalArgumentException e) {
      throw new SaslAuthException("invalid base64");
    }
    if (!nonce.equals(attrs[1].substring(2))) {
      throw new SaslAuthException("nonce mismatch");
    }
    byte[] authMessage =
        (clientFirstBare + "," + serverFirst + "," + withoutProof).getBytes(StandardCharsets.UTF_8);
    byte[] clientSignature = algorithm.hmac(credential.storedKey(), authMessage);
    if (proof.length != clientSignature.length) {
      throw new SaslAuthException("authentication failed");
    }
    byte[] clientKey = new byte[proof.length];
    for (int i = 0; i < proof.length; i++) {
      clientKey[i] = (byte) (proof[i] ^ clientSignature[i]);
    }
    if (!MessageDigest.isEqual(algorithm.hash(clientKey), credential.storedKey())) {
      throw new SaslAuthException("authentication failed");
    }
    byte[] serverSignature = algorithm.hmac(credential.serverKey(), authMessage);
    state = State.CONFIRM;
    return ("v=" + Base64.getEncoder().encodeToString(serverSignature))
        .getBytes(StandardCharsets.UTF_8);
  }
}
