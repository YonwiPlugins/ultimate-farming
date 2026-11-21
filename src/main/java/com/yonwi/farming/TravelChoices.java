package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every way to get to each patch with the player's unlocks, and its cost (roughly minutes), plus
 * the walks and boats between neighbouring patches.
 */
final class TravelChoices {
  private static final String STAFF = "Dramen/lunar staff if required";
  private static final String[] NONE = {};
  private static final List<String> TELEPORT_SPELLS =
      Arrays.asList("Falador", "Lumbridge", "Varrock", "Camelot", "Ardougne");
  /** A rune pouch spell is this much quicker than breaking the tablet, but never under 0.3. */
  private static final double SPELL_SAVING = .12;
  /** The Construction cape's Tele to POH is this much quicker than a house tablet. */
  private static final double CAPE_SAVING = .16;
  private static final double FASTEST = .3;

  private final Settings settings;
  private final List<FarmingPlanner.Choice> options = new ArrayList<>();

  private TravelChoices(Settings settings) {
    this.settings = settings;
  }

  /** Ways to get to a patch, the quickest first. */
  static List<FarmingPlanner.Choice> forPatch(Patch patch, Settings settings) {
    TravelChoices choices = new TravelChoices(settings);
    if (patch.kind.equals("herb")) choices.addHerbPatch(patch.id);
    else choices.addTreePatch(patch);
    List<FarmingPlanner.Choice> found = choices.options;
    if (patch.id.equals("kastoricalquat")) found = kastoriCalquat(found);
    found.sort(Comparator.comparingDouble(choice -> choice.cost));
    return found;
  }

  /** The Kastori fruit tree's routes, pointed at the Calquat patch beside it instead. */
  private static List<FarmingPlanner.Choice> kastoriCalquat(List<FarmingPlanner.Choice> fruitRoutes) {
    List<FarmingPlanner.Choice> revised = new ArrayList<>();
    for (FarmingPlanner.Choice choice : fruitRoutes)
      revised.add(
          new FarmingPlanner.Choice(
              choice.text
                  .replace("north to fruit tree", "north-east to Calquat patch")
                  .replace("run south-west", "run south to Calquat patch"),
              choice.cost,
              choice.items,
              choice.gear));
    return revised;
  }

  private boolean has(String unlock) {
    return settings.has(unlock);
  }

  /** A route using these items from the inventory (tablets, a whistle…). */
  private void carry(String text, double cost, String... items) {
    add(text, cost, items, NONE);
  }

  /** A route using this worn item (ring, cape, necklace…). */
  private void wear(String text, double cost, String gear) {
    add(text, cost, NONE, new String[] {gear});
  }

  /** A fairy ring route: a staff in hand unless your unlocks waive it. */
  private void fairyRing(String text, double cost, String... items) {
    add(text, cost, items, new String[] {STAFF});
  }

  private void add(String text, double cost, String[] items, String[] gear) {
    List<String> supply = new ArrayList<>(Arrays.asList(items));
    if (has("pouch"))
      for (String spell : TELEPORT_SPELLS)
        if (supply.remove(spell + " teleport tablets")) {
          supply.add("Rune pouch");
          text = text.replace(spell + " teleport", "Cast " + spell + " Teleport");
          cost = Math.max(FASTEST, cost - SPELL_SAVING);
        }
    List<String> worn = new ArrayList<>(Arrays.asList(gear));
    if (has("staffless")) worn.remove(STAFF);
    // With a fairy ring in the house, every fairy ring trip starts with a house teleport.
    if (has("houseFairy") && text.startsWith("Fairy ring ")) {
      text = "House teleport → " + text;
      if (has("cape")) worn.add("Construction cape");
      else supply.add(houseTeleport());
    }
    options.add(new FarmingPlanner.Choice(text, cost, supply, worn));
  }

  /** Into your house first: the Construction cape, else a house tablet (or the spell). */
  private void viaHouse(String text, double cost) {
    if (has("cape")) add(text, Math.max(FASTEST, cost - CAPE_SAVING), NONE, new String[] {"Construction cape"});
    else add(text, cost, new String[] {houseTeleport()}, NONE);
  }

  private String houseTeleport() {
    return has("pouch") ? "Rune pouch" : "House teleport tablets";
  }

  /** A spirit tree in the house: every spirit tree trip starts there. */
  private boolean houseTree() {
    return FarmingPlanner.houseTree(settings);
  }

  private void addHerbPatch(String id) {
    switch (id) {
      case "herbfalador":
        if (has("herbExplorer")) wear("Explorer's ring → cabbage patch", .5, "Explorer's ring");
        carry("Falador teleport → south to farm", 2, "Falador teleport tablets");
        break;
      case "herbardougne":
        if (has("herbArdy")) carry("Ardougne cloak → farm", .5, "Ardougne cloak");
        carry("Ardougne teleport → north to farm", 2, "Ardougne teleport tablets");
        break;
      case "herbcatherby":
        if (has("catherbyPortal"))
          viaHouse("House teleport → Portal nexus: Catherby, then north-west to herb patch", .6);
        if (has("catherby")) carry("Catherby teleport → north-west to herb patch", .5, "Catherby teleport tablets");
        carry("Camelot teleport → south-east to Catherby farm", 1.5, "Camelot teleport tablets");
        break;
      case "herbhosidius":
        if (has("herbXeric")) wear("Xeric's talisman → Xeric's Glade, then south-west", .7, "Xeric's talisman");
        carry("Walk from Hosidius bank to herb patch", 3);
        break;
      case "herbphasmatys":
        if (has("herbEcto")) carry("Ectophial → west to farm", 1, "Ectophial");
        // With a fairy ring in the house, house → ALQ → 42 steps beats the Ectophial's walk
        // (Shortest Path agrees).
        if (has("fairy")) fairyRing("Fairy ring ALQ → north to farm", has("houseFairy") && settings.poh() ? .9 : 2);
        break;
      case "herbtroll":
        if (has("herbBasalt")) carry("Stony basalt → Troll Stronghold patch", .5, "Stony basalt");
        if (has("herbTrollheim")) carry("Trollheim teleport → Stronghold rooftop", 2, "Trollheim teleport");
        break;
      case "herbweiss":
        if (has("herbBasalt")) carry("Icy basalt → Weiss patch", .4, "Icy basalt");
        break;
      case "herbharmony":
        if (has("herbHarmonyTab")) carry("Harmony island teleport → herb patch", .5, "Harmony island teleport");
        break;
      case "herbfortis":
        if (has("whistle")) carry("Quetzal whistle → Hunter Guild, then run north", 1, "Quetzal whistle");
        if (has("fairy")) fairyRing("Fairy ring AJP → north-west to farm", 1.8);
        if (has("quetzal"))
          carry(
              "Civitas illa Fortis teleport → quetzal to Hunter Guild, then north",
              1.6,
              "Civitas illa Fortis teleport tablet");
        break;
      case "herbguild":
        if (settings.level == 99 && has("farmingCape")) carry("Farming cape → Farming Guild", .5, "Farming cape");
        if (has("skills")) wear("Skills necklace → Farming Guild", .7, "Skills necklace");
        if (settings.poh() && has("jewellery")) viaHouse("POH jewellery box → Farming Guild", 1.2);
        break;
      default:
        break;
    }
  }

  private void addTreePatch(Patch patch) {
    switch (patch.id) {
      case "falador":
        if (has("wealth")) wear("Ring of wealth → Falador Park", .45, "Ring of wealth");
        if (settings.poh() && has("jewellery")) viaHouse("POH jewellery box → Falador Park", 1.05);
        carry("Falador teleport → east to the park", 1.8, "Falador teleport tablets");
        break;
      case "taverley":
        if (settings.poh() && settings.house.equals("taverley"))
          viaHouse("Teleport outside your Taverley house → south-east", .65);
        if (has("redirect")) carry("Redirected house tab → Taverley", .8, "Taverley teleport tablets");
        if (has("cape")) wear("Construction cape → Taverley house portal", .65, "Construction cape");
        carry("Falador teleport → through the Taverley gate", 2.65, "Falador teleport tablets");
        break;
      case "lumbridge":
        carry("Lumbridge teleport → west through the castle", 1.45, "Lumbridge teleport tablets");
        break;
      case "varrock":
        carry("Varrock teleport → palace courtyard", 1.15, "Varrock teleport tablets");
        if (has("gnome") && !houseTree()) carry("Spirit tree → Grand Exchange, then south-east", 2.1);
        break;
      case "strongtree":
        if (settings.poh() && has("spirit")) viaHouse("House spirit tree → Gnome Stronghold, then south-west", 1.4);
        if (has("slayer")) wear("Slayer ring → Stronghold Slayer Cave", .45, "Slayer ring");
        if (has("gnome") && !houseTree()) carry("Spirit tree → Stronghold, then south-west", 1.35);
        carry("Royal seed pod → Stronghold, then south-west", 1.6, "Royal seed pod");
        break;
      case "strongfruit":
        if (settings.poh() && has("spirit")) viaHouse("House spirit tree → Gnome Stronghold, then east", 1.1);
        if (has("gnome") && !houseTree()) carry("Spirit tree → Stronghold, then east", .8);
        carry("Royal seed pod → Stronghold, then east", 1.25, "Royal seed pod");
        break;
      case "village":
        if (has("gnome") && !houseTree()) carry("Spirit tree → Tree Gnome Village; follow Elkoy", 1.8);
        if (settings.poh() && has("spirit")) viaHouse("House spirit tree → Tree Gnome Village; follow Elkoy", 2.1);
        break;
      case "guildredwood":
      case "guildcelastrus":
      case "guildtree":
      case "guildfruit":
        addFarmingGuild(patch.id);
        break;
      case "catherby":
        if (has("catherbyPortal")) viaHouse("House teleport → Portal nexus: Catherby, then east to the beach", .85);
        if (has("catherby")) carry("Catherby teleport → east to the beach", .75, "Catherby teleport tablets");
        carry("Camelot teleport → south-east to Catherby", 2.25, "Camelot teleport tablets");
        break;
      case "brimhaven":
        if (settings.poh() && settings.house.equals("brimhaven"))
          viaHouse("Teleport outside your Brimhaven house → north", .7);
        if (has("redirect")) carry("Redirected house tab → Brimhaven", .85, "Brimhaven teleport tablets");
        if (has("cape")) wear("Construction cape → Brimhaven house portal, then north", .6, "Construction cape");
        carry("Ardougne teleport → boat to Brimhaven", 3.3, "Ardougne teleport tablets", "Boat fare");
        break;
      case "lletya":
        carry("Eternal or charged teleport crystal → Lletya; run south-east", .8, "Teleport crystal");
        break;
      case "auburn":
        addAuburnValley();
        break;
      case "kastoricalquat":
      case "kastori":
        addKastori();
        break;
      case "calquat":
        carry("Tai bwo wannai teleport → north to the Calquat patch", .8, "Tai bwo wannai teleport");
        if (has("fairy")) fairyRing("Fairy ring CKR → north to Tai Bwo Wannai", 1.8);
        if (has("cape")) wear("Construction cape → Brimhaven, then run south", 2.2, "Construction cape");
        break;
      case "summercalquat":
        fairyRing("Fairy ring CJQ → south, then west via the 50 Agility shortcut", 1.5);
        break;
      case "fossil1":
      case "fossil2":
      case "fossil3":
        if (has("digsite"))
          carry(
              "Digsite pendant → Fossil Island; mushtree to Mushroom Meadow, then south-east",
              1.3,
              "Digsite pendant");
        carry(
            "Digsite teleport → barge to Fossil Island; follow forest path to hardwood patches",
            4.2,
            "Digsite teleport");
        break;
      case "locus":
        addLocusOasis();
        break;
      case "anglers":
        addAnglersRetreat();
        break;
      default:
        break;
    }
  }

  /**
   * Walking tiles between the Farming Guild's patches and from where its teleports land (about
   * 1248,3718), measured with the collision map (WorldPath) from each patch's nearest side.
   */
  private static final Map<String, Integer> GUILD_FROM_LANDING = new HashMap<>();
  private static final Map<Set<String>, Integer> GUILD_WALKS = new HashMap<>();
  /** Minutes per tile walked (running), and the fixed part of any walk within the guild. */
  private static final double GUILD_MINUTES_PER_TILE = 1 / 80.0;
  private static final double GUILD_WALK_BASE = .1;

  static {
    GUILD_FROM_LANDING.put("herbguild", 17);
    GUILD_FROM_LANDING.put("guildtree", 22);
    GUILD_FROM_LANDING.put("guildcelastrus", 31);
    GUILD_FROM_LANDING.put("guildfruit", 38);
    GUILD_FROM_LANDING.put("guildredwood", 46);
    guildWalk("herbguild", "guildtree", 6);
    guildWalk("herbguild", "guildcelastrus", 27);
    guildWalk("herbguild", "guildfruit", 34);
    guildWalk("herbguild", "guildredwood", 42);
    guildWalk("guildtree", "guildcelastrus", 32);
    guildWalk("guildtree", "guildfruit", 39);
    guildWalk("guildtree", "guildredwood", 47);
    guildWalk("guildcelastrus", "guildfruit", 5);
    guildWalk("guildcelastrus", "guildredwood", 10);
    guildWalk("guildfruit", "guildredwood", 9);
  }

  private static void guildWalk(String a, String b, int tiles) {
    GUILD_WALKS.put(new HashSet<>(Arrays.asList(a, b)), tiles);
  }

  /** Minutes to walk between two Farming Guild patches. */
  static double guildWalkCost(String fromId, String toId) {
    Integer tiles = GUILD_WALKS.get(new HashSet<>(Arrays.asList(fromId, toId)));
    return GUILD_WALK_BASE + (tiles == null ? 25 : tiles) * GUILD_MINUTES_PER_TILE;
  }

  /**
   * Teleport costs are for the tree patch; each other patch adds (or saves) the extra walk from
   * the landing spot.
   */
  private void addFarmingGuild(String patchId) {
    double extra =
        (GUILD_FROM_LANDING.getOrDefault(patchId, 38) - GUILD_FROM_LANDING.get("guildtree")) * GUILD_MINUTES_PER_TILE;
    if (settings.level >= 99 && has("farmingCape")) carry("Farming cape → Farming Guild", .48 + extra, "Farming cape");
    if (has("skills")) wear("Skills necklace → Farming Guild", .75 + extra, "Skills necklace");
    if (settings.poh() && has("jewellery")) viaHouse("POH jewellery box → Farming Guild", 1.25 + extra);
  }

  private void addAuburnValley() {
    if (has("pendant")) wear("Pendant of ates → Nemus Retreat; run north", 1.2, "Pendant of ates");
    if (has("whistle"))
      carry("Quetzal whistle → Quetzacalli Gorge; follow Mountain Guide", 2.4, "Quetzal whistle");
    else if (has("quetzal"))
      carry(
          "Civitas illa Fortis teleport → Quetzal to Quetzacalli Gorge; follow Mountain Guide",
          3.25,
          "Civitas illa Fortis teleport tablet");
    if (has("fairy")) fairyRing("Fairy ring AIS → south-west, cross river, then north-west to tree patch", 2.8);
  }

  private void addKastori() {
    if (has("pendant")) wear("Pendant of ates → Kastori; run south-west", 1.15, "Pendant of ates");
    if (has("whistle"))
      carry("Quetzal whistle → Kastori landing site; run north to fruit tree", 1.55, "Quetzal whistle");
    else if (has("quetzal"))
      carry(
          "Civitas illa Fortis teleport → Quetzal to Kastori; run north", 2.35, "Civitas illa Fortis teleport tablet");
    if (has("fairy")) fairyRing("Fairy ring AIS → south-west to Achilka; boat to Kastori, then north", 4.1, "Boat fare");
  }

  private void addLocusOasis() {
    if (has("fairy")) fairyRing("Fairy ring AJP → south-east to Locus Oasis", 1.7);
    if (has("whistle"))
      carry("Quetzal whistle → Colossal Wyrm Remains landing site, then north", 2.7, "Quetzal whistle");
    else if (has("quetzal"))
      carry(
          "Civitas illa Fortis teleport → Colossal Wyrm Remains landing site, then north",
          2.7,
          "Civitas illa Fortis teleport tablet");
  }

  private void addAnglersRetreat() {
    // Catherby Teleport in your portal nexus: lands by the charter ship, much nearer than Camelot.
    if (has("catherbyPortal")) {
      String text =
          "House teleport → Portal nexus: Catherby; charter to Corsair Cove, rowboat to Anglers’ Retreat, then south";
      if (has("cape")) add(text, 4.2, new String[] {"Boat fare"}, new String[] {"Construction cape"});
      else add(text, 4.4, new String[] {houseTeleport(), "Boat fare"}, NONE);
    }
    carry(
        "Camelot teleport → Catherby charter to Corsair Cove; rowboat to Anglers’ Retreat, then south",
        4.6,
        "Camelot teleport tablets",
        "Boat fare");
  }

  /** Whether you can get to a patch at all, with the run types and unlocks chosen. */
  static boolean reachable(Patch patch, Settings settings) {
    if (!settings.kinds.contains(patch.kind) || settings.crop(patch.kind) == null || settings.excluded.contains(patch.id))
      return false;
    switch (patch.id) {
      case "herbtroll":
        return settings.has("herbTroll");
      case "herbweiss":
        return settings.has("herbWeiss");
      case "herbharmony":
        return settings.has("herbHarmony");
      case "herbfortis":
        return settings.has("herbFortis");
      case "herbphasmatys":
        return settings.has("herbPriest");
      case "herbguild":
      case "guildtree":
        return settings.level >= 65;
      case "guildfruit":
        return settings.level >= 85;
      case "village":
        return settings.has("gnome") || (settings.poh() && settings.has("spirit"));
      case "lletya":
        return settings.has("lletya");
      case "auburn":
        return settings.has("varlamore");
      case "kastori":
      case "kastoricalquat":
        return settings.has("kastori");
      case "summercalquat":
        return settings.has("summer") && settings.has("fairy");
      case "fossil1":
      case "fossil2":
      case "fossil3":
        return settings.has("fossil");
      case "locus":
        return settings.has("locus");
      case "anglers":
        return settings.has("anglers");
      default:
        return true;
    }
  }

  private static FarmingPlanner.Choice walk(String text, double cost) {
    return new FarmingPlanner.Choice(text, cost, Collections.emptyList(), Collections.emptyList());
  }

  private static FarmingPlanner.Choice boat(String text, double cost) {
    return new FarmingPlanner.Choice(text, cost, Arrays.asList("Boat fare"), Collections.emptyList());
  }

  /** A walk, spirit tree or boat straight from one patch to the next, or null if there's none. */
  static FarmingPlanner.Choice between(Patch from, Patch to, Settings settings) {
    String fromId = from.id;
    String toId = to.id;
    Set<String> pair = new HashSet<>(Arrays.asList(fromId, toId));
    if (inFarmingGuild(fromId) && inFarmingGuild(toId))
      return walk("Walk within Farming Guild to " + to.kind + " patch", guildWalkCost(fromId, toId));
    if (fromId.startsWith("fossil") && toId.startsWith("fossil"))
      return walk("Walk to Fossil Island hardwood patch " + toId.substring(6), .12);
    if (pair.containsAll(Arrays.asList("catherby", "herbcatherby"))) return walk("Walk to Catherby " + to.kind + " patch", .5);
    if (pair.containsAll(Arrays.asList("strongtree", "strongfruit")))
      return walk("Walk to the Stronghold " + to.kind + " patch", fromId.equals("strongtree") ? .38 : .43);
    if (pair.containsAll(Arrays.asList("falador", "taverley"))) return walk("Run between Falador Park and Taverley", 1.85);
    if (pair.containsAll(Arrays.asList("kastori", "kastoricalquat"))) return walk("Walk to the Kastori " + to.kind + " patch", .5);
    if (pair.containsAll(Arrays.asList("brimhaven", "calquat")))
      return walk(
          toId.equals("calquat") ? "Run south to Tai Bwo Wannai Calquat patch" : "Run north to Brimhaven fruit patch", 2);
    // With fromId spirit tree in the house, every spirit tree trip goes from there (cape → house tree).
    boolean spiritTrees = settings.has("gnome") && !FarmingPlanner.houseTree(settings);
    if (spiritTrees && fromId.startsWith("strong") && toId.equals("village"))
      return walk("Spirit tree to Tree Gnome Village, then follow Elkoy", 1.65);
    if (spiritTrees && fromId.equals("village") && toId.startsWith("strong")) return walk("Spirit tree to Gnome Stronghold", 1.7);
    if (fromId.equals("catherby") && toId.equals("brimhaven")) return boat("Charter ship to Brimhaven", 2.3);
    if (fromId.equals("brimhaven") && toId.equals("catherby")) return boat("Charter ship to Catherby", 2.5);
    if ((fromId.equals("catherby") || fromId.equals("brimhaven")) && toId.equals("anglers"))
      return boat("Charter to Corsair Cove, rowboat to Anglers’ Retreat", 2.8);
    return null;
  }

  private static boolean inFarmingGuild(String patchId) {
    return patchId.startsWith("guild") || patchId.equals("herbguild");
  }
}
