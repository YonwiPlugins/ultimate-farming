package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/**
 * The run's kit (from the sidebar): what it needs, what's still to withdraw, the rune pouch, and
 * the bank filter's layout.
 */
final class KitTracker {
  private static final int[][] POUCH_SLOTS = {
    {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
    {VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
    {VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
    {VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
    {VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5},
    {VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6}
  };
  private static final Set<Integer> DIVINE_POUCHES =
      new HashSet<>(Arrays.asList(ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER));
  /** Slots in the bank's first row, the outfit you put on. */
  private static final int FIRST_ROW = 8;

  @Inject private Client client;
  @Inject private ItemManager itemManager;
  @Inject private FarmingPlugin plugin;

  /** Kit item names in Kit tab order (wear, then bring), for the bank layout. */
  private volatile List<String> order = Collections.emptyList();
  private volatile Map<String, Integer> counts = Collections.emptyMap();
  /** Every item ID the kit can use, for the bank filter. */
  private volatile Set<Integer> items = Collections.emptySet();
  private volatile List<String> missing = Collections.emptyList();
  /** Spare variants kept out of the bank filter, e.g. a basic whistle when you own a better one. */
  private volatile Set<Integer> hiddenVariants = Collections.emptySet();
  /** One line telling you what to do with the rune pouch at the bank, or null. */
  private volatile String pouchHint;

  /** Sets the kit order, returning whether it changed. */
  boolean setOrder(List<String> names) {
    boolean changed = !names.equals(order);
    order = names;
    return changed;
  }

  void setCounts(Map<String, Integer> counts) {
    this.counts = counts;
  }

  void setItems(Set<Integer> ids) {
    items = ids;
  }

  void setMissing(List<String> list) {
    missing = list;
  }

  List<String> missing() {
    return missing;
  }

  /** Whether the bank's kit filter shows this item ID. */
  boolean inBankFilter(int id) {
    return !hiddenVariants.contains(id)
        && (items.contains(id)
            || plugin.spellbook().capeNeeded() && SpellbookSwitch.MAGIC_CAPES.contains(id));
  }

  /** How many of this item ID the run needs, or 0. */
  int count(int id) {
    String name = kitName(id);
    return name == null ? 0 : counts.get(name);
  }

  /** The kit item name this item ID counts as, or null. */
  private String kitName(int id) {
    for (Map.Entry<String, Integer> entry : counts.entrySet())
      for (int known : FarmingData.IDS.getOrDefault(entry.getKey(), new int[0]))
        if (known == id) return entry.getKey();
    return null;
  }

  /**
   * How many more of this kit item you still need to take out of the bank: what the run needs
   * minus what's in your inventory, worn, or (for runes) in the rune pouch. -1 if it isn't kit.
   */
  int remaining(int id) {
    String name = kitName(id);
    if (name == null) return -1;
    int need = counts.get(name);
    int have = 0;
    Set<Integer> ids = new HashSet<>();
    for (int known : FarmingData.IDS.getOrDefault(name, new int[0])) ids.add(known);
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer containerItems = client.getItemContainer(container);
      if (containerItems == null) continue;
      for (Item item : containerItems.getItems())
        if (item.getId() > 0 && ids.contains(itemManager.canonicalize(item.getId())))
          have += item.getQuantity();
    }
    if (isRune(name))
      for (Map.Entry<Integer, Integer> rune : pouchRunes().entrySet())
        if (ids.contains(rune.getKey())) have += rune.getValue();
    return Math.max(0, need - have);
  }

  private static boolean isRune(String name) {
    return name.endsWith(" rune");
  }

  /** Rune item IDs the run needs, whether or not you have them yet. */
  Set<Integer> runesNeeded() {
    Set<Integer> ids = new HashSet<>();
    for (String name : counts.keySet())
      if (isRune(name)) {
        int id = FarmingData.id(name);
        if (id > 0) ids.add(id);
      }
    return ids;
  }

  /**
   * Before a run: rune item IDs the run still needs that you aren't carrying (pouch + inventory),
   * when your rune pouch is in the inventory, so it can be set up. Empty otherwise.
   */
  Set<Integer> pouchShortfall() {
    if (plugin.guide() != null || missing.isEmpty()) return Collections.emptySet();
    if (!plugin.account().holdingUnnoted(FarmingData.itemIds("Rune pouch")))
      return Collections.emptySet();
    // A full pouch of this run's runes (e.g. 3 kinds when it needs 4): the rest go loose in the
    // inventory, and the bank's kit filter already shows them unticked, so nothing to set up.
    Set<Integer> needed = runesNeeded();
    Set<Integer> inPouch = new HashSet<>();
    for (Map.Entry<Integer, Integer> rune : pouchRunes().entrySet())
      if (rune.getValue() > 0) inPouch.add(rune.getKey());
    if (inPouch.size() >= pouchKinds() && needed.containsAll(inPouch)) return Collections.emptySet();
    Set<Integer> shortRunes = new HashSet<>();
    for (String name : counts.keySet()) {
      if (!isRune(name)) continue;
      int id = FarmingData.id(name);
      if (id > 0 && remaining(id) > 0) shortRunes.add(id);
    }
    return shortRunes;
  }

  /** How many kinds of rune your pouch holds: 4 in a divine rune pouch, else 3. */
  int pouchKinds() {
    return plugin.account().holdingUnnoted(DIVINE_POUCHES) ? 4 : 3;
  }

  void setPouchHint(String hint) {
    pouchHint = hint;
  }

  String pouchHint() {
    return pouchShortfall().isEmpty() ? null : pouchHint;
  }

  /** Rune item IDs and amounts inside the rune pouch, read from its varbits. */
  Map<Integer, Integer> pouchRunes() {
    Map<Integer, Integer> runes = new LinkedHashMap<>();
    EnumComposition types = client.getEnum(EnumID.RUNEPOUCH_RUNE);
    for (int[] slot : POUCH_SLOTS) {
      int type = client.getVarbitValue(slot[0]);
      int amount = client.getVarbitValue(slot[1]);
      if (type <= 0 || amount <= 0 || types == null) continue;
      int id = types.getIntValue(type);
      if (id > 0) runes.merge(id, amount, Integer::sum);
    }
    return runes;
  }

  /** Whether this varbit is one of the rune pouch's slots. */
  static boolean isPouchVarbit(int varbitId) {
    for (int[] slot : POUCH_SLOTS) if (varbitId == slot[0] || varbitId == slot[1]) return true;
    return false;
  }

  /**
   * One slot per kit item, in Kit tab order. Each slot uses the variant you actually have in the
   * bank or inventory (e.g. your ring of wealth's charge), so it shows that item or its
   * placeholder. Also decides which spare variants the filter hides.
   */
  int[] bankLayout() {
    Set<Integer> have = new HashSet<>();
    Set<Integer> bank = plugin.account().bank();
    if (bank != null) have.addAll(bank);
    // Inventory and worn too: an axe or whistle you're wearing is one you own.
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer containerItems = client.getItemContainer(container);
      if (containerItems != null)
        for (Item item : containerItems.getItems())
          if (item.getId() > 0) have.add(itemManager.canonicalize(item.getId()));
    }
    List<Integer> slots = new ArrayList<>();
    List<Integer> seeds = new ArrayList<>();
    Set<Integer> hidden = new HashSet<>();
    List<String> names = new ArrayList<>(order);
    if (plugin.spellbook().capeNeeded()) names.add("Magic cape"); // to switch spellbook before the run
    for (String name : names) {
      int[] ids = FarmingData.IDS.getOrDefault(name, new int[0]);
      if (ids.length == 0) continue;
      int chosen = FarmingData.bestOwned(name, have);
      if (chosen <= 0) chosen = ids[0];
      boolean seed = name.endsWith(" seed") || name.endsWith(" sapling");
      if (!slots.contains(chosen) && !seeds.contains(chosen)) (seed ? seeds : slots).add(chosen);
      // One quetzal whistle is enough: show the best you own and hide the rest (IDS lists the
      // best first).
      if (name.equals("Quetzal whistle") || name.equals("Axe"))
        for (int id : ids) if (id != chosen) hidden.add(id);
    }
    hiddenVariants = hidden;
    // Seeds and saplings start the second row (slot 9): the first row is the outfit you put on,
    // so they're the first thing withdrawn into the inventory.
    if (!seeds.isEmpty()) {
      while (slots.size() < FIRST_ROW) slots.add(-1);
      slots.addAll(FIRST_ROW, seeds);
    }
    return slots.stream().mapToInt(Integer::intValue).toArray();
  }
}
