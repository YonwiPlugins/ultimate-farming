package com.yonwi.farming;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.api.coords.WorldPoint;

/**
 * Walking routes over the whole game map, not just the loaded area.
 *
 * <p>Uses the Shortest Path plugin's collision map (collision-map.zip) and its flag layout and
 * movement rules, from https://github.com/Skretzo/shortest-path (SplitFlagMap, CollisionMap),
 * BSD-2-Clause: see THIRD_PARTY_NOTICES.md. Walking only: no doors-as-transports, teleports or
 * boats, since the plugin already chooses the teleport. If no route is found, the caller falls
 * back to the loaded-scene route in {@link TilePath}.
 */
final class WorldPath {
  private static final int REGION = 64;
  // Per tile: north edge open, east edge open, north wall, east wall (only the first two used).
  private static final int FLAG_NORTH = 0;
  private static final int FLAG_EAST = 1;
  private static final int FLAG_COUNT = 4;
  private static final int BITS_PER_PLANE = REGION * REGION * FLAG_COUNT;

  /** How far around the start and goal the search may roam, in tiles. */
  private static final int MARGIN = 64;
  /** Search budget, so a hopeless route can't stall a game tick. */
  private static final int MAX_TILES = 250_000;

  /** Region (x, y) packed as x << 16 | y → its bits, all planes. */
  private static volatile Map<Integer, BitSet> regions;

  private static final AtomicBoolean loading = new AtomicBoolean();

  private WorldPath() {}

  /** Whether the map is ready; the first call starts loading it in the background. */
  static boolean ready() {
    if (regions != null) return true;
    if (loading.compareAndSet(false, true)) {
      Thread loader = new Thread(WorldPath::load, "ultimate-farming-collision-map");
      loader.setDaemon(true);
      loader.start();
    }
    return false;
  }

  /** A walking shortcut: door, gate, stile or Agility shortcut, from Shortest Path's data. */
  private static final class Link {
    final int x;
    final int y;
    /** e.g. "50 Agility;78 Mining", or "". */
    final String skills;

    Link(int x, int y, String skills) {
      this.x = x;
      this.y = y;
      this.skills = skills;
    }
  }

  /** Origin tile (packed x, y, plane) → where its doors, gates and shortcuts lead (same floor). */
  private static Map<Long, List<Link>> links = new HashMap<>();

  /** Your skill levels by name ("Agility" → 72), for shortcuts with requirements. */
  private static volatile Map<String, Integer> levels = Collections.emptyMap();

  static void setLevels(Map<String, Integer> skillLevels) {
    levels = skillLevels;
  }

  private static long key(int x, int y, int z) {
    return ((long) z << 32) | ((long) x << 16) | y;
  }

  /**
   * Reads one of Shortest Path's transport tables. Keeps same-floor links that need no items,
   * varbits or varplayers; skill requirements are checked when routing. Quest requirements are
   * ignored (a farming route already assumes the usual quests).
   */
  private static void loadLinks(String resource, Map<Long, List<Link>> into) throws IOException {
    try (InputStream raw = WorldPath.class.getResourceAsStream(resource)) {
      if (raw == null) return;
      String[] lines = new String(Tables.readAll(raw), StandardCharsets.UTF_8).split("\n");
      Map<String, Integer> column = new HashMap<>();
      for (String line : lines) {
        if (line.startsWith("# Origin")) {
          String[] names = line.substring(2).split("\t");
          for (int i = 0; i < names.length; i++) column.put(names[i].trim(), i);
          continue;
        }
        if (line.isEmpty() || line.startsWith("#") || column.isEmpty()) continue;
        String[] cells = line.split("\t", -1);
        String[] from = Tables.cell(cells, column, "Origin").split(" ");
        String[] to = Tables.cell(cells, column, "Destination").split(" ");
        if (from.length < 3 || to.length < 3) continue;
        if (!Tables.cell(cells, column, "Items").isEmpty()
            || !Tables.cell(cells, column, "Varbits").isEmpty()
            || !Tables.cell(cells, column, "VarPlayers").isEmpty()) continue;
        try {
          int fromPlane = Integer.parseInt(from[2].trim());
          int toPlane = Integer.parseInt(to[2].trim());
          if (fromPlane != toPlane) continue;
          long origin = key(Integer.parseInt(from[0]), Integer.parseInt(from[1]), fromPlane);
          into.computeIfAbsent(origin, k -> new ArrayList<>())
              .add(
                  new Link(
                      Integer.parseInt(to[0]),
                      Integer.parseInt(to[1]),
                      Tables.cell(cells, column, "Skills")));
        } catch (NumberFormatException ignored) {
          // A malformed row: skip it.
        }
      }
    }
  }

  /** Whether your levels meet a requirement like "50 Agility;78 Mining". */
  private static boolean meets(String skills) {
    if (skills.isEmpty()) return true;
    for (String part : skills.split(";")) {
      String[] levelSkill = part.trim().split(" ", 2);
      if (levelSkill.length < 2) continue;
      try {
        Integer have = levels.get(levelSkill[1].trim());
        if (have == null || have < Integer.parseInt(levelSkill[0])) return false;
      } catch (NumberFormatException ignored) {
        // Unknown format: don't block on it.
      }
    }
    return true;
  }

  private static void load() {
    Map<Long, List<Link>> loadedLinks = new HashMap<>();
    try {
      loadLinks("sp-transports.tsv", loadedLinks);
      loadLinks("sp-agility_shortcuts.tsv", loadedLinks);
      // Shortcuts Shortest Path doesn't have yet (e.g. the Summer Shore rocks), in the same format.
      loadLinks("extra-shortcuts.tsv", loadedLinks);
    } catch (IOException | RuntimeException ignored) {
      // Without shortcuts the map still works, just with fewer ways through.
    }
    links = loadedLinks;
    Map<Integer, BitSet> loaded = new HashMap<>();
    try (InputStream raw = WorldPath.class.getResourceAsStream("collision-map.zip")) {
      if (raw == null) return;
      try (ZipInputStream zip = new ZipInputStream(raw)) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          String[] xy = entry.getName().split("_");
          int key = Integer.parseInt(xy[0]) << 16 | Integer.parseInt(xy[1]);
          loaded.put(key, BitSet.valueOf(Tables.readAll(zip)));
        }
      }
      regions = loaded;
    } catch (IOException | RuntimeException ignored) {
      // No map: callers keep using the loaded-scene route.
    }
  }

  /** One of a tile's flags, as in Shortest Path's SplitFlagMap.get. */
  private static boolean flag(int x, int y, int z, int flag) {
    Map<Integer, BitSet> map = regions;
    if (map == null || x < 0 || y < 0) return false;
    BitSet bits = map.get((x / REGION) << 16 | (y / REGION));
    if (bits == null) return false;
    int bit = (z * REGION * REGION + (y % REGION) * REGION + (x % REGION)) * FLAG_COUNT + flag;
    return bit < bits.size() && bits.get(bit);
  }

  // Movement rules from Shortest Path's CollisionMap: whether each edge of a tile is open.
  private static boolean openNorth(int x, int y, int z) {
    return flag(x, y, z, FLAG_NORTH);
  }

  private static boolean openSouth(int x, int y, int z) {
    return openNorth(x, y - 1, z);
  }

  private static boolean openEast(int x, int y, int z) {
    return flag(x, y, z, FLAG_EAST);
  }

  private static boolean openWest(int x, int y, int z) {
    return openEast(x - 1, y, z);
  }

  private static boolean blocked(int x, int y, int z) {
    return !openNorth(x, y, z) && !openSouth(x, y, z) && !openEast(x, y, z) && !openWest(x, y, z);
  }

  /** Whether one step (dx, dy) from (x, y) is allowed. */
  private static boolean canStep(int x, int y, int z, int dx, int dy) {
    if (blocked(x, y, z)) {
      // Standing somewhere blocked (e.g. on an object): step to any open neighbour.
      if (dx == 0 || dy == 0) return !blocked(x + dx, y + dy, z);
      return !blocked(x + dx, y + dy, z) && !blocked(x + dx, y, z) && !blocked(x, y + dy, z);
    }
    if (dx == 0 && dy == 1) return openNorth(x, y, z);
    if (dx == 0 && dy == -1) return openSouth(x, y, z);
    if (dx == 1 && dy == 0) return openEast(x, y, z);
    if (dx == -1 && dy == 0) return openWest(x, y, z);
    if (dx == 1 && dy == 1) return openNorth(x, y, z) && openEast(x, y + 1, z) && openEast(x, y, z) && openNorth(x + 1, y, z);
    if (dx == -1 && dy == 1) return openNorth(x, y, z) && openWest(x, y + 1, z) && openWest(x, y, z) && openNorth(x - 1, y, z);
    if (dx == 1 && dy == -1) return openSouth(x, y, z) && openEast(x, y - 1, z) && openEast(x, y, z) && openSouth(x + 1, y, z);
    return openSouth(x, y, z) && openWest(x, y - 1, z) && openWest(x, y, z) && openSouth(x - 1, y, z);
  }

  private static final int[][] STEPS = {
    {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
  };

  /**
   * Shortest walk from {@code from} to a tile next to (or on) {@code to}, same floor. Empty if the
   * map isn't loaded yet or there's no walking route (e.g. behind a door the map treats as shut).
   */
  static List<WorldPoint> find(WorldPoint from, WorldPoint to) {
    return to == null ? Collections.emptyList() : find(from, to, 1, 1);
  }

  /**
   * Shortest walk to the nearest tile beside a target {@code width} × {@code height} tiles big
   * whose south-west corner is {@code to} (trees and patches are often 2×2 or bigger), so it
   * heads for the closest side rather than one corner.
   */
  static List<WorldPoint> find(WorldPoint from, WorldPoint to, int targetWidth, int targetHeight) {
    if (!ready() || from == null || to == null || from.getPlane() != to.getPlane())
      return Collections.emptyList();
    int z = from.getPlane();
    int goalMinX = to.getX();
    int goalMinY = to.getY();
    int goalMaxX = to.getX() + Math.max(1, targetWidth) - 1;
    int goalMaxY = to.getY() + Math.max(1, targetHeight) - 1;
    // Search inside a box around both ends.
    int minX = Math.min(from.getX(), goalMinX) - MARGIN;
    int minY = Math.min(from.getY(), goalMinY) - MARGIN;
    int width = Math.max(from.getX(), goalMaxX) - minX + MARGIN + 1;
    int height = Math.max(from.getY(), goalMaxY) - minY + MARGIN + 1;
    int[] parent = new int[width * height];
    Arrays.fill(parent, -1);
    int start = (from.getX() - minX) + (from.getY() - minY) * width;
    parent[start] = start;
    ArrayDeque<Integer> queue = new ArrayDeque<>();
    queue.add(start);
    int goal = -1;
    int searched = 0;
    while (!queue.isEmpty() && searched++ < MAX_TILES) {
      int at = queue.poll();
      int x = at % width + minX;
      int y = at / width + minY;
      // Patches, gardeners and leprechauns are used from an adjacent tile.
      // Beside one of its sides, not a corner: you can't use a patch diagonally.
      int dx = Math.max(0, Math.max(goalMinX - x, x - goalMaxX));
      int dy = Math.max(0, Math.max(goalMinY - y, y - goalMaxY));
      if (dx + dy == 1) {
        goal = at;
        break;
      }
      // Doors, gates and shortcuts leaving this tile.
      List<Link> out = links.get(key(x, y, z));
      if (out != null)
        for (Link link : out) {
          int lx = link.x - minX;
          int ly = link.y - minY;
          if (lx < 0 || ly < 0 || lx >= width || ly >= height || !meets(link.skills)) continue;
          int next = lx + ly * width;
          if (parent[next] != -1) continue;
          parent[next] = at;
          queue.add(next);
        }
      for (int[] step : STEPS) {
        int nx = x + step[0];
        int ny = y + step[1];
        int lx = nx - minX;
        int ly = ny - minY;
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) continue;
        int next = lx + ly * width;
        if (parent[next] != -1 || !canStep(x, y, z, step[0], step[1])) continue;
        parent[next] = at;
        queue.add(next);
      }
    }
    if (goal < 0) return Collections.emptyList();
    List<WorldPoint> path = new ArrayList<>();
    for (int at = goal; at != start; at = parent[at])
      path.add(new WorldPoint(at % width + minX, at / width + minY, z));
    Collections.reverse(path);
    return path;
  }
}
