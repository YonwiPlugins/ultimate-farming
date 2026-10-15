package com.yonwi.farming;

import static com.yonwi.farming.PanelWidgets.CARD_TEXT;
import static com.yonwi.farming.PanelWidgets.LOSS;
import static com.yonwi.farming.PanelWidgets.PROFIT;
import static com.yonwi.farming.PanelWidgets.addCard;
import static com.yonwi.farming.PanelWidgets.cell;
import static com.yonwi.farming.PanelWidgets.column;
import static com.yonwi.farming.PanelWidgets.fixHeight;
import static com.yonwi.farming.PanelWidgets.gp;
import static com.yonwi.farming.PanelWidgets.html;
import static com.yonwi.farming.PanelWidgets.iconButton;
import static com.yonwi.farming.PanelWidgets.label;
import static com.yonwi.farming.PanelWidgets.minutes;
import static com.yonwi.farming.PanelWidgets.muted;
import static com.yonwi.farming.PanelWidgets.number;
import static com.yonwi.farming.PanelWidgets.onClick;
import static com.yonwi.farming.PanelWidgets.runDate;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/** The Records tab: one leaderboard per run type, with averages and totals under it. */
final class RecordsTab {
  /** Google Form where players register interest in online run-time leaderboards. */
  private static final String LEADERBOARD_URL = "https://forms.gle/hVgujVaFM8fdv3uaA";
  private static final Color LATEST = new Color(150, 230, 120);
  private static final Color LATEST_ROW = new Color(45, 70, 45);
  private static final Color DELETE = new Color(230, 80, 70);
  private static final int DEFAULT_ROWS = 10;
  /** Which small icon sits beside each stat name. */
  private static final Map<String, String> STAT_ICONS = new HashMap<>();

  static {
    STAT_ICONS.put("Runs", "runs");
    STAT_ICONS.put("Average time", "time");
    STAT_ICONS.put("Time", "time");
    STAT_ICONS.put("Rank", "rank");
    STAT_ICONS.put("Patches", "herbs");
    STAT_ICONS.put("Herbs", "herbs");
    STAT_ICONS.put("Total herbs", "herbs");
    STAT_ICONS.put("Herbs / patch", "average");
    STAT_ICONS.put("Profit", "profit");
    STAT_ICONS.put("Total profit", "profit");
    STAT_ICONS.put("Profit / run", "perRun");
    STAT_ICONS.put("GP / hour", "rate");
  }

  private final FarmingPanel panel;
  private final ConfigManager config;
  private final JPanel page = column();
  /** Run types whose table is in edit (delete) mode. */
  private final Set<String> editing = new HashSet<>();
  private final Set<String> collapsed = new HashSet<>();
  /** Tables expanded past the "Records rows shown" limit. */
  private final Set<String> expanded = new HashSet<>();
  /** The run just finished; its row glows. */
  private RunRecord latest;
  /** Set when a run finishes, so the table that run is in opens. */
  private boolean revealLatest;

  RecordsTab(FarmingPanel panel, ConfigManager config) {
    this.panel = panel;
    this.config = config;
  }

  JPanel page() {
    return page;
  }

  void reset() {
    latest = null;
    collapsed.clear();
    editing.clear();
  }

  /** A run was just finished: its row glows and its table opens. */
  void showLatest(RunRecord record) {
    latest = record;
    revealLatest = true;
  }

  void render() {
    page.removeAll();
    addLeaderboardBanner();
    Map<String, List<RunRecord>> groups = groupsBySize();
    if (groups.isEmpty()) page.add(muted("Finish a timed run to create your first leaderboard."));
    // Every table starts expanded; collapsing is up to you.
    for (Map.Entry<String, List<RunRecord>> group : groups.entrySet()) addGroup(group.getKey(), group.getValue());
    page.add(Box.createVerticalGlue());
    page.revalidate();
    page.repaint();
  }

  /** One table per run type (e.g. "herb", "tree + fruit"), the run types you do most first. */
  private Map<String, List<RunRecord>> groupsBySize() {
    // Herb runs first, then name order: kept for ties.
    Map<String, List<RunRecord>> groups =
        new TreeMap<>(Comparator.comparing((String type) -> !type.equals("herb")).thenComparing(type -> type));
    for (RunRecord record : panel.records()) groups.computeIfAbsent(record.type(), k -> new ArrayList<>()).add(record);
    List<Map.Entry<String, List<RunRecord>>> bySize = new ArrayList<>(groups.entrySet());
    bySize.sort(Comparator.comparingInt(entry -> -entry.getValue().size()));
    Map<String, List<RunRecord>> sorted = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunRecord>> entry : bySize) sorted.put(entry.getKey(), entry.getValue());
    return sorted;
  }

  private void addGroup(String type, List<RunRecord> runs) {
    // Most patches first, then fastest; runs with a struck-off time go to the bottom.
    runs.sort(
        Comparator.comparing((RunRecord record) -> record.timeVoid)
            .thenComparingInt(record -> -record.completed)
            .thenComparingLong(record -> record.milliseconds));
    // The run you just finished: open its table, and show more rows if it's below the top N.
    if (revealLatest && latest != null && runs.contains(latest)) {
      collapsed.remove(type);
      if (runs.indexOf(latest) >= rowsShown()) expanded.add(type);
      revealLatest = false;
    }
    boolean herbs = type.equals("herb");
    boolean isEditing = editing.contains(type);
    boolean isCollapsed = collapsed.contains(type);
    page.add(header(type, isEditing, isCollapsed));
    JPanel card = Style.card();
    if (isCollapsed) card.add(summaryLine(runs));
    else {
      card.add(table(type, runs, herbs, isEditing));
      card.add(Box.createVerticalStrut(8));
      card.add(statsGrid(runs, herbs));
    }
    addCard(page, card);
  }

  /** Collapsed: just how many runs and what they made, on one line (same icons as the stats). */
  private static JPanel summaryLine(List<RunRecord> runs) {
    RunRecord best = runs.get(0);
    long profit = 0;
    boolean priced = false;
    for (RunRecord record : runs)
      if (record.profit != null) {
        profit += record.profit;
        priced = true;
      }
    JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    line.setOpaque(false);
    line.setAlignmentX(Component.LEFT_ALIGNMENT);
    JLabel count = new JLabel(runs.size() + (runs.size() == 1 ? " run" : " runs"), Icons.get("runs"), JLabel.LEFT);
    count.setIconTextGap(5);
    count.setFont(Style.small());
    count.setForeground(Style.INK);
    line.add(count);
    if (priced) {
      JLabel total = new JLabel(gp(profit) + " GP", Icons.get("profit"), JLabel.LEFT);
      total.setIconTextGap(5);
      total.setFont(Style.small());
      total.setForeground(profit >= 0 ? PROFIT : LOSS);
      total.setBorder(new EmptyBorder(0, 14, 0, 0));
      line.add(total);
    }
    if (!best.timeVoid) line.setToolTipText("Best: " + best.completed + " patches in " + minutes(best.milliseconds));
    return line;
  }

  /** Averages and totals for one run type. */
  private static JPanel statsGrid(List<RunRecord> runs, boolean herbs) {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"Runs", String.valueOf(runs.size())});
    // Struck-off times are left out of everything time-based.
    List<RunRecord> timed = new ArrayList<>();
    for (RunRecord record : runs) if (!record.timeVoid) timed.add(record);
    if (!timed.isEmpty())
      rows.add(
          new String[] {
            "Average time", minutes((long) timed.stream().mapToLong(record -> record.milliseconds).average().orElse(0))
          });
    if (herbs) addHerbStats(rows, runs);
    addProfitStats(rows, runs);
    return grid(rows);
  }

  private static void addHerbStats(List<String[]> rows, List<RunRecord> runs) {
    List<RunRecord> counted = new ArrayList<>();
    for (RunRecord record : runs) if (record.herbs > 0 && record.completed > 0) counted.add(record);
    if (counted.isEmpty()) return;
    int total = counted.stream().mapToInt(record -> record.herbs).sum();
    int patches = counted.stream().mapToInt(record -> record.completed).sum();
    rows.add(new String[] {"Total herbs", number(total)});
    rows.add(new String[] {"Herbs / patch", String.format(Locale.ROOT, "%.1f", total / (double) patches)});
  }

  private static void addProfitStats(List<String[]> rows, List<RunRecord> runs) {
    List<RunRecord> priced = new ArrayList<>();
    for (RunRecord record : runs) if (record.profit != null) priced.add(record);
    if (priced.isEmpty()) return;
    long total = priced.stream().mapToLong(record -> record.profit).sum();
    rows.add(new String[] {"Total profit", gp(total)});
    rows.add(new String[] {"Profit / run", gp(total / priced.size())});
    // GP / hour only from runs with a real time.
    long timedProfit = 0;
    long time = 0;
    for (RunRecord record : priced)
      if (!record.timeVoid) {
        timedProfit += record.profit;
        time += record.milliseconds;
      }
    if (time > 0) rows.add(new String[] {"GP / hour", gp(timedProfit * 3_600_000L / time)});
  }

  /** Stat rows: icon and name on the left, value on the right, nothing cut off. */
  private static JPanel grid(List<String[]> rows) {
    JPanel grid = new JPanel();
    grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
    grid.setOpaque(false);
    grid.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (String[] row : rows) {
      JPanel line = new JPanel(new BorderLayout(6, 0));
      line.setOpaque(false);
      line.setBorder(new EmptyBorder(1, 0, 1, 0));
      JLabel name = new JLabel(row[0], Icons.get(STAT_ICONS.getOrDefault(row[0], "")), JLabel.LEFT);
      name.setIconTextGap(6);
      name.setFont(Style.small());
      name.setForeground(Style.MUTED);
      JLabel value = new JLabel(row[1], SwingConstants.RIGHT);
      value.setFont(Style.small());
      // Gains in green, losses in red, everything else plain.
      value.setForeground(row[1].startsWith("+") ? PROFIT : row[1].startsWith("−") ? LOSS : Style.INK);
      line.add(name, BorderLayout.WEST);
      line.add(value, BorderLayout.EAST);
      fixHeight(line);
      grid.add(line);
    }
    return grid;
  }

  /** The icon for a run type: a grimy herb for herbs, else the sapling of its first crop. */
  private String groupIcon(String type) {
    if (type.startsWith("herb")) return "Grimy ranarr weed";
    String kind = type.split(" \\+ ")[0];
    Crop crop = FarmingData.CROPS.containsKey(kind) ? panel.settings().crop(kind) : null;
    return crop == null ? "Magic sapling" : crop.sapling;
  }

  /** Gold heading with a crop icon, plus the edit pencil and the expand/collapse chevron. */
  private JPanel header(String type, boolean isEditing, boolean isCollapsed) {
    JPanel header = new JPanel(new BorderLayout(6, 0));
    header.setOpaque(false);
    header.setBorder(new EmptyBorder(6, 0, 3, 0));
    JLabel icon = new JLabel();
    icon.setPreferredSize(new Dimension(24, 22));
    panel.pictures().small(icon, groupIcon(type), 22, 20);
    header.add(icon, BorderLayout.WEST);
    // Long names wrap onto a second line instead of being cut off.
    JLabel title =
        new JLabel(
            "<html><div style=\"width:" + (CARD_TEXT - 40) + "px\">"
                + html(Character.toUpperCase(type.charAt(0)) + type.substring(1)) + " runs</div></html>");
    title.setToolTipText(type + " runs");
    title.setFont(Style.bold());
    title.setForeground(Style.GOLD);
    header.add(title, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    buttons.setOpaque(false);
    if (!isCollapsed)
      buttons.add(
          iconButton(
              isEditing ? "✓" : "✎",
              isEditing ? "Done editing" : "Edit: remove times or delete runs",
              isEditing ? Style.GOLD : Style.MUTED,
              () -> toggle(editing, type)));
    buttons.add(
        iconButton(
            isCollapsed ? "▾" : "▴", isCollapsed ? "Show all runs" : "Collapse", Style.INK, () -> toggle(collapsed, type)));
    header.add(buttons, BorderLayout.EAST);
    // Clicking the heading itself also expands or collapses.
    title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    onClick(title, () -> toggle(collapsed, type));
    fixHeight(header);
    return header;
  }

  /** Adds or removes a run type from a set of tables, then redraws. */
  private void toggle(Set<String> tables, String type) {
    if (!tables.remove(type)) tables.add(type);
    render();
  }

  /** Centred columns; the latest run's row is shaded green with a bar on the left. */
  private JPanel table(String type, List<RunRecord> runs, boolean herbs, boolean isEditing) {
    JPanel table = new JPanel();
    table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
    table.setOpaque(false);
    List<String> headings = new ArrayList<>();
    if (herbs) headings.add("Herbs");
    headings.addAll(Arrays.asList("Patches", "Time", isEditing ? "" : "Profit"));
    JPanel head = new JPanel(new GridLayout(1, headings.size(), 2, 0));
    head.setOpaque(false);
    head.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Style.CARD_BORDER), new EmptyBorder(0, 3, 4, 0)));
    for (String heading : headings) head.add(cell(heading, Style.INK, false));
    table.add(head);
    int limit = expanded.contains(type) ? Integer.MAX_VALUE : rowsShown();
    for (int i = 0; i < runs.size() && i < limit; i++) table.add(row(runs.get(i), headings.size(), herbs, isEditing));
    // More runs than shown: a button to reveal the rest (or hide them again).
    if (runs.size() > rowsShown()) table.add(moreButton(type, runs.size()));
    table.setAlignmentX(Component.LEFT_ALIGNMENT);
    return table;
  }

  private JPanel row(RunRecord record, int columns, boolean herbs, boolean isEditing) {
    boolean isLatest = record == latest;
    JPanel row = new JPanel(new GridLayout(1, columns, 2, 0));
    row.setOpaque(isLatest);
    row.setBackground(LATEST_ROW);
    row.setBorder(
        isLatest
            ? BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, LATEST), new EmptyBorder(3, 0, 3, 0))
            : new EmptyBorder(3, 3, 3, 0));
    row.setToolTipText((isLatest ? "Latest run · " : "") + runDate(record.date));
    Color ink = isLatest ? LATEST : Style.MUTED;
    if (herbs) row.add(cell(String.valueOf(record.herbs), ink, false));
    row.add(cell(String.valueOf(record.completed), ink, false));
    JLabel time = cell(record.timeVoid ? "–" : minutes(record.milliseconds), ink, false);
    if (record.timeVoid) time.setToolTipText("Time removed (" + minutes(record.milliseconds) + " recorded)");
    row.add(time);
    row.add(isEditing ? editButtons(record) : profitCell(record));
    fixHeight(row);
    return row;
  }

  /** Strike off the time (or bring it back), or delete the whole run. */
  private JPanel editButtons(RunRecord record) {
    JButton clock =
        iconButton(
            "",
            record.timeVoid ? "Restore this run's time" : "Remove this run's time (keeps herbs and profit)",
            Style.MUTED,
            () -> {
              record.timeVoid = !record.timeVoid;
              panel.saveRecords();
              render();
            });
    clock.setIcon(record.timeVoid ? Icons.get("time") : Icons.get("noTime"));
    JButton delete =
        iconButton(
            "✕",
            "Delete this run",
            DELETE,
            () -> {
              panel.records().remove(record);
              if (latest == record) latest = null;
              panel.saveRecords();
              render();
            });
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
    actions.setOpaque(false);
    actions.add(clock);
    actions.add(delete);
    return actions;
  }

  private static JLabel profitCell(RunRecord record) {
    Color color = record.profit == null ? Style.MUTED : record.profit >= 0 ? PROFIT : LOSS;
    JLabel profit = cell(record.profit == null ? "–" : gp(record.profit), color, false);
    if (record.profit == null) profit.setToolTipText("Not recorded (runs from before profit tracking)");
    return profit;
  }

  /** "Show 5 more" (or "Show fewer"), a proper button centred under the table. */
  private JPanel moreButton(String type, int runs) {
    boolean isExpanded = expanded.contains(type);
    JButton more =
        iconButton(
            isExpanded ? "Show fewer" : "Show " + (runs - rowsShown()) + " more",
            null,
            Style.GOLD,
            () -> toggle(expanded, type));
    more.setFont(Style.small());
    more.setContentAreaFilled(true);
    more.setBorderPainted(true);
    more.setBackground(Style.SELECT_BACKGROUND);
    more.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Style.CARD_BORDER), new EmptyBorder(3, 10, 3, 10)));
    JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
    centre.setOpaque(false);
    centre.add(more);
    fixHeight(centre);
    return centre;
  }

  /**
   * At the top until dismissed: register interest in online leaderboards. It opens a Google Form
   * in your browser, so the plugin itself sends nothing anywhere.
   */
  private void addLeaderboardBanner() {
    if ("true".equals(config.getConfiguration(FarmingPanel.GROUP, "leaderboardDismissed"))) return;
    JPanel banner = Style.card();
    JPanel top = new JPanel(new BorderLayout(4, 0));
    top.setOpaque(false);
    JLabel text =
        label(
            "<html><div style=\"width:" + (CARD_TEXT - 20) + "px\">"
                + "Want online leaderboards for run times?</div></html>");
    text.setFont(Style.small());
    top.add(text, BorderLayout.CENTER);
    JButton close =
        iconButton(
            "✕",
            "Hide this",
            Style.MUTED,
            () -> {
              config.setConfiguration(FarmingPanel.GROUP, "leaderboardDismissed", "true");
              render();
            });
    close.setFont(Style.small());
    top.add(close, BorderLayout.EAST);
    top.setAlignmentX(Component.LEFT_ALIGNMENT);
    banner.add(top);
    banner.add(Box.createVerticalStrut(6));
    JButton register = Style.primary("Register interest", null);
    register.setToolTipText("Opens a Google Form in your browser");
    register.setAlignmentX(Component.LEFT_ALIGNMENT);
    register.addActionListener(e -> LinkBrowser.browse(LEADERBOARD_URL));
    banner.add(register);
    addCard(page, banner);
  }

  /** Rows per table before "Show more" (the plugin's config, default 10). */
  private int rowsShown() {
    try {
      String value = config.getConfiguration(FarmingPanel.GROUP, "recordsShown");
      return value == null ? DEFAULT_ROWS : Math.max(1, Integer.parseInt(value));
    } catch (NumberFormatException ex) {
      return DEFAULT_ROWS;
    }
  }
}
