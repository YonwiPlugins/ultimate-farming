package com.yonwi.farming;

import java.util.*;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;

final class TargetData {
  static final Map<String, Set<Integer>> HERB_REGIONS = new HashMap<>();
  static {
HERB_REGIONS.put("herbtroll", new HashSet<>(Arrays.asList(11321)));
HERB_REGIONS.put("herbweiss", new HashSet<>(Arrays.asList(11325)));
HERB_REGIONS.put("herbharmony", new HashSet<>(Arrays.asList(15148)));
HERB_REGIONS.put("herbardougne", new HashSet<>(Arrays.asList(10548)));
HERB_REGIONS.put("herbhosidius", new HashSet<>(Arrays.asList(6967,6711)));
HERB_REGIONS.put("herbphasmatys", new HashSet<>(Arrays.asList(14391,14390)));
HERB_REGIONS.put("herbfalador", new HashSet<>(Arrays.asList(12083)));
HERB_REGIONS.put("herbfortis", new HashSet<>(Arrays.asList(6192,6447,6448,6449,6191,6193)));
HERB_REGIONS.put("herbcatherby", new HashSet<>(Arrays.asList(11062,11061,11318,11317)));
HERB_REGIONS.put("herbguild", new HashSet<>(Arrays.asList(4922,5177,5178,5179,4921,4923,4665,4666,4667)));
}

  /**
   * Where each herb patch object sits. A grown herb's name isn't "herb", so the patch is found by
   * position instead. Coordinates from Quest Helper's herb run (github.com/Zoinkwiz/quest-helper).
   */
  static final Map<String, WorldPoint> HERB_POINTS = new HashMap<>();

  static {
    HERB_POINTS.put("herbtroll", new WorldPoint(2826, 3694, 0));
    HERB_POINTS.put("herbweiss", new WorldPoint(2848, 3934, 0));
    HERB_POINTS.put("herbharmony", new WorldPoint(3789, 2837, 0));
    HERB_POINTS.put("herbardougne", new WorldPoint(2670, 3374, 0));
    HERB_POINTS.put("herbhosidius", new WorldPoint(1738, 3550, 0));
    HERB_POINTS.put("herbphasmatys", new WorldPoint(3605, 3529, 0));
    HERB_POINTS.put("herbfalador", new WorldPoint(3058, 3311, 0));
    HERB_POINTS.put("herbfortis", new WorldPoint(1582, 3094, 0));
    HERB_POINTS.put("herbcatherby", new WorldPoint(2813, 3463, 0));
    HERB_POINTS.put("herbguild", new WorldPoint(1238, 3726, 0));
  }

  /**
   * Every patch with a known position (herbs above plus trees, fruit trees and Calquat), for
   * arrival checks and tile pathing. Tree coordinates from Quest Helper's tree run.
   */
  static final Map<String, WorldPoint> PATCH_POINTS = new HashMap<>(HERB_POINTS);

  static {
    PATCH_POINTS.put("lumbridge", new WorldPoint(3193, 3231, 0));
    PATCH_POINTS.put("falador", new WorldPoint(3004, 3373, 0));
    PATCH_POINTS.put("taverley", new WorldPoint(2936, 3438, 0));
    PATCH_POINTS.put("varrock", new WorldPoint(3229, 3459, 0));
    PATCH_POINTS.put("strongtree", new WorldPoint(2436, 3415, 0));
    PATCH_POINTS.put("guildtree", new WorldPoint(1232, 3736, 0));
    PATCH_POINTS.put("auburn", new WorldPoint(1367, 3322, 0));
    PATCH_POINTS.put("strongfruit", new WorldPoint(2476, 3446, 0));
    PATCH_POINTS.put("village", new WorldPoint(2490, 3180, 0));
    PATCH_POINTS.put("brimhaven", new WorldPoint(2765, 3213, 0));
    PATCH_POINTS.put("catherby", new WorldPoint(2860, 3433, 0));
    PATCH_POINTS.put("lletya", new WorldPoint(2347, 3162, 0));
    PATCH_POINTS.put("guildfruit", new WorldPoint(1242, 3758, 0));
    PATCH_POINTS.put("kastori", new WorldPoint(1350, 3057, 0));
    // Locus Oasis hardwood patch: 3×3 from 1686,2971 to 1688,2973 (checked in-game).
    PATCH_POINTS.put("locus", new WorldPoint(1687, 2972, 0));
    PATCH_POINTS.put("calquat", new WorldPoint(2795, 3102, 0));
    PATCH_POINTS.put("kastoricalquat", new WorldPoint(1366, 3033, 0));
    // Summer Shore (Great Conch) Calquat patch.
    PATCH_POINTS.put("summercalquat", new WorldPoint(3129, 2406, 0));
  }

  static final Map<String, Integer> GARDENERS = new HashMap<>();

  static {
    GARDENERS.put("falador", NpcID.FARMING_GARDENER_TREE_2);
    GARDENERS.put("taverley", NpcID.FARMING_GARDENER_TREE_1);
    GARDENERS.put("lumbridge", NpcID.FARMING_GARDENER_TREE_4);
    GARDENERS.put("varrock", NpcID.FARMING_GARDENER_TREE_3_02);
    GARDENERS.put("strongtree", NpcID.FARMING_GARDENER_TREE_GNOME);
    GARDENERS.put("strongfruit", NpcID.FARMING_GARDENER_FRUIT_1);
    GARDENERS.put("village", NpcID.FARMING_GARDENER_FRUIT_2);
    GARDENERS.put("catherby", NpcID.FARMING_GARDENER_FRUIT_4);
    GARDENERS.put("brimhaven", NpcID.GARTH);
    GARDENERS.put("lletya", NpcID.FARMING_GARDENER_FRUIT_TREE_5);
    GARDENERS.put("guildtree", NpcID.FARMING_GARDENER_FARMGUILD_T2);
    GARDENERS.put("guildfruit", NpcID.FARMING_GARDENER_FARMGUILD_T3);
    GARDENERS.put("guildredwood", NpcID.FARMING_GARDENER_FARMGUILD_REDWOOD);
    GARDENERS.put("guildcelastrus", NpcID.FARMING_GARDENER_FARMGUILD_CELASTRUS);
    GARDENERS.put("auburn", NpcID.FARMING_GARDENER_TREE_7);
    GARDENERS.put("kastori", NpcID.FARMING_GARDENER_FRUIT_7);
    GARDENERS.put("kastoricalquat", NpcID.FARMING_GARDENER_CALQUAT_2);
    GARDENERS.put("calquat", NpcID.FARMING_GARDENER_CALQUAT);
    GARDENERS.put("fossil1", NpcID.FOSSIL_SQUIRREL_GARDENER1);
    GARDENERS.put("fossil2", NpcID.FOSSIL_SQUIRREL_GARDENER2);
    GARDENERS.put("fossil3", NpcID.FOSSIL_SQUIRREL_GARDENER3);
    GARDENERS.put("locus", 12936); // Marcellus at the patch (ID read in-game with Developer Tools)
    GARDENERS.put("anglers", NpcID.FARMING_GARDENER_HARDWOOD_TREE_5);
  }

  static boolean patchLike(ObjectComposition object, String kind) {
    String name = object.getName();
    if (name == null) return false;
    String n = name.toLowerCase(Locale.ROOT);
    if(kind.equals("herb")) return n.contains("herb");
    boolean farming = false;
    for (String action : object.getActions())
      if (action != null
          && Arrays.asList("check-health", "inspect", "guide", "clear", "rake")
              .contains(action.toLowerCase(Locale.ROOT))) farming = true;
    if (!farming || !n.contains("tree") && !n.contains("patch") && !n.contains("stump"))
      return false;
    switch (kind) {
      case "calquat":
        return n.contains("calquat");
      case "celastrus":
        return n.contains("celastrus");
      case "redwood":
        return n.contains("redwood");
      case "fruit":
        return contains(
            n,
            "fruit",
            "apple",
            "banana",
            "orange",
            "curry",
            "pineapple",
            "papaya",
            "palm",
            "dragonfruit");
      case "hardwood":
        return contains(n, "hardwood", "teak", "mahogany", "camphor", "ironwood", "rosewood");
      default:
        return !contains(
            n,
            "fruit",
            "calquat",
            "celastrus",
            "redwood",
            "hardwood",
            "teak",
            "mahogany",
            "camphor",
            "ironwood",
            "rosewood",
            "apple",
            "banana",
            "orange",
            "curry",
            "pineapple",
            "papaya",
            "palm",
            "dragonfruit",
            "spirit");
    }
  }

  private static boolean contains(String name, String... terms) {
    for (String term : terms) if (name.contains(term)) return true;
    return false;
  }
}
