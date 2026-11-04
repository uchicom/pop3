// (C) 2026 uchicom
package com.uchicom.pop3;

import java.nio.charset.StandardCharsets;

/**
 * LOGIN認証機構のサーバー側. ユーザー名とパスワードを順に問い合わせる. 平文のパスワードが流れるため、TLS確立後のみ使用する.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class LoginMechanism implements SaslMechanism {

  private static final byte[] USERNAME_CHALLENGE = "Username:".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PASSWORD_CHALLENGE = "Password:".getBytes(StandardCharsets.US_ASCII);

  private enum State {
    USERNAME,
    PASSWORD,
    DONE
  }

  private final PasswordVerifier verifier;
  private State state = State.USERNAME;
  private String user;

  public LoginMechanism(PasswordVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  public byte[] initialChallenge() {
    return USERNAME_CHALLENGE;
  }

  @Override
  public byte[] step(byte[] response) throws SaslAuthException {
    String message = new String(response, StandardCharsets.UTF_8);
    switch (state) {
      case USERNAME:
        if (message.isEmpty()) {
          throw new SaslAuthException("empty username");
        }
        user = message;
        state = State.PASSWORD;
        return PASSWORD_CHALLENGE;
      case PASSWORD:
        if (message.isEmpty() || !verifier.verify(user, message)) {
          throw new SaslAuthException("authentication failed");
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
}
