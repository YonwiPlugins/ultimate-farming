package com.yonwi.farming;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.*;

/** Current substep's patch or NPC in blue; the next substep's in orange so it's ready to click. */
final class FarmingHighlightOverlay extends Overlay {
  private final FarmingPlugin plugin;
  private final FarmingConfig config;
  private final Client client;

  @Inject
  FarmingHighlightOverlay(FarmingPlugin plugin, FarmingConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_SCENE);
  }

  @Override
  public Dimension render(Graphics2D g) {
    Guidance step = plugin.guide();
    // Before a run: the house altar to switch spellbook on.
    if (config.highlight() && step == null && client.getGameState() == GameState.LOGGED_IN)
      for (TileObject altar : plugin.spellbook().objects()) draw(g, altar, FarmingInventoryOverlay.TARGET);
    if (!config.highlight() || step == null || client.getGameState() != GameState.LOGGED_IN)
      return null;
    drawPath(g);
    // A patch made of several tiles (the Farming Guild redwood) is one square round all of it.
    int[] area = TargetData.PATCH_AREAS.get(step.patchId);
    drawAll(g, plugin.targets().nextObjects(), area, FarmingInventoryOverlay.NEXT);
    for (NPC npc : plugin.targets().nextNpcs())
      FarmingInventoryOverlay.mark(g, npc.getConvexHull(), FarmingInventoryOverlay.NEXT);
    // Once the seed or compost is selected, the patch takes the item's colour.
    // Two clicks (use an item on the patch or NPC): the item is blue, so its target is orange
    // until the item is selected, then blue. One click (pick, check health, pay): blue.
    boolean useItem =
        (step.phase.equals("Plant") && !plugin.patch().needsRake())
            || step.phase.equals("Compost")
            || step.phase.startsWith("Note")
            || plugin.noting().advice() != null
            // Herb sack shut: opening it comes first, so the patch is next.
            || plugin.harvest().openSackNow()
            // Digging up a stump or bare tree: spade first.
            || step.phase.equals("Clear") && !plugin.targets().itemIds(step).isEmpty();
    Color ink =
        plugin.targets().selectedItem(plugin.targets().itemIds(step))
            ? FarmingInventoryOverlay.ITEM
            : useItem ? FarmingInventoryOverlay.NEXT : FarmingInventoryOverlay.TARGET;
    drawAll(g, plugin.targets().objects(), area, ink);
    // On the fairy ring itself, show the code to dial.
    String code = plugin.targets().fairyCode();
    if (code != null)
      for (TileObject object : plugin.targets().objects()) {
        net.runelite.api.Point at = object.getCanvasTextLocation(g, code, 160);
        if (at != null) OverlayUtil.renderTextLocation(g, at, code, FarmingInventoryOverlay.ITEM);
      }
    for (NPC npc : plugin.targets().npcs())
      FarmingInventoryOverlay.mark(g, npc.getConvexHull(), ink);
    if (config.debug()) logHighlights(ink, area);
    return null;
  }

  /** Objects in one colour; the tiles of a patch made of several as one square round it all. */
  private void drawAll(Graphics2D g, List<TileObject> objects, int[] area, Color color) {
    TileObject tile = null;
    for (TileObject object : objects)
      if (inArea(object, area)) tile = object;
      else draw(g, object, color);
    if (tile == null) return;
    // The square's centre: the south-west tile's corner plus half the patch's size.
    WorldView view = client.getTopLevelWorldView();
    LocalPoint sw = LocalPoint.fromWorld(view, new WorldPoint(area[0], area[1], tile.getPlane()));
    if (sw == null) return;
    int sizeX = area[2] - area[0] + 1, sizeY = area[3] - area[1] + 1;
    LocalPoint centre =
        new LocalPoint(
            sw.getX() + (sizeX - 1) * Perspective.LOCAL_HALF_TILE_SIZE,
            sw.getY() + (sizeY - 1) * Perspective.LOCAL_HALF_TILE_SIZE,
            view);
    FarmingInventoryOverlay.mark(
        g, Perspective.getCanvasTileAreaPoly(client, centre, sizeX, sizeY, tile.getPlane(), 0), color);
  }

  /** Whether an object stands inside a patch made of several tiles. */
  private static boolean inArea(TileObject object, int[] area) {
    if (area == null) return false;
    WorldPoint at = object.getWorldLocation();
    return at.getX() >= area[0] && at.getX() <= area[2] && at.getY() >= area[1] && at.getY() <= area[3];
  }

  /** Debug: what's lit in the game world and in which colour, logged when it changes. */
  private void logHighlights(Color ink, int[] area) {
    List<String> parts = new ArrayList<>();
    String now = FarmingInventoryOverlay.colourName(ink), next = FarmingInventoryOverlay.colourName(FarmingInventoryOverlay.NEXT);
    logObjects(parts, now, plugin.targets().objects(), area);
    for (NPC n : plugin.targets().npcs()) parts.add(now + " " + n.getName() + " (" + n.getId() + ")");
    logObjects(parts, next, plugin.targets().nextObjects(), area);
    for (NPC n : plugin.targets().nextNpcs()) parts.add(next + " " + n.getName() + " (" + n.getId() + ")");
    List<WorldPoint> path = plugin.route().path();
    if (!path.isEmpty()) {
      WorldPoint end = path.get(path.size() - 1);
      parts.add("path " + path.size() + " tiles to " + end.getX() + "," + end.getY());
    }
    plugin.debug().noteHighlights("world", String.join(" · ", parts));
  }

  /** Debug: each object, or the tiles of a patch made of several as one square. */
  private void logObjects(List<String> parts, String colour, List<TileObject> objects, int[] area) {
    boolean square = false;
    for (TileObject o : objects)
      if (inArea(o, area)) square = true;
      else parts.add(colour + " " + objectName(o));
    if (square)
      parts.add(
          colour + " patch square " + area[0] + "," + area[1] + " to " + area[2] + "," + area[3]);
  }

  private String objectName(TileObject o) {
    net.runelite.api.ObjectComposition c = client.getObjectDefinition(o.getId());
    if (c != null && c.getImpostorIds() != null && c.getImpostor() != null) c = c.getImpostor();
    WorldPoint at = o.getWorldLocation();
    return (c == null ? "object" : c.getName()) + " (" + o.getId() + ") at " + at.getX() + "," + at.getY();
  }

  /**
   * The walking route drawn like the Shortest Path plugin's "lines" style: a 4px line through the
   * tile centres, at ground height, from you to what you click next. Worked out over the loaded
   * area's walls and fences; it extends as you walk when the patch is further away.
   */
  private void drawPath(Graphics2D g) {
    List<WorldPoint> path = plugin.route().path();
    Player player = client.getLocalPlayer();
    if (path.isEmpty() || player == null) return;
    WorldView view = client.getTopLevelWorldView();
    int plane = view.getPlane();
    List<WorldPoint> points = new ArrayList<>();
    points.add(player.getWorldLocation());
    points.addAll(path);
    g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(new Color(110, 200, 255, 180));
    net.runelite.api.Point previous = null;
    for (WorldPoint tile : points) {
      LocalPoint local = LocalPoint.fromWorld(view, tile);
      net.runelite.api.Point at =
          local == null
              ? null
              : Perspective.localToCanvas(
                  client, local.getX(), local.getY(), Perspective.getTileHeight(client, local, plane));
      if (previous != null && at != null)
        g.drawLine(previous.getX(), previous.getY(), at.getX(), at.getY());
      previous = at;
    }
    // (No end-tile marker: the line leads there on its own.)
  }

  private static void draw(Graphics2D g, TileObject object, Color color) {
    FarmingInventoryOverlay.mark(
        g, object.getClickbox() != null ? object.getClickbox() : object.getCanvasTilePoly(), color);
  }
}
