package com.yonwi.farming;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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
   * Route to the nearest tile beside a {@code width} × {@code height} object whose south-west corner is
   * {@code to} (a portal nexus or fairy ring is bigger than one tile): beside any of its sides.
   */
  static List<WorldPoint> find(WorldView view, WorldPoint from, WorldPoint to, int width, int height) {
    if (view == null || from == null || to == null || from.getPlane() != to.getPlane())
      return Collections.emptyList();
    CollisionData[] maps = view.getCollisionMaps();
    if (maps == null || maps[from.getPlane()] == null) return Collections.emptyList();
    int[][] flags = maps[from.getPlane()].getFlags();
    int size = flags.length;
    int baseX = view.getBaseX();
    int baseY = view.getBaseY();
    int startX = from.getX() - baseX;
    int startY = from.getY() - baseY;
    if (!inside(startX, startY, size)) return Collections.emptyList();
    // Destination, clamped to the loaded scene when it's further away.
    int targetX = Math.max(1, Math.min(size - 2, to.getX() - baseX));
    int targetY = Math.max(1, Math.min(size - 2, to.getY() - baseY));
    int[][] parent = new int[size][size];
    for (int[] row : parent) Arrays.fill(row, -1);
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    queue.add(new int[] {startX, startY});
    parent[startX][startY] = startX * size + startY;
    int bestX = startX;
    int bestY = startY;
    int bestDistance = Integer.MAX_VALUE;
    while (!queue.isEmpty()) {
      int[] at = queue.poll();
      // Distance to the object's footprint: 1 means beside a side (not a corner: objects can't be
      // used diagonally).
      int dx = Math.max(0, Math.max(targetX - at[0], at[0] - (targetX + Math.max(1, width) - 1)));
      int dy = Math.max(0, Math.max(targetY - at[1], at[1] - (targetY + Math.max(1, height) - 1)));
      int distance = dx + dy;
      if (distance < bestDistance) {
        bestDistance = distance;
        bestX = at[0];
        bestY = at[1];
        if (distance <= 1) break;
      }
      for (int[] step : STEPS) {
        int nextX = at[0] + step[0];
        int nextY = at[1] + step[1];
        if (!inside(nextX, nextY, size) || parent[nextX][nextY] != -1) continue;
        if (!canMove(flags, at[0], at[1], step[0], step[1])) continue;
        parent[nextX][nextY] = at[0] * size + at[1];
        queue.add(new int[] {nextX, nextY});
      }
    }
    LinkedList<WorldPoint> path = new LinkedList<>();
    int x = bestX;
    int y = bestY;
    while (!(x == startX && y == startY)) {
      path.addFirst(new WorldPoint(x + baseX, y + baseY, from.getPlane()));
      int previous = parent[x][y];
      x = previous / size;
      y = previous % size;
    }
    return path;
  }

  private static boolean inside(int x, int y, int size) {
    return x >= 0 && y >= 0 && x < size && y < size;
  }

  private static boolean blocked(int[][] flags, int x, int y) {
    return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0;
  }

  /** Whether one step from (x, y) by (dx, dy) is allowed, walls and diagonals included. */
  private static boolean canMove(int[][] flags, int x, int y, int dx, int dy) {
    int nextX = x + dx;
    int nextY = y + dy;
    if (blocked(flags, nextX, nextY)) return false;
    if (dx == 0 || dy == 0) return straight(flags, x, y, dx, dy);
    // A diagonal needs both of its straight halves to be open.
    return straight(flags, x, y, dx, 0)
        && straight(flags, x, y, 0, dy)
        && straight(flags, x + dx, y, 0, dy)
        && straight(flags, x, y + dy, dx, 0)
        && !blocked(flags, x + dx, y)
        && !blocked(flags, x, y + dy);
  }

  private static boolean straight(int[][] flags, int x, int y, int dx, int dy) {
    int nextX = x + dx;
    int nextY = y + dy;
    if (blocked(flags, nextX, nextY)) return false;
    if (dy == 1)
      return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0
          && (flags[nextX][nextY] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
    if (dy == -1)
      return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0
          && (flags[nextX][nextY] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
    if (dx == 1)
      return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0
          && (flags[nextX][nextY] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
    return (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0
        && (flags[nextX][nextY] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
  }
}
