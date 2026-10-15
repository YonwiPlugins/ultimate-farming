/*
 * Growth stages below are from RuneLite's Time Tracking plugin
 * (timetracking/farming/PatchImplementation.java, Produce.java and FarmingWorld.java):
 *
 * Copyright (c) 2018 Abex, Copyright (c) 2018, NotFoxtrot <https://github.com/NotFoxtrot>
 * All rights reserved. BSD-2-Clause; full notice in THIRD_PARTY_NOTICES.md.
 */
package com.yonwi.farming;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * When a patch will be grown, from what RuneLite's Time Tracking plugin last saw there. It stores
 * each patch's varbit value and when it was seen ("timetracking", "<region>.<varbit>" in the
 * RuneScape profile's config), so patches planted before this plugin was watching still have a
 * time. The same sums as its FarmingTracker.predictPatch.
 */
final class TimeTrackingData {
  private TimeTrackingData() {}

  /** Patch → {region, varbit, type}, from FarmingWorld. */
  private static final Map<String, Object[]> PATCHES = new HashMap<>();

  private static void patch(String id, int region, int varbit, String type) {
    PATCHES.put(id, new Object[] {region, varbit, type});
  }

  private static final int A = 4771, B = 4772, C = 4773, D = 4774, E = 4775;
  private static final int G = 7905, I = 7907, K = 7909, L = 7910;

  static {
    patch("herbardougne", 10548, D, "HERB");
    patch("herbcatherby", 11062, D, "HERB");
    patch("herbfalador", 12083, D, "HERB");
    patch("herbphasmatys", 14391, D, "HERB");
    patch("herbhosidius", 6967, D, "HERB");
    patch("herbfortis", 6192, D, "HERB");
    patch("herbguild", 4922, E, "HERB");
    patch("herbtroll", 11321, A, "HERB");
    patch("herbweiss", 11325, A, "HERB");
    patch("herbharmony", 15148, B, "HERB");
    patch("falador", 11828, A, "TREE");
    patch("taverley", 11573, A, "TREE");
    patch("lumbridge", 12594, A, "TREE");
    patch("varrock", 12854, A, "TREE");
    patch("strongtree", 9781, A, "TREE");
    patch("guildtree", 4922, G, "TREE");
    patch("auburn", 5427, A, "TREE");
    patch("strongfruit", 9781, B, "FRUIT_TREE");
    patch("village", 9777, A, "FRUIT_TREE");
    patch("brimhaven", 11058, A, "FRUIT_TREE");
    patch("catherby", 11317, A, "FRUIT_TREE");
    patch("lletya", 9265, A, "FRUIT_TREE");
    patch("guildfruit", 4922, K, "FRUIT_TREE");
    patch("kastori", 5423, B, "FRUIT_TREE");
    patch("calquat", 11056, A, "CALQUAT");
    patch("kastoricalquat", 5423, A, "CALQUAT");
    patch("summercalquat", 12581, C, "CALQUAT");
    patch("fossil1", 14651, A, "HARDWOOD_TREE");
    patch("fossil2", 14651, B, "HARDWOOD_TREE");
    patch("fossil3", 14651, C, "HARDWOOD_TREE");
    patch("locus", 6702, A, "HARDWOOD_TREE");
    patch("anglers", 9770, A, "HARDWOOD_TREE");
    patch("guildredwood", 4922, I, "REDWOOD");
    patch("guildcelastrus", 4922, L, "CELASTRUS");
  }

  /**
   * Per patch type, the varbit values of a crop still growing: {min, max, minutes per growth tick,
   * stages, how the stage is worked out (0: value - k, 1: k - value, 2: k, 3: stages - k), k}.
   * Anything else (grown, dead, diseased, weeds, empty) needs you now.
   */
  private static final Map<String, int[][]> GROWING = new HashMap<>();

  static {
    GROWING.put("HERB", new int[][] {
      {4, 7, 20, 5, 0, 4}, {11, 14, 20, 5, 0, 11}, {18, 21, 20, 5, 0, 18}, {25, 28, 20, 5, 0, 25},
      {32, 35, 20, 5, 0, 32}, {39, 42, 20, 5, 0, 39}, {46, 49, 20, 5, 0, 46}, {53, 56, 20, 5, 0, 53},
      {60, 63, 20, 5, 0, 60}, {68, 71, 20, 5, 0, 68}, {75, 78, 20, 5, 0, 75}, {82, 85, 20, 5, 0, 82},
      {89, 92, 20, 5, 0, 89}, {96, 99, 20, 5, 0, 96}, {103, 106, 20, 5, 0, 103}, {192, 195, 20, 5, 0, 192},
    });
    GROWING.put("FRUIT_TREE", new int[][] {
      {8, 13, 160, 7, 0, 8}, {34, 34, 160, 7, 3, 1}, {35, 40, 160, 7, 0, 35}, {61, 61, 160, 7, 3, 1},
      {72, 77, 160, 7, 0, 72}, {98, 98, 160, 7, 3, 1}, {99, 104, 160, 7, 0, 99}, {125, 125, 160, 7, 3, 1},
      {136, 141, 160, 7, 0, 136}, {162, 162, 160, 7, 3, 1}, {163, 168, 160, 7, 0, 163}, {189, 189, 160, 7, 3, 1},
      {200, 205, 160, 7, 0, 200}, {226, 226, 160, 7, 3, 1}, {227, 232, 160, 7, 0, 227}, {253, 253, 160, 7, 3, 1},
    });
    GROWING.put("TREE", new int[][] {
      {8, 11, 40, 5, 0, 8}, {12, 12, 40, 5, 3, 1}, {15, 20, 40, 7, 0, 15}, {21, 21, 40, 7, 3, 1},
      {24, 31, 40, 9, 0, 24}, {32, 32, 40, 9, 3, 1}, {35, 44, 40, 11, 0, 35}, {45, 45, 40, 11, 3, 1},
      {48, 59, 40, 13, 0, 48}, {60, 60, 40, 13, 3, 1},
    });
    GROWING.put("HARDWOOD_TREE", new int[][] {
      {8, 14, 640, 8, 0, 8}, {15, 15, 640, 8, 3, 1}, {30, 37, 640, 9, 0, 30}, {38, 38, 640, 9, 3, 1},
      {55, 62, 640, 9, 0, 55}, {63, 63, 640, 9, 3, 1}, {80, 87, 640, 9, 0, 80}, {88, 88, 640, 9, 3, 1},
      {105, 113, 640, 10, 0, 105}, {114, 114, 640, 10, 3, 1},
    });
    GROWING.put("REDWOOD", new int[][] {
      {8, 17, 640, 11, 0, 8}, {37, 37, 640, 11, 3, 1},
    });
    GROWING.put("CALQUAT", new int[][] {
      {4, 11, 160, 9, 0, 4}, {34, 34, 160, 9, 3, 1},
    });
    GROWING.put("CELASTRUS", new int[][] {
      {8, 12, 160, 6, 0, 8}, {13, 13, 160, 6, 3, 1},
    });
  }

  /**
   * When this patch will be grown (epoch ms), or null if Time Tracking has nothing for it. A value
   * that isn't a growing crop (ready, dead, empty) gives the time it was seen: it needs you now.
   */
  static Long readyAt(ConfigManager config, String patchId) {
    Object[] p = PATCHES.get(patchId);
    if (p == null) return null;
    String stored = config.getRSProfileConfiguration("timetracking", p[0] + "." + p[1]);
    if (stored == null) return null;
    String[] parts = stored.split(":");
    if (parts.length != 2) return null;
    int value;
    long seen;
    try {
      value = Integer.parseInt(parts[0]);
      seen = Long.parseLong(parts[1]);
    } catch (NumberFormatException e) {
      return null;
    }
    if (seen <= 0) return null;
    for (int[] r : GROWING.get((String) p[2])) {
      if (value < r[0] || value > r[1]) continue;
      int stages = r[3];
      int stage = r[4] == 0 ? value - r[5] : r[4] == 1 ? r[5] - value : r[4] == 2 ? r[5] : stages - r[5];
      return tickTime(config, r[2], stages - 1 - stage, seen) * 1000;
    }
    return seen * 1000;
  }

  /**
   * The growth tick {@code ticks} ticks after the one {@code at} falls in, in epoch seconds, with
   * the account's farming tick offset when Time Tracking has learnt it (FarmingTracker.getTickTime).
   */
  private static long tickTime(ConfigManager config, int tickRate, int ticks, long at) {
    Integer precision = config.getRSProfileConfiguration("timetracking", "farmTickOffsetPrecision", Integer.class);
    Integer offsetMins = config.getRSProfileConfiguration("timetracking", "farmTickOffset", Integer.class);
    long offset = 0;
    if (precision != null && offsetMins != null && (precision >= tickRate || precision >= 40))
      offset = (offsetMins % tickRate) * 60L;
    long now = at + offset;
    long current = now - now % (tickRate * 60L);
    return current + (long) ticks * tickRate * 60 - offset;
  }
}