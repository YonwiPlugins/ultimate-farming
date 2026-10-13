package com.yonwi.farming;

import net.runelite.client.config.*;

@ConfigGroup("yonwi-farming-companion")
public interface FarmingConfig extends Config {
  @ConfigItem(
      keyName = "infoBox",
      name = "On-screen guide",
      description = "Show current destination and manually selected action",
      position = 0)
  default boolean infoBox() {
    return true;
  }

  @ConfigItem(
      keyName = "highlight",
      name = "Highlight current targets",
      description =
          "Highlight the nearby patch, gardener or tool leprechaun for the selected action",
      position = 1)
  default boolean highlight() {
    return true;
  }

  @ConfigItem(
      keyName = "recordsShown",
      name = "Records rows shown",
      description = "Runs listed per Records table before \"Show more\"",
      position = 5)
  default int recordsShown() {
    return 10;
  }

  @ConfigItem(
      keyName = "netTotals",
      name = "Net XP and GP in banner",
      description = "Show the Farming XP and profit from all your recorded runs under the title",
      position = 6)
  default boolean netTotals() {
    return false;
  }

  @ConfigItem(
      keyName = "debug",
      name = "Debug box",
      description =
          "Show the guided patch, its in-game state and the last detected events, for testing",
      position = 4)
  default boolean debug() {
    return false;
  }
}
