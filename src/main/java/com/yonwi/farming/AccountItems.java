package com.yonwi.farming;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * What the account owns and carries: the bank (cached per account between sessions), the
 * inventory and worn items. Reads quests, diaries and items for the sidebar's unlocks.
 */
final class AccountItems {
  /** Ticks between account scans when nothing has prompted one. */
  private static final int SCAN_EVERY = 100;

  @Inject private Client client;
  @Inject private ItemManager itemManager;
  @Inject private ConfigManager configManager;
  @Inject private FarmingPlugin plugin;

  /** Item IDs in the bank, or null until it's been seen (or loaded from the saved copy). */
  private Set<Integer> bank;
  /** Everything owned (bank, inventory, worn), from the last account scan. */
  private volatile Set<Integer> owned = Collections.emptySet();
  private volatile boolean bankUnknown;
  private boolean scanDue;
  private int ticks;

  Set<Integer> bank() {
    return bank;
  }

  Set<Integer> owned() {
    return owned;
  }

  boolean bankUnknown() {
    return bankUnknown;
  }

  void forgetBank() {
    bank = null;
  }

  void scanSoon() {
    scanDue = true;
  }

  /** Scans when something prompted it, and every 100 ticks anyway. */
  void onTick() {
    if (scanDue || ++ticks % SCAN_EVERY == 0) scan();
  }

  /** The bank was seen: remember what's in it, here and in the account's saved settings. */
  void bankChanged(ItemContainer container) {
    bank = new LinkedHashSet<>();
    for (Item item : container.getItems())
      if (item.getId() > 0 && item.getQuantity() > 0) bank.add(itemManager.canonicalize(item.getId()));
    StringBuilder saved = new StringBuilder();
    for (int id : bank) saved.append(saved.length() == 0 ? "" : ",").append(id);
    configManager.setRSProfileConfiguration(FarmingPanel.GROUP, "bank", saved.toString());
  }

  /** Whether the inventory holds any of these item IDs (noted items have other IDs). */
  boolean holdingUnnoted(Set<Integer> ids) {
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    if (inventory == null) return false;
    for (Item item : inventory.getItems()) if (ids.contains(item.getId())) return true;
    return false;
  }

  /** Whether the inventory or worn items hold any of these item IDs. */
  boolean carrying(Set<Integer> ids) {
    boolean carried = false;
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer items = client.getItemContainer(container);
      if (items != null) for (Item item : items.getItems()) carried |= ids.contains(item.getId());
    }
    return carried;
  }

  /** Reads quests, diaries and owned items, then tells the sidebar which unlocks are known. */
  private void scan() {
    scanDue = false;
    if (!plugin.isActive() || client.getGameState() != GameState.LOGGED_IN) return;
    if (bank == null) loadSavedBank();
    Set<Integer> ownedNow = new HashSet<>();
    Map<Integer, Integer> carried = new HashMap<>();
    if (bank != null) ownedNow.addAll(bank);
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer items = client.getItemContainer(container);
      if (items == null) continue;
      for (Item item : items.getItems())
        if (item.getId() > 0) {
          // Notes count as the item itself, e.g. noted protection payments.
          int id = itemManager.canonicalize(item.getId());
          ownedNow.add(id);
          carried.merge(id, item.getQuantity(), Integer::sum);
        }
    }
    bankUnknown = bank == null;
    owned = ownedNow;
    // Skill levels for Agility shortcuts on walking routes.
    Map<String, Integer> levels = new HashMap<>();
    for (Skill skill : Skill.values()) levels.put(skill.getName(), client.getRealSkillLevel(skill));
    WorldPath.setLevels(levels);
    AccountScan.Result result = AccountScan.scan(client, ownedNow, bank != null);
    result.carried = carried;
    result.pouch = plugin.kit().pouchRunes();
    result.spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
    plugin.toPanel(panel -> panel.updateAccount(result));
  }

  private void loadSavedBank() {
    String saved = configManager.getRSProfileConfiguration(FarmingPanel.GROUP, "bank");
    if (saved == null) return;
    bank = new LinkedHashSet<>();
    for (String id : saved.split(",")) if (!id.isEmpty()) bank.add(Integer.parseInt(id));
  }
}
