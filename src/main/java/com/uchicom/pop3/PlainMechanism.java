// (C) 2026 uchicom
package com.uchicom.pop3;

import java.nio.charset.StandardCharsets;

/**
 * PLAIN認証機構(RFC 4616)のサーバー側. 平文のパスワードが流れるため、TLS確立後のみ使用する.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class PlainMechanism implements SaslMechanism {

  private final PasswordVerifier verifier;
  private String user;
  private boolean complete;

  public PlainMechanism(PasswordVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  public byte[] step(byte[] response) throws SaslAuthException {
    if (complete) {
      throw new SaslAuthException("already completed");
    }
    // message = [authzid] NUL authcid NUL passwd
    String message = new String(response, StandardCharsets.UTF_8);
    String[] parts = message.split("\0", -1);
    if (parts.length != 3) {
      throw new SaslAuthException("invalid message");
    }
    String authzid = parts[0];
    String authcid = parts[1];
    String password = parts[2];
    if (authcid.isEmpty() || password.isEmpty()) {
      throw new SaslAuthException("empty credential");
    }
    if (!authzid.isEmpty() && !authzid.equals(authcid)) {
      // 他ユーザーへの代理認証は未対応
      throw new SaslAuthException("authzid not supported");
    }
    if (!verifier.verify(authcid, password)) {
      throw new SaslAuthException("authentication failed");
    }
    user = authcid;
    complete = true;
    return new byte[0];
  }

  @Override
  public boolean isComplete() {
    return complete;
  }

  @Override
  public String getUser() {
    return complete ? user : null;
  }
}
