package com.yonwi.farming;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Sidebar look shared by every tab: a green gradient header, bordered cards, gold accents, rounded
 * drop-downs and a slim scrollbar.
 *
 * <p>The header, card, drop-down and scrollbar styling is adapted from the Hunter Rumours plugin's
 * PreferredLocationPreferencesPanel (https://github.com/geel9/runelite-hunter-rumours),
 * BSD-2-Clause, Copyright (c) 2024, Joshua Coffey. See THIRD_PARTY_NOTICES.md.
 */
final class Style {
  static final Color BG = ColorScheme.DARK_GRAY_COLOR;
  static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
  static final Color CARD_BORDER = new Color(62, 63, 65);
  static final Color GOLD = new Color(214, 163, 62);
  static final Color INK = Color.WHITE;
  static final Color MUTED = new Color(190, 190, 190);
  static final Color HERO_TEXT = new Color(205, 215, 207);
  static final Color DONE = new Color(64, 82, 67);
  static final Color DONE_TEXT = new Color(150, 170, 154);
  static final Color SELECT_BACKGROUND = new Color(42, 44, 46);
  static final Color SELECT_BACKGROUND_HOVER = new Color(49, 52, 54);
  static final Color SELECT_BORDER = new Color(76, 79, 82);
  static final Color SELECT_BORDER_ACTIVE = new Color(170, 128, 49);
  static final Color SCROLL_TRACK = new Color(31, 32, 34);
  static final Color SCROLL_THUMB = new Color(91, 94, 97);
  static final Color SCROLL_THUMB_HOVER = new Color(118, 121, 124);
  static final Color PRIMARY = new Color(62, 142, 65);
  static final Color PRIMARY_HOVER = new Color(75, 160, 78);

  private Style() {}

  static Font bold() {
    return FontManager.getRunescapeBoldFont();
  }

  static Font small() {
    return FontManager.getRunescapeSmallFont();
  }

  /** Header with an icon on the left of the title block and a small button on the right. */
  static JPanel hero(String title, JComponent subtitle, Icon icon, JComponent corner) {
    JPanel text = hero(title, subtitle);
    JPanel inner = new JPanel(new BorderLayout(8, 0));
    inner.setOpaque(false);
    for (Component part : text.getComponents()) text.remove(part);
    JPanel lines = new JPanel();
    lines.setOpaque(false);
    lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
    JLabel heading = new JLabel(title.toUpperCase());
    heading.setForeground(INK);
    heading.setFont(bold().deriveFont(Font.BOLD, 14f));
    lines.add(heading);
    if (subtitle != null) {
      subtitle.setForeground(HERO_TEXT);
      subtitle.setFont(small());
      subtitle.setBorder(new EmptyBorder(3, 0, 0, 0));
      lines.add(subtitle);
    }
    inner.add(new JLabel(icon), BorderLayout.WEST);
    inner.add(lines, BorderLayout.CENTER);
    if (corner != null) {
      JPanel top = new JPanel(new BorderLayout());
      top.setOpaque(false);
      top.add(corner, BorderLayout.NORTH);
      inner.add(top, BorderLayout.EAST);
    }
    text.setLayout(new BorderLayout());
    text.add(inner, BorderLayout.CENTER);
    return text;
  }

  /** A filled green button for the main action on a card (Start, Prepare run). */
  static JButton primary(String text, Icon icon) {
    JButton button =
        new JButton(text, icon) {
          @Override
          protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = !isEnabled() ? new Color(60, 74, 62) : getModel().isRollover() ? PRIMARY_HOVER : PRIMARY;
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g.dispose();
            super.paintComponent(graphics);
          }
        };
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setRolloverEnabled(true);
    button.setForeground(Color.WHITE);
    button.setFont(bold());
    button.setBorder(new EmptyBorder(6, 8, 6, 8));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return button;
  }

  /**
   * Every tickbox goes through here so they can be styled in one place. They keep RuneLite's own
   * look for now (custom light boxes looked out of place).
   */
  static JCheckBox tickbox(JCheckBox box) {
    return box;
  }

  /** Gold section heading in normal case, with an optional grey italic note after it. */
  static JLabel section(String text, String note) {
    JLabel label = section(text);
    if (note != null)
      label.setText("<html>" + text + " <i><font color='#a0a0a0'>" + note + "</font></i></html>");
    return label;
  }

  /** Green gradient header with a capitalised title. */
  static JPanel hero(String title, JComponent subtitle) {
    JPanel hero =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(
                new GradientPaint(
                    0, 0, new Color(50, 83, 58), getWidth(), getHeight(), new Color(35, 36, 38)));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g.dispose();
          }
        };
    hero.setOpaque(false);
    hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
    hero.setBorder(new EmptyBorder(13, 12, 12, 12));
    hero.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel heading = new JLabel(title.toUpperCase());
    heading.setForeground(INK);
    heading.setFont(bold().deriveFont(Font.BOLD, 14f));
    heading.setAlignmentX(Component.LEFT_ALIGNMENT);
    hero.add(heading);
    if (subtitle != null) {
      subtitle.setForeground(HERO_TEXT);
      subtitle.setFont(small());
      subtitle.setBorder(new EmptyBorder(4, 0, 0, 0));
      subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
      hero.add(subtitle);
    }
    return hero;
  }

  /** A bordered card that lays its children out top to bottom. */
  static JPanel card() {
    JPanel card = PanelWidgets.column();
    card.setBackground(CARD);
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER), new EmptyBorder(9, 9, 9, 9)));
    return card;
  }

  /** Small gold section heading, like Hunter Rumours' "Search" label. */
  static JLabel section(String text) {
    JLabel label = new JLabel(text);
    label.setFont(small());
    label.setForeground(GOLD);
    label.setBorder(new EmptyBorder(6, 2, 3, 0));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    return label;
  }

  /** A rounded drop-down with a gold arrow. */
  static void combo(JComboBox<?> choice) {
    choice.setUI(new ComboUI());
    choice.setOpaque(false);
    choice.setBackground(SELECT_BACKGROUND);
    choice.setForeground(INK);
    choice.setFont(small());
    choice.setBorder(new EmptyBorder(1, 1, 1, 1));
    choice.setMaximumRowCount(8);
    choice.setPreferredSize(new Dimension(0, 30));
    ListCellRenderer<Object> base = new DefaultListCellRenderer();
    choice.setRenderer(
        (list, value, index, selected, focused) -> {
          JLabel label =
              (JLabel) base.getListCellRendererComponent(list, value, index, selected, focused);
          label.setOpaque(index >= 0);
          label.setFont(index < 0 ? bold() : small());
          label.setBorder(new EmptyBorder(index < 0 ? 5 : 7, 9, index < 0 ? 5 : 7, 7));
          label.setBackground(
              index < 0 ? new Color(0, 0, 0, 0) : selected ? DONE : SELECT_BACKGROUND);
          label.setForeground(selected && index >= 0 ? new Color(238, 218, 176) : INK);
          return label;
        });
  }

  /** A slim scrollbar with no arrow buttons. */
  static void scrollBar(JScrollBar bar) {
    bar.setPreferredSize(new Dimension(10, 0));
    bar.setUnitIncrement(48);
    bar.setBlockIncrement(240);
    bar.setUI(new ScrollUI());
  }

  private static final class ComboUI extends BasicComboBoxUI {
    private boolean hovered;

    @Override
    protected void installListeners() {
      super.installListeners();
      comboBox.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
              hovered = true;
              comboBox.repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
              hovered = false;
              comboBox.repaint();
            }
          });
    }

    @Override
    public void paint(Graphics graphics, JComponent component) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(hovered ? SELECT_BACKGROUND_HOVER : SELECT_BACKGROUND);
      g.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 8, 8);
      g.setColor(
          comboBox.hasFocus() || comboBox.isPopupVisible() ? SELECT_BORDER_ACTIVE : SELECT_BORDER);
      g.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 8, 8);
      g.dispose();
      super.paint(graphics, component);
    }

    @Override
    public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean focus) {
      // The rounded background is painted once in paint().
    }

    @Override
    protected JButton createArrowButton() {
      JButton button =
          new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
              Graphics2D g = (Graphics2D) graphics.create();
              g.setRenderingHint(
                  RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
              g.setColor(getModel().isRollover() ? Color.WHITE : GOLD);
              int x = getWidth() / 2;
              int y = getHeight() / 2;
              g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
              g.drawLine(x - 4, y - 2, x, y + 2);
              g.drawLine(x, y + 2, x + 4, y - 2);
              g.dispose();
            }
          };
      button.setOpaque(false);
      button.setContentAreaFilled(false);
      button.setBorderPainted(false);
      button.setFocusPainted(false);
      button.setRolloverEnabled(true);
      button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      return button;
    }

    @Override
    protected ComboPopup createPopup() {
      return new BasicComboPopup(comboBox) {
        @Override
        protected void configurePopup() {
          super.configurePopup();
          setBorder(BorderFactory.createLineBorder(SELECT_BORDER_ACTIVE));
        }

        @Override
        protected void configureList() {
          super.configureList();
          list.setBackground(SELECT_BACKGROUND);
          list.setSelectionBackground(DONE);
          list.setSelectionForeground(Color.WHITE);
          list.setFixedCellHeight(30);
        }

        @Override
        protected JScrollPane createScroller() {
          JScrollPane scroller = super.createScroller();
          scroller.setBorder(BorderFactory.createEmptyBorder());
          scroller.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
          scroller.getVerticalScrollBar().setUnitIncrement(30);
          scroller.getVerticalScrollBar().setUI(new ScrollUI());
          return scroller;
        }
      };
    }
  }

  private static final class ScrollUI extends BasicScrollBarUI {
    @Override
    protected void configureScrollBarColors() {
      trackColor = SCROLL_TRACK;
      thumbColor = SCROLL_THUMB;
      thumbDarkShadowColor = SCROLL_THUMB;
      thumbHighlightColor = SCROLL_THUMB;
      thumbLightShadowColor = SCROLL_THUMB;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
      return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
      return zeroButton();
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
      graphics.setColor(SCROLL_TRACK);
      graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
      if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(isThumbRollover() ? SCROLL_THUMB_HOVER : SCROLL_THUMB);
      g.fillRoundRect(
          bounds.x + 2,
          bounds.y + 2,
          Math.max(4, bounds.width - 4),
          Math.max(8, bounds.height - 4),
          6,
          6);
      g.dispose();
    }

    private static JButton zeroButton() {
      JButton button = new JButton();
      Dimension zero = new Dimension(0, 0);
      button.setPreferredSize(zero);
      button.setMinimumSize(zero);
      button.setMaximumSize(zero);
      return button;
    }
  }
}
