package com.yonwi.farming;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Small building blocks and text formats shared by the sidebar's tabs. */
final class PanelWidgets {
  /** Wrapping width for text inside a card, leaving a safe gap before the scrollbar. */
  static final int CARD_TEXT = 140;
  static final Color WARN = new Color(255, 140, 110);
  static final Color PROFIT = new Color(120, 220, 110);
  static final Color LOSS = new Color(230, 90, 80);

  private PanelWidgets() {}

  static JPanel column() {
    JPanel panel = new Column();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBackground(Style.BG);
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    return panel;
  }

  /**
   * A top-to-bottom panel. As a tab's scroll view it always takes the viewport's width, so nothing
   * can run under the scrollbar; only the height scrolls.
   */
  private static final class Column extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
      return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      // Fill a short tab, but scroll a long one.
      return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
    }

    @Override
    protected void addImpl(Component component, Object constraints, int index) {
      // Mixed alignments make BoxLayout centre things; tall fields come from spare height.
      if (component instanceof JComponent) {
        JComponent child = (JComponent) component;
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (child instanceof JSpinner || child instanceof JComboBox || child instanceof AbstractButton)
          child.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
      }
      super.addImpl(component, constraints, index);
    }
  }

  static JScrollPane scroll(JPanel page) {
    JScrollPane scroll = new JScrollPane(page);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setViewportBorder(null);
    scroll.getViewport().setBackground(Style.BG);
    // The same right-hand gap on every tab, scrollbar or not.
    page.setBorder(new EmptyBorder(10, 4, 0, 6));
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    Style.scrollBar(scroll.getVerticalScrollBar());
    scroll.setPreferredSize(new Dimension(225, 440));
    return scroll;
  }

  static JLabel label(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(Style.INK);
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    return label;
  }

  /** Text wrapped to {@code width} pixels, as html for a label. */
  static String wrapped(int width, String text) {
    return "<html><div style=\"width:" + width + "px\">" + html(text) + "</div></html>";
  }

  /** Small grey text wrapped to a card's width. */
  static JLabel muted(String text) {
    JLabel label = label(wrapped(CARD_TEXT, text));
    label.setForeground(Style.MUTED);
    label.setFont(Style.small());
    return label;
  }

  /** Adds a card at its natural height with a small gap below, like Hunter Rumours' list. */
  static void addCard(JPanel page, JPanel card) {
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
    page.add(card);
    page.add(Box.createRigidArea(new Dimension(0, 5)));
  }

  /** Keeps a component at its preferred height in a top-to-bottom layout. */
  static void fixHeight(JComponent component) {
    component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
  }

  static String html(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /** A flat text button, e.g. ✎, ✕ or ▾. */
  static JButton iconButton(String text, String tip, Color color, Runnable action) {
    JButton button = new JButton(text);
    button.setToolTipText(tip);
    button.setFont(button.getFont().deriveFont(14f));
    button.setForeground(color);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setFocusPainted(false);
    button.setMargin(new Insets(0, 2, 0, 2));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(e -> action.run());
    return button;
  }

  /** Runs {@code action} when the component is clicked. */
  static void onClick(JComponent component, Runnable action) {
    component.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            action.run();
          }
        });
  }

  /** A gold section heading with a ▾/▴ button on the right; the whole row toggles. */
  static JPanel collapsibleHeading(JLabel section, boolean open, String hideTip, String showTip, Runnable toggle) {
    JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    heading.add(section, BorderLayout.CENTER);
    heading.add(iconButton(open ? "▴" : "▾", open ? hideTip : showTip, Style.MUTED, toggle), BorderLayout.EAST);
    heading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    onClick(heading, toggle);
    heading.setAlignmentX(Component.LEFT_ALIGNMENT);
    fixHeight(heading);
    return heading;
  }

  /** A centred table cell. */
  static JLabel cell(String text, Color color, boolean bold) {
    JLabel label = new JLabel(text, SwingConstants.CENTER);
    label.setFont(bold ? Style.bold() : Style.small());
    label.setForeground(color);
    return label;
  }

  static String number(double n) {
    return String.format(Locale.UK, "%,.0f", n);
  }

  /** Run time as minutes and seconds, e.g. 5:30 or 72:05. */
  static String minutes(long ms) {
    long seconds = ms / 1000;
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  /** The run timer, e.g. 00:05:30. */
  static String time(long ms) {
    long seconds = ms / 1000;
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
  }

  /** Signed GP, short: +1.2k, −350. */
  static String gp(long gp) {
    return (gp >= 0 ? "+" : "−") + shortGp(Math.abs(gp));
  }

  /** 1,234 → 1.2k, 1,234,567 → 1.2m. */
  private static String shortGp(long gp) {
    if (gp >= 1_000_000) return String.format(Locale.ROOT, "%.1fm", gp / 1_000_000.0);
    if (gp >= 1_000) return String.format(Locale.ROOT, "%.1fk", gp / 1_000.0);
    return String.valueOf(gp);
  }

  /** A run's finish time in local time, e.g. "12 Oct 2026, 14:05". */
  static String runDate(String iso) {
    try {
      return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)
          .format(Instant.parse(iso).atZone(ZoneId.systemDefault()));
    } catch (RuntimeException ex) {
      return iso == null ? "" : iso.substring(0, Math.min(10, iso.length()));
    }
  }

  /** A small numbered circle: plain, guided (gold), or done (green tick). */
  static final class NumberBadge extends JComponent {
    static final int PLAIN = 0;
    static final int GUIDED = 1;
    static final int DONE = 2;
    private final int number;
    private final int state;

    NumberBadge(int number, int state) {
      this.number = number;
      this.state = state;
      setPreferredSize(new Dimension(20, 20));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      if (state == DONE) {
        Icons.get("check").paintIcon(this, graphics, 2, 2);
        return;
      }
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(state == GUIDED ? Style.GOLD : new Color(70, 72, 75));
      g.fillOval(1, 1, 18, 18);
      g.setColor(state == GUIDED ? new Color(40, 30, 10) : Style.INK);
      g.setFont(Style.small());
      String text = String.valueOf(number);
      // Centred on the digits' drawn shape: the RuneScape font's ascent has a lot of empty space.
      Rectangle2D shape = g.getFont().createGlyphVector(g.getFontRenderContext(), text).getVisualBounds();
      g.drawString(
          text,
          (float) (10 - shape.getX() - shape.getWidth() / 2),
          (float) (10 - shape.getY() - shape.getHeight() / 2));
      g.dispose();
    }
  }
}
