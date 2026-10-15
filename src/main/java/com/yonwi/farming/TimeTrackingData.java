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

  // The patches' varbits, named as in RuneLite's VarbitID (FARMING_TRANSMIT_A…).
  private static final int TRANSMIT_A = 4771;
  private static final int TRANSMIT_B = 4772;
  private static final int TRANSMIT_C = 4773;
  private static final int TRANSMIT_D = 4774;
  private static final int TRANSMIT_E = 4775;
  private static final int TRANSMIT_G = 7905;
  private static final int TRANSMIT_I = 7907;
  private static final int TRANSMIT_K = 7909;
  private static final int TRANSMIT_L = 7910;

  static {
    patch("herbardougne", 10548, TRANSMIT_D, "HERB");
    patch("herbcatherby", 11062, TRANSMIT_D, "HERB");
    patch("herbfalador", 12083, TRANSMIT_D, "HERB");
    patch("herbphasmatys", 14391, TRANSMIT_D, "HERB");
    patch("herbhosidius", 6967, TRANSMIT_D, "HERB");
    patch("herbfortis", 6192, TRANSMIT_D, "HERB");
    patch("herbguild", 4922, TRANSMIT_E, "HERB");
    patch("herbtroll", 11321, TRANSMIT_A, "HERB");
    patch("herbweiss", 11325, TRANSMIT_A, "HERB");
    patch("herbharmony", 15148, TRANSMIT_B, "HERB");
    patch("falador", 11828, TRANSMIT_A, "TREE");
    patch("taverley", 11573, TRANSMIT_A, "TREE");
    patch("lumbridge", 12594, TRANSMIT_A, "TREE");
    patch("varrock", 12854, TRANSMIT_A, "TREE");
    patch("strongtree", 9781, TRANSMIT_A, "TREE");
    patch("guildtree", 4922, TRANSMIT_G, "TREE");
    patch("auburn", 5427, TRANSMIT_A, "TREE");
    patch("strongfruit", 9781, TRANSMIT_B, "FRUIT_TREE");
    patch("village", 9777, TRANSMIT_A, "FRUIT_TREE");
    patch("brimhaven", 11058, TRANSMIT_A, "FRUIT_TREE");
    patch("catherby", 11317, TRANSMIT_A, "FRUIT_TREE");
    patch("lletya", 9265, TRANSMIT_A, "FRUIT_TREE");
    patch("guildfruit", 4922, TRANSMIT_K, "FRUIT_TREE");
    patch("kastori", 5423, TRANSMIT_B, "FRUIT_TREE");
    patch("calquat", 11056, TRANSMIT_A, "CALQUAT");
    patch("kastoricalquat", 5423, TRANSMIT_A, "CALQUAT");
    patch("summercalquat", 12581, TRANSMIT_C, "CALQUAT");
    patch("fossil1", 14651, TRANSMIT_A, "HARDWOOD_TREE");
    patch("fossil2", 14651, TRANSMIT_B, "HARDWOOD_TREE");
    patch("fossil3", 14651, TRANSMIT_C, "HARDWOOD_TREE");
    patch("locus", 6702, TRANSMIT_A, "HARDWOOD_TREE");
    patch("anglers", 9770, TRANSMIT_A, "HARDWOOD_TREE");
    patch("guildredwood", 4922, TRANSMIT_I, "REDWOOD");
    patch("guildcelastrus", 4922, TRANSMIT_L, "CELASTRUS");
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
    Object[] patch = PATCHES.get(patchId);
    if (patch == null) return null;
    String stored = config.getRSProfileConfiguration("timetracking", patch[0] + "." + patch[1]);
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
    for (int[] range : GROWING.get((String) patch[2])) {
      if (value < range[0] || value > range[1]) continue;
      int stages = range[3];
      int stage = stageOf(value, range);
      return tickTime(config, range[2], stages - 1 - stage, seen) * 1000;
    }
    return seen * 1000;
  }

  /** The growth stage a varbit value means, by the range's rule (see {@link #GROWING}). */
  private static int stageOf(int value, int[] range) {
    int k = range[5];
    switch (range[4]) {
      case 0:
        return value - k;
      case 1:
        return k - value;
      case 2:
        return k;
      default:
        return range[3] - k;
    }
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