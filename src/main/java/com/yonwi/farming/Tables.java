package com.yonwi.farming;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Reading the Shortest Path plugin's tab-separated data files bundled with this plugin. */
final class Tables {
  private Tables() {}

  static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
    return out.toByteArray();
  }

  /** The named column's cell in a row, trimmed, or "" if there's no such column. */
  static String cell(String[] cells, Map<String, Integer> column, String name) {
    Integer index = column.get(name);
    return index == null || index >= cells.length ? "" : cells[index].trim();
  }
}
