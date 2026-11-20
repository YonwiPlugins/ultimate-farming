package com.yonwi.farming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.Test;

public class DiseasedHerbsTest {
  private static StepProgress.PatchLook look(String name, String... actions) {
    return new StepProgress.PatchLook(name, actions);
  }

  @Test
  public void diseasedHerbsHoldHarvestAndPlant() {
    StepProgress.PatchLook diseased = look("Diseased herbs", "Cure", "Inspect", "Guide");
    assertTrue(diseased.diseased);
    assertFalse(StepProgress.stepDone("Harvest", diseased));
    assertFalse(StepProgress.stepDone("Plant", diseased));
  }

  @Test
  public void deadHerbsAreNotDiseased() {
    assertFalse(look("Dead herbs", "Clear", "Inspect").diseased);
  }

  @Test
  public void dugUpPatchMovesOnToPlanting() {
    StepProgress.PatchLook empty = look("Herb patch", "Rake", "Inspect", "Guide");
    assertTrue(StepProgress.stepDone("Harvest", empty));
    assertFalse(StepProgress.stepDone("Plant", empty));
  }

  @Test
  public void choiceDecidesTheAction() {
    assertEquals("dig", Guidance.diseasedAction("dig", true));
    assertEquals("cure", Guidance.diseasedAction("leprechaun", true));
    assertEquals("fetch", Guidance.diseasedAction("leprechaun", false));
    assertEquals("cure", Guidance.diseasedAction("carry", true));
    assertEquals("dig", Guidance.diseasedAction("carry", false));
    assertEquals("dig", Guidance.diseasedAction(null, false));
  }

  @Test
  public void plantCureOnlyInTheKitWhenCarried() {
    Settings settings = new Settings();
    settings.level = 99;
    settings.kinds = new LinkedHashSet<>(Arrays.asList("herb"));
    assertFalse(FarmingPlanner.kit(FarmingPlanner.plan(settings), settings).bring.containsKey("Plant cure"));
    settings.diseasedHerbs = "leprechaun";
    assertFalse(FarmingPlanner.kit(FarmingPlanner.plan(settings), settings).bring.containsKey("Plant cure"));
    settings.diseasedHerbs = "carry";
    assertEquals(
        Integer.valueOf(1), FarmingPlanner.kit(FarmingPlanner.plan(settings), settings).bring.get("Plant cure"));
  }
}
