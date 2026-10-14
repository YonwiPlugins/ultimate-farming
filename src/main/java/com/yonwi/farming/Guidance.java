package com.yonwi.farming;

import java.util.*;

final class Guidance {
  final String patchId, destination, kind, travel, action, phase;
  /** Every action at this patch in order, and which one is being guided. */
  final List<String> phases;
  /** Specific wording for each substep, e.g. "Plant Ranarr seed". */
  final List<String> labels = new ArrayList<>();

  final int phaseIndex;
  /** Standard spellbook teleport to cast while travelling, or null. */
  final String spell;
  final int step, total;
  final List<String> items;
  /** The substep after this one and its items, highlighted in a second colour; null at the end. */
  final String nextPhase;

  final List<String> nextItems;
  /** Expected herbs from one patch, to judge whether the inventory has room for the next. */
  int herbsPerPatch = 8;
  /** Expected herbs here if the patch is alive, for judging whether they'll fit. */
  int herbsIfAlive = 8;
  /** The stage before this one (its patch and kind), or null for the first. */
  String prevPatchId, prevKind;
  /** The next patch's teleport item(s), shown in orange during this patch's last step. */
  List<String> nextTravelItems = Collections.emptyList();
  /** The crop growing here, e.g. "Magic", to recognise its tree. */
  String cropName = "";
  /** What a harvest here gives, unnoted ("Celastrus bark", "Coconut"…); empty for none or herbs. */
  String produce = "";
  /** Dead herbs here should get Resurrect Crops before anything else. */
  boolean resurrect;
  /** The patch after this one and how to get there, for the "Next up" line; null at the end. */
  String nextDestination, nextTravel;

  Guidance(FarmingPlanner.Step s, Settings setup, int step, int total, String phase) {
    this(s, setup, step, total, phase, true);
  }

  private Guidance(
      FarmingPlanner.Step s, Settings setup, int step, int total, String phase, boolean withNext) {
    patchId = s.patch.id;
    destination = s.patch.name;
    kind = s.patch.kind;
    travel = s.travel.text;
    this.step = step;
    this.total = total;
    this.phase = phase;
    phases = phases(s, setup);
    phaseIndex = Math.max(0, phases.indexOf(phase));
    spell = spell(s.travel);
    for (String p : phases) labels.add(label(p, s, setup));
    Crop crop = setup.crop(kind);
    if (crop != null && crop.name != null) cropName = crop.name;
    if (kind.equals("celastrus")) produce = "Celastrus bark";
    else if (crop != null && crop.fruit != null && !kind.equals("herb")) produce = crop.fruit;
    items = new ArrayList<>();
    switch (phase) {
      case "Travel":
        action = travel;
        items.addAll(s.travel.items);
        items.addAll(s.travel.gear);
        if (spell != null) items.remove("Rune pouch");
        break;
      case "Note carried":
        action = "Note carried herbs at the leprechaun before harvesting this patch.";
        items.add(crop.fruit);
        break;
      case "Check health":
        action = "Check the grown " + kind + " tree’s health.";
        break;
      case "Harvest":
        action =
            kind.equals("herb") ? "Harvest all herbs. If inventory fills, note here; otherwise carry to the next noting stop." : kind.equals("celastrus")
                ? "Harvest Celastrus bark with an axe."
                : "Pick all six fruit before clearing.";
        // (No axe highlight: it's used straight from the inventory.)
        if (kind.equals("herb") && setup.secateurs) items.add("Magic secateurs");
        break;
      case "Clear":
        action = "Clear the old tree and stump. Use the gardener where removal is available.";
        // Where the gardener removes the tree, only the gardener is needed.
        // Only the gardener or the patch is highlighted, never the spade or axe.
        break;
      case "Plant":
        action = "Rake if needed, then plant " + crop.sapling + ".";
        items.add(crop.sapling);
        items.add("Rake");
        break;
      case "Protect":
        action = "Pay gardener: " + crop.paymentQty + " " + crop.payment + ".";
        // Only the gardener is highlighted; the payment is handed over in the dialogue.
        break;
      case "Compost":
        action = "Apply ultracompost to the new crop.";
        items.add(setup.bucket ? "Bottomless compost bucket" : "Ultracompost");
        break;
      default:
        action = "Note harvested produce at the tool leprechaun if needed.";
        items.add(crop.fruit);
    }
    nextPhase =
        withNext && phaseIndex + 1 < phases.size() ? phases.get(phaseIndex + 1) : null;
    nextItems =
        nextPhase == null
            ? Collections.emptyList()
            : new Guidance(s, setup, step, total, nextPhase, false).items;
  }

  static List<String> phases(FarmingPlanner.Step step, Settings s) {
    if(step.patch.kind.equals("herb")) {
      List<String> herb = new ArrayList<>(); herb.add("Travel");
      if(step.noteBefore) herb.add("Note carried");
      herb.addAll(Arrays.asList("Harvest","Plant","Compost"));
      if(step.noteAfter) herb.add("Note harvest");
      return herb;
    }
    List<String> p = new ArrayList<>(Arrays.asList("Travel", "Check health"));
    Crop crop = s.crop(step.patch.kind);
    if (s.harvests(step.patch.kind) && (step.patch.kind.equals("celastrus") || !crop.fruit.isEmpty())) p.add("Harvest");
    p.add("Clear");
    p.add("Plant");
    if (s.pays(step.patch)) p.add("Protect");
    if (s.composts(step.patch)) p.add("Compost");
    if (s.harvests(step.patch.kind) && (!crop.fruit.isEmpty() || step.patch.kind.equals("celastrus")))
      p.add("Note harvest");
    return p;
  }

  /** The spell cast from the rune pouch for this travel choice, if any. */
  static String spell(FarmingPlanner.Choice travel) {
    if (!travel.items.contains("Rune pouch")) return null;
    for (String place : Arrays.asList("Falador", "Lumbridge", "Varrock", "Camelot", "Ardougne"))
      if (travel.text.startsWith("Cast " + place + " Teleport")) return place;
    return "House";
  }

  static String label(String phase, FarmingPlanner.Step s, Settings setup) {
    String kind = s.patch.kind;
    Crop crop = setup.crop(kind);
    switch (phase) {
      case "Travel":
        return s.travel.text;
      case "Note carried":
        return "Note carried herbs at the leprechaun";
      case "Check health":
        return "Check the " + crop.name.toLowerCase(Locale.ROOT) + " tree's health";
      case "Harvest":
        return kind.equals("herb")
            ? "Pick the " + crop.fruit.toLowerCase(Locale.ROOT).replace("grimy ", "")
            : kind.equals("celastrus") ? "Chop Celastrus bark" : "Pick 6 " + crop.fruit.toLowerCase(Locale.ROOT);
      case "Clear":
        return FarmingPlugin.GARDENER_REMOVAL.contains(kind)
            ? "Pay the gardener to remove the tree"
            : kind.equals("celastrus")
                ? "Chop the tree down, then dig up the stump"
                : kind.equals("calquat")
                    ? "Dig up the bare tree with a spade"
                    : "Dig up the stump with a spade";
      case "Plant":
        return "Plant " + crop.sapling;
      case "Protect":
        return "Pay the gardener " + crop.paymentQty + " × " + crop.payment;
      case "Compost":
        return setup.bucket ? "Use the bottomless bucket (ultracompost)" : "Apply ultracompost";
      default:
        return "Note " + (kind.equals("herb") ? "herbs" : crop.fruit.toLowerCase(Locale.ROOT)) + " at the leprechaun";
    }
  }
}
