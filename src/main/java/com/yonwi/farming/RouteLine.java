package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/** The line on the ground from you to whatever you click next, or the patch itself. */
final class RouteLine {
  /** Where the Mountain Guide stands in Quetzacalli Gorge (Shortest Path's transport data). */
  private static final WorldPoint GORGE_GUIDE = new WorldPoint(1486, 3232, 0);
  /** Where Elkoy stands outside the Tree Gnome Village maze (Shortest Path's transport data). */
  private static final WorldPoint ELKOY = new WorldPoint(2515, 3159, 0);
  /** On the Catherby dock, in sight of the charter ship's crew. */
  private static final WorldPoint CATHERBY_DOCK = new WorldPoint(2798, 3414, 0);
  /** How close you have to be to head for one of those spots. */
  private static final int SPOT_RANGE = 100;
  /** Within this many tiles of a patch you've started on, no line. */
  private static final int AT_PATCH_TILES = 10;
  /** Ticks to wait before searching again for a target no route was found to. */
  private static final int RETRY_TICKS = 10;
  private static final long SHORTEST_PATH_CHECK_MS = 10_000;

  @Inject private Client client;
  @Inject private EventBus eventBus;
  @Inject private PluginManager pluginManager;
  @Inject private FarmingPlugin plugin;

  /** Tiles to walk from you to whatever you click next, or the patch itself. */
  private volatile List<WorldPoint> path = Collections.emptyList();
  /** The last whole-map route and where it was going, reused while you walk along it. */
  private List<WorldPoint> worldRoute = Collections.emptyList();
  private WorldPoint worldRouteTarget;
  private int worldRouteSearchedAt;
  private WorldPoint shortestPathTarget;
  private long shortestPathChecked;
  private boolean shortestPathOn;

  List<WorldPoint> path() {
    return path;
  }

  void update(Guidance step, Player player) {
    path = walkingPath(step, player);
  }

  /** A target the line goes to, and the size of what stands there. */
  private static final class Destination {
    WorldPoint at;
    int width = 1;
    int height = 1;
  }

  private List<WorldPoint> walkingPath(Guidance step, Player player) {
    WorldPoint from = player.getWorldLocation();
    // In the house (an instance) Shortest Path has no map, so our own route over the loaded
    // rooms leads to the portal, nexus, jewellery box, fairy ring or spirit tree.
    if (client.getTopLevelWorldView().isInstance()) return houseRoute(from);
    String advice = plugin.noting().advice();
    // Noting herbs: the leprechaun is the destination, not the patch.
    boolean noting = advice != null || step.phase.startsWith("Note");
    List<NPC> npcs = plugin.targets().npcs();
    List<TileObject> objects = plugin.targets().objects();
    // At a patch you've started on, or noting: no line (the highlights say what to click). "At"
    // is within 10 tiles, not its map square: clicking the patch from further off (Kastori, 33
    // tiles) hid the line for the rest of the walk.
    WorldPoint patchAt = TargetData.PATCH_POINTS.get(step.patchId);
    boolean here =
        !step.phase.equals("Travel")
            && (patchAt != null
                ? patchAt.distanceTo2D(from) <= AT_PATCH_TILES
                : StepProgress.inPatchArea(step.patchId, from));
    // Noting advised but the leprechaun isn't in view yet: head for where he stands.
    WorldPoint leprechaunSpot =
        advice != null && npcs.isEmpty() && patchAt != null ? TargetData.nearestLeprechaun(patchAt) : null;
    boolean started =
        step.patchId.equals(plugin.progress().workedPatch())
            || step.patchId.equals(plugin.harvest().pickingPatch());
    if (leprechaunSpot == null && (advice != null || here && (noting || started))) {
      shortestPath(null);
      return Collections.emptyList();
    }
    Destination to = new Destination();
    to.at = destination(step, from, noting, leprechaunSpot);
    if (!npcs.isEmpty() && to.at != null && to.at.equals(npcs.get(0).getWorldLocation())) {
      NPCComposition npc = npcs.get(0).getComposition();
      to.width = to.height = npc == null ? 1 : Math.max(1, npc.getSize());
    } else if (leprechaunSpot == null) {
      fitToObject(to, !objects.isEmpty() ? objects.get(0) : plugin.patch().object());
    }
    shortestPath(null); // never drive the Shortest Path plugin any more
    // Already beside it: no line.
    if (to.at == null || to.at.getPlane() != from.getPlane() || besideArea(from, to.at, to.width, to.height))
      return Collections.emptyList();
    // Our own route over the whole map (Shortest Path's collision data, doors, gates and Agility
    // shortcuts), so fences beyond the loaded area count too. The loaded-scene route is the
    // fallback while the map loads.
    List<WorldPoint> route = worldRoute(from, to.at, to.width, to.height);
    if (!route.isEmpty()) return route;
    return TilePath.find(client.getTopLevelWorldView(), from, to.at);
  }

  /** In the house: our own route over the loaded rooms to the highlighted object. */
  private List<WorldPoint> houseRoute(WorldPoint from) {
    shortestPath(null);
    List<TileObject> objects = plugin.targets().objects();
    if (objects.isEmpty()) return Collections.emptyList();
    TileObject object = objects.get(0);
    Destination to = new Destination();
    to.at = object.getWorldLocation();
    // Its whole footprint (south-west corner and size), so the line stops at the nearest side.
    if (object instanceof GameObject) fitToGameObject(to, (GameObject) object);
    if (besideArea(from, to.at, to.width, to.height)) return Collections.emptyList();
    return TilePath.find(client.getTopLevelWorldView(), from, to.at, to.width, to.height);
  }

  /** Where the line goes: the leprechaun, what's highlighted, the patch, or a spot on the way. */
  private WorldPoint destination(Guidance step, WorldPoint from, boolean noting, WorldPoint leprechaunSpot) {
    List<NPC> npcs = plugin.targets().npcs();
    List<TileObject> objects = plugin.targets().objects();
    boolean travel = step.phase.equals("Travel");
    WorldPoint to = null;
    if (leprechaunSpot != null) to = leprechaunSpot;
    else if (!npcs.isEmpty()) to = npcs.get(0).getWorldLocation();
    else if (!objects.isEmpty()) to = objects.get(0).getWorldLocation();
    else if (!travel || arrivedLeg(step)) to = TargetData.PATCH_POINTS.get(step.patchId);
    // Patches have fixed spots; prefer them over a moving NPC so Shortest Path isn't re-asked.
    WorldPoint fixed = TargetData.PATCH_POINTS.get(step.patchId);
    // (While travelling, a highlighted guide or object on the way wins: e.g. the Mountain Guide.)
    if (fixed != null && (!noting || to == null)
        && (objects.isEmpty() && npcs.isEmpty() || !travel)
        && (!travel || arrivedLeg(step))) to = fixed;
    if (!travel || !npcs.isEmpty() || from.getPlane() != 0) return to;
    String route = step.travel.toLowerCase(Locale.ROOT);
    // Charter from Catherby, the crew not in view yet: head for the dock, where they are.
    if (route.contains("charter") && route.contains("catherby") && near(CATHERBY_DOCK, from))
      to = CATHERBY_DOCK;
    // Landed in Quetzacalli Gorge but the Mountain Guide isn't in view yet: head for his spot.
    if (route.contains("mountain guide") && near(GORGE_GUIDE, from)) to = GORGE_GUIDE;
    // Tree Gnome Village, Elkoy not in view yet: head for his spot, not round the maze.
    if (route.contains("elkoy") && near(ELKOY, from)) to = ELKOY;
    return to;
  }

  private static boolean near(WorldPoint spot, WorldPoint from) {
    return spot.distanceTo(from) < SPOT_RANGE;
  }

  /** The target's full size, so the route goes to its nearest side (trees are often 2×2+). */
  private void fitToObject(Destination to, TileObject object) {
    if (object == null || to.at == null || object.getWorldLocation().distanceTo(to.at) > 3) return;
    to.at = object.getWorldLocation();
    if (object instanceof GameObject) {
      fitToGameObject(to, (GameObject) object);
      return;
    }
    // Herb patches aren't GameObjects: take the size from the object's definition, so the route
    // stops at the patch's edge instead of walking into it.
    ObjectComposition definition = client.getObjectDefinition(object.getId());
    if (definition != null) {
      to.width = Math.max(1, definition.getSizeX());
      to.height = Math.max(1, definition.getSizeY());
    }
  }

  /**
   * Its size and south-west corner tile: the object's own position is its middle, which is a tile
   * off on a 2×2 tree and aimed the route at the wrong side.
   */
  private void fitToGameObject(Destination to, GameObject object) {
    to.width = object.sizeX();
    to.height = object.sizeY();
    net.runelite.api.Point corner = object.getSceneMinLocation();
    if (corner != null)
      to.at =
          WorldPoint.fromScene(client.getTopLevelWorldView(), corner.getX(), corner.getY(), object.getPlane());
  }

  /** Whether a tile is on or next to a w × h area whose south-west corner is {@code corner}. */
  private static boolean besideArea(WorldPoint tile, WorldPoint corner, int width, int height) {
    int dx = Math.max(0, Math.max(corner.getX() - tile.getX(), tile.getX() - (corner.getX() + width - 1)));
    int dy = Math.max(0, Math.max(corner.getY() - tile.getY(), tile.getY() - (corner.getY() + height - 1)));
    return dx + dy <= 1; // on it, or beside a side (corners don't count)
  }

  /**
   * The whole-map route to {@code to}: the cached one, trimmed to where you are, while you're on
   * it; otherwise a fresh search.
   */
  private List<WorldPoint> worldRoute(WorldPoint from, WorldPoint to, int width, int height) {
    if (!WorldPath.ready()) return Collections.emptyList(); // still loading the map
    if (to.equals(worldRouteTarget)) {
      // Still on the saved route: draw it from the route tile nearest you onwards. The saved
      // route itself is kept whole, so standing still gives the same line every tick.
      int nearest = -1;
      int best = 2;
      for (int i = 0; i < worldRoute.size(); i++) {
        int distance = worldRoute.get(i).distanceTo(from);
        if (distance < best) {
          best = distance;
          nearest = i;
        }
      }
      if (nearest >= 0) return new ArrayList<>(worldRoute.subList(nearest + 1, worldRoute.size()));
      // No route to this target last time: only try again every few ticks.
      if (worldRoute.isEmpty() && client.getTickCount() - worldRouteSearchedAt < RETRY_TICKS)
        return worldRoute;
    }
    worldRouteTarget = to;
    worldRouteSearchedAt = client.getTickCount();
    worldRoute = WorldPath.find(from, to, width, height);
    return worldRoute;
  }

  /**
   * On a Travel step, once teleported out: the rest is on foot towards the patch. Before the
   * teleport there's no path, however near the patch is (e.g. Falador Park to Taverley): the
   * teleport is the step. Walking routes ("Walk to …") have no teleport, so they path straight away.
   */
  private boolean arrivedLeg(Guidance step) {
    WorldPoint at = TargetData.PATCH_POINTS.get(step.patchId);
    Player player = client.getLocalPlayer();
    boolean onFoot = step.travel.toLowerCase(Locale.ROOT).startsWith("walk");
    return at != null
        && player != null
        && (plugin.progress().teleportedThisLeg() || onFoot)
        && at.distanceTo(player.getWorldLocation()) < 120;
  }

  /** Clears any route this plugin set in the Shortest Path plugin. */
  void clearShortestPath() {
    shortestPath(null);
  }

  /** Whether the Shortest Path plugin is installed and switched on (checked every 10 seconds). */
  private boolean shortestPathEnabled() {
    long now = System.currentTimeMillis();
    if (now - shortestPathChecked > SHORTEST_PATH_CHECK_MS) {
      shortestPathChecked = now;
      shortestPathOn = false;
      for (Plugin other : pluginManager.getPlugins())
        if ("Shortest Path".equals(other.getName()) && pluginManager.isPluginEnabled(other))
          shortestPathOn = true;
    }
    return shortestPathOn;
  }

  /**
   * Sets (or with null, clears) Shortest Path's target through its plugin-message API, the way
   * other plugins drive it. Only sent when the target changes.
   */
  private void shortestPath(WorldPoint target) {
    if (Objects.equals(target, shortestPathTarget)) return;
    if (target == null && shortestPathTarget == null) return;
    shortestPathTarget = target;
    if (!shortestPathEnabled()) return;
    if (target == null) {
      eventBus.post(new PluginMessage("shortestpath", "clear"));
      return;
    }
    Map<String, Object> data = new HashMap<>();
    data.put("target", target);
    eventBus.post(new PluginMessage("shortestpath", "path", data));
  }
}
