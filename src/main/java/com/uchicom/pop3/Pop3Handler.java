// (C) 2013 uchicom
package com.uchicom.pop3;

import com.uchicom.server.Handler;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

/**
 * NIOで接続を処理するハンドラ. コマンド処理は {@link Pop3Session} に委譲し、入出力のみを担当する。
 *
 * <p>出力時に一度全てバッファに溜め込むので負荷があがってしまう。 随時書き込むようにしないといけない。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class Pop3Handler implements Handler {

  /** 出力用の文字列バッファ */
  StringBuffer strBuff = new StringBuffer(1024);

  /** コマンド処理 */
  Pop3Session session;

  ByteBuffer readBuff = ByteBuffer.allocate(128);

  /**
   * 設定を保持するコンストラクタ.
   *
   * @param base 基本パス
   * @param hostName ホスト名
   */
  public Pop3Handler(File base, String hostName) {
    String timestamp =
        "<"
            + Thread.currentThread().getId()
            + "."
            + System.currentTimeMillis()
            + "@"
            + hostName
            + ">";
    session = new Pop3Session(base, timestamp);
    strBuff.append(Constants.RECV_OK);
    strBuff.append(' ');
    strBuff.append(timestamp);
    strBuff.append(Constants.RECV_LINE_END);
  }

  /* (non-Javadoc)
   * @see com.uchicom.dirpop3.Handler#handle(java.nio.channels.SelectionKey)
   */
  @Override
  public void handle(SelectionKey key) throws IOException {
    SocketChannel channel = (SocketChannel) key.channel();
    if (key.isReadable()) {
      int length = channel.read(readBuff);
      if (length > 0 && checkCmd()) {
        session.execute(getCmd(), strBuff);
        if (strBuff.length() > 0) {
          key.interestOps(SelectionKey.OP_WRITE);
        }
        readBuff.clear();
      }
    }
    if (key.isWritable() && strBuff.length() > 0) {
      // 初回の出力を実施
      int size = channel.write(ByteBuffer.wrap(strBuff.toString().getBytes()));
      // 書き込み処理が終わっていないかを確認する。
      // 処理が途中の場合は途中から実施する。
      if (size < strBuff.length()) {
        strBuff = new StringBuffer(strBuff.substring(size));
      } else {
        strBuff.setLength(0);
        key.interestOps(SelectionKey.OP_READ);
        // 終了処理
        if (session.isFinished()) {
          key.cancel();
          channel.close();
        }
      }
    }
  }

  /**
   * コマンド行が入力されたかどうかチェックする.
   *
   * @return コマンド行が入力された場合にtrue,それ以外はfalseを返します
   */
  public boolean checkCmd() {
    String line = new String(Arrays.copyOfRange(readBuff.array(), 0, readBuff.position()));
    return line.indexOf("\r\n") >= 0;
  }

  /**
   * コマンド行から文字列を取得する. \r\nは除外する.
   *
   * @return コマンド行から取得した文字列を返します
   */
  public String getCmd() {
    String line = new String(Arrays.copyOfRange(readBuff.array(), 0, readBuff.position()));
    return line.substring(0, line.indexOf("\r\n"));
  }
}
