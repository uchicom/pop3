// (C) 2026 uchicom
package com.uchicom.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileComparatorTest {

  private final FileComparator comparator = new FileComparator();

  @TempDir File dir;

  private File file(String name, long lastModified) throws IOException {
    var file = new File(dir, name);
    Files.writeString(file.toPath(), "x");
    assertThat(file.setLastModified(lastModified)).isTrue();
    return file;
  }

  @Test
  void compare_olderFirst() throws IOException {
    var older = file("a", 1_000_000L);
    var newer = file("b", 2_000_000L);

    assertThat(comparator.compare(older, newer)).isNegative();
    assertThat(comparator.compare(newer, older)).isPositive();
  }

  @Test
  void compare_sameTime() throws IOException {
    var a = file("a", 1_000_000L);
    var b = file("b", 1_000_000L);

    assertThat(comparator.compare(a, b)).isZero();
  }

  @Test
  void compare_null() throws IOException {
    var a = file("a", 1_000_000L);

    assertThat(comparator.compare(null, null)).isZero();
    assertThat(comparator.compare(null, a)).isPositive();
    assertThat(comparator.compare(a, null)).isNegative();
  }

  @Test
  void sort() throws IOException {
    var c = file("c", 3_000_000L);
    var a = file("a", 1_000_000L);
    var b = file("b", 2_000_000L);
    List<File> list = new ArrayList<>(List.of(c, a, b));

    list.sort(comparator);

    assertThat(list).containsExactly(a, b, c);
  }
}
