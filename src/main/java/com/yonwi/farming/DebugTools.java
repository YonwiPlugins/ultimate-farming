package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** With the debug setting on: the debug box's lines, and extra detail for client.log. */
final class DebugTools {
  /** Charter ports, to find them on the charter map and list whatever the route. */
  private static final List<String> PORTS =
      Arrays.asList(
          "corsair", "catherby", "port sarim", "brimhaven", "musa point", "port khazard",
          "port phasmatys", "shipyard", "port tyras", "prifddinas", "piscarilius", "anglers");
  /** Words in the names of boats, crews and ships worth logging. */
  private static final String[] BOAT_WORDS = {
    "boat", "row", "crew", "ship", "charter", "captain", "sail", "angler"
  };
  private static final int SCAN_TILES = 15;
  private static final int WIDGET_DEPTH = 12;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  /** Interfaces open at the last scan, to log each opening and closing. */
  private Set<Integer> openGroups = new HashSet<>();
  /** The last dialogue logged (NPC line, your line and options), to log each once. */
  private String lastDialogue;

  /** Per stage: one leg from its teleport click to standing next to its patch. */
  private String legPatch;
  private int legStart;
  private int legLastClick;
  private int legTeleport;
  private int legTransport;
  private int legTiles;
  private int legWalkTicks;
  private int legJumps;
  private boolean legDone;
  private WorldPoint legLast;

  /** What the plugin currently believes, for the debug box. Called on the client thread. */
  List<String> lines() {
    List<String> lines = new ArrayList<>();
    Guidance step = plugin.guide();
    Player player = client.getLocalPlayer();
    // Where you're standing, e.g. to map a new shortcut or patch.
    if (player != null) {
      WorldPoint at = player.getWorldLocation();
      lines.add("tile " + at.getX() + ", " + at.getY() + ", " + at.getPlane() + " · region " + at.getRegionID());
    }
    if (step == null) lines.add("No step (bank first or no route)");
    else addStepLines(lines, step, player);
    if (player != null) {
      WorldPoint at = player.getWorldLocation();
      lines.add(
          "you " + at.getX() + "," + at.getY() + "," + at.getPlane() + " region " + at.getRegionID()
              + (client.getTopLevelWorldView().isInstance() ? " (instance)" : ""));
    }
    addPatchLines(lines);
    if (plugin.patch().diseased())
      lines.add("diseased · " + (step == null ? "-" : step.diseasedHerbs + " → " + plugin.patch().diseasedAction(step)));
    NPC gardener = plugin.patch().gardener();
    boolean leprechaunLit = !plugin.targets().npcs().isEmpty() || !plugin.targets().nextNpcs().isEmpty();
    lines.add("gardener " + (gardener == null ? "none" : gardener.getId()) + " · lep " + (leprechaunLit ? "lit" : "-"));
    addQuetzalIcons(lines);
    String hint = plugin.debug().overlayHint();
    if (!hint.isEmpty()) lines.add("hint: " + hint);
    for (String event : plugin.debug().events()) lines.add("> " + event);
    return lines;
  }

  private void addStepLines(List<String> lines, Guidance step, Player player) {
    lines.add(
        step.patchId + " · " + step.phase + " (" + (step.phaseIndex + 1) + "/"
            + step.phases.size() + ") next " + step.nextPhase);
    lines.add("travel multi-leg " + StepProgress.multiLeg(step.travel) + " · rake " + plugin.patch().needsRake());
    if (player != null) lines.add("arrived " + plugin.progress().arrived(step.patchId, player));
    if (plugin.harvest().holdingOpenSack())
      lines.add(
          "herb sack " + plugin.harvest().sackHerbs() + "/" + HarvestTracker.HERB_SACK_SIZE
              + " (counted since the bank)");
    if (step.phase.startsWith("Note"))
      lines.add("unnoted held " + plugin.account().holdingUnnoted(FarmingData.itemIds(step.items)));
  }

  private void addPatchLines(List<String> lines) {
    TileObject patch = plugin.patch().object();
    if (patch == null) {
      lines.add("patch: not found");
      return;
    }
    ObjectComposition base = client.getObjectDefinition(patch.getId());
    ObjectComposition definition = base.getImpostorIds() != null ? base.getImpostor() : base;
    lines.add(
        "patch " + patch.getId() + " -> "
            + (definition == null ? "?" : definition.getId() + " '" + definition.getName() + "'"));
    if (definition != null) {
      List<String> actions = new ArrayList<>();
      for (String action : definition.getActions()) if (action != null) actions.add(action);
      lines.add("actions " + String.join("/", actions));
    }
  }

  /** While a quetzal map is open, its destination icons, so the highlight can be checked. */
  private void addQuetzalIcons(List<String> lines) {
    for (int id : new int[] {InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.QuetzalMenu.ICONS}) {
      Widget icons = client.getWidget(id);
      Widget[] children = icons == null || icons.isHidden() ? null : icons.getDynamicChildren();
      if (children == null) continue;
      List<String> names = new ArrayList<>();
      for (Widget icon : children)
        if (icon != null && icon.getName() != null && !icon.getName().isEmpty())
          names.add(Text.removeTags(icon.getName()));
      lines.add("quetzal icons: " + String.join(", ", names));
    }
  }

  /** Logs the dialogue on screen (NPC line, your line and options) once each time it changes. */
  void logDialogue() {
    StringBuilder line = new StringBuilder();
    for (int[] part :
        new int[][] {
          {InterfaceID.ChatLeft.NAME, InterfaceID.ChatLeft.TEXT},
          {InterfaceID.ChatRight.NAME, InterfaceID.ChatRight.TEXT}
        }) {
      Widget name = client.getWidget(part[0]);
      Widget text = client.getWidget(part[1]);
      if (text != null && !text.isHidden() && text.getText() != null)
        line.append(name == null ? "" : Text.removeTags(String.valueOf(name.getText())))
            .append(": ")
            .append(Text.removeTags(text.getText()))
            .append(' ');
    }
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (options != null && !options.isHidden() && options.getDynamicChildren() != null) {
      List<String> choices = new ArrayList<>();
      for (Widget option : options.getDynamicChildren())
        if (option != null && option.getText() != null && !option.getText().isEmpty())
          choices.add(Text.removeTags(option.getText()));
      if (!choices.isEmpty()) line.append("options ").append(choices);
    }
    String now = line.toString().trim();
    if (now.isEmpty() || now.equals(lastDialogue)) {
      if (now.isEmpty()) lastDialogue = null;
      return;
    }
    lastDialogue = now;
    plugin.debug().log("dialogue: " + now);
  }

  /** Any menu click: later transport (boat, charter, guide) is timed from the click that set it off. */
  void clicked() {
    legLastClick = client.getTickCount();
  }

  /**
   * Measures this stage's leg in ticks and tiles, logged once you're next to the patch: the
   * teleport (click to landing), any later transport (charter, boat, guide), and the tiles walked,
   * as running ticks (2 tiles a tick) and the ticks actually spent walking.
   */
  void trackLeg() {
    Guidance step = plugin.guide();
    Player player = client.getLocalPlayer();
    if (step == null || player == null) {
      legPatch = null;
      return;
    }
    int tick = client.getTickCount();
    WorldPoint here = player.getWorldLocation();
    if (!step.patchId.equals(legPatch)) {
      startLeg(step, tick, here);
      return;
    }
    if (legDone) return;
    // Before the first jump, the leg starts when this stage's teleport or transport is used:
    // walking about the bank beforehand doesn't count.
    int used = plugin.progress().lastTransportTick(step);
    if (legJumps == 0 && used > legStart) {
      legStart = used;
      legTiles = 0;
      legWalkTicks = 0;
    }
    WorldPoint before = legLast;
    legLast = here;
    if (before != null && !before.equals(here)) countMove(before, here, tick);
    TileObject patch = plugin.patch().object();
    if (patch == null || patch.getPlane() != here.getPlane() || patch.getWorldLocation().distanceTo(here) > 2)
      return;
    legDone = true;
    plugin.debug().log(
        "leg " + step.patchId + ": teleport " + legTeleport + " ticks · transport " + legTransport
            + " ticks · walked " + legTiles + " tiles (" + (legTiles + 1) / 2 + " ticks running, "
            + legWalkTicks + " actual) · total " + (tick - legStart) + " ticks");
  }

  private void startLeg(Guidance step, int tick, WorldPoint here) {
    legPatch = step.patchId;
    legStart = tick;
    legTeleport = 0;
    legTransport = 0;
    legTiles = 0;
    legWalkTicks = 0;
    legJumps = 0;
    legDone = false;
    legLast = here;
  }

  private void countMove(WorldPoint before, WorldPoint here, int tick) {
    boolean jump = before.getPlane() != here.getPlane() || before.distanceTo(here) > 2;
    if (!jump) {
      legTiles += before.distanceTo(here);
      legWalkTicks++;
      return;
    }
    // The teleport from its own click (the leg's start); later transport (boat, charter, guide)
    // from the click that set it off.
    if (legJumps++ == 0) legTeleport += tick - legStart;
    else legTransport += tick - Math.max(legStart, legLastClick);
  }

  /**
   * Into client.log: which interfaces open and close; every widget naming the route's charter port
   * (or any charter port) in its name, text or actions; and boats, crew and ships within 15 tiles
   * with their IDs and tiles.
   */
  void scan() {
    if (client.getGameState() != GameState.LOGGED_IN) return;
    Widget[] roots = client.getWidgetRoots();
    logInterfaces(roots);
    Guidance step = plugin.guide();
    List<String> words = new ArrayList<>(PORTS);
    String port = step == null ? null : FarmingPlanner.charterPort(step.travel);
    if (port != null) words.add(0, port);
    if (roots != null) for (Widget root : roots) logWidgets(root, words, 0);
    Player player = client.getLocalPlayer();
    if (player == null || client.getTickCount() % 5 != 0) return;
    WorldPoint here = player.getWorldLocation();
    logLeprechauns();
    logBoatNpcs(here);
    logBoatObjects(here);
  }

  private void logInterfaces(Widget[] roots) {
    Set<Integer> groups = new HashSet<>();
    if (roots != null) for (Widget root : roots) if (root != null) groups.add(root.getId() >>> 16);
    for (int group : groups) if (!openGroups.contains(group)) plugin.debug().log("interface opened: group " + group);
    for (int group : openGroups) if (!groups.contains(group)) plugin.debug().log("interface closed: group " + group);
    openGroups = groups;
  }

  /**
   * Every tool leprechaun in the loaded area, wherever you are, with the herb patches whose area
   * it's in: their tiles decide where noting is the shorter walk.
   */
  private void logLeprechauns() {
    for (NPC npc : plugin.scene().npcs()) {
      if (npc.getName() == null || !npc.getName().toLowerCase(Locale.ROOT).contains("leprechaun")) continue;
      WorldPoint at = npc.getWorldLocation();
      List<String> patches = new ArrayList<>();
      for (Map.Entry<String, Set<Integer>> entry : TargetData.HERB_REGIONS.entrySet())
        if (entry.getValue().contains(at.getRegionID())) patches.add(entry.getKey());
      plugin.debug().logOnce(
          "leprechaun " + npc.getName() + " at " + at.getX() + "," + at.getY() + "," + at.getPlane()
              + " region " + at.getRegionID() + " herb patches " + patches);
    }
  }

  private void logBoatNpcs(WorldPoint here) {
    for (NPC npc : plugin.scene().npcs()) {
      String name = npc.getName() == null ? "" : npc.getName().toLowerCase(Locale.ROOT);
      if (npc.getWorldLocation().distanceTo(here) > SCAN_TILES || !isBoatThing(name)) continue;
      WorldPoint at = npc.getWorldLocation();
      plugin.debug().logOnce(
          "npc " + npc.getName() + " id " + npc.getId() + " at " + at.getX() + "," + at.getY() + "," + at.getPlane());
    }
  }

  private void logBoatObjects(WorldPoint here) {
    Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
    int plane = client.getTopLevelWorldView().getPlane();
    for (Tile[] column : tiles[plane])
      for (Tile tile : column) {
        if (tile == null || tile.getWorldLocation().distanceTo(here) > SCAN_TILES) continue;
        List<TileObject> objects = new ArrayList<>();
        objects.add(tile.getGroundObject());
        objects.add(tile.getDecorativeObject());
        objects.add(tile.getWallObject());
        objects.addAll(Arrays.asList(tile.getGameObjects()));
        for (TileObject object : objects) logBoatObject(object);
      }
  }

  private void logBoatObject(TileObject object) {
    if (object == null) return;
    ObjectComposition definition = client.getObjectDefinition(object.getId());
    if (definition != null && definition.getImpostorIds() != null && definition.getImpostor() != null)
      definition = definition.getImpostor();
    if (definition == null
        || definition.getName() == null
        || !isBoatThing(definition.getName().toLowerCase(Locale.ROOT))) return;
    WorldPoint at = object.getWorldLocation();
    plugin.debug().logOnce(
        "object " + definition.getName() + " id " + object.getId() + " at " + at.getX() + "," + at.getY() + ","
            + at.getPlane() + " actions " + Arrays.toString(definition.getActions()));
  }

  private static boolean isBoatThing(String name) {
    for (String word : BOAT_WORDS) if (name.contains(word)) return true;
    return false;
  }

  private void logWidgets(Widget widget, List<String> words, int depth) {
    if (widget == null || widget.isHidden() || depth > WIDGET_DEPTH) return;
    StringBuilder all = new StringBuilder();
    if (widget.getName() != null) all.append(widget.getName()).append(' ');
    if (widget.getText() != null) all.append(widget.getText()).append(' ');
    if (widget.getActions() != null)
      for (String action : widget.getActions()) if (action != null) all.append(action).append(' ');
    String text = Text.removeTags(all.toString()).toLowerCase(Locale.ROOT);
    for (String word : words)
      if (text.contains(word)) {
        plugin.debug().logOnce(
            "widget group " + (widget.getId() >>> 16) + " child " + (widget.getId() & 0xFFFF) + " index "
                + widget.getIndex() + " type " + widget.getType() + " name '"
                + Text.removeTags(String.valueOf(widget.getName())) + "' text '"
                + Text.removeTags(String.valueOf(widget.getText())) + "' actions "
                + Arrays.toString(widget.getActions()) + " bounds " + widget.getBounds());
        break;
      }
    for (Widget[] children :
        new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
      if (children != null) for (Widget child : children) logWidgets(child, words, depth + 1);
  }
}
