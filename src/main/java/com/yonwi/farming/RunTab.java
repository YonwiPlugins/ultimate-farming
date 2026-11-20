package com.yonwi.farming;

import static com.yonwi.farming.PanelWidgets.CARD_TEXT;
import static com.yonwi.farming.PanelWidgets.addCard;
import static com.yonwi.farming.PanelWidgets.collapsibleHeading;
import static com.yonwi.farming.PanelWidgets.column;
import static com.yonwi.farming.PanelWidgets.fixHeight;
import static com.yonwi.farming.PanelWidgets.html;
import static com.yonwi.farming.PanelWidgets.label;
import static com.yonwi.farming.PanelWidgets.muted;
import static com.yonwi.farming.PanelWidgets.number;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * The Run tab. Between runs: the run types, crops and patches for the next run. During a run: the
 * guide card and the patches in order, with a reorder list on double-click.
 */
final class RunTab {
  private final FarmingPanel panel;
  private final ItemManager items;
  private final ClientThread clientThread;
  private final JPanel page = column();
  /** True while the tab shows the reorder list instead of the run. */
  private boolean reordering;
  /** The patch list is shown (it starts collapsed each session). */
  private boolean patchesOpen;
  /** "Don't warn this run" was pressed on the Attas warning; cleared once a run starts. */
  private boolean attasDismissed;

  RunTab(FarmingPanel panel, ItemManager items, ClientThread clientThread) {
    this.panel = panel;
    this.items = items;
    this.clientThread = clientThread;
  }

  JPanel page() {
    return page;
  }

  void reset() {
    reordering = false;
  }

  void render() {
    page.removeAll();
    if (panel.runStarted()) attasDismissed = false;
    // Between runs: choose the run here, then "Prepare run" at the bottom.
    if (!panel.prepared() && !panel.runStarted() && !reordering) {
      addRunChoices();
      addAttasWarning();
      page.add(Box.createRigidArea(new Dimension(0, 4)));
      page.add(panel.guideCard().card());
      finish();
      return;
    }
    page.add(panel.guideCard().card());
    page.add(Box.createRigidArea(new Dimension(0, 8)));
    List<FarmingPlanner.Step> route = panel.route();
    if (route.isEmpty()) page.add(muted("Choose trees and travel options in Setup."));
    else if (reordering) {
      renderReorder();
      return;
    } else {
      JLabel patches =
          Style.section(route.size() + " patches" + (panel.settings().order.isEmpty() ? "" : " · your order"));
      patches.setToolTipText("Double-click a patch to reorder");
      page.add(patches);
    }
    for (int i = 0; i < route.size(); i++) addCard(page, patchRow(route.get(i), i));
    finish();
  }

  private void finish() {
    page.add(Box.createVerticalGlue());
    page.revalidate();
    page.repaint();
  }

  /** One patch of the run: number, travel picture and route; click to guide it. */
  private JPanel patchRow(FarmingPlanner.Step step, int index) {
    Settings settings = panel.settings();
    JPanel row = Style.card();
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    boolean done = settings.completed.contains(step.patch.id);
    boolean guided = index == panel.guideIndex() && !done;
    row.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(guided ? Style.GOLD : Style.CARD_BORDER),
            new EmptyBorder(6, 6, 6, 4)));
    JLabel name =
        label(
            "<html><div style=\"width:" + (CARD_TEXT - 50) + "px\">"
                + (done ? "<s>" : "")
                + html(step.travel.text)
                + (done ? "</s>" : "")
                + "</div></html>");
    if (guided) name.setFont(Style.bold());
    name.setForeground(done ? Style.DONE_TEXT : Style.INK);
    // Number badge and the travel item's picture on the left, a chevron on the right.
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    left.setOpaque(false);
    int badge = guided ? PanelWidgets.NumberBadge.GUIDED : done ? PanelWidgets.NumberBadge.DONE : PanelWidgets.NumberBadge.PLAIN;
    left.add(new PanelWidgets.NumberBadge(index + 1, badge));
    JLabel item = new JLabel();
    item.setPreferredSize(new Dimension(24, 22));
    panel.pictures().small(item, FarmingPlanner.travelItem(step.travel, settings), 24, 21);
    left.add(item);
    JPanel line = new JPanel(new BorderLayout(4, 0));
    line.setOpaque(false);
    line.add(left, BorderLayout.WEST);
    line.add(name, BorderLayout.CENTER);
    line.add(new JLabel(Icons.get("chevron")), BorderLayout.EAST);
    line.setAlignmentX(Component.LEFT_ALIGNMENT);
    // Single click guides this patch; double-click opens the reorder list.
    MouseAdapter pick =
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() >= 2 && !panel.runStarted()) {
              reordering = true;
              render();
              return;
            }
            panel.guidePatch(index);
          }
        };
    row.addMouseListener(pick);
    name.addMouseListener(pick);
    item.addMouseListener(pick);
    row.add(line);
    name.setToolTipText(patchTip(step, settings));
    if (done) row.setBackground(Style.DONE);
    return row;
  }

  private static String patchTip(FarmingPlanner.Step step, Settings settings) {
    Crop crop = settings.crop(step.patch.kind);
    if (step.patch.kind.equals("herb"))
      return "Harvest, plant " + crop.sapling + ", apply ultracompost. "
          + (step.noteBefore
              ? "Note carried herbs before picking; note again afterwards."
              : step.noteAfter ? "Note herbs here as needed." : "Carry herbs to the next nearby leprechaun; note here if full.");
    String harvest = settings.harvests(step.patch.kind) && !crop.fruit.isEmpty() ? "harvest, " : "";
    String care =
        settings.diseaseFree(step.patch)
            ? ". Disease-free: no payment or compost"
            : settings.pays(step.patch) ? ". Pay " + crop.paymentQty + " " + crop.payment : ". Use ultracompost";
    return "Check health, " + harvest + "clear and plant " + crop.sapling + care;
  }

  /** A compact list with up/down arrows; travel between patches is recalculated on each move. */
  private void renderReorder() {
    List<FarmingPlanner.Step> route = panel.route();
    page.add(Style.section("Reorder run"));
    JPanel list = Style.card();
    for (int i = 0; i < route.size(); i++) list.add(reorderRow(route, i));
    addCard(page, list);
    JPanel actions = new JPanel(new GridLayout(1, 2, 5, 0));
    actions.setOpaque(false);
    JButton done = new JButton("Done");
    JButton suggested = new JButton("Suggested");
    suggested.setToolTipText("Go back to the plugin's suggested order");
    suggested.setEnabled(!panel.settings().order.isEmpty());
    done.addActionListener(
        e -> {
          reordering = false;
          render();
        });
    suggested.addActionListener(e -> panel.useSuggestedOrder());
    actions.add(done);
    actions.add(suggested);
    fixHeight(actions);
    page.add(actions);
    finish();
  }

  private JPanel reorderRow(List<FarmingPlanner.Step> route, int at) {
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(3, 0, 3, 0));
    Patch patch = route.get(at).patch;
    JLabel name =
        label(
            "<html><div style=\"width:" + (CARD_TEXT - 50) + "px\">"
                + (at + 1) + ". " + html(patch.name) + " · " + html(patch.kind)
                + "</div></html>");
    name.setFont(Style.small());
    row.add(name, BorderLayout.CENTER);
    JPanel arrows = new JPanel(new GridLayout(1, 2, 2, 0));
    arrows.setOpaque(false);
    JButton up = new JButton("▲");
    JButton down = new JButton("▼");
    for (JButton button : new JButton[] {up, down}) {
      button.setMargin(new Insets(0, 2, 0, 2));
      button.setFont(Style.small());
      button.setFocusPainted(false);
    }
    up.setEnabled(at > 0);
    down.setEnabled(at < route.size() - 1);
    up.addActionListener(e -> movePatch(at, at - 1));
    down.addActionListener(e -> movePatch(at, at + 1));
    arrows.add(up);
    arrows.add(down);
    row.add(arrows, BorderLayout.EAST);
    return row;
  }

  /** Moves a patch to a new position and keeps that order for later runs. */
  private void movePatch(int from, int to) {
    List<String> ids = new ArrayList<>();
    for (FarmingPlanner.Step step : panel.route()) ids.add(step.patch.id);
    ids.add(to, ids.remove(from));
    panel.setOrder(ids);
  }

  /**
   * "Your run": run types with their crops, then which patches. This is what changes from run to
   * run, so it sits right above "Prepare run".
   */
  private void addRunChoices() {
    page.add(Style.section("Your run"));
    if (panel.clientLevel() == 0) page.add(muted("Log in to read your Farming level."));
    if (panel.runStarted()) page.add(muted("Finish or reset your run to change it."));
    for (String kind : FarmingData.CROPS.keySet()) addCard(page, runTypeCard(kind));
    FarmingPlanner.Kit kit = panel.kit();
    int slots = kit == null ? 0 : InventorySpace.needed(panel.route(), kit, panel.settings());
    if (slots > InventorySpace.INVENTORY_SLOTS) {
      JLabel over = muted("Too many items needed to fit in your inventory");
      over.setForeground(PanelWidgets.WARN);
      page.add(over);
    }
    addPatchChoices();
  }

  /**
   * Just above "Prepare run": no living Attas was seen at the Farming Guild, so herbs miss its 5%.
   * It never blocks the run, and can be hidden until the next one.
   */
  private void addAttasWarning() {
    Settings settings = panel.settings();
    int level = panel.clientLevel() > 0 ? panel.clientLevel() : settings.level;
    if (attasDismissed || !panel.attasWarningOn() || !settings.warnNoAttas(panel.route(), level)) return;
    JPanel card = Style.card();
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PanelWidgets.WARN), new EmptyBorder(7, 7, 7, 7)));
    card.setToolTipText("As of your last visit: an Attas that died since then won't show here");
    JLabel title = label("No Attas planted");
    title.setFont(Style.bold());
    title.setForeground(PanelWidgets.WARN);
    card.add(title);
    card.add(Box.createVerticalStrut(3));
    card.add(
        muted(
            "+5% herbs if you plant one at the Farming Guild first. Last checked "
                + daysAgo(settings.attasCheckedAt) + "."));
    card.add(Box.createVerticalStrut(6));
    JButton hide = new JButton("Don't warn this run");
    hide.setFont(Style.small());
    hide.setFocusPainted(false);
    hide.setAlignmentX(Component.LEFT_ALIGNMENT);
    hide.addActionListener(
        e -> {
          attasDismissed = true;
          render();
        });
    card.add(hide);
    addCard(page, card);
  }

  /** "today", "yesterday" or "3 days ago". */
  private static String daysAgo(long at) {
    long days = Math.max(0, (System.currentTimeMillis() - at) / 86_400_000L);
    return days == 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
  }

  /** One card per run type: tick to include it, then pick the crop from its drop-down. */
  private JPanel runTypeCard(String kind) {
    Settings settings = panel.settings();
    JPanel card = Style.card();
    boolean on = settings.kinds.contains(kind);
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(on ? Style.GOLD : Style.CARD_BORDER),
            new EmptyBorder(7, 7, 7, 14))); // keeps the tickbox off the right edge
    JCheckBox include = Style.tickbox(new JCheckBox("", on));
    include.setOpaque(false);
    // Adding this run type would need more than a full inventory: greyed out.
    boolean fits = on || panel.fitsWith(kind);
    include.setEnabled(!panel.runStarted() && fits);
    include.addActionListener(
        e ->
            panel.change(
                () -> {
                  if (include.isSelected()) settings.kinds.add(kind);
                  else settings.kinds.remove(kind);
                }));
    // Crop picture, then the name over its patch count, with the tickbox on the right.
    Crop shown = settings.crop(kind) != null ? settings.crop(kind) : FarmingData.CROPS.get(kind).get(0);
    JLabel picture = new JLabel();
    picture.setPreferredSize(new Dimension(26, 24));
    panel.pictures().small(picture, shown.sapling, 26, 23);
    long patches = FarmingData.PATCHES.stream().filter(patch -> patch.kind.equals(kind)).count();
    JLabel name = label(kindTitle(kind));
    name.setFont(Style.bold());
    JLabel count = new JLabel(patches + (patches == 1 ? " patch" : " patches"));
    count.setFont(Style.small());
    count.setForeground(Style.MUTED);
    JPanel words = new JPanel(new GridLayout(2, 1));
    words.setOpaque(false);
    words.add(name);
    words.add(count);
    JPanel head = new JPanel(new BorderLayout(8, 0));
    head.setOpaque(false);
    head.add(picture, BorderLayout.WEST);
    head.add(words, BorderLayout.CENTER);
    head.add(include, BorderLayout.EAST);
    head.setAlignmentX(Component.LEFT_ALIGNMENT);
    fixHeight(head);
    if (!fits) {
      name.setForeground(Style.DONE_TEXT);
      count.setForeground(Style.DONE_TEXT);
      picture.setEnabled(false);
      String tip = "Too many items needed to fit in your inventory";
      for (JComponent part : new JComponent[] {card, head, name, count, picture, include}) part.setToolTipText(tip);
    }
    card.add(head);
    if (on) addCropChoice(card, kind);
    return card;
  }

  private void addCropChoice(JPanel card, String kind) {
    Settings settings = panel.settings();
    JComboBox<Crop> crops =
        new JComboBox<>(
            FarmingData.CROPS.get(kind).stream().filter(crop -> crop.level <= settings.level).toArray(Crop[]::new));
    Style.combo(crops);
    crops.setSelectedItem(settings.crop(kind));
    crops.setEnabled(!panel.runStarted());
    crops.addActionListener(
        e -> {
          Crop crop = (Crop) crops.getSelectedItem();
          if (crop != null) panel.change(() -> settings.crops.put(kind, crop.name));
        });
    card.add(Box.createVerticalStrut(6));
    card.add(crops);
    addCropOptions(card, kind);
  }

  /**
   * "Protection payment?" and "Harvest?" for one run type. Hovering shows what each costs or earns
   * for this run at guide prices.
   */
  private void addCropOptions(JPanel card, String kind) {
    Settings settings = panel.settings();
    Crop crop = settings.crop(kind);
    if (crop == null || kind.equals("herb")) return;
    long patches = panel.route().stream().filter(step -> step.patch.kind.equals(kind)).count();
    card.add(Box.createVerticalStrut(6));
    JCheckBox protect = null;
    JCheckBox harvest = null;
    if (!crop.payment.isEmpty()) {
      protect =
          panel.settingBox(
              "Protection payment?",
              settings.protects(kind),
              on -> {
                if (on) settings.unprotected.remove(kind);
                else settings.unprotected.add(kind);
              });
      card.add(protect);
    }
    // (Not for Calquat: its fruit must be picked before the tree can be dug up.)
    if ((!crop.fruit.isEmpty() || kind.equals("celastrus")) && !kind.equals("calquat")) {
      harvest =
          panel.settingBox(
              "Harvest?",
              settings.harvests(kind),
              on -> {
                if (on) settings.unharvested.remove(kind);
                else settings.unharvested.add(kind);
              });
      card.add(harvest);
    }
    priceTips(crop, kind, patches, protect, harvest);
  }

  /** Fills in the tickboxes' price tooltips once guide prices are read on the client thread. */
  private void priceTips(Crop crop, String kind, long patches, JCheckBox protect, JCheckBox harvest) {
    clientThread.invoke(
        () -> {
          if (panel.isDisposed()) return;
          long pay = items.getItemPrice(FarmingData.id(crop.payment)) * (long) crop.paymentQty;
          boolean bark = kind.equals("celastrus");
          int count = bark ? (int) Math.round(FarmingPlanner.bark(panel.settings())) : 6;
          String produce = bark ? "Celastrus bark" : crop.fruit;
          long fruit = items.getItemPrice(FarmingData.id(produce)) * (long) count;
          String each = patches == 1 ? "" : " (" + patches + " patches)";
          String payTip =
              "Pay " + crop.paymentQty + " × " + crop.payment + " per patch: −"
                  + number(pay * Math.max(1, patches)) + " GP" + each
                  + ". Without it, ultracompost is used: " + diseaseRisk(crop) + ".";
          String fruitTip =
              (bark ? "Harvest about " : "Pick ") + count + " × " + produce + " per patch: +"
                  + number(fruit * Math.max(1, patches)) + " GP" + each + ".";
          SwingUtilities.invokeLater(
              () -> {
                if (protect != null) protect.setToolTipText(payTip);
                if (harvest != null) harvest.setToolTipText(fruitTip);
              });
        });
  }

  /**
   * Rough chance an ultracomposted, unprotected crop dies (gets diseased and isn't cured) while
   * growing, as 1 - (1 - p)^n from the OSRS wiki "Disease (Farming)" page. The wiki gives fruit
   * trees 2/128 over 4 cycles, maple 2/128 over 7 and magic 1/128 over 11 with ultracompost;
   * other trees use the maple rate over their own growth cycles (stages - 2).
   */
  private static String diseaseRisk(Crop crop) {
    double perCycle = 2 / 128.0;
    int[] growth = FarmingData.GROWTH.get(crop.name);
    int cycles = growth == null ? 4 : Math.max(1, growth[1] - 2);
    if (FarmingData.CROPS.get("fruit").contains(crop)) cycles = 4;
    else if (crop.name.equals("Magic")) perCycle = 1 / 128.0;
    double risk = 1 - Math.pow(1 - perCycle, cycles);
    return String.format(Locale.ROOT, "roughly %.0f%% chance of dying", Math.max(1, risk * 100));
  }

  /** The patches to visit, by run type; collapsed until clicked (most runs use every patch). */
  private void addPatchChoices() {
    Settings settings = panel.settings();
    long chosen =
        FarmingData.PATCHES.stream()
            .filter(patch -> settings.kinds.contains(patch.kind) && !settings.excluded.contains(patch.id))
            .count();
    // (The arrow is the same as the Records tables' collapse button.)
    page.add(
        collapsibleHeading(
            Style.section("Patches", "(" + chosen + " selected)"),
            patchesOpen,
            "Hide patches",
            "Choose which patches to visit",
            () -> {
              patchesOpen = !patchesOpen;
              render();
            }));
    if (!patchesOpen) return;
    JPanel patchCard = Style.card();
    // One heading per run type (only worth it when there's more than one), patches under it.
    boolean mixed = settings.kinds.size() > 1;
    for (String kind : FarmingData.CROPS.keySet()) {
      if (!settings.kinds.contains(kind)) continue;
      if (mixed) patchCard.add(groupHeading(kind, patchCard.getComponentCount() == 0));
      for (Patch patch : FarmingData.PATCHES)
        if (patch.kind.equals(kind))
          patchCard.add(
              panel.settingBox(
                  patch.name,
                  !settings.excluded.contains(patch.id),
                  on -> {
                    if (on) settings.excluded.remove(patch.id);
                    else settings.excluded.add(patch.id);
                  }));
    }
    if (patchCard.getComponentCount() > 0) addCard(page, patchCard);
  }

  /** "Tree patches", "Fruit tree patches", "Herb patches"… */
  private static JLabel groupHeading(String kind, boolean first) {
    String one = kind.equals("tree") ? "Tree" : kind.equals("fruit") ? "Fruit tree" : kindTitle(kind);
    JLabel group = label(one + " patches");
    group.setFont(Style.bold());
    group.setForeground(Style.GOLD);
    group.setBorder(new EmptyBorder(first ? 0 : 6, 0, 2, 0));
    group.setAlignmentX(Component.LEFT_ALIGNMENT);
    return group;
  }

  /** A run type's name for headings: "Trees", "Fruit trees", "Herb", "Calquat"… */
  private static String kindTitle(String kind) {
    if (kind.equals("fruit")) return "Fruit trees";
    if (kind.equals("tree")) return "Trees";
    return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
  }
}
