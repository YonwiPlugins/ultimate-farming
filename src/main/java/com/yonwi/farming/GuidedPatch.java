package com.yonwi.farming;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;

/** The guided stage's patch and gardener, found every tick, and what the patch shows. */
final class GuidedPatch {
  /** How far a gardener can be to count as this stage's. */
  private static final int GARDENER_RANGE = 32;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  /** The guided patch object, found each tick while nearby. */
  private TileObject object;
  private NPC gardener;
  /** Whether the guided patch currently offers "Rake", i.e. has weeds to clear. */
  private volatile boolean needsRake;
  /** The guided patch's crop has died and needs clearing before anything else. */
  private volatile boolean dead;

  TileObject object() {
    return object;
  }

  NPC gardener() {
    return gardener;
  }

  /** The guided patch has weeds: raking it is a single click on the patch. */
  boolean needsRake() {
    return needsRake;
  }

  boolean dead() {
    return dead;
  }

  void setDead(boolean dead) {
    this.dead = dead;
  }

  /** Finds the stage's patch every tick, so step completion can be read whatever is highlighted. */
  void find(Guidance step, Player player) {
    object = null;
    // The dead flag keeps the last reading until the progress check reads the patch again, so
    // the targets chosen this tick already know a dead crop is there.
    if (step == null || player == null) return;
    NPC nearbyGardener = findGardener(step, player);
    TileObject patch = step.kind.equals("herb") ? findHerbPatch(step) : findTreePatch(step);
    if (patch == null && !step.kind.equals("herb") && nearbyGardener != null)
      patch = findPatchByGardener(step, nearbyGardener);
    object = patch;
    if (patch == null) dead = false;
    gardener = nearbyGardener;
    needsRake = plugin.scene().offers(patch, "rake");
  }

  private NPC findGardener(Guidance step, Player player) {
    int gardenerId = TargetData.GARDENERS.getOrDefault(step.patchId, -1);
    for (NPC npc : plugin.scene().npcs())
      if (npc.getId() == gardenerId
          && npc.getWorldLocation().distanceTo(player.getWorldLocation()) <= GARDENER_RANGE)
        return npc;
    return null;
  }

  /** The farming object (one that changes with growth) at the herb patch's position. */
  private TileObject findHerbPatch(Guidance step) {
    WorldPoint at = TargetData.HERB_POINTS.get(step.patchId);
    if (at == null) return null;
    TileObject patch = null;
    int closest = 2;
    for (TileObject candidate : plugin.scene().objects()) {
      if (client.getObjectDefinition(candidate.getId()).getImpostorIds() == null) continue;
      int distance = candidate.getWorldLocation().distanceTo(at);
      if (distance < closest) {
        patch = candidate;
        closest = distance;
      }
    }
    return patch;
  }

  /**
   * Trees with a known position: the farming object there, found as soon as it's loaded and
   * whether or not there's a gardener (Summer Shore's Calquat has none).
   */
  private TileObject findTreePatch(Guidance step) {
    WorldPoint at = TargetData.PATCH_POINTS.get(step.patchId);
    if (at == null) return null;
    TileObject patch = null;
    int closest = 4;
    for (TileObject candidate : plugin.scene().objects()) {
      if (candidate.getPlane() != at.getPlane()) continue;
      int distance = candidate.getWorldLocation().distanceTo(at);
      if (distance >= closest) continue;
      ObjectComposition definition = client.getObjectDefinition(candidate.getId());
      if (definition == null || definition.getImpostorIds() == null) continue;
      definition = definition.getImpostor();
      if (definition == null || !TargetData.patchLike(definition, step.kind)) continue;
      patch = candidate;
      closest = distance;
    }
    return patch;
  }

  /** No known position: the patch-like object nearest the gardener. */
  private TileObject findPatchByGardener(Guidance step, NPC gardener) {
    TileObject patch = null;
    int closest = 15;
    for (TileObject candidate : plugin.scene().objects()) {
      int distance = candidate.getWorldLocation().distanceTo(gardener.getWorldLocation());
      if (distance > closest) continue;
      ObjectComposition definition = client.getObjectDefinition(candidate.getId());
      if (definition.getImpostorIds() != null) definition = definition.getImpostor();
      if (definition == null || !TargetData.patchLike(definition, step.kind)) continue;
      if (distance < closest) {
        patch = candidate;
        closest = distance;
      }
    }
    return patch;
  }
}
