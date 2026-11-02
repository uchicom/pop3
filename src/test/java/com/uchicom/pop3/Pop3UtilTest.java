// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Pop3UtilTest {

  record Case(String name, Predicate<String> predicate, List<String> ok, List<String> ng) {}

  /** ok は一致するコマンド(大文字小文字・後続スペース許容)、ng は一致しないコマンド. */
  static Case of(String name, Predicate<String> predicate, String ok1, String ok2, String... ng) {
    return new Case(name, predicate, List.of(ok1, ok2), List.of(ng));
  }

  static Stream<Arguments> commands() {
    return Stream.of(
            of("isUser", Pop3Util::isUser, "USER alice", "user alice ", "USER", "USER a b"),
            of("isPass", Pop3Util::isPass, "PASS secret", "pass secret", "PASS", "PASS a b"),
            of("isStat", Pop3Util::isStat, "STAT", "stat ", "STAT 1"),
            of("isList", Pop3Util::isList, "LIST", "list", "LIST 1"),
            of("isListNum", Pop3Util::isListNum, "LIST 1", "list 12", "LIST", "LIST 0", "LIST a"),
            of("isRetr", Pop3Util::isRetr, "RETR", "retr ", "RETR 1"),
            of("isRetrNum", Pop3Util::isRetrNum, "RETR 1", "retr 10", "RETR", "RETR 0"),
            of("isDeleNum", Pop3Util::isDeleNum, "DELE 1", "dele 10", "DELE", "DELE 0"),
            of("isRset", Pop3Util::isRset, "RSET", "rset", "RSET 1"),
            of("isQuit", Pop3Util::isQuit, "QUIT", "quit ", "QUIT now"),
            of(
                "isTopNumNum",
                Pop3Util::isTopNumNum,
                "TOP 1 0",
                "top 2 10",
                "TOP",
                "TOP 1",
                "TOP 0 1",
                "TOP 1 a"),
            of("isUidl", Pop3Util::isUidl, "UIDL", "uidl", "UIDL 1"),
            of("isUidlNum", Pop3Util::isUidlNum, "UIDL 1", "uidl 3", "UIDL", "UIDL 0"),
            of(
                "isApopNameDigest",
                Pop3Util::isApopNameDigest,
                "APOP alice abcdef",
                "apop alice abc",
                "APOP",
                "APOP alice"),
            of("isNoop", Pop3Util::isNoop, "NOOP", "noop", "NOOP 1"),
            of("isCapa", Pop3Util::isCapa, "CAPA", "capa", "CAPA 1"),
            of("isStls", Pop3Util::isStls, "STLS", "stls", "STLS 1"))
        .flatMap(
            c ->
                Stream.concat(
                    c.ok().stream().map(s -> Arguments.of(c.name(), c.predicate(), s, true)),
                    c.ng().stream().map(s -> Arguments.of(c.name(), c.predicate(), s, false))));
  }

  @ParameterizedTest(name = "{0}(\"{2}\") = {3}")
  @MethodSource("commands")
  void commandMatching(String name, Predicate<String> predicate, String line, boolean expected) {
    assertThat(predicate.test(line)).isEqualTo(expected);
  }

  @Test
  void recieveLine_joinsStringsAndAppendsCrLf() {
    var out = new ByteArrayOutputStream();
    var ps = new PrintStream(out, true, StandardCharsets.UTF_8);

    Pop3Util.recieveLine(ps, Constants.RECV_OK, " ", "1", " ", "20");

    assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("+OK 1 20\r\n");
  }
}
