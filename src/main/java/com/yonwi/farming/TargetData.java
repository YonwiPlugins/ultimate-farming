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
   * Where every tool leprechaun stands, from the OSRS wiki's Tool Leprechaun page; Troll
   * Stronghold's (not listed there) from the plugin's debug log.
   */
  static final List<WorldPoint> LEPRECHAUNS = new ArrayList<>();

  static {
    int[][] tiles = {
      {1234, 3761}, {1236, 3732}, {1252, 3756}, {1262, 3731}, {1741, 3552}, {1696, 3544},
      {1806, 3555}, {2596, 3860}, {2848, 3930}, {2439, 3414}, {2478, 3444}, {2346, 3164},
      {2573, 3108}, {2485, 3178}, {2619, 3221}, {2671, 3368}, {2663, 3521}, {2815, 3466},
      {2863, 3432}, {2812, 3332}, {2773, 3211}, {2797, 3104}, {2939, 3219}, {3064, 3255},
      {3053, 3305}, {3087, 3352}, {3196, 3228}, {3319, 3204}, {3222, 3316}, {3182, 3354},
      {3232, 3455}, {3454, 3468}, {3596, 3524}, {3792, 2840}, {3712, 3839}, {2941, 3432},
      {3008, 3370}, {1593, 3099}, {1683, 2971}, {1364, 2943}, {1369, 3318}, {1448, 3355},
      {1352, 3058}, {1354, 3030}, {2826, 3685}
    };
    for (int[] t : tiles) LEPRECHAUNS.add(new WorldPoint(t[0], t[1], 0));
  }

  /**
   * Patches made of several clickable tiles, as {minX, minY, maxX, maxY}: every one of them is
   * highlighted, not just one. The Farming Guild redwood has a tile at each corner.
   */
  static final Map<String, int[]> PATCH_AREAS = new HashMap<>();

  static {
    PATCH_AREAS.put("guildredwood", new int[] {1225, 3751, 1232, 3758});
  }

  /** The tool leprechaun nearest a spot, or null if none is within 30 tiles. */
  static WorldPoint nearestLeprechaun(WorldPoint at) {
    WorldPoint best = null;
    int closest = 30;
    for (WorldPoint l : LEPRECHAUNS)
      if (l.getPlane() == at.getPlane() && l.distanceTo(at) < closest) {
        best = l;
        closest = l.distanceTo(at);
      }
    return best;
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
    // Farming Guild redwood (8×8, 1225,3751 to 1232,3758; middle) and Celastrus (centre), from
    // Ned's in-game tile checks.
    PATCH_POINTS.put("guildredwood", new WorldPoint(1228, 3754, 0));
    PATCH_POINTS.put("guildcelastrus", new WorldPoint(1244, 3750, 0));
    // Anglers' Retreat hardwood patch, 3×3 from 2470,2702.
    PATCH_POINTS.put("anglers", new WorldPoint(2471, 2703, 0));
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
        // Anglers' Retreat's empty patch is just "Tree patch".
        return n.equals("tree patch")
            || contains(n, "hardwood", "teak", "mahogany", "camphor", "ironwood", "rosewood");
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
