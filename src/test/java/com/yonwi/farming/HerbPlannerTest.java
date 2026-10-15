package com.yonwi.farming;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class HerbPlannerTest {
  private Settings setup() {
    Settings s = new Settings();
    s.level = 99;
    s.kinds = new LinkedHashSet<>(Arrays.asList("herb"));
    s.unlocks.addAll(Arrays.asList("herbTroll", "herbWeiss", "herbHarmony", "herbFortis", "herbPriest", "herbBasalt", "herbHarmonyTab", "herbEcto", "herbXeric", "herbArdy", "herbExplorer", "whistle"));
    s.crops.put("herb", "Ranarr");
    return s;
  }
  @Test public void tenPatchRouteWithoutNoting() {
    List<FarmingPlanner.Step> r = FarmingPlanner.plan(setup());
    assertEquals(10, r.size());
    assertEquals("herbtroll", r.get(0).patch.id);
    assertEquals("herbweiss", r.get(1).patch.id);
    assertEquals("herbharmony", r.get(2).patch.id);
    assertEquals("herbguild", r.get(9).patch.id);
    for (FarmingPlanner.Step step : r) assertFalse(Guidance.phases(step, setup()).stream().anyMatch(p -> p.startsWith("Note")));
  }
  @Test public void herbKitIgnoresGardenerProtectionAndTagsAllSupplies() {
    Settings s=setup(); List<FarmingPlanner.Step> r=FarmingPlanner.plan(s);
    FarmingPlanner.Kit k=FarmingPlanner.kit(r,s);
    assertEquals(Integer.valueOf(10),k.bring.get("Ranarr seed"));
    assertEquals(Integer.valueOf(1),k.bring.get("Bottomless compost bucket"));
    assertTrue(k.bring.containsKey("Seed dibber")); assertFalse(k.bring.containsKey("Coins"));
    assertTrue(k.wear.contains("Magic secateurs"));
    String tags=FarmingPlanner.tags(k,s);
    for(String n:k.bring.keySet()) {assertTrue(n,FarmingData.id(n)>0); assertTrue(tags.contains(","+FarmingData.IDS.get(n)[0]));}
  }
  @Test public void ownedHerbSackJoinsTheHerbKitAndBankTag() {
    Settings s=setup(); FarmingPlanner.Kit k=FarmingPlanner.kit(FarmingPlanner.plan(s),s);
    assertFalse(k.bring.containsKey("Herb sack"));
    s.unlocks.add("herbSack"); k=FarmingPlanner.kit(FarmingPlanner.plan(s),s);
    assertEquals(Integer.valueOf(1),k.bring.get("Herb sack"));
    String tags=FarmingPlanner.tags(k,s);
    // Shut, open and silk sacks all show in the bank filter.
    for(int id:FarmingData.IDS.get("Herb sack")) assertTrue(tags.contains(","+id));
  }
  @Test public void herbEconomicsAndPhasesAreIndependentOfFruitToggle() {
    Settings s=setup();s.harvest=false;s.farmer=false;s.unlocks.remove("farmingCape");
    List<FarmingPlanner.Step> r=FarmingPlanner.plan(s);
    // OSRS wiki herb calculator, Ranarr at 99 with magic secateurs (no farming cape):
    // chance to save = (88 + 1) / 256, 6 lives with ultracompost, survival (125/128)^3.
    double protectedYield = 6 / (1 - 89 / 256.0);
    double survival = Math.pow(125 / 128.0, 3);
    // Troll Stronghold, Weiss and Harmony are disease-free; the other 7 patches can die.
    double xp = 3 * (protectedYield * 30.5 + 27) + 7 * (protectedYield * survival * 30.5 + 27 * survival);
    assertEquals(xp, FarmingPlanner.xp(r,s), .001);
    long net = 0;
    for (FarmingPlanner.Step step : r) {
      boolean safe = Arrays.asList("herbtroll","herbweiss","herbharmony").contains(step.patch.id);
      net += -100 - 50 + Math.round(100 * (safe ? protectedYield : protectedYield * survival));
    }
    assertEquals(Long.valueOf(net),FarmingPlanner.net(r,s,n -> 100));
    List<String> p=Guidance.phases(r.get(1),s);
    assertEquals(Arrays.asList("Travel","Harvest","Plant","Compost"),p);
    // The dibber stays in the kit but is never clicked, so planting only highlights the seed.
    assertFalse(new Guidance(r.get(1),s,1,10,"Plant").items.contains("Seed dibber"));
  }
  @Test public void farmingCapeIsWornAndResurrectCropsUsesTheWikiFormula() {
    Settings s=setup();s.unlocks.add("cape");s.magic=99;
    List<FarmingPlanner.Step> r=FarmingPlanner.plan(s);
    FarmingPlanner.Kit k=FarmingPlanner.kit(r,s);
    assertTrue(k.wear.contains("Farming cape"));
    assertFalse(k.wear.contains("Construction cape"));
    assertTrue(FarmingPlanner.farmingCapeWorn(r,s));
    // 50% at 78 Magic, 75% at 99.
    assertEquals(.5, FarmingPlanner.resurrectChance(78), 1e-9);
    assertEquals(.75, FarmingPlanner.resurrectChance(99), 1e-9);
    assertTrue(s.resurrects(r));
    assertEquals(Integer.valueOf(50), k.bring.get("Earth rune")); // 2 casts' worth
    Patch falador = r.stream().filter(x -> x.patch.id.equals("herbfalador")).findFirst().get().patch;
    assertTrue(FarmingPlanner.herbSurvival(falador,s,true) > FarmingPlanner.herbSurvival(falador,s,false));
  }

  @Test public void mixedRoutesKeepHerbsAndTreesAndGuildTransfers() {
    Settings s=setup();s.kinds.add("tree");List<FarmingPlanner.Step> r=FarmingPlanner.plan(s);
    assertTrue(r.stream().anyMatch(x -> x.patch.kind.equals("tree")));
    assertEquals(10,r.stream().filter(x -> x.patch.kind.equals("herb")).count());
  }
}
