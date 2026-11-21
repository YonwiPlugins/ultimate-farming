package com.yonwi.farming;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Test;

public class GuildOrderTest {
  /** The Farming Guild patches of a suggested route, in the order visited. */
  private static List<String> guildOrder(String... kinds) {
    Settings settings = new Settings();
    settings.level = 99;
    settings.kinds = new LinkedHashSet<>(Arrays.asList(kinds));
    List<String> order = new ArrayList<>();
    for (FarmingPlanner.Step step : FarmingPlanner.plan(settings))
      if (step.patch.id.startsWith("guild") || step.patch.id.equals("herbguild")) order.add(step.patch.id);
    return order;
  }

  @Test
  public void fruitTreeBeforeTheRedwood() {
    assertEquals(Arrays.asList("guildtree", "guildfruit", "guildredwood"), guildOrder("tree", "fruit", "redwood"));
  }

  @Test
  public void allFourTreesInOneLoop() {
    assertEquals(
        Arrays.asList("guildtree", "guildcelastrus", "guildfruit", "guildredwood"),
        guildOrder("tree", "fruit", "redwood", "celastrus"));
  }

  @Test
  public void walkCostFollowsDistance() {
    double near = TravelChoices.guildWalkCost("guildfruit", "guildredwood");
    double far = TravelChoices.guildWalkCost("guildtree", "guildredwood");
    assertEquals(true, near < far);
    assertEquals(near, TravelChoices.guildWalkCost("guildredwood", "guildfruit"), 1e-9);
  }
}
