package com.yonwi.farming;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Moves the guide on by itself each tick from the game's state: Travel ends on a teleport or on
 * arrival, patch steps from what the patch currently is, and noting once nothing is left unnoted.
 * Also keeps track of the trip to the patch (teleports, house, charter) that this depends on.
 */
final class StepProgress {
  /** A move of more tiles than this in one tick is a teleport. */
  private static final int TELEPORT_TILES = 30;
  /** A teleport this many ticks after using the step's transport is that transport's. */
  private static final int TRANSPORT_TICKS = 20;
  /** A teleport landing within this many tiles of a known landing spot counts. */
  private static final int LANDING_TILES = 10;
  /** The game only sends a patch's state within its area; 10 tiles is inside every one. */
  private static final int PATCH_READ_TILES = 10;
  /** A teleport click lights the teleport down for this many ticks. */
  private static final int TELEPORT_CLICK_TICKS = 8;

  private static final List<String> SECOND_LEGS =
      Arrays.asList(
          "follow", "boat", "charter", "rowboat", "barge", "mushtree", "quetzal to", "spirit tree",
          "fairy ring");

  /** Item options that aren't a teleport. */
  private static final Set<String> TELEPORT_NOT =
      new HashSet<>(
          Arrays.asList(
              "wear", "wield", "remove", "examine", "drop", "use", "check", "check-charges",
              "uncharge", "configure", "open", "empty", "fill", "destroy", "cancel", "walk here"));

  /** A quetzal map, spirit tree list, portal nexus or jewellery box. */
  private static final int[] DESTINATION_MENUS = {
    InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.QuetzalMenu.ICONS,
    InterfaceID.Menu.LJ_LAYER1, InterfaceID.Menu.LJ_LAYER2,
    InterfaceID.TelenexusTeleport.UNIVERSE, InterfaceID.PohJewelleryBox.UNIVERSE
  };

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  private WorldPoint lastLocation;
  /** Whether you were in the house last tick. */
  private boolean lastInHouse;
  /** Tick this step's transport was last used (item, spell, destination, nexus, ring…). */
  private int stepTransportAt = -100;
  /** Whether you've just used a fairy ring, spirit tree, portal or box. */
  private boolean usedTransport;
  /** Tick a teleport was clicked on this step, and which patch's step it was. */
  private int teleportClickedAt = -100;
  private String teleportClickedPatch;
  /** The patch whose trip you walked out of the house on (exit portal), so its outside portal leads back in. */
  private volatile String leftHousePatch;
  /** The outside house portal is what to click (you walked out mid-trip). */
  private volatile boolean outsidePortalShown;
  /** Set once this patch's trip has teleported out of the house or through the ring/tree. */
  private boolean jumped;
  private String jumpPatch;
  /** This patch's trip has teleported at least once (so walking to the patch is what's left). */
  private volatile boolean teleportedThisLeg;
  /** The stage whose charter has been sailed ("You pay … and sail to …"), or null. */
  private volatile String charteredPatch;
  /** The patch you've done something at on this stage (picked, raked, planted…), so no path. */
  private volatile String workedPatch;

  private String lastReading;
  private int readingTicks;
  /** Tick the last scene load (teleport, region change) happened. */
  private int loadedAtTick;

  /** The patch Resurrect Crops was last cast on, until its result shows. */
  private String resurrectCastOn;
  /** Patches Resurrect Crops has been tried on this run (a crop can only be revived once). */
  private final Set<String> resurrectTried = new HashSet<>();

  void sceneLoaded() {
    loadedAtTick = client.getTickCount();
  }

  /** Nothing guided: a new run may try Resurrect Crops again everywhere. */
  void clearRun() {
    resurrectTried.clear();
  }

  String workedPatch() {
    return workedPatch;
  }

  void setWorkedPatch(String patchId) {
    workedPatch = patchId;
  }

  String leftHousePatch() {
    return leftHousePatch;
  }

  boolean jumped() {
    return jumped;
  }

  boolean teleportedThisLeg() {
    return teleportedThisLeg;
  }

  void setOutsidePortalShown(boolean shown) {
    outsidePortalShown = shown;
  }

  /** The latest tick this stage's teleport or transport was used, or -1. */
  int lastTransportTick(Guidance step) {
    return Math.max(step.patchId.equals(teleportClickedPatch) ? teleportClickedAt : -1, stepTransportAt);
  }

  /** Each tick: moves the guide on when the current step is done. */
  void check() {
    Guidance step = plugin.guide();
    Player player = client.getLocalPlayer();
    if (step == null || player == null) return;
    WorldPoint here = player.getWorldLocation();
    WorldPoint before = lastLocation;
    lastLocation = here;
    if (step.phase.equals("Travel")) checkTravel(step, player, here, before);
    // Noting is done once no unnoted produce is left in the inventory.
    else if (step.phase.startsWith("Note")) {
      if (!plugin.account().holdingUnnoted(FarmingData.itemIds(step.items))) phaseDone(step);
    }
    // Every other step is read from what the patch is right now, not from what was clicked.
    else checkPatch(step, here);
  }

  private void checkTravel(Guidance step, Player player, WorldPoint here, WorldPoint before) {
    // A jump of many tiles in one tick is a teleport, so the teleport item is used up. Landing
    // in the house (an instance) doesn't count: the house portal is still to come.
    boolean inHouse = client.getTopLevelWorldView().isInstance();
    boolean wasInHouse = lastInHouse;
    lastInHouse = inHouse;
    if (inHouse && !wasInHouse) backInHouse();
    boolean teleported =
        before != null
            && (before.getPlane() != here.getPlane() || before.distanceTo(here) > TELEPORT_TILES)
            && !inHouse;
    boolean rightPlace = landedRight(step, here);
    if (teleported)
      plugin.debug().event((rightPlace ? "landed right: " : "landed elsewhere: ") + here.getX() + "," + here.getY());
    // Landed somewhere else: the teleport is to be used again, so it lights up straight away.
    if (teleported && !rightPlace) teleportClickedAt = -100;
    if (teleported && !rightPlace && wasInHouse) leftHousePatch = step.patchId;
    // A second leg after the teleport (guide, boat, quetzal...) means only arriving counts.
    if (!step.patchId.equals(jumpPatch)) {
      jumpPatch = step.patchId;
      jumped = false;
      teleportedThisLeg = false;
    }
    if (teleported && rightPlace) {
      teleportedThisLeg = true;
      leftHousePatch = null;
    }
    // After the teleport out (e.g. through the fairy ring), the ring or tree is no longer the
    // thing to click: the route to the patch is. Only a jump right after using a fairy ring,
    // spirit tree, portal or box counts: popping out to the bank and back mustn't lose the
    // house route.
    if (teleported && rightPlace && usedTransport) jumped = true;
    if (teleported) usedTransport = false;
    // A walk has nothing to click first: the patch is the step straight away (it was orange,
    // "next", the whole way). A guide's or boat's drop-off counts once it's in the patch's own
    // map square (Nemus Retreat), not only within 10 tiles.
    boolean walk = step.travel.toLowerCase(Locale.ROOT).startsWith("walk");
    if (walk
        || teleported && rightPlace && !multiLeg(step.travel)
        || teleported && inPatchArea(step.patchId, here)
        || arrived(step.patchId, player)) phaseDone(step);
  }

  /**
   * Arriving in the house uses up the teleport that got you there: the next jump to count has to
   * come from the nexus, ring, box or portal inside.
   */
  private void backInHouse() {
    stepTransportAt = -100;
    leftHousePatch = null;
    // Back in the house partway through the trip (e.g. for something forgotten): the route
    // starts over from here, so its nexus, box, ring or tree is the thing to use again.
    jumped = false;
    teleportedThisLeg = false;
    charteredPatch = null;
  }

  /**
   * A teleport only counts if it lands where this step's route goes: in the patch's own area, or
   * within 10 tiles of where its teleport lands (Shortest Path's teleport tables). That works
   * however the destination was picked (mouse or number key). Anything else (another destination,
   * item or spell, or walking out of the house) leaves the step as it was. For a route with no
   * known landing spot, it falls back to a teleport just after using its transport.
   */
  private boolean landedRight(Guidance step, WorldPoint here) {
    boolean justUsedTransport = client.getTickCount() - stepTransportAt <= TRANSPORT_TICKS;
    return inPatchArea(step.patchId, here)
        || (TeleportData.landingsFor(step.travel).isEmpty()
            ? justUsedTransport
            : TeleportData.landedFor(step.travel, here, LANDING_TILES));
  }

  private void checkPatch(Guidance step, WorldPoint here) {
    TileObject patch = plugin.patch().object();
    if (patch == null) return;
    // The game only sends a patch's state once you're in that patch's area. Until then it shows
    // the last patch sharing its varbit (every ordinary tree patch shares one): from the house
    // portal, Taverley showed Falador's new sapling. 10 tiles is inside every patch's area in
    // RuneLite's FarmingWorld; the map square isn't (part of Catherby's fruit tree square sends
    // the herb patch's state).
    if (patch.getWorldLocation().distanceTo2D(here) > PATCH_READ_TILES) return;
    ObjectComposition definition = plugin.scene().definition(patch);
    if (definition == null || definition.getName() == null) return;
    PatchLook look = new PatchLook(definition);
    plugin.patch().setDead(look.dead);
    if (checkResurrected(step, look)) return;
    boolean done = stepDone(step.phase, look);
    // Only trust a reading that holds for two ticks: just after a teleport the scene is still
    // loading and the wrong object, or a half-loaded one, can briefly be found.
    String reading = step.patchId + step.phase + patch.getWorldLocation() + definition.getId() + done;
    if (!reading.equals(lastReading))
      plugin.debug().log(
          "patch " + definition.getName() + " id " + definition.getId() + " at " + patch.getWorldLocation()
              + " actions " + Arrays.toString(definition.getActions()) + " · " + step.phase + " done " + done);
    readingTicks = reading.equals(lastReading) ? readingTicks + 1 : 0;
    lastReading = reading;
    // Straight away normally; two matching ticks just after a scene load.
    if (done && (readingTicks >= 1 || client.getTickCount() - loadedAtTick > 3)) phaseDone(step);
  }

  /** What a patch shows, from its name and right-click options. */
  private static final class PatchLook {
    /** Empty and weedy patches are named "... patch"; grown, growing and dead crops are not. */
    final boolean empty;
    final boolean pick;
    final boolean check;
    /** Dead crops (herbs or trees) have to be cleared by you; no gardener will do it. */
    final boolean clearDead;
    final boolean dead;

    PatchLook(ObjectComposition definition) {
      String name = definition.getName().toLowerCase(Locale.ROOT);
      boolean canPick = false;
      boolean canCheck = false;
      boolean canClear = false;
      for (String action : definition.getActions())
        if (action != null) {
          String option = action.toLowerCase(Locale.ROOT);
          canPick |= option.startsWith("pick") || option.startsWith("harvest");
          canCheck |= option.equals("check-health");
          canClear |= option.equals("clear");
        }
      empty = name.contains("patch");
      pick = canPick;
      check = canCheck;
      // Dead only by name ("Dead herbs", "Dead … tree"): a healthy grown Calquat also offers Clear.
      dead = name.contains("dead");
      clearDead = canClear && dead;
    }
  }

  /**
   * Resurrect Crops cast here: growing again means it worked and the patch is finished; a failed
   * cast clears the patch, and the normal plant and compost steps follow.
   */
  private boolean checkResurrected(Guidance step, PatchLook look) {
    if (step.patchId.equals(resurrectCastOn) && !look.dead && !look.empty && !look.pick && !look.check) {
      plugin.debug().event("resurrected: " + step.patchId);
      resurrectCastOn = null;
      String id = step.patchId;
      plugin.toPanel(panel -> panel.resurrected(id));
      return true;
    }
    if (step.patchId.equals(resurrectCastOn) && look.empty) resurrectCastOn = null;
    return false;
  }

  private static boolean stepDone(String phase, PatchLook look) {
    switch (phase) {
      case "Harvest":
        // Dead herbs must be cleared first; that is shown as "Clear the dead herbs".
        return !look.pick && !look.clearDead;
      case "Check health":
        // No Check-health left: checked, or nothing to check (empty, growing or dead). Any tree
        // counts, not just the crop being planted, so switching crop doesn't hold the guide here.
        return !look.check;
      case "Clear":
        return look.empty;
      case "Plant":
        // A crop is now growing: not empty, nothing to pick, check or clear.
        return !look.empty && !look.pick && !look.check && !look.clearDead;
      default:
        return false;
    }
  }

  private void phaseDone(Guidance step) {
    plugin.debug().event("step done: " + step.phase);
    if (!step.phase.equals("Travel")) workedPatch = step.patchId;
    plugin.toPanel(panel -> panel.phaseDone(step.patchId, step.phase));
  }

  /** Travel that needs another transport after the first teleport, e.g. "…; follow Mountain Guide". */
  static boolean multiLeg(String travel) {
    int arrow = travel.indexOf('→');
    String after = (arrow < 0 ? travel : travel.substring(arrow + 1)).toLowerCase(Locale.ROOT);
    for (String leg : SECOND_LEGS) if (after.contains(leg)) return true;
    return false;
  }

  /**
   * In the patch's own area: RuneLite's farming regions for a herb patch, or the 64×64 map region
   * holding a tree patch.
   */
  static boolean inPatchArea(String patchId, WorldPoint here) {
    Set<Integer> regions = TargetData.HERB_REGIONS.get(patchId);
    if (regions != null) return regions.contains(here.getRegionID());
    WorldPoint at = TargetData.PATCH_POINTS.get(patchId);
    return at != null && at.getPlane() == here.getPlane() && at.getRegionID() == here.getRegionID();
  }

  /** In the patch's herb region, or its gardener is anywhere in the loaded scene. */
  boolean arrived(String patchId, Player player) {
    // Known patch position: within 10 tiles, so a nearby bank (e.g. Falador east) isn't "there".
    // Tree Gnome Village allows 20: through the maze counts, not just its spirit tree.
    WorldPoint at = TargetData.PATCH_POINTS.get(patchId);
    int radius = patchId.equals("village") ? 20 : 10;
    if (at != null && !patchId.startsWith("herb"))
      return at.getPlane() == player.getWorldLocation().getPlane()
          && at.distanceTo(player.getWorldLocation()) <= radius;
    if (TargetData.HERB_REGIONS
        .getOrDefault(patchId, Collections.emptySet())
        .contains(player.getWorldLocation().getRegionID())) return true;
    int gardener = TargetData.GARDENERS.getOrDefault(patchId, -1);
    for (NPC npc : plugin.scene().npcs()) if (npc.getId() == gardener) return true;
    return false;
  }

  /**
   * The travel item for this step has done its job: just clicked, or you've already teleported on
   * this trip (e.g. landed from the whistle). Either way it's not to be highlighted again.
   */
  boolean travelItemDone() {
    Guidance step = plugin.guide();
    return step != null && step.patchId.equals(jumpPatch) && teleportedThisLeg
        || teleportClicked()
        || outsidePortalShown;
  }

  /** A teleport for the current Travel step was clicked in the last few ticks. */
  private boolean teleportClicked() {
    Guidance step = plugin.guide();
    return step != null
        && step.patchId.equals(teleportClickedPatch)
        && client.getTickCount() - teleportClickedAt <= TELEPORT_CLICK_TICKS;
  }

  /**
   * In the house partway through a stage whose route came through it (e.g. back for something
   * forgotten): its nexus, portal, box, ring or tree leads back out. The stage keeps its place.
   */
  boolean houseReturn() {
    Guidance step = plugin.guide();
    if (step == null || step.phase.equals("Travel")) return false;
    if (!client.getTopLevelWorldView().isInstance()) return false;
    return Targets.throughHouse(step.travel.toLowerCase(Locale.ROOT));
  }

  /** This stage's charter is done: its crew aren't lit any more, the gangplank and boat are. */
  boolean chartered() {
    Guidance step = plugin.guide();
    return step != null && step.patchId.equals(charteredPatch);
  }

  /**
   * "You pay 500 coins and sail to Corsair Cove.": this stage's charter is done, so its crew are
   * never lit again; the ship's gangplank and then the next leg are what's left.
   */
  void charterSailed() {
    Guidance step = plugin.guide();
    if (step != null && step.phase.equals("Travel")) {
      charteredPatch = step.patchId;
      plugin.debug().event("chartered: " + step.patchId);
    }
  }

  /** Cast Resurrect Crops now: on, dead herbs at the guided patch, not tried here yet. */
  boolean resurrectNow() {
    Guidance step = plugin.guide();
    return step != null
        && step.resurrect
        && step.kind.equals("herb")
        && step.phase.equals("Harvest")
        && plugin.patch().dead()
        && !resurrectTried.contains(step.patchId);
  }

  /** The game says the crop was already resurrected once. */
  void alreadyResurrected() {
    Guidance step = plugin.guide();
    if (step != null) resurrectTried.add(step.patchId);
  }

  /** A menu option was clicked on a guided step: notes the transport or teleport used. */
  void travelClicked(MenuOptionClicked e, Guidance step, String option, String target) {
    int tick = client.getTickCount();
    // Fairy rings, spirit trees, portals and boxes: the next big jump is the route's own.
    // (Not the house's plain exit "Portal".)
    if (target.contains("fairy") || target.contains("spirit tree")
        || target.contains("portal") && !target.equals("portal")
        || target.contains("jewellery box") || target.contains("nexus")) {
      usedTransport = true;
      stepTransportAt = tick;
    }
    if (!step.phase.equals("Travel")) return;
    // A choice in a chat dialogue while travelling, e.g. a ring of wealth's "Falador Park".
    if (e.getMenuAction() == MenuAction.WIDGET_CONTINUE) stepTransportAt = tick;
    // A teleport on this step's item (or a teleport spell) was clicked: it's under way.
    if (!TELEPORT_NOT.contains(option) && isStepTeleport(step, option, target)) {
      teleportClickedAt = tick;
      teleportClickedPatch = step.patchId;
      stepTransportAt = tick;
      // The run has set off: a used-up teleport (a scroll, a ring's last charge) mustn't send
      // the guide back to Bank first.
      plugin.toPanel(FarmingPanel::setOff);
    }
  }

  private boolean isStepTeleport(Guidance step, String option, String target) {
    boolean ours = option.equals("cast") && target.contains("teleport");
    // Choosing where to go on a quetzal map, spirit tree list, portal nexus or jewellery box.
    ours |= destinationMenuShowing();
    for (String item : step.items) ours |= target.startsWith(MenuSwaps.menuName(FarmingPlanner.normalize(item)));
    return ours;
  }

  /** A quetzal map, spirit tree list, portal nexus or jewellery box is open. */
  private boolean destinationMenuShowing() {
    for (int id : DESTINATION_MENUS) {
      Widget menu = client.getWidget(id);
      if (menu != null && !menu.isHidden()) return true;
    }
    return false;
  }

  /** Resurrect Crops was cast on this step's patch. */
  void resurrectCast(Guidance step) {
    resurrectCastOn = step.patchId;
    resurrectTried.add(step.patchId);
    plugin.debug().event("cast resurrect: " + step.patchId);
  }
}
