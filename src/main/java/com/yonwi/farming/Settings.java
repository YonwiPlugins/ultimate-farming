package com.yonwi.farming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The player's choices and unlocks, saved as JSON in the RuneLite profile. Field names are the saved keys. */
final class Settings {
  /** No longer used: expected herbs come from the wiki formula. Kept so old saves still load. */
  double herbYield = 8;
  /** Herbs actually picked per patch on a finished run, for its real profit; null for estimates. */
  Double herbActual;
  /** Cast Resurrect Crops (Arceuus) on dead herb patches. */
  boolean resurrect = true;
  /** Magic level, read from the account; Resurrect Crops needs 78. */
  int magic;

  /** Teleport spells the rune pouch casts on this route? (They need the standard spellbook.) */
  static boolean usesSpells(List<FarmingPlanner.Step> route) {
    for (FarmingPlanner.Step step : route) if (Guidance.spell(step.travel) != null) return true;
    return false;
  }

  /**
   * Resurrect Crops is in play: ticked, 78+ Magic, herbs in the run, and no standard-spellbook
   * teleports on the route (the spell is on the Arceuus spellbook).
   */
  boolean resurrects(List<FarmingPlanner.Step> route) {
    if (!resurrect || magic < 78 || usesSpells(route)) return false;
    for (FarmingPlanner.Step step : route) if (step.patch.kind.equals("herb")) return true;
    return false;
  }
  boolean secateurs = true;
  int level = 1;
  int bark = 3;
  /** Old all-or-nothing switches, kept so old saves load; per crop now (unprotected, unharvested). */
  boolean protect = true;
  boolean harvest = true;
  boolean farmer = true;
  boolean bucket = true;
  /** Start the run timer automatically from the first finished patch or picked herb. */
  boolean autoStart = true;
  /** Bring a stamina potion on longer runs. */
  boolean stamina = true;
  String house = "brimhaven";
  Set<String> kinds = new LinkedHashSet<>(Arrays.asList("tree", "fruit"));
  Set<String> unlocks =
      new LinkedHashSet<>(Arrays.asList("gnome", "wealth", "skills", "farmingCape", "fossil"));
  Map<String, String> crops = new LinkedHashMap<>();
  Set<String> excluded = new LinkedHashSet<>();
  Set<String> completed = new LinkedHashSet<>();
  /** Plant, Protect and Compost actions seen in-game for each patch this run. */
  Map<String, Set<String>> progress = new LinkedHashMap<>();
  /** When each patch was last planted (epoch millis), to say when the next run is ready. */
  Map<String, Long> plantedAt = new LinkedHashMap<>();
  /** Patch IDs in the player's chosen order; empty uses the suggested route. */
  List<String> order = new ArrayList<>();
  /** Run types where gardeners aren't paid (compost instead) or produce isn't harvested. */
  Set<String> unprotected = new LinkedHashSet<>();
  Set<String> unharvested = new LinkedHashSet<>();

  /** Pay the gardener for this type of tree. Herbs have no protection payment. */
  boolean protects(String kind) {
    return protect && !kind.equals("herb") && !unprotected.contains(kind);
  }

  /** Pick the fruit (or Calquat, or Celastrus bark) before clearing. Herbs are always harvested. */
  boolean harvests(String kind) {
    // Calquat too: the tree can only be dug up once it's barren (wiki: "using a spade on the
    // barren tree"), so its fruit is always picked.
    return kind.equals("herb") || kind.equals("calquat") || harvest && !unharvested.contains(kind);
  }

  /**
   * Patches that can't get diseased, so need neither payment nor compost. Per the OSRS wiki
   * (Disease (Farming)), the Falador elite diary protects the Falador Park tree.
   */
  boolean diseaseFree(Patch patch) {
    return patch.id.equals("falador") && has("faladorElite");
  }

  /** Pay this patch's gardener. */
  boolean pays(Patch patch) {
    // Only where there's a gardener to pay (Summer Shore's Calquat patch has none).
    return protects(patch.kind) && !diseaseFree(patch) && TargetData.GARDENERS.containsKey(patch.id);
  }

  /** Use compost here: herbs always, Celastrus when harvested; trees when unprotected and not disease-free. */
  boolean composts(Patch patch) {
    // Herbs and Celastrus: compost raises the yield (wiki: "The yield of celastrus bark can be
    // improved by composting"). Other trees: it only fights disease, so it's pointless when the
    // gardener protects the tree.
    // Celastrus only when its bark is harvested (or the gardener isn't paid to protect it).
    return patch.kind.equals("herb")
        || patch.kind.equals("celastrus") && harvests("celastrus")
        || !pays(patch) && !diseaseFree(patch);
  }

  boolean has(String key) {
    return unlocks.contains(key);
  }

  /** When the Farming Guild's anima patch was last read (epoch millis); null if never. */
  Long attasCheckedAt;

  /**
   * Warn before the run that there's no Attas plant: herbs in the run, 76+ Farming (the anima
   * patch needs it), and the anima patch last seen without a living Attas. Quiet until it's been
   * seen once, and when Attas is ticked in Setup.
   */
  boolean warnNoAttas(List<FarmingPlanner.Step> route, int farmingLevel) {
    if (has("attas") || attasCheckedAt == null || farmingLevel < 76) return false;
    for (FarmingPlanner.Step step : route) if (step.patch.kind.equals("herb")) return true;
    return false;
  }

  /** You can get into your house (Construction cape, rune pouch spell, or house features). */
  boolean poh() {
    return has("cape") || has("pouch") || has("jewellery") || has("spirit");
  }

  /** The crop chosen for a run type, or the best one your level allows; null below them all. */
  Crop crop(String kind) {
    Crop chosen = null;
    for (Crop crop : FarmingData.CROPS.get(kind))
      if (crop.level <= level) {
        chosen = crop;
        if (crop.name.equals(crops.get(kind))) return crop;
      }
    return chosen;
  }
}
