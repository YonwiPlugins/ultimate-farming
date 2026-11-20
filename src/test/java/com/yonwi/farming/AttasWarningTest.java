package com.yonwi.farming;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Test;

public class AttasWarningTest {
  private Settings settings(String... kinds) {
    Settings settings = new Settings();
    settings.level = 99;
    settings.kinds = new LinkedHashSet<>(Arrays.asList(kinds));
    settings.attasCheckedAt = System.currentTimeMillis();
    return settings;
  }

  private static List<FarmingPlanner.Step> route(Settings settings) {
    return FarmingPlanner.plan(settings);
  }

  @Test
  public void warnsWhenHerbsAndNoAttasSeen() {
    Settings settings = settings("herb");
    assertTrue(settings.warnNoAttas(route(settings), 99));
  }

  @Test
  public void quietWithAttasPlanted() {
    Settings settings = settings("herb");
    settings.unlocks.add("attas");
    assertFalse(settings.warnNoAttas(route(settings), 99));
  }

  @Test
  public void quietBelowLevel76() {
    Settings settings = settings("herb");
    assertFalse(settings.warnNoAttas(route(settings), 75));
  }

  @Test
  public void quietUntilTheGuildHasBeenSeen() {
    Settings settings = settings("herb");
    settings.attasCheckedAt = null;
    assertFalse(settings.warnNoAttas(route(settings), 99));
  }

  @Test
  public void quietWithoutHerbsInTheRun() {
    Settings settings = settings("tree", "fruit");
    assertFalse(settings.warnNoAttas(route(settings), 99));
  }
}
