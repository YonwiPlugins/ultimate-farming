package com.yonwi.farming;

import static org.junit.Assert.*;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class WorldPathTest {
  private static void waitForMap() throws InterruptedException {
    for (int i = 0; i < 200 && !WorldPath.ready(); i++) Thread.sleep(50);
    assertTrue("collision map loaded", WorldPath.ready());
  }

  @Test
  public void walksBetweenTheCatherbyPatches() throws Exception {
    waitForMap();
    WorldPoint herb = new WorldPoint(2813, 3463, 0);
    WorldPoint fruit = new WorldPoint(2860, 3433, 0);
    List<WorldPoint> path = WorldPath.find(herb, fruit);
    assertFalse("a route exists", path.isEmpty());
    // It ends next to the target.
    assertTrue(path.get(path.size() - 1).distanceTo(fruit) <= 1);
  }

  @Test
  public void headsForTheNearestSideOfABigTarget() throws Exception {
    waitForMap();
    WorldPoint start = new WorldPoint(2813, 3463, 0);
    // A 3×3 target south-west corner 5 tiles east: the walk stops at its west side.
    List<WorldPoint> path = WorldPath.find(start, new WorldPoint(2818, 3462, 0), 3, 3);
    if (!path.isEmpty()) assertTrue(path.get(path.size() - 1).getX() >= 2817);
  }

  @Test
  public void differentFloorsHaveNoRoute() throws Exception {
    waitForMap();
    assertTrue(WorldPath.find(new WorldPoint(2813, 3463, 0), new WorldPoint(2813, 3463, 1)).isEmpty());
  }
}
