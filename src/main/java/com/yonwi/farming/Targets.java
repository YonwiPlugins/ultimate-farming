package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

/**
 * What to click now (blue) and next (orange) in the game world: the patch, its gardener, the
 * leprechaun, or the transport on the way there. Also the items to use on them.
 */
final class Targets {
  /** Tree types whose gardener clears the old tree for a fee. */
  static final Set<String> GARDENER_REMOVAL =
      new HashSet<>(Arrays.asList("tree", "fruit", "redwood", "hardwood"));
  /** Corsair Cove's rowboat to Anglers' Retreat. */
  private static final int CORSAIR_ROWBOAT = 58637;
  /** How far the leprechaun can be from the patch to count as its own. */
  private static final int LEPRECHAUN_RANGE = 33;
  /** An item used on the step's target stays "selected" for this many ticks. */
  private static final int USED_ITEM_TICKS = 10;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  private List<TileObject> objects = Collections.emptyList();
  private List<NPC> npcs = Collections.emptyList();
  /** Targets for the substep after the current one, drawn in orange. */
  private List<TileObject> nextObjects = Collections.emptyList();
  private List<NPC> nextNpcs = Collections.emptyList();
  /** The charter ship's crew in view on a charter leg, to highlight them all. */
  private List<NPC> charterCrew = Collections.emptyList();
  /** Set once you squeeze through the Tree Gnome Village railing on this patch's trip. */
  private boolean railingDone;
  /** Set once you've clicked a Magic Mushtree on this patch's trip (Fossil Island). */
  private boolean mushtreeUsed;
  /** The patch whose trip the railing and mushtree flags are for. */
  private String routePatch;
  /** When an item was last used on an object or NPC, and on which step. */
  private int usedOnTick = -100;
  private String usedOnStep;
  /** The item last used on an object or NPC, or -1 if not known. */
  private int usedItem = -1;

  List<TileObject> objects() {
    return objects;
  }

  List<NPC> npcs() {
    return npcs;
  }

  List<TileObject> nextObjects() {
    return nextObjects;
  }

  List<NPC> nextNpcs() {
    return nextNpcs;
  }

  void clear() {
    objects = Collections.emptyList();
    npcs = Collections.emptyList();
  }

  /** Each tick (and when the step changes): finds the patch, then what to click now and next. */
  void refresh() {
    objects = Collections.emptyList();
    npcs = Collections.emptyList();
    nextObjects = Collections.emptyList();
    nextNpcs = Collections.emptyList();
    Guidance step = plugin.guide();
    Player player = client.getLocalPlayer();
    plugin.patch().find(step, player);
    if (step == null || player == null) return;
    TileObject patch = plugin.patch().object();
    NPC gardener = plugin.patch().gardener();
    // The leprechaun nearest the patch, or failing that the gardener or the player.
    WorldPoint anchor =
        patch != null
            ? patch.getWorldLocation()
            : gardener != null ? gardener.getWorldLocation() : player.getWorldLocation();
    NPC leprechaun = nearestLeprechaun(anchor);
    // Back in the house partway through a stage that came through it: the way back out again.
    boolean houseReturn = plugin.progress().houseReturn();
    Object current =
        step.phase.equals("Travel")
            ? transportObject(step, player)
            : houseReturn ? houseObject(step, player) : target(step, step.phase, gardener, patch, leprechaun);
    Object next =
        step.nextPhase == null || houseReturn
            ? null
            : target(step, step.nextPhase, gardener, patch, leprechaun);
    showCurrent(step, current, patch, player);
    // The next step's target is only shown in orange when it isn't already the current one.
    if (next != current && next instanceof TileObject)
      nextObjects = patchTiles(step, (TileObject) next, patch, player);
    if (next != current && next instanceof NPC) nextNpcs = Collections.singletonList((NPC) next);
    plugin.noting().update(step, player, patch, leprechaun);
    if (plugin.config().debug()) plugin.noting().log(step, leprechaun);
    focusOnNoting(leprechaun);
    plugin.route().update(step, player);
  }

  private NPC nearestLeprechaun(WorldPoint anchor) {
    if (anchor == null) return null;
    NPC leprechaun = null;
    int closest = LEPRECHAUN_RANGE;
    for (NPC npc : plugin.scene().npcs())
      if (npc.getName() != null && npc.getName().toLowerCase(Locale.ROOT).contains("leprechaun")) {
        int distance = npc.getWorldLocation().distanceTo(anchor);
        if (distance < closest) {
          leprechaun = npc;
          closest = distance;
        }
      }
    return leprechaun;
  }

  private void showCurrent(Guidance step, Object current, TileObject patch, Player player) {
    if (current instanceof TileObject) objects = patchTiles(step, (TileObject) current, patch, player);
    if (current instanceof NPC) npcs = Collections.singletonList((NPC) current);
    // Every crewmember of the charter ship, not just the nearest.
    if (current instanceof NPC && charterCrew.contains(current)) {
      List<NPC> crew = new ArrayList<>(charterCrew);
      crew.remove(current);
      crew.add(0, (NPC) current);
      npcs = crew;
    }
  }

  /** When noting is advised or coming up, the leprechaun is what to click. */
  private void focusOnNoting(NPC leprechaun) {
    String advice = plugin.noting().advice();
    if (advice == null && plugin.noting().soon() && leprechaun != null) {
      nextNpcs = Collections.singletonList(leprechaun);
      nextObjects = Collections.emptyList();
    }
    // Noting comes first: just the leprechaun (herbs, then him), nothing else lit up.
    if (advice != null && leprechaun != null) {
      npcs = Collections.singletonList(leprechaun);
      objects = Collections.emptyList();
      nextObjects = Collections.emptyList();
      nextNpcs = Collections.emptyList();
    }
    // Noting advised before he's in view: nothing at the patch is lit; the line leads to his spot.
    if (advice != null && leprechaun == null) {
      objects = Collections.emptyList();
      nextObjects = Collections.emptyList();
      nextNpcs = Collections.emptyList();
    }
  }

  /** What to click for a substep: the patch, its gardener or the nearby tool leprechaun. */
  private Object target(Guidance step, String phase, NPC gardener, TileObject patch, NPC leprechaun) {
    if (phase.equals("Travel")) return null;
    if (phase.startsWith("Note")) return leprechaun;
    if (!step.kind.equals("herb") && paysGardener(step, phase)) return gardener;
    return patch;
  }

  /** The gardener is paid on this step: to protect the crop, or to remove a healthy tree. */
  boolean paysGardener(Guidance step, String phase) {
    return phase.equals("Protect")
        || phase.equals("Clear") && GARDENER_REMOVAL.contains(step.kind) && !plugin.patch().dead();
  }

  /**
   * The target to highlight: just it, or, when it's a patch made of several tiles (the Farming
   * Guild redwood), every one of its tiles, the nearest to you first so the path goes there.
   */
  private List<TileObject> patchTiles(Guidance step, TileObject target, TileObject patch, Player player) {
    int[] area = TargetData.PATCH_AREAS.get(step.patchId);
    if (area == null || target != patch) return Collections.singletonList(target);
    List<TileObject> tiles = new ArrayList<>();
    for (TileObject object : plugin.scene().objects()) {
      WorldPoint at = object.getWorldLocation();
      if (at.getPlane() != patch.getPlane()
          || at.getX() < area[0] || at.getX() > area[2] || at.getY() < area[1] || at.getY() > area[3])
        continue;
      ObjectComposition definition = plugin.scene().definition(object);
      if (definition != null && TargetData.patchLike(definition, step.kind)) tiles.add(object);
    }
    if (!tiles.contains(target)) tiles.add(target);
    WorldPoint you = player.getWorldLocation();
    tiles.sort(Comparator.comparingInt(tile -> tile.getWorldLocation().distanceTo(you)));
    return tiles;
  }

  private Object transportObject(Guidance step, Player player) {
    String travel = step.travel.toLowerCase(Locale.ROOT);
    boolean house = client.getTopLevelWorldView().isInstance();
    // After the quetzal lands at Quetzacalli Gorge: the Mountain Guide takes you to Auburn Valley.
    // (The whistle isn't a ring or tree, so no jump is waited for: the guide is only ever there.)
    if (travel.contains("mountain guide")) return nearestNpcNamed(player, "mountain guide");
    // Walked out of the house mid-trip: its outside portal (within 10 tiles) leads back in.
    plugin.progress().setOutsidePortalShown(false);
    if (!house && step.patchId.equals(plugin.progress().leftHousePatch()) && !plugin.progress().jumped()) {
      TileObject portal = plugin.scene().nearestObject(player, 10, "portal");
      if (portal != null) {
        plugin.progress().setOutsidePortalShown(true);
        return portal;
      }
    }
    charterCrew = Collections.emptyList();
    if (travel.contains("charter") && !house && plugin.progress().chartered())
      return afterCharter(travel, player);
    // A charter leg (e.g. Catherby to Corsair Cove): the ship's Trader Crewmembers on the dock.
    if (travel.contains("charter") && !house) {
      NPC crew = charterCrew(player);
      if (crew != null) return crew;
    }
    // Fossil Island: after the pendant, the Magic Mushtree by the House on the Hill takes you on
    // to Mushroom Meadow. Once it's been used, the Meadow's own mushtree isn't lit up.
    if (travel.contains("mushtree") && !house) {
      if (mushtreeUsed) return null;
      TileObject mushtree = plugin.scene().nearestObject(player, 40, "magic mushtree");
      if (mushtree != null) return mushtree;
    }
    // Already through the fairy ring or spirit tree: Tree Gnome Village's railing and Elkoy are
    // still to come, but nothing else is.
    boolean jumped = plugin.progress().jumped();
    if (jumped && !travel.contains("elkoy")) return null;
    // Tree Gnome Village: squeeze through the loose railing, then follow Elkoy through the maze.
    if (travel.contains("elkoy") && !house) {
      NPC elkoy = null;
      for (NPC npc : plugin.scene().npcs())
        if (npc.getName() != null && npc.getName().equalsIgnoreCase("elkoy")) elkoy = npc;
      TileObject railing = plugin.scene().nearestObject(player, 30, "railing");
      if (railing != null && !railingDone) return railing;
      if (elkoy != null) return elkoy;
      // Already through the spirit tree: never fall back to highlighting the village's own tree.
      if (jumped) return null;
    }
    List<String> names = routeObjects(step.travel, house);
    if (names.isEmpty()) return null;
    return plugin.scene().nearestObject(player, 40, names.toArray(new String[0]));
  }

  private NPC nearestNpcNamed(Player player, String name) {
    NPC nearest = null;
    int closest = Integer.MAX_VALUE;
    for (NPC npc : plugin.scene().npcs()) {
      if (npc.getName() == null || !npc.getName().equalsIgnoreCase(name)) continue;
      int distance = npc.getWorldLocation().distanceTo(player.getWorldLocation());
      if (distance < closest) {
        nearest = npc;
        closest = distance;
      }
    }
    return nearest;
  }

  /**
   * After the charter: on the ship, its gangplank; on the dock, the rowboat on (Corsair Cove's to
   * Anglers' Retreat, by ID: the one back from there is another).
   */
  private TileObject afterCharter(String travel, Player player) {
    if (player.getWorldLocation().getPlane() > 0)
      return plugin.scene().nearestObject(player, 10, "gangplank");
    if (!travel.contains("rowboat")) return null;
    TileObject boat = null;
    int closest = 60;
    for (TileObject object : plugin.scene().objects()) {
      if (object.getId() != CORSAIR_ROWBOAT) continue;
      int distance = object.getWorldLocation().distanceTo(player.getWorldLocation());
      if (distance < closest) {
        boat = object;
        closest = distance;
      }
    }
    return boat;
  }

  /** All the Trader Crewmembers within 25 tiles (all are highlighted); returns the nearest. */
  private NPC charterCrew(Player player) {
    NPC nearest = null;
    int closest = 25;
    List<NPC> all = new ArrayList<>();
    for (NPC npc : plugin.scene().npcs()) {
      if (npc.getName() == null || !npc.getName().equalsIgnoreCase("trader crewmember")) continue;
      int distance = npc.getWorldLocation().distanceTo(player.getWorldLocation());
      if (distance >= 25) continue;
      all.add(npc);
      if (distance < closest) {
        nearest = npc;
        closest = distance;
      }
    }
    charterCrew = all;
    return nearest;
  }

  /** Whether a route goes through your house. */
  static boolean throughHouse(String travel) {
    return travel.startsWith("house ") || travel.startsWith("poh ") || travel.contains("your house");
  }

  /** The fairy ring, spirit tree, jewellery box or portal (nexus) this route uses. */
  private static List<String> routeObjects(String route, boolean house) {
    String travel = route.toLowerCase(Locale.ROOT);
    List<String> names = new ArrayList<>();
    // A route through the house uses the house's own ring or tree: nothing outside it.
    if (!house && (travel.startsWith("house ") || travel.startsWith("poh "))) return names;
    if (travel.contains("fairy ring")) names.addAll(Arrays.asList("fairy ring", "spiritual fairy tree"));
    if (travel.contains("spirit tree")) names.addAll(Arrays.asList("spirit tree", "spiritual fairy tree"));
    // In the house: the jewellery box or the portal (nexus) the route uses.
    if (house && travel.contains("jewellery box")) names.add("jewellery box");
    // The nexus or the destination's own portal (e.g. "Ardougne portal"), never the plain exit
    // "Portal" by the house entrance.
    if (house && travel.contains("portal")) {
      names.add("nexus");
      String place = FarmingInventoryOverlay.destination(route);
      if (place.length() > 3) names.add(place);
    }
    return names;
  }

  /** The way back out of the house for this stage: see {@link StepProgress#houseReturn()}. */
  private TileObject houseObject(Guidance step, Player player) {
    List<String> names = routeObjects(step.travel, true);
    return names.isEmpty() ? null : plugin.scene().nearestObject(player, 40, names.toArray(new String[0]));
  }

  /** A menu option was clicked: notes the railing and mushtree used on this patch's trip. */
  void routeClicked(Guidance step, String option, String target) {
    if (!step.patchId.equals(routePatch)) {
      routePatch = step.patchId;
      railingDone = false;
      mushtreeUsed = false;
    }
    if (target.contains("mushtree")) mushtreeUsed = true;
    if (option.equals("squeeze-through") && target.contains("railing")) railingDone = true;
  }

  /** An item was used on an object or NPC (seed, sapling, compost, herbs on the leprechaun). */
  void itemUsed(Guidance step) {
    usedOnTick = client.getTickCount();
    usedOnStep = step.patchId + step.phase;
    Widget held = client.getSelectedWidget();
    usedItem = held == null || held.getItemId() <= 0 ? -1 : held.getItemId();
    plugin.debug().log("used item " + usedItem + " on step " + usedOnStep);
  }

  /** Whether the player has one of these items selected, ready to use on a patch. */
  boolean selectedItem(Set<Integer> ids) {
    // Just used on the patch (or NPC) for this step: it stays "selected" while you walk over, so
    // it doesn't light up again until the step finishes (or ~6 seconds pass with nothing).
    Guidance step = plugin.guide();
    // Only the step's own item (seed, sapling, compost): herbs used on a leprechaun mustn't hide
    // the next teleport. (Unknown item: as before.)
    if (step != null
        && (step.patchId + step.phase).equals(usedOnStep)
        && client.getTickCount() - usedOnTick <= USED_ITEM_TICKS
        && (usedItem < 0 || ids.contains(usedItem))) return true;
    if (!client.isWidgetSelected()) return false;
    Widget selected = client.getSelectedWidget();
    return selected != null && ids.contains(selected.getItemId());
  }

  /** Item IDs (all charge and tier variants) used by the guided action. */
  Set<Integer> itemIds(Guidance step) {
    // Travelling: never the coins (a boat fare is paid in the chat), so a spell route shows the
    // magic tab and spell rather than your coin stack.
    if (step.phase.equals("Travel")) {
      Set<Integer> ids = new HashSet<>(usableIds(step.items));
      ids.removeAll(FarmingData.itemIds("Coins"));
      return ids;
    }
    // Paying the gardener: just him, never the coins or the payment.
    if (step.phase.equals("Protect")) return Collections.emptySet();
    // Clearing a tree yourself: nothing to highlight in the inventory. Chop it down (the axe is
    // used from the inventory), then Clear on the stump or bare tree uses the spade by itself.
    if (step.phase.equals("Clear")) return Collections.emptySet();
    // Dead herbs: nothing to pick, so no secateurs; just Clear (or Resurrect Crops) on the patch.
    if (step.phase.equals("Harvest") && plugin.patch().dead()) return Collections.emptySet();
    return usableIds(step.items);
  }

  /** Item IDs to highlight, leaving the rake out unless the patch actually has weeds. */
  Set<Integer> usableIds(List<String> names) {
    List<String> wanted = new ArrayList<>(names);
    if (!plugin.patch().needsRake()) wanted.remove("Rake");
    return FarmingData.itemIds(wanted);
  }

  /** The fairy ring code to dial on this Travel step, or null. */
  String fairyCode() {
    Guidance step = plugin.guide();
    return step == null || !step.phase.equals("Travel") ? null : FarmingPlanner.fairyCode(step.travel);
  }

  /** Picking herbs right now, with the magic secateurs in the inventory instead of wielded. */
  boolean secateursAlert() {
    Guidance step = plugin.guide();
    return step != null
        && step.phase.equals("Harvest")
        && step.items.contains("Magic secateurs")
        && !plugin.patch().dead()
        && secateursToWield();
  }

  /** Harvesting is now or next, the secateurs are wanted, and they're in the inventory unworn. */
  private boolean secateursToWield() {
    Guidance step = plugin.guide();
    if (step == null) return false;
    boolean harvestSoon =
        step.phase.equals("Harvest") && step.items.contains("Magic secateurs")
            || "Harvest".equals(step.nextPhase) && step.nextItems.contains("Magic secateurs");
    return harvestSoon && plugin.account().holdingUnnoted(FarmingData.itemIds("Magic secateurs"));
  }
}
