package com.yonwi.farming;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.ItemID;

/**
 * Counts what's picked: grimy herbs reaching the inventory or an open herb sack (which starts a
 * herb run's timer), and Celastrus bark chopped.
 */
final class HarvestTracker {
  static final int HERB_SACK_SIZE = 30;
  /**
   * Open herb sacks: picked grimy herbs go straight in, not into the inventory, up to 30 of each
   * herb (OSRS wiki; assumed the same for the silk sack).
   */
  private static final Set<Integer> OPEN_HERB_SACKS =
      new HashSet<>(Arrays.asList(ItemID.SLAYER_HERB_SACK_OPEN, ItemID.SLAYER_HERB_SACK_SILK_OPEN));
  private static final Set<Integer> SHUT_HERB_SACKS =
      new HashSet<>(Arrays.asList(ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_SILK));
  /** Unnoted grimy herbs, whose count rising means a herb was just picked. */
  static final Set<Integer> GRIMY_HERBS = new HashSet<>();

  static {
    for (Crop crop : FarmingData.CROPS.get("herb"))
      for (int id : FarmingData.IDS.getOrDefault(crop.fruit, new int[0])) GRIMY_HERBS.add(id);
  }

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  private int grimyCount = -1;
  private int barkCount = -1;
  /**
   * Herbs picked into the open sack since the bank was last open (where it's assumed emptied). It
   * can't be read from the game, so it's counted: Farming XP with no new grimy herb in the bag.
   */
  private int sackHerbs;
  private long farmingXp = -1;
  /** This tick: Farming XP went up, grimy herbs in the inventory went up. */
  private boolean xpGained;
  private boolean grimyGained;
  private boolean wasPickable;
  /** The herb patch you've started picking at (set by the first herb picked there). */
  private volatile String pickingPatch;
  /** Herbs picked at {@link #pickingPatch} so far. */
  private int pickedHere;

  static Set<Integer> shutHerbSacks() {
    return SHUT_HERB_SACKS;
  }

  String pickingPatch() {
    return pickingPatch;
  }

  /** Herbs picked so far at this patch, if it's the one being picked. */
  int pickedAt(String patchId) {
    return patchId.equals(pickingPatch) ? pickedHere : 0;
  }

  int sackHerbs() {
    return sackHerbs;
  }

  boolean holdingOpenSack() {
    return plugin.account().holdingUnnoted(OPEN_HERB_SACKS);
  }

  /** Logged out: counts start again from the next inventory seen. */
  void reset() {
    grimyCount = -1;
    farmingXp = -1;
  }

  /** The herb sack is emptied at the bank between runs (assumed; corrected once it's seen full). */
  void bankSeen() {
    sackHerbs = 0;
  }

  void farmingXpChanged(long xp) {
    if (farmingXp >= 0 && xp > farmingXp) xpGained = true;
    farmingXp = xp;
  }

  void inventoryChanged(ItemContainer inventory) {
    int count = 0;
    for (Item item : inventory.getItems())
      if (GRIMY_HERBS.contains(item.getId())) count += item.getQuantity();
    if (grimyCount >= 0 && count > grimyCount) {
      grimyGained = true;
      plugin.debug().event("herb picked: " + grimyCount + " -> " + count);
      herbPicked(count - grimyCount);
    }
    grimyCount = count;
    // Celastrus bark gained while guided at the Celastrus patch counts towards real profit.
    int bark = 0;
    for (Item item : inventory.getItems())
      if (item.getId() == ItemID.CELASTRUS_WOOD) bark += item.getQuantity();
    Guidance step = plugin.guide();
    if (barkCount >= 0 && bark > barkCount && step != null && step.kind.equals("celastrus")) {
      int gained = bark - barkCount;
      plugin.debug().event("bark chopped: +" + gained);
      plugin.toPanel(panel -> panel.barkChopped(gained));
    }
    barkCount = bark;
  }

  /**
   * A herb picked into an open herb sack never reaches the inventory, so it's spotted as Farming XP
   * with no new grimy herb while the guided herb patch offers Pick (this tick or last, as the last
   * herb empties it). It counts as picked, so the run timer starts and the herb is recorded. A herb
   * reaching the inventory with the sack open means the sack is full.
   */
  void countSackHerbs() {
    boolean xp = xpGained;
    boolean grimy = grimyGained;
    xpGained = false;
    grimyGained = false;
    boolean pickable = plugin.scene().offers(plugin.patch().object(), "pick");
    boolean was = wasPickable;
    wasPickable = pickable;
    Guidance step = plugin.guide();
    if (!xp || !(pickable || was) || step == null || !step.kind.equals("herb") || !holdingOpenSack())
      return;
    if (grimy) {
      if (sackHerbs < HERB_SACK_SIZE) plugin.debug().event("herb sack full");
      sackHerbs = HERB_SACK_SIZE;
      return;
    }
    if (sackHerbs < HERB_SACK_SIZE) sackHerbs++;
    plugin.debug().event("herb into sack: " + sackHerbs + "/" + HERB_SACK_SIZE);
    herbPicked(1);
  }

  /** Room left in an open herb sack carried in the inventory (0 without one). */
  int sackRoom() {
    return holdingOpenSack() ? Math.max(0, HERB_SACK_SIZE - sackHerbs) : 0;
  }

  /**
   * At a herb patch offering Pick with the herb sack carried shut (none open) and not yet full:
   * open it first, so the herbs go in it instead of the inventory.
   */
  boolean openSackNow() {
    Guidance step = plugin.guide();
    return step != null
        && step.kind.equals("herb")
        && step.phase.equals("Harvest")
        && wasPickable
        && !plugin.patch().dead()
        && sackHerbs < HERB_SACK_SIZE
        && plugin.account().holdingUnnoted(SHUT_HERB_SACKS)
        && !holdingOpenSack();
  }

  /** Picking a herb at a route patch starts a herb run's timer. */
  private void herbPicked(int picked) {
    Player player = client.getLocalPlayer();
    if (player == null) return;
    Set<String> nearby = new HashSet<>();
    int region = player.getWorldLocation().getRegionID();
    for (Map.Entry<String, Set<Integer>> entry : TargetData.HERB_REGIONS.entrySet())
      if (entry.getValue().contains(region)) nearby.add(entry.getKey());
    if (nearby.isEmpty()) return;
    // Picking has started here: from now on, noting is only suggested if the inventory fills.
    Guidance step = plugin.guide();
    if (step != null && nearby.contains(step.patchId)) {
      if (!step.patchId.equals(pickingPatch)) pickedHere = 0;
      pickingPatch = step.patchId;
      plugin.progress().setWorkedPatch(step.patchId);
      pickedHere += picked;
    }
    plugin.toPanel(panel -> panel.herbPicked(nearby, picked));
  }
}
