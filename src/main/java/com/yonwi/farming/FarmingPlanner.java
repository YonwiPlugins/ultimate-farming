package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plans a run: which patches, in what order and how to get to each, what to wear and bring, and
 * what it's expected to make.
 */
final class FarmingPlanner {
  private static final Pattern FAIRY_CODE = Pattern.compile("Fairy ring ([A-Z]{3})");
  private static final Pattern CHARTER_TO = Pattern.compile("charter to ([a-z' ]+?)(;|,| then|$)");

  /** One way to travel: its text, rough cost in minutes, and items carried and worn. */
  static final class Choice {
    final String text;
    final double cost;
    final List<String> items;
    final List<String> gear;

    Choice(String text, double cost, List<String> items, List<String> gear) {
      this.text = text;
      this.cost = cost;
      this.items = items;
      this.gear = gear;
    }
  }

  /** One patch of the run and how to get there. */
  static final class Step {
    final Patch patch;
    final Choice travel;
    boolean noteBefore;
    boolean noteAfter = true;

    Step(Patch patch, Choice travel) {
      this.patch = patch;
      this.travel = travel;
    }
  }

  /** What to wear, and what to bring with how many of each. */
  static final class Kit {
    final List<String> wear = new ArrayList<>();
    final Map<String, Integer> bring = new LinkedHashMap<>();

    void add(String name, int quantity) {
      bring.merge(name, quantity, Integer::sum);
    }
  }

  private FarmingPlanner() {}

  /** A spirit tree in the house: every spirit tree trip starts there. */
  static boolean houseTree(Settings settings) {
    return settings.poh() && settings.has("spirit");
  }

  /** The three-letter fairy ring code in a travel line, or null. */
  static String fairyCode(String travel) {
    Matcher code = FAIRY_CODE.matcher(travel);
    return code.find() ? code.group(1) : null;
  }

  /** The port in a travel line's "charter to …", lower case, or null. */
  static String charterPort(String travel) {
    Matcher port = CHARTER_TO.matcher(travel.toLowerCase(Locale.ROOT));
    return port.find() ? port.group(1).trim() : null;
  }

  /** Ways to get to a patch, the quickest first. */
  static List<Choice> choices(Patch patch, Settings settings) {
    return TravelChoices.forPatch(patch, settings);
  }

  /** Travel between two patches: a walk or boat between them if quicker, else the second's own quickest. */
  private static Choice edge(List<Patch> patches, List<Choice> direct, int from, int to, Settings settings) {
    Choice between = TravelChoices.between(patches.get(from), patches.get(to), settings);
    return between != null && between.cost < direct.get(to).cost ? between : direct.get(to);
  }

  static List<Step> plan(Settings settings) {
    return reorder(suggested(settings), settings);
  }

  /** Applies the player's chosen order, recalculating travel between neighbouring patches. */
  static List<Step> reorder(List<Step> route, Settings settings) {
    if (settings.order == null || settings.order.isEmpty()) return route;
    List<Step> sorted = new ArrayList<>(route);
    sorted.sort(
        Comparator.comparingInt(
            step -> settings.order.contains(step.patch.id) ? settings.order.indexOf(step.patch.id) : Integer.MAX_VALUE));
    List<Step> result = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      Patch patch = sorted.get(i).patch;
      Choice direct = choices(patch, settings).get(0);
      Choice between = i == 0 ? null : TravelChoices.between(sorted.get(i - 1).patch, patch, settings);
      result.add(new Step(patch, between != null && between.cost < direct.cost ? between : direct));
    }
    setNoting(result);
    return result;
  }

  /**
   * The plugin's own order: patches linked by a walk or boat are grouped, and each group is visited
   * in its quickest order. Herb runs keep the patch list's order.
   */
  static List<Step> suggested(Settings settings) {
    List<Patch> patches = new ArrayList<>();
    List<Choice> direct = new ArrayList<>();
    for (Patch patch : FarmingData.PATCHES)
      if (TravelChoices.reachable(patch, settings)) {
        List<Choice> ways = choices(patch, settings);
        if (!ways.isEmpty()) {
          patches.add(patch);
          direct.add(ways.get(0));
        }
      }
    List<Step> result = new ArrayList<>();
    if (settings.kinds.size() == 1 && settings.kinds.contains("herb")) {
      for (int i = 0; i < patches.size(); i++) result.add(new Step(patches.get(i), direct.get(i)));
      setNoting(result);
      return result;
    }
    Set<Integer> unseen = new LinkedHashSet<>();
    for (int i = 0; i < patches.size(); i++) unseen.add(i);
    while (!unseen.isEmpty()) {
      List<Integer> group = linkedGroup(unseen, patches, direct, settings);
      List<Integer> order = quickestOrder(group, patches, direct, settings);
      for (int i = 0; i < order.size(); i++) {
        int at = order.get(i);
        result.add(new Step(patches.get(at), i == 0 ? direct.get(at) : edge(patches, direct, order.get(i - 1), at, settings)));
      }
    }
    setNoting(result);
    return result;
  }

  /** The first unseen patch and every patch linked to it by a quicker walk or boat (removed from unseen). */
  private static List<Integer> linkedGroup(
      Set<Integer> unseen, List<Patch> patches, List<Choice> direct, Settings settings) {
    List<Integer> group = new ArrayList<>();
    int first = unseen.iterator().next();
    group.add(first);
    unseen.remove(first);
    for (int i = 0; i < group.size(); i++)
      for (int j : new ArrayList<>(unseen))
        if (edge(patches, direct, group.get(i), j, settings) != direct.get(j)
            || edge(patches, direct, j, group.get(i), settings) != direct.get(group.get(i))) {
          group.add(j);
          unseen.remove(j);
        }
    return group;
  }

  /** The group's patches in their quickest order (every order tried, as a bitmask shortest path). */
  private static List<Integer> quickestOrder(
      List<Integer> group, List<Patch> patches, List<Choice> direct, Settings settings) {
    int size = group.size();
    int full = (1 << size) - 1;
    double[][] cost = new double[full + 1][size];
    int[][] parent = new int[full + 1][size];
    for (int visited = 0; visited <= full; visited++) {
      Arrays.fill(cost[visited], Double.POSITIVE_INFINITY);
      Arrays.fill(parent[visited], -1);
    }
    for (int j = 0; j < size; j++) cost[1 << j][j] = direct.get(group.get(j)).cost;
    for (int visited = 1; visited <= full; visited++)
      for (int i = 0; i < size; i++)
        if ((visited & (1 << i)) != 0)
          for (int j = 0; j < size; j++)
            if ((visited & (1 << j)) == 0) {
              int next = visited | (1 << j);
              double total = cost[visited][i] + edge(patches, direct, group.get(i), group.get(j), settings).cost;
              if (total < cost[next][j]) {
                cost[next][j] = total;
                parent[next][j] = i;
              }
            }
    int end = 0;
    for (int i = 1; i < size; i++) if (cost[full][i] < cost[full][end]) end = i;
    int visited = full;
    List<Integer> order = new ArrayList<>();
    while (end >= 0) {
      order.add(group.get(end));
      int previous = parent[visited][end];
      visited ^= 1 << end;
      end = previous;
    }
    Collections.reverse(order);
    return order;
  }

  /** Herb runs have no noting steps: the player notes herbs whenever suits them. */
  static void setNoting(List<Step> route) {
    for (Step step : route) {
      step.noteBefore = false;
      step.noteAfter = false;
    }
  }

  /** The kit name for a travel item, e.g. "Boat fare" is coins and tablets are by teleport name. */
  static String normalize(String name) {
    if (name.equals("Boat fare") || name.equals("Coins for tree removal")) return "Coins";
    if (name.endsWith(" tablets")) return name.substring(0, name.length() - 8);
    if (name.equals("Civitas illa Fortis teleport tablet")) return "Civitas illa Fortis teleport";
    if (name.equals("Dramen/lunar staff if required")) return "Dramen or lunar staff";
    return name;
  }

  /** The equipment slot an item is worn in, from its name, or "" if it isn't worn. */
  static String slot(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.contains("hat") || lower.contains("hood")) return "head";
    if (lower.contains("cape")) return "cape";
    if (lower.contains("necklace") || lower.contains("pendant") || lower.contains("talisman")) return "neck";
    if (lower.contains("staff") || lower.contains("secateurs")) return "weapon";
    if (lower.contains("jacket") || lower.contains(" top")) return "body";
    if (lower.contains("trousers") || lower.contains(" legs")) return "legs";
    if (lower.contains("gloves")) return "hands";
    if (lower.contains("boots")) return "feet";
    if (lower.contains("ring")) return "ring";
    return "";
  }

  private static boolean hasKind(List<Step> route, String kind) {
    return route.stream().anyMatch(step -> step.patch.kind.equals(kind));
  }

  /** What to wear and bring for a route. */
  static Kit kit(List<Step> route, Settings settings) {
    Kit kit = new Kit();
    if (route.isEmpty()) return kit;
    addGear(kit, route, settings);
    kit.add("Spade", 1);
    kit.add("Rake", 1);
    int compost = 0;
    int removal = 0;
    for (Step step : route) {
      Crop crop = settings.crop(step.patch.kind);
      kit.add(crop.sapling, 1);
      if (settings.pays(step.patch)) kit.add(crop.payment, crop.paymentQty);
      if (settings.composts(step.patch)) compost++;
      removal += removalCost(step.patch);
      addTravelItems(kit, step);
    }
    boolean herbs = hasKind(route, "herb");
    if (herbs) kit.add("Seed dibber", 1);
    // An owned herb sack comes along: open, it takes the herbs before the inventory does.
    if (settings.has("herbSack") && herbs) kit.bring.put("Herb sack", 1);
    if (removal > 0) kit.add("Coins", removal);
    if (compost > 0) kit.add(settings.bucket ? "Bottomless compost bucket" : "Ultracompost", settings.bucket ? 1 : compost);
    if (hasKind(route, "celastrus")) kit.add("Axe", 1);
    addRunes(kit, route, settings);
    if (settings.stamina && route.size() > 5) kit.add("Stamina potion(4)", 1);
    return kit;
  }

  /** The outfit and worn travel items, one per slot; anything else worn-type goes in the bag. */
  private static void addGear(Kit kit, List<Step> route, Settings settings) {
    Set<String> occupied = new HashSet<>();
    Set<String> gear = new LinkedHashSet<>();
    // The farming cape is worn first (+5% herbs, and it still teleports); a Construction cape
    // then rides in the inventory, where its Tele to POH still works.
    boolean farmingCape = settings.level >= 99 && settings.has("farmingCape");
    if (farmingCape) gear.add("Farming cape");
    for (Step step : route) gear.addAll(step.travel.gear);
    if (settings.secateurs && hasKind(route, "herb")) gear.add("Magic secateurs");
    gear.addAll(
        settings.farmer
            ? Arrays.asList("Farmer's strawhat", "Farmer's jacket", "Farmer's boro trousers", "Farmer's boots")
            : Arrays.asList(
                "Graceful hood", "Graceful cape", "Graceful top", "Graceful legs", "Graceful gloves", "Graceful boots"));
    for (String raw : gear) {
      String name = normalize(raw);
      String slot = slot(name);
      if (farmingCape && name.equals("Graceful cape")) continue; // the cape slot is taken
      if (!slot.isEmpty() && occupied.add(slot)) kit.wear.add(name);
      else kit.add(name, 1);
    }
  }

  private static void addTravelItems(Kit kit, Step step) {
    for (String raw : step.travel.items) {
      String name = normalize(raw);
      if (kit.wear.contains(name)) continue; // already worn, e.g. the farming cape
      if (name.equals("Coins")) kit.bring.put(name, Math.max(10000, kit.bring.getOrDefault(name, 0)));
      else if (name.endsWith("teleport")) kit.add(name, 1);
      else kit.bring.put(name, 1);
    }
  }

  /**
   * The pouch carries the standard teleport runes when the route casts them, otherwise the
   * Resurrect Crops runes (earth runes ride loose: the pouch holds 3 types).
   */
  private static void addRunes(Kit kit, List<Step> route, Settings settings) {
    if (settings.has("pouch") && Settings.usesSpells(route)) {
      kit.bring.put("Rune pouch", 1);
      kit.bring.put("Law rune", 1);
      kit.bring.put("Lava rune", 1);
      kit.bring.put("Mist rune", 1);
    } else if (settings.resurrects(route)) {
      if (settings.has("pouch")) kit.bring.put("Rune pouch", 1);
      // Enough for 2 casts: covers 2 dead patches (about 1 run in 19 has 2; 3+ is 1 in 180).
      for (String[] rune : RESURRECT_RUNES) kit.bring.put(rune[0], 2 * Integer.parseInt(rune[1]));
    }
  }

  /** Coins the gardener charges to remove the old tree. */
  static int removalCost(Patch patch) {
    if (patch.kind.equals("redwood")) return 2000;
    return Arrays.asList("calquat", "celastrus", "herb").contains(patch.kind) ? 0 : 200;
  }

  // ---- Herb yield, from the OSRS wiki's herb calculator (Module:Herb_Farming_calculator) ----

  /** Each herb's chance to save a harvest life at level 1, out of 256; it's 80 at level 99. */
  private static final Map<String, Integer> HERB_LOW_CTS = new HashMap<>();

  static {
    String[] names = {
      "Guam", "Marrentill", "Tarromin", "Harralander", "Ranarr", "Toadflax", "Irit", "Avantoe",
      "Kwuarm", "Snapdragon", "Cadantine", "Lantadyme", "Dwarf weed", "Torstol"
    };
    int[] low = {25, 28, 31, 36, 39, 43, 46, 50, 54, 57, 60, 64, 67, 71};
    for (int i = 0; i < names.length; i++) HERB_LOW_CTS.put(names[i], low[i]);
  }

  private static final int HERB_HIGH_CTS = 80;

  /** Harvest lives with ultracompost: 3 base + 3 from the compost. */
  private static final int ULTRACOMPOST_LIVES = 6;

  /** Chance to die per growth stage with ultracompost: (floor(27 / 10) + 1) / 128. */
  private static final double ULTRACOMPOST_DEATH_PER_STAGE = (27 / 10 + 1) / 128.0;

  /** Runes for one Resurrect Crops cast. */
  static final String[][] RESURRECT_RUNES = {
    {"Soul rune", "8"}, {"Nature rune", "12"}, {"Blood rune", "8"}, {"Earth rune", "25"}
  };

  /** Herb patches that can't get diseased, always or with the given unlock. */
  private static boolean diseaseFree(Patch patch, Settings settings) {
    switch (patch.id) {
      case "herbtroll": // My Arm's Big Adventure
      case "herbweiss": // Fire of Nourishment
      case "herbharmony": // Morytania elite diary
        return true;
      case "herbhosidius":
        return settings.has("kourendEasy");
      case "herbfortis":
        return settings.has("fortisGlory");
      default:
        return false;
    }
  }

  /** Chance the herbs survive to harvest: (1 - P)^3, or 1 on a disease-free patch. */
  static double herbSurvival(Patch patch, Settings settings) {
    return herbSurvival(patch, settings, false);
  }

  /**
   * With Resurrect Crops, the wiki's death chance is
   * (1-(1-P)^4)(1-R) + 6P²(1-P)² + 4P³(1-P) + P⁴, R being the spell's success chance.
   */
  static double herbSurvival(Patch patch, Settings settings, boolean resurrect) {
    if (diseaseFree(patch, settings)) return 1;
    double p = ULTRACOMPOST_DEATH_PER_STAGE;
    if (!resurrect) return Math.pow(1 - p, 3);
    double r = resurrectChance(settings.magic);
    double death =
        (1 - Math.pow(1 - p, 4)) * (1 - r) + 6 * p * p * Math.pow(1 - p, 2) + 4 * Math.pow(p, 3) * (1 - p) + Math.pow(p, 4);
    return 1 - death;
  }

  /** Resurrect Crops success: 50% at 78 Magic to 75% at 99 (wiki: interpolate -111 to 191). */
  static double resurrectChance(int magic) {
    int level = Math.max(1, Math.min(99, magic));
    double value = (int) (-111 * (99 - level) / 98.0) + (int) (191 * (level - 1) / 98.0) + 1;
    return Math.min(Math.max(value / 256, 0), 1);
  }

  /** Diary yield bonus as a flat addition to the chance to save (5% = 10, 10% = 17, 15% = 25). */
  private static int diaryBonus(Patch patch, Settings settings) {
    if ((patch.id.equals("herbhosidius") || patch.id.equals("herbguild")) && settings.has("kourendHard")) return 10;
    if (patch.id.equals("herbcatherby")) {
      if (settings.has("kandarinElite")) return 25;
      if (settings.has("kandarinHard")) return 17;
      if (settings.has("kandarinMedium")) return 10;
    }
    return 0;
  }

  /** Whether the farming cape (+5% yield) is worn: 99 Farming, owned, and the cape slot is free. */
  static boolean farmingCapeWorn(List<Step> route, Settings settings) {
    if (settings.level < 99 || !settings.has("farmingCape")) return false;
    for (String worn : kit(route, settings).wear)
      if (slot(worn).equals("cape") && !worn.equals("Farming cape")) return false;
    return true;
  }

  /**
   * Expected herbs from one patch, dead patches included, exactly as the wiki calculator works it
   * out: chance to save interpolated between level 1 and 99 values (after item and diary bonuses),
   * harvest lives / (1 - chance to save), times the chance the patch survives.
   */
  static double expectedHerbs(Patch patch, Settings settings, boolean capeWorn) {
    return expectedHerbs(patch, settings, capeWorn, false);
  }

  static double expectedHerbs(Patch patch, Settings settings, boolean capeWorn, boolean resurrect) {
    Crop crop = settings.crop(patch.kind);
    Integer low = crop == null ? null : HERB_LOW_CTS.get(crop.name);
    if (low == null) return 0;
    double itemBonus = (settings.secateurs ? .1 : 0) + (capeWorn ? .05 : 0);
    int diary = diaryBonus(patch, settings);
    // An Attas plant at the Farming Guild multiplies the chance to save by 1.05.
    double attas = settings.has("attas") ? .05 : 0;
    int lowFinal = (int) Math.floor(((int) Math.floor(low * (1 + itemBonus)) + diary) * (1 + attas));
    int highFinal = (int) Math.floor(((int) Math.floor(HERB_HIGH_CTS * (1 + itemBonus)) + diary) * (1 + attas));
    int level = Math.max(1, Math.min(99, settings.level));
    // Module:Skilling success chart: truncated interpolation, + 1, out of 256.
    double value = (int) (lowFinal * (99 - level) / 98.0) + (int) (highFinal * (level - 1) / 98.0) + 1;
    double chanceToSave = Math.min(Math.max(value / 256, 0), 1);
    double yield = chanceToSave >= 1 ? 0 : ULTRACOMPOST_LIVES / (1 - chanceToSave);
    return yield * herbSurvival(patch, settings, resurrect);
  }

  /** Expected herbs from one patch that survives to harvest. */
  static double expectedHerbsIfAlive(Patch patch, Settings settings, boolean capeWorn) {
    double survival = herbSurvival(patch, settings, false);
    return survival <= 0 ? 0 : expectedHerbs(patch, settings, capeWorn, false) / survival;
  }

  /** Farming XP the run is expected to give. */
  static double xp(List<Step> route, Settings settings) {
    double total = 0;
    boolean cape = farmingCapeWorn(route, settings);
    boolean resurrect = settings.resurrects(route);
    for (Step step : route) {
      Crop crop = settings.crop(step.patch.kind);
      if (step.patch.kind.equals("herb")) {
        // As the wiki: herbs picked plus planting when the patch survives; no compost XP.
        total +=
            expectedHerbs(step.patch, settings, cape, resurrect) * crop.harvest
                + crop.plant * herbSurvival(step.patch, settings, resurrect);
        continue;
      }
      double harvest =
          !settings.harvests(step.patch.kind)
              ? 0
              : step.patch.kind.equals("celastrus") ? bark(settings) * 23.5 : crop.harvest;
      total += crop.plant + crop.check + harvest;
    }
    return total * (settings.farmer ? 1.025 : 1);
  }

  /**
   * GP the run is expected to make at these prices: produce minus saplings, payments, compost,
   * removal and (with Resurrect Crops) runes. Null if a price is missing.
   */
  static Long net(List<Step> route, Settings settings, ToLongFunction<String> price) {
    long net = 0;
    boolean cape = farmingCapeWorn(route, settings);
    boolean resurrect = settings.resurrects(route);
    long castCost = 0;
    if (resurrect)
      for (String[] rune : RESURRECT_RUNES) {
        long each = price.applyAsLong(rune[0]);
        if (each <= 0) return null;
        castCost += each * Integer.parseInt(rune[1]);
      }
    for (Step step : route) {
      // Rune cost × the patch's death chance with resurrect, as the wiki calculator charges it.
      if (resurrect && step.patch.kind.equals("herb"))
        net -= Math.round(castCost * (1 - herbSurvival(step.patch, settings, true)));
      Long patchNet = patchNet(step, settings, price, cape, resurrect);
      if (patchNet == null) return null;
      net += patchNet;
    }
    return net;
  }

  /** One patch's produce minus its costs, or null if a price is missing. */
  private static Long patchNet(
      Step step, Settings settings, ToLongFunction<String> price, boolean cape, boolean resurrect) {
    Crop crop = settings.crop(step.patch.kind);
    long net = 0;
    long sapling = price.applyAsLong(crop.sapling);
    if (sapling <= 0) return null;
    net -= sapling;
    net -= removalCost(step.patch);
    if (settings.pays(step.patch)) {
      long payment = price.applyAsLong(crop.payment);
      if (payment <= 0) return null;
      net -= payment * crop.paymentQty;
    }
    if (settings.composts(step.patch)) {
      long compost = price.applyAsLong("Ultracompost");
      if (compost <= 0) return null;
      net -= settings.bucket ? compost / 2 : compost;
    }
    String harvest = step.patch.kind.equals("celastrus") ? "Celastrus bark" : crop.fruit;
    if (!harvest.isEmpty() && settings.harvests(step.patch.kind)) {
      long value = price.applyAsLong(harvest);
      if (value <= 0 && !harvest.equals("Calquat fruit")) return null;
      double herbs =
          settings.herbActual != null
              ? Math.max(0, settings.herbActual) // a finished run's real average
              : expectedHerbs(step.patch, settings, cape, resurrect);
      double amount =
          step.patch.kind.equals("herb") ? herbs : step.patch.kind.equals("celastrus") ? bark(settings) : 6;
      net += Math.round(value * amount);
    }
    return net;
  }

  /** Every item ID for the kit, including charged variants, for bank tags and the bank view. */
  static Set<Integer> itemIds(Kit kit, Settings settings) {
    Set<Integer> ids = new LinkedHashSet<>();
    List<String> items = new ArrayList<>(kit.wear);
    items.addAll(kit.bring.keySet());
    if (settings.level == 99) items.add("Farming cape");
    for (String name : items) for (int id : FarmingData.IDS.getOrDefault(name, new int[0])) ids.add(id);
    return ids;
  }

  /** A Bank Tags import string for the kit. */
  static String tags(Kit kit, Settings settings) {
    StringBuilder tags = new StringBuilder("Ultimate Farming");
    for (int id : itemIds(kit, settings)) tags.append(',').append(id);
    return tags.toString();
  }

  /** The item pictured beside a travel line in the Run tab. */
  static String travelItem(Choice travel, Settings settings) {
    String text = travel.text.toLowerCase(Locale.ROOT);
    if (text.startsWith("walk") || text.startsWith("run ")) return "Graceful boots";
    if (text.contains("spirit tree")) return "Spirit sapling";
    if (text.contains("farming cape")) return "Farming cape";
    if (text.contains("construction cape")) return "Construction cape";
    if (!travel.items.isEmpty()) return normalize(travel.items.get(0));
    if (!travel.gear.isEmpty()) return normalize(travel.gear.get(0));
    return "Spade";
  }

  /** Identifies a run's setup, so records of the same run can be compared. */
  static String signature(List<Step> route, Kit kit, Settings settings) {
    List<String> parts = new ArrayList<>();
    for (Step step : route) parts.add(step.patch.id + ":" + step.travel.text);
    Collections.sort(parts);
    List<String> gear = new ArrayList<>(kit.wear);
    Collections.sort(gear);
    return String.join("|", parts)
        + "/" + gear
        + "/" + settings.farmer
        + "/" + settings.unprotected
        + "/" + settings.unharvested
        + "/" + settings.bucket
        + "/" + (hasKind(route, "celastrus") ? settings.level : 0);
  }

  /**
   * Celastrus bark per patch, worked out rather than asked for: the wiki's money making guide
   * expects 8.8 at 99 Farming (ultracompost, magic secateurs), and about 6 at 85. In between it
   * scales with level; Attas adds 5%.
   */
  static double bark(Settings settings) {
    double level = Math.max(85, Math.min(99, settings.level));
    double bark = 6 + 2.8 * (level - 85) / 14;
    if (!settings.secateurs) bark /= 1.1;
    if (settings.has("attas")) bark *= 1.05;
    return bark;
  }
}
