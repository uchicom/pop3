// (C) 2026 uchicom
package com.uchicom.pop3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POP3のコマンド処理を行うクラス.
 *
 * <p>入出力には依存せず、コマンド行を受け取って応答を {@link Appendable} に書き出す。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Pop3Session {

  private static final Logger logger = Logger.getLogger(Pop3Session.class.getCanonicalName());

  /** SCRAMの反復回数 */
  private static final int SCRAM_ITERATIONS = 4096;

  private static final String MECHANISM_PLAIN = "PLAIN";

  private static final String MECHANISM_LOGIN = "LOGIN";

  /** SCRAMのソルト生成用のプロセス固有の秘密値 */
  private static final byte[] SERVER_SECRET = new byte[32];

  static {
    new SecureRandom().nextBytes(SERVER_SECRET);
  }

  /** メールボックスのベースディレクトリ */
  private final File base;

  /** ダイジェスト用のタイムスタンプ */
  private final String timestamp;

  /** ユーザー設定済みフラグ */
  private boolean bUser;

  /** 認証済みフラグ */
  private boolean bPass;

  /** 終了フラグ */
  private boolean finished;

  /** ユーザー名 */
  private String user;

  /** 実行中のSASL認証(AUTH実行中でなければnull) */
  private SaslMechanism sasl;

  /** STLSが利用可能かどうか(CAPAで通知する) */
  private boolean stlsAvailable;

  /** tls-server-end-pointのバインディングデータ(PLUSを提示しない場合はnull) */
  private byte[] channelBindingData;

  /** TLSが確立済みかどうか(AUTH PLAIN/LOGINの受付とCAPAの通知に使う) */
  private boolean tlsActive;

  /** メールボックス内のリスト(認証成功時に設定される) */
  private List<File> mailList;

  /** 削除リスト(認証成功時に設定される) */
  private List<File> delList;

  /** メールごとのオクテット数。mailList と同じ順(認証成功時に計算される) */
  private List<Long> sizeList;

  /** メールボックスの排他ロック(認証成功中のみ保持) */
  private FileLock mailboxLock;

  /** 認証失敗の上限回数(超過で切断) */
  private int maxAuthFailures = Integer.parseInt(Constants.DEFAULT_MAX_AUTH_FAILURES);

  /** 認証失敗時の応答遅延(ミリ秒) */
  private long authFailureDelay;

  /** 認証失敗回数 */
  private int authFailures;

  /** 監査ログに出力する接続元 */
  private String remoteAddress = "unknown";

  /**
   * コンストラクタ.
   *
   * @param base メールボックスのベースディレクトリ
   * @param timestamp APOPダイジェスト用のタイムスタンプ
   */
  public Pop3Session(File base, String timestamp) {
    this.base = base;
    this.timestamp = timestamp;
  }

  /**
   * 認証失敗の上限回数を設定する. 上限に達した接続は切断対象(終了扱い)となる.
   *
   * @param maxAuthFailures 上限回数
   */
  public void setMaxAuthFailures(int maxAuthFailures) {
    this.maxAuthFailures = maxAuthFailures;
  }

  /**
   * 認証失敗時の応答遅延を設定する.
   *
   * @param authFailureDelay 遅延(ミリ秒)。0以下は遅延なし
   */
  public void setAuthFailureDelay(long authFailureDelay) {
    this.authFailureDelay = authFailureDelay;
  }

  /**
   * 監査ログに出力する接続元を設定する.
   *
   * @param remoteAddress 接続元
   */
  public void setRemoteAddress(String remoteAddress) {
    this.remoteAddress = remoteAddress;
  }

  /**
   * STLSが利用可能かどうかを設定する. CAPAの応答に反映される.
   *
   * <p>trueの間はTLS確立前とみなし、USER/PASS/APOP/AUTHを拒否する(RFC 2595)。
   *
   * @param stlsAvailable 利用可能な場合はtrue
   */
  public void setStlsAvailable(boolean stlsAvailable) {
    this.stlsAvailable = stlsAvailable;
  }

  /**
   * tls-server-end-pointのバインディングデータを設定する. TLS確立後かつ設定済みの場合のみSCRAM-*-PLUSを提示する.
   *
   * @param channelBindingData サーバー証明書のハッシュ。提示できない場合はnull
   */
  public void setChannelBindingData(byte[] channelBindingData) {
    this.channelBindingData = channelBindingData;
  }

  private boolean plusAvailable() {
    return tlsActive && channelBindingData != null;
  }

  /**
   * TLSが確立済みかどうかを設定する. trueの場合のみAUTH PLAIN/LOGINを受け付け、CAPAで通知する.
   *
   * @param tlsActive 確立済みの場合はtrue
   */
  public void setTlsActive(boolean tlsActive) {
    this.tlsActive = tlsActive;
  }

  /**
   * 認証済みかどうか.
   *
   * @return 認証済みの場合はtrue
   */
  public boolean isAuthenticated() {
    return bPass;
  }

  /**
   * QUITコマンドで終了したかどうか.
   *
   * @return 終了した場合はtrue
   */
  public boolean isFinished() {
    return finished;
  }

  /** メールボックスのロックを解放する. 接続の終了時に呼び出す. 複数回呼び出しても良い. */
  public void close() {
    FileLock lock = mailboxLock;
    if (lock == null) {
      return;
    }
    mailboxLock = null;
    try (FileChannel channel = lock.channel()) {
      lock.release();
    } catch (IOException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
    }
  }

  /**
   * コマンド行を処理し、応答を出力する.
   *
   * @param line コマンド行(行終端文字列は含まない)
   * @param out 応答の出力先
   * @throws IOException ファイル読み取り時、または出力時に発生
   */
  public void execute(String line, Appendable out) throws IOException {
    if (sasl != null) {
      authResponse(line, out);
      return;
    }
    if (Pop3Util.isCapa(line)) {
      capa(out);
    } else if (stlsAvailable
        && (Pop3Util.isAuth(line)
            || Pop3Util.isUser(line)
            || Pop3Util.isPass(line)
            || Pop3Util.isApopNameDigest(line))) {
      // TLS確立前は平文パスワードが流れるおそれがあるため認証を拒否する
      writeLine(out, Constants.RECV_ERR_AUTH_TLS_REQUIRED);
    } else if (Pop3Util.isAuth(line)) {
      auth(line, out);
    } else if (Pop3Util.isUser(line)) {
      user(line, out);
    } else if (Pop3Util.isPass(line)) {
      pass(line, out);
    } else if (Pop3Util.isStat(line)) {
      stat(out);
    } else if (Pop3Util.isList(line)) {
      list(out);
    } else if (Pop3Util.isListNum(line)) {
      listNum(line, out);
    } else if (Pop3Util.isRetr(line)) {
      retr(out);
    } else if (Pop3Util.isRetrNum(line)) {
      retrNum(line, out);
    } else if (Pop3Util.isDeleNum(line)) {
      deleNum(line, out);
    } else if (Pop3Util.isRset(line)) {
      rset(out);
    } else if (Pop3Util.isQuit(line)) {
      quit(out);
    } else if (Pop3Util.isNoop(line)) {
      writeLine(out, Constants.RECV_OK);
    } else if (Pop3Util.isTopNumNum(line)) {
      topNumNum(line, out);
    } else if (Pop3Util.isUidl(line)) {
      uidl(out);
    } else if (Pop3Util.isUidlNum(line)) {
      uidlNum(line, out);
    } else if (Pop3Util.isApopNameDigest(line)) {
      apopNameDigest(line, out);
    } else if (line.isEmpty()) {
      // 何もしない
    } else {
      writeLine(out, Constants.RECV_ERR_CMD_NOT_FOUND);
    }
  }

  void capa(Appendable out) throws IOException {
    writeLine(out, Constants.RECV_OK, " Capability list follows");
    writeLine(out, "TOP");
    writeLine(out, "RESP-CODES");
    writeLine(out, "PIPELINING");
    writeLine(out, "IMPLEMENTATION uchicom-pop3");
    if (!stlsAvailable) {
      // TLS確立前は認証を受け付けないため、USERとSASLを通知しない
      writeLine(out, "USER");
    }
    writeLine(out, "UIDL");
    if (!stlsAvailable) {
      StringBuilder mechanisms = new StringBuilder("SASL");
      for (ScramAlgorithm algorithm : ScramAlgorithm.values()) {
        mechanisms.append(' ').append(algorithm.getMechanismName());
        if (plusAvailable()) {
          mechanisms.append(' ').append(algorithm.getPlusMechanismName());
        }
      }
      if (tlsActive) {
        mechanisms.append(' ').append(MECHANISM_PLAIN).append(' ').append(MECHANISM_LOGIN);
      }
      writeLine(out, mechanisms.toString());
    }
    if (stlsAvailable) {
      writeLine(out, "STLS");
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void auth(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    if (bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    if (args.length == 1) {
      // 対応機構の一覧
      writeLine(out, Constants.RECV_OK);
      for (ScramAlgorithm algorithm : ScramAlgorithm.values()) {
        writeLine(out, algorithm.getMechanismName());
        if (plusAvailable()) {
          writeLine(out, algorithm.getPlusMechanismName());
        }
      }
      if (tlsActive) {
        writeLine(out, MECHANISM_PLAIN);
        writeLine(out, MECHANISM_LOGIN);
      }
      writeLine(out, Constants.RECV_DATA);
      return;
    }
    sasl = newMechanism(args[1]);
    if (sasl == null) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    if (args.length == 2) {
      // 初期応答なし:機構ごとのチャレンジ(多くは空)を送る
      writeLine(out, "+ ", Base64.getEncoder().encodeToString(sasl.initialChallenge()));
    } else {
      authResponse(args[2], out);
    }
  }

  /** AUTH実行中のクライアント応答(base64)を処理する. */
  void authResponse(String line, Appendable out) throws IOException {
    SaslMechanism mechanism = sasl;
    try {
      if ("*".equals(line)) {
        throw new SaslAuthException("cancelled");
      }
      // 初期応答の"="は空の応答
      byte[] response = "=".equals(line) ? new byte[0] : Base64.getDecoder().decode(line);
      byte[] challenge = mechanism.step(response);
      if (!mechanism.isComplete()) {
        writeLine(out, "+ ", Base64.getEncoder().encodeToString(challenge));
        return;
      }
      sasl = null;
      user = mechanism.getUser();
      File userBox = findUserBox(user);
      if (userBox == null) {
        authFailed("AUTH", user, out);
        return;
      }
      loginResponse("AUTH", userBox, out);
    } catch (UncheckedIOException e) {
      sasl = null;
      throw e.getCause();
    } catch (SaslAuthException | IllegalArgumentException e) {
      sasl = null;
      authFailed("AUTH", mechanism.getUser(), out);
    }
  }

  /** 機構名からSASL機構を作る. 未対応、またはTLS確立前のPLAIN/LOGINの場合はnull. */
  private SaslMechanism newMechanism(String name) {
    if (tlsActive && MECHANISM_PLAIN.equalsIgnoreCase(name)) {
      return new PlainMechanism(this::verifyPassword);
    }
    if (tlsActive && MECHANISM_LOGIN.equalsIgnoreCase(name)) {
      return new LoginMechanism(this::verifyPassword);
    }
    boolean plus = name.toUpperCase(Locale.ROOT).endsWith(ScramAlgorithm.PLUS_SUFFIX);
    if (plus && !plusAvailable()) {
      return null;
    }
    ScramAlgorithm algorithm =
        ScramAlgorithm.find(
            plus ? name.substring(0, name.length() - ScramAlgorithm.PLUS_SUFFIX.length()) : name);
    return algorithm == null
        ? null
        : new ScramMechanism(algorithm, this::findScramCredential, plus, channelBindingData);
  }

  /** ユーザー名とパスワードを照合する. ユーザー不在、パスワードファイルなしはfalse. */
  private boolean verifyPassword(String name, String password) {
    try {
      File userBox = findUserBox(name);
      return userBox != null && constantTimeEquals(password, readPassword(userBox));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** SCRAMの資格情報をパスワードから導出する. 存在しないユーザーにも導出値を返す. */
  private ScramCredential findScramCredential(String name, ScramAlgorithm algorithm) {
    try {
      File userBox = findUserBox(name);
      String password = userBox == null ? null : readPassword(userBox);
      byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
      byte[] salt = Arrays.copyOf(algorithm.hmac(SERVER_SECRET, nameBytes), 16);
      if (password == null) {
        // 存在しないユーザー、パスワードファイルなしの場合は、誰にも一致しない擬似パスワードを使う
        password = Base64.getEncoder().encodeToString(algorithm.hmac(SERVER_SECRET, salt));
      }
      return algorithm.derive(password, salt, SCRAM_ITERATIONS);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void user(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    if (args.length < 2) {
      // 引数なしエラー
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    bUser = true;
    user = args[1];
    writeLine(out, Constants.RECV_OK);
  }

  void pass(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    if (args.length < 2 || !bUser || bPass) {
      // 引数なし、ユーザー名未入力、認証済みエラー
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    String pass = args[1];
    File userBox = findUserBox(user);
    if (userBox == null || pass.isEmpty()) {
      // ユーザー存在しない、パスワード入力なしエラー
      authFailed("PASS", user, out);
      return;
    }
    if (constantTimeEquals(pass, readPassword(userBox))) {
      loginResponse("PASS", userBox, out);
    } else {
      // パスワードファイルなし、パスワード不一致エラー
      authFailed("PASS", user, out);
    }
  }

  void apopNameDigest(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    if (args.length < 3 || bPass) {
      // 引数不足、パスワード認証後に再度認証はエラー
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    user = args[1];
    String digest = args[2];
    File userBox = findUserBox(user);
    if (userBox == null) {
      authFailed("APOP", user, out);
      return;
    }
    String password = readPassword(userBox);
    if (password == null) {
      // パスワードファイルなしエラー
      authFailed("APOP", user, out);
      return;
    }
    try {
      if (constantTimeEquals(digest, md5(timestamp + password))) {
        loginResponse("APOP", userBox, out);
      } else {
        // パスワード不一致エラー
        authFailed("APOP", user, out);
      }
    } catch (NoSuchAlgorithmException e) {
      logger.log(Level.WARNING, e.getMessage(), e);
      writeLine(out, Constants.RECV_ERR_CMD_NOT_FOUND);
    }
  }

  void stat(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    long fileLength = 0;
    int fileCnt = 0;
    for (int i = 0; i < mailList.size(); i++) {
      if (!delList.contains(mailList.get(i))) {
        fileLength += sizeList.get(i);
        fileCnt++;
      }
    }
    writeLine(
        out, Constants.RECV_OK, " ", String.valueOf(fileCnt), " ", String.valueOf(fileLength));
  }

  void list(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK);
    for (int i = 0; i < mailList.size(); i++) {
      File child = mailList.get(i);
      if (!delList.contains(child)) {
        writeLine(out, String.valueOf(i + 1), " ", String.valueOf(sizeList.get(i)));
      }
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void listNum(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    int index = findMail(args[1]);
    if (index < 0) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK, " ", args[1], " ", String.valueOf(sizeList.get(index)));
  }

  void retr(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK);
    for (File child : mailList) {
      if (!delList.contains(child)) {
        writeFile(out, child);
      }
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void retrNum(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    int index = findMail(args[1]);
    if (index < 0) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK, " ", String.valueOf(sizeList.get(index)));
    writeFile(out, mailList.get(index));
    writeLine(out, Constants.RECV_DATA);
  }

  void deleNum(String line, Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    int index = Pop3Util.parseNumber(Pop3Util.splitArgs(line)[1]) - 1;
    if (0 <= index && index < mailList.size() && !delList.contains(mailList.get(index))) {
      delList.add(mailList.get(index));
      writeLine(out, Constants.RECV_OK);
    } else {
      // index範囲外、または削除済み
      writeLine(out, Constants.RECV_ERR);
    }
  }

  void rset(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    // 消去マークを無くす
    delList.clear();
    writeLine(out, Constants.RECV_OK);
  }

  void quit(Appendable out) throws IOException {
    boolean deleted = true;
    if (delList != null) {
      // 消去マークの入ったファイルを削除する。失敗しても残りは削除を試みる
      for (File delFile : delList) {
        if (!delFile.delete()) {
          deleted = false;
        }
      }
    }
    writeLine(out, deleted ? Constants.RECV_OK : Constants.RECV_ERR);
    finished = true;
    close();
  }

  void topNumNum(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    int index = findMail(args[1]);
    int maxRow = Pop3Util.parseNumber(args[2]);
    if (index < 0 || maxRow < 0) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK);
    try (BufferedReader reader = newReader(mailList.get(index))) {
      int row = 0;
      boolean messageHead = true;
      String readLine = reader.readLine();
      while (readLine != null && (messageHead || row < maxRow)) {
        writeMailLine(out, readLine);
        if (!messageHead) {
          row++;
        } else if (readLine.isEmpty()) {
          // 空行でヘッダ終了(先頭行が空行の場合も含む)
          messageHead = false;
        }
        readLine = reader.readLine();
      }
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void uidl(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK);
    for (int i = 0; i < mailList.size(); i++) {
      File child = mailList.get(i);
      if (!delList.contains(child)) {
        writeLine(out, String.valueOf(i + 1), " ", toUid(child));
      }
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void uidlNum(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    int index = findMail(args[1]);
    if (index < 0) {
      writeLine(out, Constants.RECV_ERR);
      return;
    }
    writeLine(out, Constants.RECV_OK, " ", args[1], " ", toUid(mailList.get(index)));
  }

  /** 認証済みで、指定番号のメールが存在し削除予定でない場合にそのインデックスを返す. 該当しない場合は-1. */
  private int findMail(String number) {
    if (!bPass) {
      return -1;
    }
    int index = Pop3Util.parseNumber(number) - 1;
    if (index < 0 || mailList.size() <= index) {
      return -1;
    }
    return delList.contains(mailList.get(index)) ? -1 : index;
  }

  /** ユーザーのメールボックスを探す. 存在しない場合はnullを返す. */
  private File findUserBox(String name) {
    File[] boxes = base.listFiles();
    if (boxes == null) {
      return null;
    }
    for (File box : boxes) {
      if (box.isDirectory() && name.equals(box.getName())) {
        return box;
      }
    }
    return null;
  }

  /** パスワードファイルの先頭の空でない行を返す. ファイルがない場合はnullを返す. */
  private String readPassword(File userBox) throws IOException {
    File passwordFile = new File(userBox, Constants.PASSWORD_FILE_NAME);
    if (!passwordFile.isFile()) {
      return null;
    }
    // クライアントのコマンド行(UTF-8)と比較するため、メール本文用ではなくUTF-8で読む
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(passwordFile), StandardCharsets.UTF_8))) {
      String password = reader.readLine();
      while ("".equals(password)) {
        password = reader.readLine();
      }
      return password;
    }
  }

  /** メールボックスをロックしてログインし、結果を応答する. */
  private void loginResponse(String method, File userBox, Appendable out) throws IOException {
    if (login(userBox)) {
      logger.info(auditMessage("success", method, user));
      writeLine(out, Constants.RECV_OK);
    } else {
      logger.warning(auditMessage("in-use", method, user));
      writeLine(out, Constants.RECV_ERR_IN_USE);
    }
  }

  /** 認証失敗を記録し、遅延後に応答する. 上限に達した場合は切断対象(終了扱い)とする. */
  private void authFailed(String method, String name, Appendable out) throws IOException {
    logger.warning(auditMessage("failure", method, name));
    authFailures++;
    delay();
    writeLine(out, Constants.RECV_ERR);
    if (authFailures >= maxAuthFailures) {
      logger.warning(auditMessage("too many failures", method, name));
      finished = true;
    }
  }

  private void delay() {
    if (authFailureDelay <= 0) {
      return;
    }
    try {
      Thread.sleep(authFailureDelay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** 監査ログ用のメッセージ(パスワードは含めない). */
  private String auditMessage(String result, String method, String name) {
    return "auth "
        + result
        + " method="
        + method
        + " user="
        + sanitize(name)
        + " remote="
        + remoteAddress;
  }

  /** ログ偽装を防ぐため、制御文字を置換する. */
  private static String sanitize(String value) {
    return value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "?");
  }

  /** 定数時間で文字列を比較する. どちらかがnullの場合はfalse. */
  static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * メールボックスをロックし、メールリストとサイズを読み込む.
   *
   * @return ロックを取得できず、使用中の場合はfalse
   */
  private boolean login(File userBox) throws IOException {
    if (!lock(userBox)) {
      return false;
    }
    try {
      load(userBox);
    } catch (IOException | RuntimeException e) {
      close();
      throw e;
    }
    bPass = true;
    return true;
  }

  /** メールボックスの排他ロックを取得する. 他の接続が保持している場合はfalse. */
  private boolean lock(File userBox) throws IOException {
    RandomAccessFile file = new RandomAccessFile(new File(userBox, Constants.LOCK_FILE_NAME), "rw");
    try {
      FileLock lock = file.getChannel().tryLock();
      if (lock != null) {
        mailboxLock = lock;
        return true;
      }
    } catch (OverlappingFileLockException e) {
      // 同一プロセス内の他の接続が保持している
    } catch (IOException | RuntimeException e) {
      file.close();
      throw e;
    }
    file.close();
    return false;
  }

  /** メールリストとサイズを読み込む. */
  private void load(File userBox) throws IOException {
    File[] mails =
        userBox.listFiles(
            (dir, name) -> {
              File file = new File(dir, name);
              return file.isFile()
                  && !file.isHidden()
                  && file.canRead()
                  && !Constants.PASSWORD_FILE_NAME.equals(name)
                  && !Constants.LOCK_FILE_NAME.equals(name);
            });
    mailList = Arrays.asList(mails == null ? new File[0] : mails);
    Collections.sort(mailList, FileComparator.instance);
    sizeList = new ArrayList<>(mailList.size());
    for (File mail : mailList) {
      sizeList.add(octets(mail));
    }
    delList = new ArrayList<>();
  }

  private static String md5(String value) throws NoSuchAlgorithmException {
    byte[] bytes = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder builder = new StringBuilder(32);
    for (byte b : bytes) {
      builder.append(String.format("%02x", b & 0xFF));
    }
    return builder.toString();
  }

  /** ファイル名全体から導出した、使用可能文字(0x21〜0x7E)のみで70文字以内の一意値を返す. */
  private static String toUid(File child) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256")
              .digest(child.getName().getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(64);
      for (byte b : bytes) {
        builder.append(String.format("%02x", b & 0xFF));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 は全JREで必須
      throw new IllegalStateException(e);
    }
  }

  /** ファイルをバイト単位で扱うため、ISO_8859_1 で読む. */
  private static BufferedReader newReader(File file) throws IOException {
    return new BufferedReader(
        new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1));
  }

  /** メールを送信する際のオクテット数(改行はCRLF、ドットスタッフィング前)を返す. */
  private static long octets(File file) throws IOException {
    long length = 0;
    try (BufferedReader reader = newReader(file)) {
      String readLine = reader.readLine();
      while (readLine != null) {
        length += readLine.length() + Constants.RECV_LINE_END.length();
        readLine = reader.readLine();
      }
    }
    return length;
  }

  private static void writeFile(Appendable out, File file) throws IOException {
    try (BufferedReader reader = newReader(file)) {
      String readLine = reader.readLine();
      while (readLine != null) {
        writeMailLine(out, readLine);
        readLine = reader.readLine();
      }
    }
  }

  /** メールの1行を、行頭が . の場合はドットスタッフィングして出力する(RFC 1939). */
  private static void writeMailLine(Appendable out, String line) throws IOException {
    if (line.startsWith(".")) {
      writeLine(out, ".", line);
    } else {
      writeLine(out, line);
    }
  }

  private static void writeLine(Appendable out, String... strings) throws IOException {
    for (String string : strings) {
      out.append(string);
    }
    out.append(Constants.RECV_LINE_END);
  }
}
