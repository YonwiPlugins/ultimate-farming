package com.yonwi.farming;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** How much inventory a run's kit and harvest take, to keep a run within 28 slots. */
final class InventorySpace {
  static final int INVENTORY_SLOTS = 28;
  /** Runes kept in the rune pouch (it holds 3 types). */
  static final List<String> POUCH_RUNES =
      Arrays.asList("Law rune", "Lava rune", "Mist rune", "Soul rune", "Nature rune", "Blood rune");
  /** Fruit picked from one fruit tree or Calquat. */
  private static final int FRUIT_PER_TREE = 6;

  private InventorySpace() {}

  /**
   * How many squares an item takes in the Kit tab's Bring section: one per sapling or
   * ultracompost, none for runes kept in the rune pouch, one for anything else.
   */
  static int squares(String name, int quantity, Settings settings) {
    if (settings.has("pouch") && POUCH_RUNES.contains(name)) return 0;
    return name.endsWith("sapling") || name.equals("Ultracompost") ? quantity : 1;
  }

  /** Squares in the Kit tab's Bring section: the inventory space the run needs. */
  private static int bringSquares(FarmingPlanner.Kit kit, Settings settings) {
    int total = 0;
    for (Map.Entry<String, Integer> item : kit.bring.entrySet())
      total += squares(item.getKey(), item.getValue(), settings);
    return total;
  }

  /**
   * Free slots a harvest needs: the biggest single patch's produce (6 fruit or Calquat, the
   * expected Celastrus bark or herbs), as it's noted at the leprechaun before the next patch.
   * Nothing when nothing is harvested.
   */
  private static int harvestSquares(List<FarmingPlanner.Step> plan, Settings settings) {
    boolean cape = FarmingPlanner.farmingCapeWorn(plan, settings);
    int most = 0;
    for (FarmingPlanner.Step step : plan) {
      String kind = step.patch.kind;
      if (!settings.harvests(kind)) continue;
      int produce =
          kind.equals("herb")
              ? (int) Math.ceil(FarmingPlanner.expectedHerbs(step.patch, settings, cape))
              : kind.equals("celastrus")
                  ? (int) Math.round(FarmingPlanner.bark(settings))
                  : settings.crop(kind).fruit.isEmpty() ? 0 : FRUIT_PER_TREE;
      most = Math.max(most, produce);
    }
    return most;
  }

  /** Inventory space the run needs: the Bring section plus room for a patch's harvest. */
  static int needed(List<FarmingPlanner.Step> plan, FarmingPlanner.Kit kit, Settings settings) {
    return bringSquares(kit, settings) + harvestSquares(plan, settings);
  }

  /** Whether the run still fits in 28 inventory slots with this run type added. */
  static boolean fitsWith(Settings settings, String kind, Gson gson) {
    Settings copy = gson.fromJson(gson.toJson(settings), Settings.class);
    copy.kinds.add(kind);
    List<FarmingPlanner.Step> plan = FarmingPlanner.plan(copy);
    return needed(plan, FarmingPlanner.kit(plan, copy), copy) <= INVENTORY_SLOTS;
  }
}
