package com.yonwi.farming;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;

/**
 * When to note produce at a tool leprechaun: before leaving a herb patch, on the way in, or when
 * the inventory fills while picking.
 */
final class NotingAdvice {
  private static final int INVENTORY_SLOTS = 28;
  /** Room to keep beyond a patch's expected herbs before noting is suggested. */
  private static final int SPARE_SLOTS = 2;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  private volatile String advice;
  /** Picking herbs that will likely fill the inventory: herbs and leprechaun are next. */
  private volatile boolean soon;
  /** The patch whose leprechaun you've been told to note at on the way in, or null. */
  private String onWayInPatch;
  /** Noting costs per pair of stages ("from>to"): {here, detour at the next patch}, in tiles. */
  private final Map<String, int[]> costs = new HashMap<>();
  /** Debug: noting advice last logged, and the stage last logged as started. */
  private String lastNoteLog;
  private String lastStageLog;

  /** Shown when produce should be noted now, with the leprechaun and produce highlighted. */
  String advice() {
    return advice;
  }

  boolean soon() {
    return soon;
  }

  /** Works out this tick's advice. */
  void update(Guidance step, Player player, TileObject patch, NPC leprechaun) {
    soon = false;
    advice = advise(step, player, patch, leprechaun);
  }

  /** The unnoted produce to note at the leprechaun on this step: grimy herbs, fruit or bark. */
  Set<Integer> produce() {
    Guidance step = plugin.guide();
    if (step == null || step.kind.equals("herb")) return HarvestTracker.GRIMY_HERBS;
    return step.produce.isEmpty() ? Collections.emptySet() : FarmingData.itemIds(step.produce);
  }

  private String advise(Guidance step, Player player, TileObject patch, NPC leprechaun) {
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    if (inventory == null) return null;
    // Herbs, fruit, Calquat or Celastrus bark picked here and not yet noted.
    Set<Integer> produce = produce();
    int used = 0;
    int unnoted = 0;
    for (Item item : inventory.getItems())
      if (item.getId() > 0) {
        used++;
        if (produce.contains(item.getId())) unnoted++;
      }
    if (unnoted == 0) return null;
    int free = INVENTORY_SLOTS - used;
    boolean herbs = step.kind.equals("herb");
    // Herbs fill an open herb sack before the inventory, so its room counts too.
    int sackRoom = herbs ? plugin.harvest().sackRoom() : 0;
    Room room = new Room(free, sackRoom);
    if (herbs && noteBeforeLeaving(step, player, leprechaun, room))
      return "Note your herbs here: " + room + ", the next patch gives about " + step.herbsIfAlive;
    String onWayIn = noteOnWayIn(step, player, leprechaun, room);
    if (onWayIn != null) return onWayIn;
    return noteWhenFull(step, player, patch, leprechaun, room);
  }

  /** Free inventory slots and herb sack room, e.g. "5 free slots + 12 in the herb sack". */
  private static final class Room {
    final int free;
    final int sack;

    Room(int free, int sack) {
      this.free = free;
      this.sack = sack;
    }

    int total() {
      return free + sack;
    }

    @Override
    public String toString() {
      return free + " free slots" + (sack > 0 ? " + " + sack + " in the herb sack" : "");
    }
  }

  /**
   * Leaving a herb patch for another: if the next one's herbs won't fit with 2 to spare, note at
   * this patch's leprechaun first (e.g. Catherby before Troll Stronghold).
   */
  private boolean noteBeforeLeaving(Guidance step, Player player, NPC leprechaun, Room room) {
    return step.phase.equals("Travel")
        && "herb".equals(step.prevKind)
        && leprechaun != null
        && !client.getTopLevelWorldView().isInstance()
        && StepProgress.inPatchArea(step.prevPatchId, player.getWorldLocation())
        && room.total() < step.herbsIfAlive + SPARE_SLOTS
        && noteHereShorter(step.prevPatchId, step.patchId, step.travel);
  }

  /**
   * On the way in, when this patch's herbs won't fit and the leprechaun comes before the patch
   * (e.g. Falador): note as you pass him. Once advised it stays until you've noted or started
   * picking. When he's past the patch (e.g. Catherby), pick until full instead.
   */
  private String noteOnWayIn(Guidance step, Player player, NPC leprechaun, Room room) {
    WorldPoint at = TargetData.PATCH_POINTS.get(step.patchId);
    WorldPoint here = player.getWorldLocation();
    boolean approaching =
        step.kind.equals("herb")
            && (step.phase.equals("Travel") || step.phase.equals("Harvest"))
            && !step.patchId.equals(plugin.harvest().pickingPatch())
            // The same rule as noting before leaving: room for this patch's herbs if it's alive,
            // with 2 to spare.
            && room.total() < step.herbsIfAlive + SPARE_SLOTS
            && at != null
            && at.getPlane() == here.getPlane()
            // In the patch's own area (RuneLite's farming regions for it), not just anywhere.
            && TargetData.HERB_REGIONS
                .getOrDefault(step.patchId, Collections.emptySet())
                .contains(here.getRegionID());
    // Where he stands: NPCs only show within about 15 tiles, so until then his known spot.
    WorldPoint leprechaunAt =
        leprechaun != null
            ? leprechaun.getWorldLocation()
            : at == null ? null : TargetData.nearestLeprechaun(at);
    String text = "Note your herbs on the way: " + room + ", this patch gives about " + step.herbsIfAlive;
    if (!approaching) onWayInPatch = null;
    else if (step.patchId.equals(onWayInPatch)) return text;
    else if (leprechaunAt != null && leprechaunAt.distanceTo(here) < at.distanceTo(here)) {
      onWayInPatch = step.patchId;
      return text;
    }
    return null;
  }

  /**
   * Otherwise noting only happens when the inventory fills up while picking: pick until full, note
   * at that patch's leprechaun, then finish. Sets {@link #soon} when that's likely.
   */
  private String noteWhenFull(Guidance step, Player player, TileObject patch, NPC leprechaun, Room room) {
    if (!plugin.progress().arrived(step.patchId, player)) return null;
    boolean herbs = step.kind.equals("herb");
    boolean harvesting = step.phase.equals("Harvest");
    boolean stillToPick = stillToPick(patch);
    // Herbs likely to fill the inventory before the patch is done: the herbs and the leprechaun
    // are next (orange), so noting is ready before it's full.
    int left = Math.max(1, step.herbsIfAlive - plugin.harvest().pickedAt(step.patchId));
    soon = herbs && harvesting && stillToPick && room.total() > 0 && room.total() < left;
    // Composting, the last step here, with a herb patch next: if you'll have to note before it
    // and this leprechaun is the shorter walk, the herbs and him are next, not the teleport (it
    // turns into "note here" once compost is done).
    soon |=
        herbs
            && step.phase.equals("Compost")
            && "herb".equals(step.nextKind)
            && leprechaun != null
            && room.total() < step.nextHerbsIfAlive + SPARE_SLOTS
            && noteHereShorter(step.patchId, step.nextPatchId, step.nextTravel);
    // Full with more still to harvest (any crop): note at this patch's leprechaun, then finish.
    // Herbs still go into an open herb sack with room, however full the inventory is.
    if (harvesting && stillToPick && room.total() == 0)
      return "Inventory full: note your "
          + (herbs ? "herbs" : step.produce.toLowerCase(Locale.ROOT))
          + " at this leprechaun, then finish "
          + (step.kind.equals("celastrus") ? "harvesting" : "picking");
    return null;
  }

  private boolean stillToPick(TileObject patch) {
    if (patch == null) return false;
    ObjectComposition definition = plugin.scene().definition(patch);
    if (definition == null) return false;
    boolean stillToPick = false;
    for (String action : definition.getActions())
      stillToPick |=
          action != null
              && (action.toLowerCase(Locale.ROOT).startsWith("pick")
                  || action.toLowerCase(Locale.ROOT).startsWith("harvest"));
    return stillToPick;
  }

  /**
   * Whether noting at the previous herb patch's leprechaun is a shorter walk than noting at this
   * stage's: here it's patch to leprechaun (you teleport from there); there it's the detour from
   * where its teleport lands, via its leprechaun, to the patch (about 0 when he's on the way in,
   * as at Falador). True when either isn't known.
   */
  private boolean noteHereShorter(String fromPatch, String toPatch, String toTravel) {
    if (fromPatch == null || toPatch == null || toTravel == null) return true;
    String key = fromPatch + ">" + toPatch;
    int[] known = costs.get(key);
    if (known == null) {
      WorldPoint previous = TargetData.HERB_POINTS.get(fromPatch);
      WorldPoint next = TargetData.HERB_POINTS.get(toPatch);
      if (previous == null || next == null) return true;
      WorldPoint leprechaunHere = TargetData.nearestLeprechaun(previous);
      WorldPoint leprechaunNext = TargetData.nearestLeprechaun(next);
      if (leprechaunHere == null || leprechaunNext == null) return true;
      WorldPoint landing = likelyLanding(TeleportData.landingsFor(toTravel), next);
      int here = walkTiles(previous, leprechaunHere);
      int detour =
          Math.max(
              0,
              walkTiles(landing, leprechaunNext) + walkTiles(leprechaunNext, next) - walkTiles(landing, next));
      known = new int[] {here, detour};
      // Only kept once the map is loaded, so a straight-line guess isn't stuck.
      if (WorldPath.ready()) {
        costs.put(key, known);
        plugin.debug().log(
            "note costs " + key + ": here " + here + " tiles, detour at the next patch " + detour
                + " tiles (landing " + landing.getX() + "," + landing.getY() + ")");
      }
    }
    return known[0] < known[1];
  }

  /** Tiles to walk between two spots along real paths, or straight-line tiles without a route. */
  private static int walkTiles(WorldPoint from, WorldPoint to) {
    if (from.getPlane() != to.getPlane()) return 1000;
    if (from.distanceTo(to) <= 1) return 0;
    List<WorldPoint> route = WorldPath.find(from, to);
    return route.isEmpty() ? from.distanceTo(to) : route.size();
  }

  /**
   * Where a route's teleport most likely lands: the spot its matching teleport rows name most
   * often (a ring's or spell's several rows agree), nearest the patch on a tie; the patch itself
   * when none is within 100 tiles.
   */
  static WorldPoint likelyLanding(List<WorldPoint> landings, WorldPoint patch) {
    Map<WorldPoint, Integer> count = new HashMap<>();
    for (WorldPoint landing : landings)
      if (landing.getPlane() == patch.getPlane() && landing.distanceTo(patch) < 100)
        count.merge(landing, 1, Integer::sum);
    WorldPoint best = patch;
    int most = 0;
    for (Map.Entry<WorldPoint, Integer> entry : count.entrySet())
      if (entry.getValue() > most
          || entry.getValue() == most && entry.getKey().distanceTo(patch) < best.distanceTo(patch)) {
        best = entry.getKey();
        most = entry.getValue();
      }
    return best;
  }

  /** Debug: a line when a stage starts, and when noting advice turns on, off or changes. */
  void log(Guidance step, NPC leprechaun) {
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    int used = 0;
    int unnoted = 0;
    Set<Integer> produce = produce();
    if (inventory != null)
      for (Item item : inventory.getItems())
        if (item.getId() > 0) {
          used++;
          if (produce.contains(item.getId())) unnoted++;
        }
    int free = INVENTORY_SLOTS - used;
    if (!step.patchId.equals(lastStageLog)) {
      lastStageLog = step.patchId;
      plugin.debug().log(
          "stage: " + step.patchId + " (" + step.kind + ") · " + step.travel + " · steps " + step.phases
              + " · " + free + " free slots · expected herbs " + step.herbsIfAlive
              + " (with deaths " + step.herbsPerPatch + ")");
    }
    String state = advice + "|" + soon;
    if (state.equals(lastNoteLog)) return;
    lastNoteLog = state;
    plugin.debug().log(
        "noting: " + (advice != null ? "advice '" + advice + "'" : soon ? "soon (orange)" : "off")
            + " · " + step.phase + " · free " + free + " · unnoted produce " + unnoted
            + " · expected " + step.herbsIfAlive + " · picked here " + plugin.harvest().pickedAt(step.patchId)
            + " · previous stage " + step.prevPatchId + " (" + step.prevKind + ") · leprechaun "
            + (leprechaun == null
                ? "none"
                : leprechaun.getWorldLocation().getX() + "," + leprechaun.getWorldLocation().getY()));
  }
}
