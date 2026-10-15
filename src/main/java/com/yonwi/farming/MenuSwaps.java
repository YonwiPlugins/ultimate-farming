package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.client.util.Text;

/**
 * Makes the option the guide wants the left-click one: the rune pouch's Configure, the spellbook
 * switch, paying the gardener, and the teleport, ring, tree or boat for the trip. Option names
 * are from RuneLite's Menu Entry Swapper.
 */
final class MenuSwaps {
  private static final Pattern CHARTER_TO = Pattern.compile("charter to ([a-z' ]+?)(;|,| then|$)");
  /** Plain teleport options, after one naming the destination. */
  private static final List<String> TELEPORT_OPTIONS =
      Arrays.asList(
          "teleport", "farm teleport", "tele to poh", "travel", "teleport menu", "squeeze-through", "follow");
  /** Objects and NPCs on the way: house spirit tree, portal nexus, jewellery box, the Tree Gnome Village railing and Elkoy. */
  private static final String[] ON_THE_WAY = {
    "spirit tree", "portal nexus", "jewellery box", "railing", "elkoy", "mountain guide"
  };
  /** No option ranked: nothing to move. */
  private static final int UNRANKED = 9;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  /** How an item's name starts in game menus, lower case (some are abbreviated there). */
  static String menuName(String itemName) {
    String name = itemName.toLowerCase(Locale.ROOT);
    return name.equals("construction cape") ? "construct. cape" : name;
  }

  /** After the menu is sorted: puts the wanted option on top. */
  void swap() {
    Guidance step = plugin.guide();
    boolean menuOpen = client.isMenuOpen();
    // The rune pouch doesn't hold what the run needs: Configure is the left-click.
    if (step == null && !menuOpen && !plugin.kit().pouchShortfall().isEmpty() && swapPouchConfigure())
      return;
    if (step == null && plugin.spellbook().hint() != null && !menuOpen) {
      swapForSpellbookSwitch();
      return;
    }
    // Paying the gardener (protection, or removing the tree): "Pay" is his left-click.
    if (step != null && !menuOpen && plugin.targets().paysGardener(step, step.phase)) {
      swapGardenerPay(step);
      return;
    }
    if (step == null || !step.phase.equals("Travel") && !plugin.progress().houseReturn() || menuOpen)
      return;
    swapForTravel(step);
  }

  private boolean swapPouchConfigure() {
    MenuEntry[] entries = client.getMenuEntries();
    for (MenuEntry entry : entries)
      if (target(entry).contains("rune pouch") && option(entry).equals("configure")) {
        moveToLeftClick(entries, entry);
        return true;
      }
    return false;
  }

  /**
   * Before a run that needs another spellbook: the magic cape's Spellbook, the altar's option for
   * that book, or (when the house is the way) the Construction cape's or tablet's Tele to POH.
   */
  private void swapForSpellbookSwitch() {
    String word = plugin.spellbook().word();
    if (word == null) return;
    MenuEntry[] entries = client.getMenuEntries();
    String[][] wanted = {
      {"magic cape", "spellbook"},
      {"max cape", "spellbook"},
      {"altar of the occult", word}, // options: Venerate, Standard, Ancient, Lunar (+ Arceuus)
      {"occult altar", word},
      {"dark altar", word},
      {"dark altar", "venerate"},
      {"construct", "tele to poh"}, // the game names it "Construct. cape(t)"
      {"teleport to house", "break"}
    };
    for (String[] want : wanted) {
      boolean toHouse = want[0].equals("construct") || want[0].equals("teleport to house");
      if (toHouse && !"house".equals(plugin.spellbook().route())) continue;
      for (MenuEntry entry : entries)
        if (target(entry).contains(want[0]) && option(entry).equals(want[1])) {
          moveToLeftClick(entries, entry);
          return;
        }
    }
  }

  /** With more than one patch he has "Pay (tree patch)", "Pay (fruit tree)"…: the one for this patch. */
  private void swapGardenerPay(Guidance step) {
    int gardenerId = TargetData.GARDENERS.getOrDefault(step.patchId, -1);
    MenuEntry[] entries = client.getMenuEntries();
    MenuEntry pay = null;
    for (MenuEntry entry : entries) {
      NPC npc = entry.getNpc();
      String option = option(entry);
      if (npc == null || npc.getId() != gardenerId || !option.startsWith("pay")) continue;
      boolean fruit = option.contains("fruit");
      if (pay == null || fruit == step.kind.equals("fruit")) pay = entry;
    }
    if (pay != null) moveToLeftClick(entries, pay);
  }

  /**
   * While travelling: the cloak or ring teleport, the fairy ring's Last-destination (when it's
   * already this code) or Configure, and the spirit tree's Travel (Tree on a spiritual fairy tree).
   */
  private void swapForTravel(Guidance step) {
    MenuEntry[] entries = client.getMenuEntries();
    MenuEntry best = bestTeleport(step, entries);
    // Exact swaps first (worn items use the destination as the option, e.g. "Ardougne Farm").
    for (String[] want : wantedOptions(step))
      for (MenuEntry entry : entries) {
        String option = option(entry);
        boolean exact = want[1].equals("teleport") || want[1].equals("tree") || want[1].equals("charter");
        if (!target(entry).contains(want[0])
            || !(exact ? option.equals(want[1]) : option.contains(want[1]))) continue;
        moveToLeftClick(entries, entry);
        return;
      }
    if (best != null) moveToLeftClick(entries, best);
  }

  /** {target contains, option contains} pairs for this trip, best first. */
  private List<String[]> wantedOptions(Guidance step) {
    String travel = step.travel.toLowerCase(Locale.ROOT);
    String code = FarmingPlanner.fairyCode(step.travel);
    List<String[]> wanted = new ArrayList<>();
    if (step.travel.startsWith("Ardougne cloak")) wanted.add(new String[] {"ardougne cloak", "farm"});
    if (step.travel.startsWith("Explorer's ring")) wanted.add(new String[] {"explorer's ring", "teleport"});
    // The Construction cape's own portals (Taverley, Brimhaven) are under "Teleport"; its
    // "Tele to POH" is only for trips through your house.
    // (In menus the game calls it "Construct. cape(t)", so match on "construct".)
    if (step.travel.startsWith("Construction cape")) wanted.add(new String[] {"construct", "teleport"});
    // Eternal or charged teleport crystal: its "Lletya" option.
    if (travel.contains("lletya")) wanted.add(new String[] {"teleport crystal", "lletya"});
    // Standard spells: "Cast", not an alternative like Varrock's Grand Exchange.
    String spell = step.spell;
    if (spell != null && !spell.equals("House"))
      wanted.add(new String[] {spell.toLowerCase(Locale.ROOT) + " teleport", "cast"});
    if (code != null) {
      wanted.add(new String[] {"fairy", "last-destination (" + code.toLowerCase(Locale.ROOT)});
      wanted.add(new String[] {"fairy", "configure"});
    }
    // A charter ship (e.g. Catherby to Corsair Cove): the crew's "Charter-to <place>" when it's
    // already this destination (it remembers your last one), otherwise plain "Charter".
    if (travel.contains("charter")) {
      Matcher to = CHARTER_TO.matcher(travel);
      if (plugin.progress().chartered()) {
        // Off the ship, then on: the gangplank's Cross and the rowboat's Travel.
        wanted.add(new String[] {"gangplank", "cross"});
        wanted.add(new String[] {"rowboat", "travel"});
      } else {
        if (to.find()) wanted.add(new String[] {"trader crew", "charter-to " + to.group(1).trim()});
        wanted.add(new String[] {"trader crew", "charter"});
      }
    }
    if (travel.contains("spirit tree")) {
      wanted.add(new String[] {"spiritual fairy tree", "tree"});
      wanted.add(new String[] {"spirit tree", "travel"});
    }
    return wanted;
  }

  /**
   * Any other teleport item on this step (talisman, capes, jewellery…): prefer an option naming
   * the destination, e.g. "Falador Park" or "Xeric's Glade", then Teleport / Tele to POH, then Rub.
   */
  private MenuEntry bestTeleport(Guidance step, MenuEntry[] entries) {
    String travel = step.travel.toLowerCase(Locale.ROOT);
    int arrow = step.travel.indexOf('→');
    String place =
        arrow < 0 ? "" : travel.substring(arrow + 1).split("[,;]| then ")[0].replace("→", "").trim();
    String destination = FarmingInventoryOverlay.destination(step.travel);
    // House routes (house fairy ring, spirit tree, jewellery box, nexus) want "Tele to POH", not
    // the cape's portal list.
    boolean toHouse = Targets.throughHouse(travel);
    MenuEntry best = null;
    int bestRank = UNRANKED;
    for (MenuEntry entry : entries) {
      if (!isOnRoute(step, travel, target(entry))) continue;
      int rank = rank(option(entry), place, destination, toHouse);
      if (rank < bestRank) {
        best = entry;
        bestRank = rank;
      }
    }
    return best;
  }

  /** Whether a menu target is this step's item, or an object or NPC the route uses. */
  private static boolean isOnRoute(Guidance step, String travel, String target) {
    boolean ours = false;
    for (String item : step.items) ours |= target.startsWith(menuName(FarmingPlanner.normalize(item)));
    for (String thing : ON_THE_WAY) ours |= target.contains(thing) && travel.contains(thing.split(" ")[0]);
    ours |= target.contains("portal") && travel.contains("portal");
    return ours;
  }

  /** 0 for the option naming the destination, 1 for a plain teleport, 2 for Rub, else unranked. */
  private static int rank(String option, String place, String destination, boolean toHouse) {
    if (toHouse && option.equals("tele to poh")) return 0;
    boolean namesPlace =
        !place.isEmpty() && option.contains(place)
            || destination.length() > 3 && option.contains(destination)
            // Worn jewellery shortens it: the worn ring's "Falador" for Falador Park.
            || option.length() > 3 && !place.isEmpty() && place.startsWith(option);
    if (namesPlace) return 0;
    if (TELEPORT_OPTIONS.contains(option)) return 1;
    return option.equals("rub") ? 2 : UNRANKED;
  }

  /** The last menu entry is the left-click option. */
  private void moveToLeftClick(MenuEntry[] entries, MenuEntry entry) {
    List<MenuEntry> list = new ArrayList<>(Arrays.asList(entries));
    list.remove(entry);
    list.add(entry);
    // Item ops 4 and 5 (e.g. the cloak's Farm Teleport, the cape's Tele to POH) are
    // CC_OP_LOW_PRIORITY, which the game never uses as the left-click: it opens the menu or picks
    // the next option instead. Menu Entry Swapper raises them to CC_OP for the same reason.
    if (entry.getType() == MenuAction.CC_OP_LOW_PRIORITY) entry.setType(MenuAction.CC_OP);
    // A deprioritised entry on top also makes a left-click open the menu.
    entry.setDeprioritized(false);
    plugin.debug().event("left-click: " + Text.removeTags(entry.getOption()) + " " + Text.removeTags(entry.getTarget()));
    client.setMenuEntries(list.toArray(new MenuEntry[0]));
  }

  /**
   * In a right-click submenu, e.g. the construction cape's Teleport list, the route's destination
   * and the option that opens it are drawn in the highlight colour.
   */
  void colourDestination(Guidance step) {
    if (!client.isMenuOpen()) return;
    String word = FarmingInventoryOverlay.destination(step.travel).split(" ")[0];
    if (word.length() < 4) return;
    String colour = String.format("<col=%06x>", FarmingInventoryOverlay.TARGET.getRGB() & 0xFFFFFF);
    for (MenuEntry entry : client.getMenu().getMenuEntries()) {
      Menu sub = entry.getSubMenu();
      if (sub == null) continue;
      for (MenuEntry choice : sub.getMenuEntries()) {
        String option = choice.getOption();
        if (option == null || option.startsWith("<col=")) continue;
        if (!option.toLowerCase(Locale.ROOT).contains(word)) continue;
        choice.setOption(colour + option + "</col>");
        if (!entry.getOption().startsWith("<col=")) entry.setOption(colour + entry.getOption() + "</col>");
      }
    }
  }

  private static String option(MenuEntry entry) {
    return Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT);
  }

  private static String target(MenuEntry entry) {
    return Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT);
  }
}
