package com.yonwi.farming;

import java.util.*;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/**
 * Walking route over the loaded scene's collision map (breadth-first, 8 directions). If the
 * destination is beyond the loaded area, it heads for the walkable edge tile nearest to it, so the
 * line always points the right way and extends as you walk.
 */
final class TilePath {
  private static final int[][] STEPS = {
    {0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, -1}, {-1, 1}
  };

  private TilePath() {}

  static List<WorldPoint> find(WorldView view, WorldPoint from, WorldPoint to) {
    return find(view, from, to, 1, 1);
  }

  /**
   * Route to the nearest tile beside a {@code w} × {@code h} object whose south-west corner is
   * {@code to} (a portal nexus or fairy ring is bigger than one tile): beside any of its sides.
   */
  static List<WorldPoint> find(WorldView view, WorldPoint from, WorldPoint to, int w, int h) {
    if (view == null || from == null || to == null || from.getPlane() != to.getPlane())
      return Collections.emptyList();
    CollisionData[] maps = view.getCollisionMaps();
    if (maps == null || maps[from.getPlane()] == null) return Collections.emptyList();
    int[][] flags = maps[from.getPlane()].getFlags();
    int size = flags.length, baseX = view.getBaseX(), baseY = view.getBaseY();
    int sx = from.getX() - baseX, sy = from.getY() - baseY;
    if (!inside(sx, sy, size)) return Collections.emptyList();
    // Destination, clamped to the loaded scene when it's further away.
    int tx = Math.max(1, Math.min(size - 2, to.getX() - baseX));
    int ty = Math.max(1, Math.min(size - 2, to.getY() - baseY));
    int[][] parent = new int[size][size];
    for (int[] row : parent) Arrays.fill(row, -1);
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    queue.add(new int[] {sx, sy});
    parent[sx][sy] = sx * size + sy;
    int bestX = sx, bestY = sy, bestDistance = Integer.MAX_VALUE;
    while (!queue.isEmpty()) {
      int[] at = queue.poll();
      // Distance to the object's footprint: 1 means beside a side (not a corner: objects can't be
      // used diagonally).
      int dx = Math.max(0, Math.max(tx - at[0], at[0] - (tx + Math.max(1, w) - 1)));
      int dy = Math.max(0, Math.max(ty - at[1], at[1] - (ty + Math.max(1, h) - 1)));
      int d = dx + dy;
      if (d < bestDistance) {
        bestDistance = d;
        bestX = at[0];
        bestY = at[1];
        if (d <= 1) break;
      }
      for (int[] step : STEPS) {
        int nx = at[0] + step[0], ny = at[1] + step[1];
        if (!inside(nx, ny, size) || parent[nx][ny] != -1) continue;
        if (!canMove(flags, at[0], at[1], step[0], step[1])) continue;
        parent[nx][ny] = at[0] * size + at[1];
        queue.add(new int[] {nx, ny});
      }
    }
    LinkedList<WorldPoint> path = new LinkedList<>();
    int x = bestX, y = bestY;
    while (!(x == sx && y == sy)) {
      path.addFirst(new WorldPoint(x + baseX, y + baseY, from.getPlane()));
      int p = parent[x][y];
      x = p / size;
      y = p % size;
    }
    return path;
  }

  private static boolean inside(int x, int y, int size) {
    return x >= 0 && y >= 0 && x < size && y < size;
  }

  private static boolean blocked(int[][] f, int x, int y) {
    return (f[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0;
  }

  /** Whether one step from (x, y) by (dx, dy) is allowed, walls and diagonals included. */
  private static boolean canMove(int[][] f, int x, int y, int dx, int dy) {
    int nx = x + dx, ny = y + dy;
    if (blocked(f, nx, ny)) return false;
    if (dx == 0 || dy == 0) return straight(f, x, y, dx, dy);
    // A diagonal needs both of its straight halves to be open.
    return straight(f, x, y, dx, 0)
        && straight(f, x, y, 0, dy)
        && straight(f, x + dx, y, 0, dy)
        && straight(f, x, y + dy, dx, 0)
        && !blocked(f, x + dx, y)
        && !blocked(f, x, y + dy);
  }

  private static boolean straight(int[][] f, int x, int y, int dx, int dy) {
    int nx = x + dx, ny = y + dy;
    if (blocked(f, nx, ny)) return false;
    if (dy == 1)
      return (f[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
          && (f[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
    if (dy == -1)
      return (f[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
          && (f[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
    if (dx == 1)
      return (f[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
          && (f[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
    return (f[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
        && (f[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
  }
}
