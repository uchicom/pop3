// (C) 2026 uchicom
package com.uchicom.pop3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * POP3のコマンド処理を行うクラス.
 *
 * <p>入出力には依存せず、コマンド行を受け取って応答を {@link Appendable} に書き出す。 ブロッキングI/OとNIOのどちらからでも利用できる。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Pop3Session {

  /** SCRAMの反復回数 */
  private static final int SCRAM_ITERATIONS = 4096;

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

  /** メールボックス内のリスト(認証成功時に設定される) */
  private List<File> mailList;

  /** 削除リスト(認証成功時に設定される) */
  private List<File> delList;

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
      writeLine(out, Constants.RECV_NG_CMD_NOT_FOUND);
    }
  }

  void capa(Appendable out) throws IOException {
    writeLine(out, Constants.RECV_OK, " Capability list follows");
    writeLine(out, "TOP");
    if (!stlsAvailable) {
      // TLS確立前は認証を受け付けないため、USERとSASLを通知しない
      writeLine(out, "USER");
    }
    writeLine(out, "UIDL");
    if (!stlsAvailable) {
      StringBuilder mechanisms = new StringBuilder("SASL");
      for (ScramAlgorithm algorithm : ScramAlgorithm.values()) {
        mechanisms.append(' ').append(algorithm.getMechanismName());
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
      writeLine(out, Constants.RECV_NG);
      return;
    }
    if (args.length == 1) {
      // 対応機構の一覧
      writeLine(out, Constants.RECV_OK);
      for (ScramAlgorithm algorithm : ScramAlgorithm.values()) {
        writeLine(out, algorithm.getMechanismName());
      }
      writeLine(out, Constants.RECV_DATA);
      return;
    }
    ScramAlgorithm algorithm = ScramAlgorithm.find(args[1]);
    if (algorithm == null) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    sasl = new ScramMechanism(algorithm, this::findScramCredential);
    if (args.length == 2) {
      // 初期応答なし:空のチャレンジを送る
      writeLine(out, "+ ");
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
        writeLine(out, Constants.RECV_NG);
        return;
      }
      login(userBox);
      writeLine(out, Constants.RECV_OK);
    } catch (UncheckedIOException e) {
      sasl = null;
      throw e.getCause();
    } catch (SaslAuthException | IllegalArgumentException e) {
      sasl = null;
      writeLine(out, Constants.RECV_NG);
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
      writeLine(out, Constants.RECV_NG);
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
      writeLine(out, Constants.RECV_NG);
      return;
    }
    String pass = args[1];
    File userBox = findUserBox(user);
    if (userBox == null || pass.isEmpty()) {
      // ユーザー存在しない、パスワード入力なしエラー
      writeLine(out, Constants.RECV_NG);
      return;
    }
    if (pass.equals(readPassword(userBox))) {
      login(userBox);
      writeLine(out, Constants.RECV_OK);
    } else {
      // パスワードファイルなし、パスワード不一致エラー
      writeLine(out, Constants.RECV_NG);
    }
  }

  void apopNameDigest(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    if (args.length < 3 || bPass) {
      // 引数不足、パスワード認証後に再度認証はエラー
      writeLine(out, Constants.RECV_NG);
      return;
    }
    user = args[1];
    String digest = args[2];
    File userBox = findUserBox(user);
    if (userBox == null) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    String password = readPassword(userBox);
    if (password == null) {
      // パスワードファイルなしエラー
      writeLine(out, Constants.RECV_NG);
      return;
    }
    try {
      if (digest.equals(md5(timestamp + password))) {
        login(userBox);
        writeLine(out, Constants.RECV_OK);
      } else {
        // パスワード不一致エラー
        writeLine(out, Constants.RECV_NG);
      }
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      writeLine(out, Constants.RECV_NG_CMD_NOT_FOUND);
    }
  }

  void stat(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    long fileLength = 0;
    int fileCnt = 0;
    for (File child : mailList) {
      if (!delList.contains(child)) {
        fileLength += child.length();
        fileCnt++;
      }
    }
    writeLine(
        out, Constants.RECV_OK, " ", String.valueOf(fileCnt), " ", String.valueOf(fileLength));
  }

  void list(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    writeLine(out, Constants.RECV_OK);
    for (int i = 0; i < mailList.size(); i++) {
      File child = mailList.get(i);
      if (!delList.contains(child)) {
        writeLine(out, String.valueOf(i + 1), " ", String.valueOf(child.length()));
      }
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void listNum(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    File child = findMail(args[1]);
    if (child == null) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    writeLine(out, Constants.RECV_OK, " ", args[1], " ", String.valueOf(child.length()));
  }

  void retr(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_NG);
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
    File child = findMail(args[1]);
    if (child == null) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    writeLine(out, Constants.RECV_OK, " ", String.valueOf(child.length()));
    writeFile(out, child);
    writeLine(out, Constants.RECV_DATA);
  }

  void deleNum(String line, Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    int index = Pop3Util.parseNumber(Pop3Util.splitArgs(line)[1]) - 1;
    if (0 <= index && index < mailList.size()) {
      delList.add(mailList.get(index));
      writeLine(out, Constants.RECV_OK);
    } else {
      // index範囲外
      writeLine(out, Constants.RECV_NG);
    }
  }

  void rset(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    // 消去マークを無くす
    delList.clear();
    writeLine(out, Constants.RECV_OK);
  }

  void quit(Appendable out) throws IOException {
    if (delList != null) {
      // 消去マークの入ったファイルを削除する
      for (File delFile : delList) {
        delFile.delete();
      }
    }
    writeLine(out, Constants.RECV_OK);
    finished = true;
  }

  void topNumNum(String line, Appendable out) throws IOException {
    String[] args = Pop3Util.splitArgs(line);
    File child = findMail(args[1]);
    int maxRow = Pop3Util.parseNumber(args[2]);
    if (child == null || maxRow < 0) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    writeLine(out, Constants.RECV_OK);
    try (BufferedReader reader = newReader(child)) {
      String readLine = reader.readLine();
      int row = 0;
      boolean messageHead = true;
      while (readLine != null && (messageHead || row <= maxRow)) {
        writeLine(out, readLine);
        readLine = reader.readLine();
        if (!messageHead) {
          row++;
        }
        if ("".equals(readLine)) {
          messageHead = false;
        }
      }
    }
    writeLine(out, Constants.RECV_DATA);
  }

  void uidl(Appendable out) throws IOException {
    if (!bPass) {
      writeLine(out, Constants.RECV_NG);
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
    File child = findMail(args[1]);
    if (child == null) {
      writeLine(out, Constants.RECV_NG);
      return;
    }
    writeLine(out, Constants.RECV_OK, " ", args[1], " ", toUid(child));
  }

  /** 認証済みで、指定番号のメールが存在し削除予定でない場合にそのファイルを返す. */
  private File findMail(String number) {
    if (!bPass) {
      return null;
    }
    int index = Pop3Util.parseNumber(number) - 1;
    if (index < 0 || mailList.size() <= index) {
      return null;
    }
    File child = mailList.get(index);
    return delList.contains(child) ? null : child;
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
    try (BufferedReader reader = newReader(passwordFile)) {
      String password = reader.readLine();
      while ("".equals(password)) {
        password = reader.readLine();
      }
      return password;
    }
  }

  /** 認証成功時にメールリストを読み込む. */
  private void login(File userBox) {
    File[] mails =
        userBox.listFiles(
            (dir, name) -> {
              File file = new File(dir, name);
              return file.isFile()
                  && !file.isHidden()
                  && file.canRead()
                  && !Constants.PASSWORD_FILE_NAME.equals(name);
            });
    mailList = Arrays.asList(mails == null ? new File[0] : mails);
    Collections.sort(mailList, FileComparator.instance);
    delList = new ArrayList<>();
    bPass = true;
  }

  private static String md5(String value) throws NoSuchAlgorithmException {
    byte[] bytes = MessageDigest.getInstance("MD5").digest(value.getBytes());
    StringBuilder builder = new StringBuilder(32);
    for (byte b : bytes) {
      builder.append(String.format("%02x", b & 0xFF));
    }
    return builder.toString();
  }

  private static String toUid(File child) {
    String name = child.getName();
    int lastIndex = name.lastIndexOf('~');
    if (lastIndex >= 0) {
      return name.substring(lastIndex);
    }
    return name.length() > 70 ? name.substring(name.length() - 70) : name;
  }

  private static BufferedReader newReader(File file) throws IOException {
    return new BufferedReader(new InputStreamReader(new FileInputStream(file)));
  }

  private static void writeFile(Appendable out, File file) throws IOException {
    try (BufferedReader reader = newReader(file)) {
      String readLine = reader.readLine();
      while (readLine != null) {
        writeLine(out, readLine);
        readLine = reader.readLine();
      }
    }
  }

  private static void writeLine(Appendable out, String... strings) throws IOException {
    for (String string : strings) {
      out.append(string);
    }
    out.append(Constants.RECV_LINE_END);
  }
}
