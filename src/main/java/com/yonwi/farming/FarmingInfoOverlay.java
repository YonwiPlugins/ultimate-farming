package com.yonwi.farming;

import java.awt.*;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.*;

final class FarmingInfoOverlay extends OverlayPanel {
  private static final Color TITLE = new Color(188, 213, 191),
      DONE = new Color(120, 130, 122),
      WARN = new Color(255, 120, 90);
  private final FarmingPlugin plugin;
  private final FarmingConfig config;
  private final Client client;

  @Inject
  FarmingInfoOverlay(FarmingPlugin plugin, FarmingConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.TOP_LEFT);
    setPriority(OverlayPriority.LOW);
    panelComponent.setPreferredSize(new Dimension(240, 0));
  }

  @Override
  public Dimension render(Graphics2D g) {
    Guidance step = plugin.guide();
    if (!config.infoBox() || client.getGameState() != GameState.LOGGED_IN) return null;
    panelComponent.getChildren().clear();
    if (plugin.bankUnknown()) {
      // Until the bank has been seen, the route may be built on guessed unlocks.
      title("Ultimate Farming");
      wrap("Open your bank once so your teleports and unlocks can be worked out.", null);
      return super.render(g);
    }
    List<String> withdraw = plugin.missingItems();
    if (step == null && !withdraw.isEmpty()) {
      // Bank first: list what to withdraw instead of the first teleport.
      title("Bank first");
      for (String item : withdraw) wrap("• " + item, WARN);
      // How to switch spellbook, step by step.
      String how = plugin.switchHint();
      if (how != null) wrap(how, FarmingInventoryOverlay.TARGET);
      String pouch = plugin.pouchHint();
      if (pouch != null) wrap(pouch, FarmingInventoryOverlay.TARGET);
      return super.render(g);
    }
    if (step == null && plugin.idleMessage() != null) {
      title("Ultimate Farming");
      wrap(plugin.idleMessage(), FarmingInventoryOverlay.TARGET);
      return super.render(g);
    }
    if (step == null) return null;
    title(step.step + " / " + step.total + " · " + step.destination + " " + step.kind + " patch");
    // Kept plain: the current step in blue, done steps grey, the rest white, red only for warnings.
    if (step.nextDestination == null) wrap("Last patch of the run!", null);
    if (step.phase.equals("Travel")) {
      if (step.spell != null && client.getVarbitValue(VarbitID.SPELLBOOK) != 0)
        wrap("Switch to the standard spellbook first", WARN);
    }
    for (int i = 0; i < step.phases.size(); i++) {
      Color c =
          i < step.phaseIndex ? DONE : i == step.phaseIndex ? FarmingInventoryOverlay.TARGET : null;
      String label =
          plugin.deadPatch() && step.phases.get(i).equals("Harvest") && step.kind.equals("herb")
              ? plugin.resurrectNow() ? "Cast Resurrect Crops on the dead herbs" : "Clear the dead herbs"
              : plugin.deadPatch() && step.phases.get(i).equals("Clear")
                  ? "Clear the dead tree yourself (spade)"
                  : step.labels.get(i);
      wrap((i + 1) + ". " + label, c);
    }
    String note = plugin.noteAdvice();
    if (note != null) wrap(note, WARN);
    return super.render(g);
  }

  private void title(String text) {
    StringBuilder b = new StringBuilder();
    for (String word : text.split(" ")) {
      if (b.length() + word.length() > 30) {
        panelComponent.getChildren().add(TitleComponent.builder().text(b.toString()).color(TITLE).build());
        b.setLength(0);
      }
      if (b.length() > 0) b.append(' ');
      b.append(word);
    }
    if (b.length() > 0)
      panelComponent.getChildren().add(TitleComponent.builder().text(b.toString()).color(TITLE).build());
  }

  private void line(String text, Color color) {
    LineComponent.LineComponentBuilder b = LineComponent.builder().left(text);
    if (color != null) b.leftColor(color);
    panelComponent.getChildren().add(b.build());
  }

  private void wrap(String text, Color color) {
    StringBuilder b = new StringBuilder();
    for (String word : text.split(" ")) {
      if (b.length() + word.length() > 33) {
        line(b.toString(), color);
        b.setLength(0);
      }
      if (b.length() > 0) b.append(' ');
      b.append(word);
    }
    if (b.length() > 0) line(b.toString(), color);
  }
}
