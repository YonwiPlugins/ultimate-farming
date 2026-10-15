package com.yonwi.farming;

import static com.yonwi.farming.PanelWidgets.addCard;
import static com.yonwi.farming.PanelWidgets.column;
import static com.yonwi.farming.PanelWidgets.html;
import static com.yonwi.farming.PanelWidgets.number;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/** The Kit tab: what to wear (laid out like the equipment screen) and what to bring. */
final class KitTab {
  /** Equipment slots in the worn equipment screen's 3 × 5 layout ("" for a gap). */
  private static final String[] WORN_LAYOUT = {
    "", "head", "", "cape", "neck", "weapon", "", "body", "", "", "legs", "", "hands", "feet", "ring"
  };

  private final FarmingPanel panel;
  private final ItemManager items;
  private final ClientThread clientThread;
  private final JPanel page = column();

  KitTab(FarmingPanel panel, ItemManager items, ClientThread clientThread) {
    this.panel = panel;
    this.items = items;
    this.clientThread = clientThread;
  }

  JPanel page() {
    return page;
  }

  void render() {
    page.removeAll();
    page.add(Style.section("Wear"));
    addCard(page, wearCard());
    page.add(Style.section("Bring"));
    addCard(page, bringCard());
    page.add(Box.createVerticalGlue());
    page.revalidate();
  }

  private JPanel wearCard() {
    JPanel gear = new JPanel(new GridLayout(5, 3, 3, 3));
    gear.setOpaque(false);
    Map<String, String> slots = new HashMap<>();
    for (String name : panel.kit().wear) slots.put(FarmingPlanner.slot(name), name);
    for (String slot : WORN_LAYOUT) {
      JLabel cell = new JLabel();
      cell.setHorizontalAlignment(SwingConstants.CENTER);
      cell.setPreferredSize(new Dimension(40, 38));
      if (!slot.isEmpty()) {
        cell.setOpaque(true);
        cell.setBackground(Style.SELECT_BACKGROUND);
        String name = slots.get(slot);
        if (name != null) panel.pictures().full(cell, name);
      }
      gear.add(cell);
    }
    JPanel card = Style.card();
    card.add(gear);
    return card;
  }

  private JPanel bringCard() {
    JPanel inventory = new JPanel(new GridLayout(0, 4, 3, 3));
    inventory.setOpaque(false);
    List<Map.Entry<String, Integer>> entries = new ArrayList<>(panel.kit().bring.entrySet());
    entries.sort(Comparator.comparingInt(entry -> entry.getKey().endsWith("sapling") ? 0 : 1));
    for (Map.Entry<String, Integer> entry : entries) {
      String name = entry.getKey();
      int repeats = InventorySpace.squares(name, entry.getValue(), panel.settings());
      // (A pouch rune takes no square: it's shown on the pouch.)
      for (int i = 0; i < repeats; i++) inventory.add(bringCell(name, entry.getValue(), repeats));
    }
    JPanel card = Style.card();
    card.add(inventory);
    return card;
  }

  private JPanel bringCell(String name, int quantity, int repeats) {
    JPanel cell = new JPanel(new BorderLayout());
    cell.setOpaque(false);
    if (name.equals("Rune pouch")) cell.add(pouchIcon(cell), BorderLayout.CENTER);
    else {
      JLabel picture = new JLabel();
      picture.setHorizontalAlignment(SwingConstants.CENTER);
      panel.pictures().full(picture, name);
      cell.add(picture, BorderLayout.CENTER);
      cell.setToolTipText(name + " × " + quantity);
    }
    JLabel amount = new JLabel(repeats == 1 && quantity > 1 ? number(quantity) : " ");
    amount.setFont(Style.small());
    amount.setForeground(Style.MUTED);
    amount.setHorizontalAlignment(SwingConstants.CENTER);
    cell.add(amount, BorderLayout.SOUTH);
    cell.setPreferredSize(new Dimension(38, 46));
    return cell;
  }

  /**
   * The rune pouch with small icons of its runes drawn over it. Uses the pouch's real contents when
   * logged in, otherwise the runes the run needs. Hovering lists them.
   */
  private JComponent pouchIcon(JPanel cell) {
    AccountScan.Result account = panel.account();
    boolean known = account != null && !account.pouch.isEmpty();
    Map<Integer, Integer> runes = new LinkedHashMap<>();
    if (known) runes.putAll(account.pouch);
    else
      for (String rune : InventorySpace.POUCH_RUNES)
        if (panel.kit().bring.containsKey(rune)) runes.put(FarmingData.id(rune), 0);
    BufferedImage[] images = new BufferedImage[runes.size() + 1];
    JComponent icon =
        new JComponent() {
          @Override
          protected void paintComponent(Graphics g) {
            int x = (getWidth() - 36) / 2;
            int y = (getHeight() - 32) / 2;
            if (images[0] != null) g.drawImage(images[0], x, y, null);
            // Runes in rows of three across the bottom of the pouch, at about a third of full size.
            for (int i = 1; i < images.length; i++)
              if (images[i] != null)
                g.drawImage(images[i], x + (i - 1) % 3 * 12, y + 20 - (i - 1) / 3 * 11, 14, 12, null);
          }
        };
    icon.setPreferredSize(new Dimension(36, 32));
    List<Integer> ids = new ArrayList<>();
    ids.add(panel.pictures().shownId("Rune pouch"));
    ids.addAll(runes.keySet());
    clientThread.invoke(
        () -> {
          if (panel.isDisposed()) return;
          List<String> named = new ArrayList<>();
          for (int i = 0; i < ids.size(); i++) {
            AsyncBufferedImage image = items.getImage(ids.get(i));
            images[i] = image;
            image.onLoaded(icon::repaint);
            if (i > 0) {
              int amount = runes.get(ids.get(i));
              named.add(
                  html(items.getItemComposition(ids.get(i)).getName())
                      + (amount > 0 ? " × " + number(amount) : ""));
            }
          }
          AccountScan.Result now = panel.account();
          boolean real = now != null && !now.pouch.isEmpty();
          String tip =
              "<html>Rune pouch" + (real ? "" : " (needs)") + "<br>" + String.join("<br>", named) + "</html>";
          SwingUtilities.invokeLater(
              () -> {
                cell.setToolTipText(tip);
                icon.repaint();
              });
        });
    return icon;
  }
}
