package com.yonwi.farming;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FarmingPluginTest {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(FarmingPlugin.class);
    RuneLite.main(args);
  }
}
