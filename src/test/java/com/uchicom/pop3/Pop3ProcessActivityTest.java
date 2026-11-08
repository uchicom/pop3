// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** getLastTime(通信のたびに更新される最終処理時刻)のテスト. */
class Pop3ProcessActivityTest {

  @TempDir File base;

  @Test
  void lastTime_isVolatile() throws Exception {
    var field = Pop3Process.class.getDeclaredField("lastTime");

    // 監視スレッドから参照されるため
    assertThat(Modifier.isVolatile(field.getModifiers())).isTrue();
  }

  @Test
  void activityInputStream_notifiesOnEveryRead() throws Exception {
    var count = new AtomicInteger();
    try (var in =
        new Pop3Process.ActivityInputStream(
            new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), count::incrementAndGet)) {
      assertThat(in.read()).isEqualTo(1);
      assertThat(count).hasValue(1);

      var buf = new byte[3];
      assertThat(in.read(buf, 0, 3)).isEqualTo(3);
      assertThat(count).hasValue(2);
      assertThat(buf).containsExactly(2, 3, 4);
    }
  }

  @Test
  void activityOutputStream_notifiesOnEveryWriteAndPassesData() throws Exception {
    var count = new AtomicInteger();
    var sink = new ByteArrayOutputStream();
    try (var out = new Pop3Process.ActivityOutputStream(sink, count::incrementAndGet)) {
      out.write('a');
      assertThat(count).hasValue(1);

      out.write(new byte[] {'b', 'c', 'd'}, 1, 2);
      assertThat(count).hasValue(2);
    }

    assertThat(sink.toString("ISO-8859-1")).isEqualTo("acd");
  }

  @Test
  void getLastTime_advancesWhileReceivingPartialLine() throws Exception {
    var parameter = new Pop3Parameter(new String[0]);
    parameter.put("dir", base.getAbsolutePath());
    parameter.init();
    try (var serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        var client = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
        var accepted = serverSocket.accept()) {
      var process = new Pop3Process(parameter, accepted);
      var thread = new Thread(process::execute);
      thread.start();
      try {
        client.setSoTimeout(5000);
        // 挨拶(書き込み)を受け取った時点で、書き込みによる更新が済んでいる
        var greeting = client.getInputStream().read();
        assertThat(greeting).isEqualTo('+');
        var before = process.getLastTime();

        Thread.sleep(50);
        // 改行を送らない不完全な行でも、受信のたびに更新される
        client.getOutputStream().write("NO".getBytes());
        client.getOutputStream().flush();

        var deadline = System.currentTimeMillis() + 3000;
        while (process.getLastTime() <= before && System.currentTimeMillis() < deadline) {
          Thread.sleep(10);
        }
        assertThat(process.getLastTime()).isGreaterThan(before);
      } finally {
        client.close();
        thread.join(5000);
      }
    }
  }
}
