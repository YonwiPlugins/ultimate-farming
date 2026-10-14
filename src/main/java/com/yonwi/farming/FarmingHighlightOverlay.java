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
      for (TileObject altar : plugin.switchObjects()) draw(g, altar, FarmingInventoryOverlay.TARGET);
    if (!config.highlight() || step == null || client.getGameState() != GameState.LOGGED_IN)
      return null;
    drawPath(g);
    for (TileObject object : plugin.nextObjects()) draw(g, object, FarmingInventoryOverlay.NEXT);
    for (NPC npc : plugin.nextNpcs())
      FarmingInventoryOverlay.mark(g, npc.getConvexHull(), FarmingInventoryOverlay.NEXT);
    // Once the seed or compost is selected, the patch takes the item's colour.
    // Two clicks (use an item on the patch or NPC): the item is blue, so its target is orange
    // until the item is selected, then blue. One click (pick, check health, pay): blue.
    boolean useItem =
        (step.phase.equals("Plant") && !plugin.needsRake())
            || step.phase.equals("Compost")
            || step.phase.startsWith("Note")
            || plugin.noteAdvice() != null
            // Digging up a stump or bare tree: spade first.
            || step.phase.equals("Clear") && !plugin.itemIds(step).isEmpty();
    Color ink =
        plugin.selectedItem(plugin.itemIds(step))
            ? FarmingInventoryOverlay.ITEM
            : useItem ? FarmingInventoryOverlay.NEXT : FarmingInventoryOverlay.TARGET;
    for (TileObject object : plugin.highlightObjects()) {
      draw(g, object, ink);
      // On the fairy ring itself, show the code to dial.
      String code = plugin.fairyCode();
      if (code != null) {
        net.runelite.api.Point at = object.getCanvasTextLocation(g, code, 160);
        if (at != null) OverlayUtil.renderTextLocation(g, at, code, FarmingInventoryOverlay.ITEM);
      }
    }
    for (NPC npc : plugin.highlightNpcs())
      FarmingInventoryOverlay.mark(g, npc.getConvexHull(), ink);
    if (config.debug()) logHighlights(ink);
    return null;
  }

  /** Debug: what's lit in the game world and in which colour, logged when it changes. */
  private void logHighlights(Color ink) {
    List<String> parts = new ArrayList<>();
    String now = FarmingInventoryOverlay.colourName(ink), next = FarmingInventoryOverlay.colourName(FarmingInventoryOverlay.NEXT);
    for (TileObject o : plugin.highlightObjects()) parts.add(now + " " + objectName(o));
    for (NPC n : plugin.highlightNpcs()) parts.add(now + " " + n.getName() + " (" + n.getId() + ")");
    for (TileObject o : plugin.nextObjects()) parts.add(next + " " + objectName(o));
    for (NPC n : plugin.nextNpcs()) parts.add(next + " " + n.getName() + " (" + n.getId() + ")");
    List<WorldPoint> path = plugin.path();
    if (!path.isEmpty()) {
      WorldPoint end = path.get(path.size() - 1);
      parts.add("path " + path.size() + " tiles to " + end.getX() + "," + end.getY());
    }
    plugin.noteHighlights("world", String.join(" · ", parts));
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
    List<WorldPoint> path = plugin.path();
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
