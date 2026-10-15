package com.yonwi.farming;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;

/** Farming-related objects and every NPC in the loaded scene, kept up to date by spawn events. */
final class SceneTracker {
  /** Objects worth remembering by name, besides anything that changes look (an impostor). */
  private static final List<String> REMEMBERED =
      Arrays.asList(
          "tree", "patch", "fairy ring", "railing", "portal", "jewellery box", "gangplank",
          "rowboat", "altar");

  @Inject private Client client;

  private final Set<TileObject> objects = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<NPC> npcs = Collections.newSetFromMap(new IdentityHashMap<>());

  Set<TileObject> objects() {
    return objects;
  }

  Set<NPC> npcs() {
    return npcs;
  }

  void remember(TileObject object) {
    if (object == null) return;
    ObjectComposition definition = client.getObjectDefinition(object.getId());
    if (definition == null) return;
    String name = definition.getName();
    if (definition.getImpostorIds() != null || name != null && isRemembered(name))
      objects.add(object);
  }

  private static boolean isRemembered(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    for (String word : REMEMBERED) if (lower.contains(word)) return true;
    return false;
  }

  void forget(TileObject object) {
    objects.remove(object);
  }

  void addNpc(NPC npc) {
    npcs.add(npc);
  }

  void removeNpc(NPC npc) {
    npcs.remove(npc);
  }

  void clear() {
    objects.clear();
    npcs.clear();
  }

  /** Reads the whole loaded scene, e.g. after logging in. */
  void seed() {
    if (client.getGameState() != GameState.LOGGED_IN) return;
    clear();
    for (NPC npc : client.getTopLevelWorldView().npcs()) npcs.add(npc);
    Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
    for (Tile[][] plane : tiles)
      for (Tile[] column : plane)
        for (Tile tile : column)
          if (tile != null) {
            remember(tile.getGroundObject());
            remember(tile.getDecorativeObject());
            for (GameObject object : tile.getGameObjects()) remember(object);
          }
  }

  /** The object's definition as it looks now (its impostor, for farming patches), or null. */
  ObjectComposition definition(TileObject object) {
    ObjectComposition definition = client.getObjectDefinition(object.getId());
    if (definition != null && definition.getImpostorIds() != null) definition = definition.getImpostor();
    return definition;
  }

  /** Whether an object (resolved through its impostor) has this right-click option. */
  boolean offers(TileObject object, String option) {
    if (object == null) return false;
    ObjectComposition definition = definition(object);
    if (definition == null) return false;
    for (String action : definition.getActions()) if (option.equalsIgnoreCase(action)) return true;
    return false;
  }

  /** The nearest remembered object whose name contains one of these (lower case) words. */
  TileObject nearestObject(Player player, int within, String... names) {
    TileObject best = null;
    int closest = within;
    for (TileObject object : objects) {
      ObjectComposition definition = definition(object);
      if (definition == null || definition.getName() == null) continue;
      String name = definition.getName().toLowerCase(Locale.ROOT);
      boolean match = false;
      for (String wanted : names) match |= name.contains(wanted);
      if (!match) continue;
      int distance = object.getWorldLocation().distanceTo(player.getWorldLocation());
      if (distance < closest) {
        best = object;
        closest = distance;
      }
    }
    return best;
  }
}
