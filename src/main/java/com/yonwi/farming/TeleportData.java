package com.yonwi.farming;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.api.coords.WorldPoint;

/**
 * Where teleports land, from Shortest Path's transport tables (BSD-2, see THIRD_PARTY_NOTICES):
 * items, spells, house portals, jewellery boxes, fairy rings, spirit trees and quetzals. Used to
 * tell whether a teleport went where this step's route goes.
 */
final class TeleportData {
  private TeleportData() {}

  private static final String[] FILES = {
    "sp-teleportation_items.tsv",
    "sp-teleportation_spells.tsv",
    "sp-teleportation_portals_poh.tsv",
    "sp-teleportation_portals.tsv",
    "sp-teleportation_boxes.tsv",
    "sp-fairy_rings.tsv",
    "sp-spirit_trees.tsv",
    "sp-quetzal_whistle.tsv",
    "sp-quetzals.tsv"
  };

  /** A landing tile and the words naming it (display info plus menu text), normalised. */
  private static final class Landing {
    final WorldPoint at;
    final String name;

    Landing(WorldPoint at, String name) {
      this.at = at;
      this.name = name;
    }
  }

  private static volatile List<Landing> landings;

  /** Words in travel text that don't name a place. */
  private static final Set<String> FILLER =
      new HashSet<>(
          Arrays.asList(
              "then", "north", "south", "east", "west", "northeast", "northwest", "southeast",
              "southwest", "patch", "herb", "herbs", "tree", "fruit", "house", "portal", "beach",
              "walk", "follow", "landing", "site", "through", "castle", "with", "from", "into",
              "near", "farm", "your", "teleport", "cast", "guide", "mountain", "charter", "rowboat",
              "boat", "nearby", "inside", "outside", "nexus"));

  /** Lower case, letters, digits and single spaces only ("Xeric's Glade" → "xerics glade"). */
  static String norm(String s) {
    return s.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "").replaceAll("[^a-z0-9]+", " ").trim();
  }

  private static List<Landing> all() {
    List<Landing> loaded = landings;
    if (loaded != null) return loaded;
    loaded = new ArrayList<>();
    for (String file : FILES) {
      try (InputStream raw = TeleportData.class.getResourceAsStream(file)) {
        if (raw == null) continue;
        String[] lines = new String(readAll(raw), StandardCharsets.UTF_8).split("\n");
        Map<String, Integer> column = new HashMap<>();
        for (String line : lines) {
          String trimmed = line.replace("\r", "");
          if (trimmed.startsWith("#") && trimmed.contains("Destination")) {
            column.clear();
            String[] names = trimmed.replaceFirst("^#\\s*", "").split("\t");
            for (int i = 0; i < names.length; i++) column.put(names[i].trim(), i);
            continue;
          }
          if (trimmed.isEmpty() || trimmed.startsWith("#") || column.isEmpty()) continue;
          String[] cells = trimmed.split("\t", -1);
          String[] xyz = cell(cells, column, "Destination").split(" ");
          if (xyz.length < 3) continue;
          try {
            WorldPoint at =
                new WorldPoint(
                    Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
            String name =
                norm(cell(cells, column, "Display info") + " " + cell(cells, column, "menuOption menuTarget objectID"));
            loaded.add(new Landing(at, name));
          } catch (NumberFormatException ignored) {
            // a malformed row
          }
        }
      } catch (IOException | RuntimeException ignored) {
        // Without a table the check falls back to trusting the click.
      }
    }
    landings = loaded;
    return loaded;
  }

  private static String cell(String[] cells, Map<String, Integer> column, String name) {
    Integer i = column.get(name);
    return i == null || i >= cells.length ? "" : cells[i].trim();
  }

  private static byte[] readAll(InputStream in) throws IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
    return out.toByteArray();
  }

  private static final Map<String, List<WorldPoint>> cache = new HashMap<>();

  /**
   * Where this route's teleports can land: entries naming its destination (e.g. "Falador Park",
   * "Catherby Portal", "Hunter Guild"), its fairy ring code, or its teleport item or spell
   * ("Ectophial", "Camelot Teleport"). Empty when nothing matches.
   */
  static synchronized List<WorldPoint> landingsFor(String travel) {
    List<WorldPoint> known = cache.get(travel);
    if (known != null) return known;
    int arrow = travel.indexOf('→');
    String method = norm(arrow < 0 ? travel : travel.substring(0, arrow)).replaceFirst("^cast ", "");
    String after = arrow < 0 ? "" : travel.substring(arrow + 1).split("[,;]| then ")[0];
    List<String> words = new ArrayList<>();
    for (String w : norm(after).split(" ")) if (w.length() >= 4 && !FILLER.contains(w)) words.add(w);
    String code = FarmingPlanner.fairyCode(travel);
    String spaced = code == null ? null : norm(String.join(" ", code.split("")));
    List<WorldPoint> found = new ArrayList<>();
    // First by the place it names (or its fairy ring code)…
    for (Landing l : all()) {
      boolean match = false;
      if (spaced != null) match = (" " + l.name + " ").contains(" " + spaced + " ");
      else if (!words.isEmpty()) {
        match = true;
        for (String w : words) match &= (" " + l.name + " ").contains(" " + w);
      }
      if (match) found.add(l.at);
    }
    // …or, when that names nothing, by the item or spell itself ("Ectophial", "Explorer's ring:
    // Teleport"). Never both: a ring of wealth to the Grand Exchange mustn't count for Falador.
    if (found.isEmpty() && spaced == null && method.length() >= 5 && !method.startsWith("house"))
      for (Landing l : all()) if (l.name.startsWith(method)) found.add(l.at);
    cache.put(travel, found);
    return found;
  }

  /** Whether you're within {@code range} tiles of where this route's teleports land. */
  static boolean landedFor(String travel, WorldPoint here, int range) {
    for (WorldPoint at : landingsFor(travel))
      if (at.getPlane() == here.getPlane() && at.distanceTo(here) <= range) return true;
    return false;
  }
}
