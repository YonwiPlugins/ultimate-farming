package com.yonwi.farming;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The box in the top left: bank first, the next run, or the guided patch's steps in order. */
final class FarmingInfoOverlay extends OverlayPanel {
  private static final Color TITLE = new Color(188, 213, 191);
  private static final Color DONE = new Color(120, 130, 122);
  private static final Color WARN = new Color(255, 120, 90);
  private static final int TITLE_WIDTH = 30;
  private static final int LINE_WIDTH = 33;
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
    if (plugin.account().bankUnknown()) {
      // Until the bank has been seen, the route may be built on guessed unlocks.
      title("Ultimate Farming");
      wrap("Open your bank once so your teleports and unlocks can be worked out.", null);
      return super.render(g);
    }
    List<String> withdraw = plugin.kit().missing();
    if (step == null && !withdraw.isEmpty()) {
      // Bank first: list what to withdraw instead of the first teleport.
      title("Bank first");
      for (String item : withdraw) wrap("• " + item, WARN);
      // How to switch spellbook, step by step.
      String how = plugin.spellbook().hint();
      if (how != null) wrap(how, FarmingInventoryOverlay.TARGET);
      String pouch = plugin.kit().pouchHint();
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
      Color color =
          i < step.phaseIndex ? DONE : i == step.phaseIndex ? FarmingInventoryOverlay.TARGET : null;
      wrap((i + 1) + ". " + stepLabel(step, i), color);
    }
    if (plugin.harvest().openSackNow())
      wrap("Open your herb sack first, so the herbs go in it", FarmingInventoryOverlay.TARGET);
    String note = plugin.noting().advice();
    if (note != null) wrap(note, WARN);
    return super.render(g);
  }

  /** A step's wording, or what to do instead when the crop has died. */
  private String stepLabel(Guidance step, int index) {
    String phase = step.phases.get(index);
    boolean dead = plugin.patch().dead();
    if (dead && phase.equals("Harvest") && step.kind.equals("herb"))
      return plugin.progress().resurrectNow() ? "Cast Resurrect Crops on the dead herbs" : "Clear the dead herbs";
    if (dead && phase.equals("Clear")) return "Clear the dead tree yourself (spade)";
    return step.labels.get(index);
  }

  private void title(String text) {
    for (String line : wrapWords(text, TITLE_WIDTH))
      panelComponent.getChildren().add(TitleComponent.builder().text(line).color(TITLE).build());
  }

  private void line(String text, Color color) {
    LineComponent.LineComponentBuilder line = LineComponent.builder().left(text);
    if (color != null) line.leftColor(color);
    panelComponent.getChildren().add(line.build());
  }

  private void wrap(String text, Color color) {
    for (String line : wrapWords(text, LINE_WIDTH)) line(line, color);
  }

  /** Text split into lines of about {@code width} characters, at spaces. */
  private static List<String> wrapWords(String text, int width) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      if (line.length() + word.length() > width) {
        lines.add(line.toString());
        line.setLength(0);
      }
      if (line.length() > 0) line.append(' ');
      line.append(word);
    }
    if (line.length() > 0) lines.add(line.toString());
    return lines;
  }
}
