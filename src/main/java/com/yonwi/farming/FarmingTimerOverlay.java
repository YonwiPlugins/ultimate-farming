package com.yonwi.farming;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** The run timer on the game screen, while a run is timed. Off unless turned on in settings. */
final class FarmingTimerOverlay extends OverlayPanel {
  private final FarmingPlugin plugin;
  private final FarmingConfig config;

  @Inject
  FarmingTimerOverlay(FarmingPlugin plugin, FarmingConfig config) {
    this.plugin = plugin;
    this.config = config;
    setPosition(OverlayPosition.TOP_LEFT);
  }

  @Override
  public Dimension render(Graphics2D g) {
    String time = plugin.runTime();
    if (!config.timerOverlay() || time == null) return null;
    panelComponent.getChildren().add(LineComponent.builder().left("Run time").right(time).build());
    return super.render(g);
  }
}
