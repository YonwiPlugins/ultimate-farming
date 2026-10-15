package com.yonwi.farming;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/** Switching spellbook before a run: with the magic cape (5 a day) or the house altar. */
final class SpellbookSwitch {
  static final int CAPE_SWAPS_PER_DAY = 5;
  static final Set<Integer> MAGIC_CAPES = FarmingData.itemIds("Magic cape");
  private static final int STANDARD = 0;
  private static final int ARCEUUS = 3;
  private static final Pattern CAPE_MENU_TITLE =
      Pattern.compile("Choose spellbook: \\((\\d+)/(\\d+) left\\)");
  private static final Pattern CHANGES_MESSAGE =
      Pattern.compile("changed your spellbook (\\d+)/\\d+ times today");
  /** A spellbook change this many ticks after clicking the cape's Spellbook counts as one. */
  private static final int CAPE_CLICK_TICKS = 100;

  @Inject private Client client;
  @Inject private ConfigManager configManager;
  @Inject private FarmingPlugin plugin;

  /** Tick the magic cape's Spellbook option was clicked, or -1. */
  private int capeClickedAt = -1;
  /** Show the magic cape in the bank filter, to withdraw for a spellbook switch. */
  private volatile boolean capeNeeded;
  /** "cape" or "altar" when that's what to click to switch, else null. */
  private volatile String method;
  /** One line for the overlay saying how to switch, or null. */
  private volatile String hint;
  /** The house altar to click (or, after the cape, the bank booth). */
  private volatile List<TileObject> objects = Collections.emptyList();

  boolean capeNeeded() {
    return capeNeeded;
  }

  String hint() {
    return hint;
  }

  List<TileObject> objects() {
    return objects;
  }

  /** "cape", "altar", "house" (go home first) or "bank" (withdraw the cape), or null. */
  String route() {
    if (hint == null) return null;
    if (method != null) return method;
    return capeNeeded ? "bank" : "house";
  }

  /** The spellbook the run needs and you're not on: 0 standard, 3 Arceuus, or -1 if fine. */
  int wanted() {
    List<String> missing = plugin.kit().missing();
    if (missing.contains(FarmingPanel.SWITCH_ARCEUUS)) return ARCEUUS;
    if (missing.contains(FarmingPanel.SWITCH_STANDARD)) return STANDARD;
    return -1;
  }

  /** "arceuus" or "standard", for matching menu and dialogue options. */
  String word() {
    int wanted = wanted();
    return wanted == ARCEUUS ? "arceuus" : wanted == STANDARD ? "standard" : null;
  }

  /** The magic cape's spellbook changes used today. The game's day starts at 00:00 UTC. */
  int capeSwapsUsed() {
    String saved = configManager.getRSProfileConfiguration(FarmingPanel.GROUP, "capeSwaps");
    String today = today();
    if (saved == null || !saved.startsWith(today + ":")) return 0;
    try {
      return Integer.parseInt(saved.substring(today.length() + 1));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private void setCapeSwapsUsed(int used) {
    int clamped = Math.max(0, Math.min(CAPE_SWAPS_PER_DAY, used));
    configManager.setRSProfileConfiguration(FarmingPanel.GROUP, "capeSwaps", today() + ":" + clamped);
  }

  private static String today() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  /** The magic cape's Spellbook option was clicked. */
  void capeClicked() {
    capeClickedAt = client.getTickCount();
  }

  /** The spellbook changed: soon after clicking the cape's Spellbook, that's one of today's. */
  void spellbookChanged() {
    if (capeClickedAt < 0 || client.getTickCount() - capeClickedAt >= CAPE_CLICK_TICKS) return;
    capeClickedAt = -1;
    setCapeSwapsUsed(capeSwapsUsed() + 1);
    plugin.debug().event("magic cape swaps used today: " + capeSwapsUsed());
  }

  /** The game says "You have changed your spellbook 1/5 times today." after each change. */
  void gameMessage(String message) {
    plugin.debug().event("msg: " + message);
    Matcher used = CHANGES_MESSAGE.matcher(message);
    if (used.find()) {
      setCapeSwapsUsed(Integer.parseInt(used.group(1)));
      capeClickedAt = -1; // counted from the message, not the varbit change
    }
  }

  /** The magic cape's menu title, "Choose spellbook: (4/5 left)", says how many changes are left. */
  private void readCapeMenu() {
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (options == null || options.isHidden() || options.getDynamicChildren() == null) return;
    for (Widget line : options.getDynamicChildren()) {
      String text = line == null || line.getText() == null ? "" : Text.removeTags(line.getText());
      Matcher left = CAPE_MENU_TITLE.matcher(text);
      if (left.find()) {
        int used = Integer.parseInt(left.group(2)) - Integer.parseInt(left.group(1));
        if (used != capeSwapsUsed()) setCapeSwapsUsed(used);
        return;
      }
    }
  }

  /**
   * Works out how to switch spellbook: the magic cape if you're carrying it and have changes left
   * today, the altar when you're in your house, otherwise what to fetch first.
   */
  void refresh() {
    boolean before = capeNeeded;
    work();
    // The cape comes and goes from the bank filter: refresh it if it's showing.
    if (before != capeNeeded && plugin.bankButton().filterOpen()) plugin.bankButton().refreshFilter();
  }

  private void work() {
    int wanted = wanted();
    Player player = client.getLocalPlayer();
    if (wanted < 0 || player == null || client.getVarbitValue(VarbitID.SPELLBOOK) == wanted) {
      nothingToSwitch(player);
      return;
    }
    String book = wanted == ARCEUUS ? "Arceuus" : "standard";
    int left = CAPE_SWAPS_PER_DAY - capeSwapsUsed();
    boolean carried = plugin.account().carrying(MAGIC_CAPES);
    boolean owned = false;
    for (int id : MAGIC_CAPES) owned |= plugin.account().owned().contains(id);
    TileObject altar =
        client.getTopLevelWorldView().isInstance()
            ? plugin.scene().nearestObject(player, 40, "altar of the occult", "occult altar", "dark altar")
            : null;
    readCapeMenu();
    capeNeeded = !carried && owned && left > 0;
    if (carried && left > 0) {
      method = "cape";
      objects = Collections.emptyList();
      hint = "Magic cape → Spellbook → " + book + " (" + left + " left today)";
    } else if (altar != null) {
      method = "altar";
      objects = Collections.singletonList(altar);
      hint = "Use the altar to switch to " + book;
    } else {
      method = null;
      objects = Collections.emptyList();
      hint =
          capeNeeded
              ? "Withdraw your magic cape to switch to " + book + " (" + left + " left today)"
              : "Teleport to your house and use the altar to switch to " + book;
    }
  }

  /** On the right spellbook (or none needed): after the cape, the bank is next, to put it back. */
  private void nothingToSwitch(Player player) {
    method = null;
    hint = null;
    objects = Collections.emptyList();
    capeNeeded = false;
    Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (player != null
        && plugin.kit().missing().contains(FarmingPanel.DEPOSIT_CAPE)
        && (bank == null || bank.isHidden())) {
      TileObject booth = plugin.scene().nearestObject(player, 25, "bank booth", "bank chest", "bank deposit box");
      if (booth != null) objects = Collections.singletonList(booth);
    }
  }
}
