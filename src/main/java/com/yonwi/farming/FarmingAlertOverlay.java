package com.yonwi.farming;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A big pulsing message across the top of the game screen for things you mustn't miss, e.g.
 * picking herbs without your magic secateurs wielded.
 */
final class FarmingAlertOverlay extends Overlay {
  private static final Color TEXT = new Color(255, 215, 90);

  private final FarmingPlugin plugin;
  private final FarmingConfig config;
  private final Client client;

  @Inject
  FarmingAlertOverlay(FarmingPlugin plugin, FarmingConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_WIDGETS);
  }

  @Override
  public Dimension render(Graphics2D g) {
    if (!config.highlight() || client.getGameState() != GameState.LOGGED_IN) return null;
    String message = plugin.secateursAlert() ? "Wield your magic secateurs!" : null;
    if (message == null) return null;
    // Pulse between half and full strength about once a second.
    double phase = (System.currentTimeMillis() % 1000) / 1000.0;
    int alpha = (int) (140 + 115 * (0.5 + 0.5 * Math.sin(phase * 2 * Math.PI)));
    g.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 28f));
    FontMetrics metrics = g.getFontMetrics();
    int x = (client.getCanvasWidth() - metrics.stringWidth(message)) / 2;
    int y = client.getCanvasHeight() / 4;
    g.setColor(new Color(0, 0, 0, alpha));
    g.drawString(message, x + 2, y + 2);
    g.setColor(new Color(TEXT.getRed(), TEXT.getGreen(), TEXT.getBlue(), alpha));
    g.drawString(message, x, y);
    return null;
  }
}
