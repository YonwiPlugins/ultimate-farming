package com.yonwi.farming;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class FarmingPlannerTest {
  private Settings full() {
    Settings s = new Settings();
    s.level = 99;
    s.unlocks.addAll(Arrays.asList("cape", "pouch", "lletya", "slayer", "catherby", "spirit"));
    return s;
  }

  @Test
  public void mixAndMatchOnlyIncludesChosenTypes() {
    Settings s = full();
    s.kinds = new LinkedHashSet<>(Arrays.asList("calquat", "celastrus", "redwood"));
    List<FarmingPlanner.Step> route = FarmingPlanner.plan(s);
    assertEquals(3, route.size());
    for (FarmingPlanner.Step step : route) assertTrue(s.kinds.contains(step.patch.kind));
  }

  @Test
  public void pouchReachesLumbridgeDirectly() {
    Settings s = full();
    FarmingPlanner.Step step =
        FarmingPlanner.plan(s).stream()
            .filter(x -> x.patch.id.equals("lumbridge"))
            .findFirst()
            .get();
    assertTrue(step.travel.text.contains("Lumbridge"));
    assertTrue(step.travel.items.contains("Rune pouch"));
  }

  @Test
  public void guildPrefersCape() {
    Settings s = full();
    FarmingPlanner.Step step =
        FarmingPlanner.plan(s).stream()
            .filter(x -> x.patch.id.equals("guildtree"))
            .findFirst()
            .get();
    assertTrue(step.travel.text.contains("Farming cape"));
  }

  @Test
  public void lowerLevelCannotUseFarmingCape() {
    Settings s = full();
    s.level = 85;
    Patch p = FarmingData.PATCHES.stream().filter(x -> x.id.equals("guildtree")).findFirst().get();
    assertTrue(
        FarmingPlanner.choices(p, s).stream().noneMatch(c -> c.text.contains("Farming cape")));
  }

  @Test
  public void tagIncludesAllKitVariantsAndEternalCrystal() {
    Settings s = full();
    s.protect = false;
    FarmingPlanner.Kit kit = FarmingPlanner.kit(FarmingPlanner.plan(s), s);
    Set<String> tag = new HashSet<>(Arrays.asList(FarmingPlanner.tags(kit, s).split(",")));
    List<String> names = new ArrayList<>(kit.wear);
    names.addAll(kit.bring.keySet());
    for (String name : names) {
      assertTrue("Missing item mapping " + name, FarmingData.id(name) > 0);
      for (int id : FarmingData.IDS.get(name)) assertTrue(tag.contains(String.valueOf(id)));
    }
    assertTrue(tag.contains("23946"));
    assertFalse(tag.contains("23904"));
    assertTrue(tag.contains("22997"));
    assertTrue(tag.contains("5341"));
    assertFalse(kit.wear.contains("Teleport crystal"));
  }

  @Test
  public void equipmentSlotsDoNotCompete() {
    Settings s = full();
    Set<String> slots = new HashSet<>();
    for (String n : FarmingPlanner.kit(FarmingPlanner.plan(s), s).wear)
      assertTrue(slots.add(FarmingPlanner.slot(n)));
  }

  @Test
  public void cropsDoNotSplitLeaderboardsButTravelDoes() {
    Settings s = full();
    List<FarmingPlanner.Step> route = FarmingPlanner.plan(s);
    String before = FarmingPlanner.signature(route, FarmingPlanner.kit(route, s), s);
    s.crops.put("tree", "Yew");
    route = FarmingPlanner.plan(s);
    assertEquals(before, FarmingPlanner.signature(route, FarmingPlanner.kit(route, s), s));
    s.unlocks.remove("pouch");
    route = FarmingPlanner.plan(s);
    assertNotEquals(before, FarmingPlanner.signature(route, FarmingPlanner.kit(route, s), s));
  }

  @Test
  public void xpAndProtectionCostsRespondToSelections() {
    Settings s = full();
    s.kinds = new LinkedHashSet<>(Arrays.asList("calquat"));
    List<FarmingPlanner.Step> route = FarmingPlanner.plan(s);
    assertEquals((129.5 + 12096 + 291) * 1.025, FarmingPlanner.xp(route, s), .001);
    // Calquat fruit is always picked: the tree can only be dug up once it's bare.
    s.harvest = false;
    assertEquals((129.5 + 12096 + 291) * 1.025, FarmingPlanner.xp(route, s), .001);
    Long protectedNet = FarmingPlanner.net(route, s, n -> 100);
    s.protect = false;
    assertTrue(FarmingPlanner.net(route, s, n -> 100) > protectedNet);
  }

  @Test
  public void celastrusAlwaysNeedsCompostAndAxe() {
    Settings s = full();
    s.kinds = new LinkedHashSet<>(Arrays.asList("celastrus"));
    List<FarmingPlanner.Step> route = FarmingPlanner.plan(s);
    FarmingPlanner.Kit kit = FarmingPlanner.kit(route, s);
    assertTrue(kit.bring.containsKey("Axe"));
    assertTrue(kit.bring.containsKey("Bottomless compost bucket"));
    List<String> phases = Guidance.phases(route.get(0), s);
    assertTrue(phases.containsAll(Arrays.asList("Harvest", "Protect", "Compost")));
  }

  @Test
  public void chosenOrderIsKeptAndTravelRecalculated() {
    Settings s = new Settings();
    s.level = 99;
    List<FarmingPlanner.Step> suggested = FarmingPlanner.plan(s);
    String last = suggested.get(suggested.size() - 1).patch.id;
    s.order = new ArrayList<>(Collections.singletonList(last));
    List<FarmingPlanner.Step> moved = FarmingPlanner.plan(s);
    assertEquals(suggested.size(), moved.size());
    assertEquals(last, moved.get(0).patch.id);
    assertEquals(
        FarmingPlanner.choices(moved.get(0).patch, s).get(0).text, moved.get(0).travel.text);
  }

  @Test
  public void everyTravelStepHasAnIconMapping() {
    Settings s = full();
    s.kinds = new LinkedHashSet<>(FarmingData.CROPS.keySet());
    s.unlocks.addAll(
        Arrays.asList(
            "varlamore", "kastori", "whistle", "fairy", "summer", "digsite", "locus", "anglers"));
    for (FarmingPlanner.Step step : FarmingPlanner.plan(s))
      assertTrue(
          FarmingPlanner.travelItem(step.travel, s),
          FarmingData.id(FarmingPlanner.travelItem(step.travel, s)) > 0);
  }


  @Test
  public void faladorEliteDiaryMakesTheParkTreeDiseaseFree() {
    Settings s = new Settings();
    s.level = 99;
    s.unlocks.add("faladorElite");
    FarmingPlanner.Step park =
        FarmingPlanner.plan(s).stream().filter(x -> x.patch.id.equals("falador")).findFirst().get();
    List<String> phases = Guidance.phases(park, s);
    assertFalse(phases.contains("Protect"));
    assertFalse(phases.contains("Compost"));
  }
}