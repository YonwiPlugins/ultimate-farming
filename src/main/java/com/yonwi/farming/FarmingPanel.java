package com.yonwi.farming;

import com.google.gson.Gson;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

final class FarmingPanel extends PluginPanel {
  static final String GROUP = "yonwi-farming-companion";
  /** Wrapping width for text inside a card, leaving a safe gap before the scrollbar. */
  private static final int CARD_TEXT = 140;
  /** Google Form where players register interest in online run-time leaderboards. */
  private static final String LEADERBOARD_URL = "https://forms.gle/hVgujVaFM8fdv3uaA";
  private static final Color BG = Style.BG,
      INK = Style.INK,
      MUTED = Style.MUTED,
      DONE = Style.DONE,
      SLOT = Style.SELECT_BACKGROUND;
  private final ItemManager items;
  private final ClientThread clientThread;
  private final ConfigManager config;
  private final Gson gson;
  private final Consumer<Guidance> guideChanged;
  private final Consumer<Set<Integer>> kitChanged;
  private final Consumer<List<String>> missingChanged;
  private int guideIndex, phaseIndex;
  private final JPanel guidanceControls = Style.card();
  private Settings settings;
  private List<FarmingPlanner.Step> route = new ArrayList<>();
  private FarmingPlanner.Kit kit;
  private final JPanel body = column();
  private final JLabel clock = new JLabel("00:00:00"), yield = new JLabel();
  private final JPanel timerBar = new JPanel(new GridLayout(0, 2, 4, 4));
  private final JTabbedPane tabs = new JTabbedPane();
  private final JPanel routePage = column(),
      kitPage = column(),
      setupPage = column(),
      bestPage = column();
  private final JButton start = Style.primary("Start", null),
      pause = new JButton("Pause"),
      finish = new JButton("Finish"),
      reset = new JButton("Abandon");
  private final javax.swing.Timer ticker;
  private long elapsed, nanoStart;
  private boolean running, disposed;
  private int clientLevel;
  private Session session;
  private List<RunRecord> records = new ArrayList<>();
  private int setupStep;
  private AccountScan.Result account;

  private static final class Session {
    String signature, label, setup;
    long elapsed;
    int patches;
    /** Grimy herbs picked during the run. */
    int herbs;
    /** Celastrus bark chopped during the run. */
    int bark;
    /** Farming XP when the run started, or 0 if unknown. */
    long startXp;
  }

  private static final class RunRecord {
    String signature, label, date, setup;
    long milliseconds;
    /** The time was struck off (paused, logged out mid-run…): the run still counts, its time doesn't. */
    boolean timeVoid;
    int patches, completed, herbs, bark;
    /** Farming XP gained during the run (0 for runs from before it was counted). */
    long xp;
    /** GP at guide prices: produce minus seeds, saplings, payments and compost. Null if unpriced. */
    Long profit;
  }

  /** Runs when a timed run is finished and recorded, e.g. for fireworks. */
  private Runnable onFinish = () -> {};

  void setOnFinish(Runnable onFinish) {
    this.onFinish = onFinish;
  }

  /** Receives the kit's item names in Kit tab order whenever the kit changes. */
  private Consumer<List<String>> kitOrder = names -> {};

  void setKitOrder(Consumer<List<String>> kitOrder) {
    this.kitOrder = kitOrder;
    sendKitOrder();
  }

  private Consumer<Map<String, Integer>> kitCounts = counts -> {};

  /** Receives how many of each kit item to bring, for the bank filter's counts. */
  private Consumer<Boolean> preparingChanged = preparing -> {};

  /** Told whether a run is prepared but not yet begun (the bank button shows only then). */
  void setPreparing(Consumer<Boolean> preparingChanged) {
    this.preparingChanged = preparingChanged;
    updateGuide();
  }

  void setKitCounts(Consumer<Map<String, Integer>> kitCounts) {
    this.kitCounts = kitCounts;
    sendKitOrder();
  }

  private void sendKitOrder() {
    if (kit == null) return;
    List<String> names = new ArrayList<>(kit.wear);
    List<String> bring = new ArrayList<>(kit.bring.keySet());
    bring.sort(Comparator.comparingInt(n -> n.endsWith("sapling") ? 0 : 1));
    names.addAll(bring);
    if (settings.level == 99 && !names.contains("Farming cape")) names.add("Farming cape");
    kitOrder.accept(names);
    // Worn items count too (one each), so the bank can tick them off as well.
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String worn : kit.wear) counts.put(worn, 1);
    counts.putAll(kit.bring);
    kitCounts.accept(counts);
  }

  FarmingPanel(
      ItemManager items,
      ClientThread thread,
      ConfigManager config,
      Gson gson,
      Consumer<Guidance> guideChanged,
      Consumer<Set<Integer>> kitChanged,
      Consumer<List<String>> missingChanged) {
    super(false);
    this.items = items;
    clientThread = thread;
    this.config = config;
    this.gson = gson;
    this.guideChanged = guideChanged;
    this.kitChanged = kitChanged;
    this.missingChanged = missingChanged;
    setLayout(new BorderLayout());
    setBackground(BG);
    setBorder(new EmptyBorder(8, 6, 8, 6));
    loadState();
    Image leaf = FarmingPlugin.leafIcon().getScaledInstance(26, 26, Image.SCALE_SMOOTH);
    JPanel hero = Style.hero("Ultimate Farming", yield, new ImageIcon(leaf), null);
    hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
    body.add(hero);
    body.add(Box.createVerticalStrut(8));
    tabs.addTab("Run", Icons.get("run"), scroll(routePage));
    tabs.addTab("Kit", Icons.get("kit"), scroll(kitPage));
    tabs.addTab("Setup", Icons.get("setup"), scroll(setupPage));
    tabs.addTab("Records", Icons.get("records"), scroll(bestPage));
    // Icon above small text keeps all four tabs on one row, always in the same order.
    String[][] tabParts = {{"Run", "run"}, {"Kit", "kit"}, {"Setup", "setup"}, {"Records", "records"}};
    for (int i = 0; i < tabParts.length; i++) {
      JLabel tab = new JLabel(tabParts[i][0], Icons.get(tabParts[i][1]), SwingConstants.CENTER);
      tab.setVerticalTextPosition(SwingConstants.BOTTOM);
      tab.setHorizontalTextPosition(SwingConstants.CENTER);
      tab.setIconTextGap(1);
      tab.setFont(Style.small());
      tab.setForeground(INK);
      tabs.setTabComponentAt(i, tab);
    }
    body.add(tabs);
    add(body, BorderLayout.CENTER);
    JPanel bottom = column();
    clock.setForeground(INK);
    // The RuneScape bold font, like the rest of the sidebar.
    clock.setFont(Style.bold().deriveFont(22f));
    JPanel timeRow = new JPanel(new BorderLayout(6, 0));
    timeRow.setOpaque(false);
    timeRow.add(clock, BorderLayout.WEST);
    JCheckBox autoStart = Style.tickbox(new JCheckBox("Auto-start", settings.autoStart));
    autoStart.setOpaque(false);
    autoStart.setForeground(MUTED);
    autoStart.setToolTipText(
        "Start the timer when the first patch is finished, or the first herb is picked");
    autoStart.addActionListener(
        e -> {
          settings.autoStart = autoStart.isSelected();
          save();
        });
    timeRow.add(autoStart, BorderLayout.EAST);
    timeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, timeRow.getPreferredSize().height));
    bottom.add(timeRow);
    bottom.add(Box.createVerticalStrut(5));
    timerBar.add(start);
    timerBar.add(pause);
    timerBar.add(finish);
    timerBar.add(reset);
    bottom.add(timerBar);
    add(bottom, BorderLayout.SOUTH);
    start.addActionListener(e -> startRun(false));
    pause.addActionListener(
        e -> {
          if (running) pauseRun();
          else if (session != null) {
            running = true;
            nanoStart = System.nanoTime();
            updateTimer();
          }
        });
    finish.addActionListener(e -> finishRun());
    reset.addActionListener(e -> resetRun());
    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == 3) renderBests();
          styleTabs();
        });
    styleTabs();
    start.setIcon(Icons.get("play"));
    pause.setIcon(Icons.get("pause"));
    finish.setIcon(Icons.get("flag"));
    reset.setIcon(Icons.get("trash"));
    ticker = new javax.swing.Timer(250, e -> updateTimer());
    ticker.start();
    rebuild();
    updateTimer();
    // (Your run is chosen on the Run tab, so it opens there even the first time.)
  }

  private static final String[][] TAB_PARTS = {
    {"Run", "run"}, {"Kit", "kit"}, {"Setup", "setup"}, {"Records", "records"}
  };

  /** The open tab's icon and name in gold, the rest in white. */
  private void styleTabs() {
    for (int i = 0; i < TAB_PARTS.length && i < tabs.getTabCount(); i++) {
      Component c = tabs.getTabComponentAt(i);
      if (!(c instanceof JLabel)) continue;
      JLabel tab = (JLabel) c;
      boolean open = i == tabs.getSelectedIndex();
      ImageIcon icon = Icons.get(TAB_PARTS[i][1]);
      tab.setIcon(open ? Icons.tint(icon, Style.GOLD) : Icons.tint(icon, INK));
      tab.setForeground(open ? Style.GOLD : INK);
    }
  }

  /** Reads settings, records and any unfinished run from the active RuneLite profile. */
  private void loadState() {
    settings = load("settings", Settings.class, new Settings());
    sanitize();
    records = new ArrayList<>();
    RunRecord[] saved = load("records", RunRecord[].class, new RunRecord[0]);
    for (RunRecord r : saved)
      if (r != null && r.signature != null && r.milliseconds > 0) records.add(r);
    elapsed = 0;
    session = load("session", Session.class, null);
    if (session != null) {
      elapsed = Math.max(0, session.elapsed);
      if (session.signature == null) session = null;
    }
    importRestore();
  }

  /**
   * The RuneLite profile changed. Everything in memory belongs to the old profile, so it's dropped
   * unsaved and the new profile's data is read instead. Saving first would copy the old profile's
   * records and settings over the new one's.
   */
  void reload() {
    if (disposed) return;
    running = false;
    prepared = false;
    skipBankCheck = false;
    reordering = false;
    guideIndex = 0;
    phaseIndex = 0;
    setupStep = 0;
    latestRecord = null;
    tablesInitialised = false;
    collapsedTables.clear();
    editingTables.clear();
    loadState();
    rebuild();
    updateTimer();
  }

  /** A one-off import of recovered records and route order, for a named RuneLite profile. */
  private static final class Restore {
    String profile;
    List<RunRecord> records;
    List<String> order;
  }

  /**
   * Merges .runelite/ultimate-farming/restore.json into this profile's records (skipping runs it
   * already has) and restores the route order if none is set, then renames the file so it only
   * happens once. Ignored when it names a different RuneLite profile.
   */
  private void importRestore() {
    java.io.File file = new java.io.File(net.runelite.client.RuneLite.RUNELITE_DIR, "ultimate-farming/restore.json");
    if (!file.isFile()) return;
    net.runelite.client.config.ConfigProfile profile = config.getProfile();
    boolean imported = false;
    try (java.io.Reader in =
        new java.io.InputStreamReader(
            new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
      Restore restore = gson.fromJson(in, Restore.class);
      if (restore != null
          && restore.records != null
          && profile != null
          && profile.getName().equals(restore.profile)) {
        Set<String> dates = new HashSet<>();
        for (RunRecord r : records) dates.add(r.date);
        for (RunRecord r : restore.records)
          if (r != null && r.signature != null && r.milliseconds > 0 && dates.add(r.date))
            records.add(r);
        records.sort(Comparator.comparing(r -> r.date == null ? "" : r.date));
        if (settings.order.isEmpty() && restore.order != null)
          settings.order = new ArrayList<>(restore.order);
        config.setConfiguration(GROUP, "records", gson.toJson(records));
        config.setConfiguration(GROUP, "settings", gson.toJson(settings));
        imported = true;
      }
    } catch (java.io.IOException | RuntimeException ex) {
      return; // unreadable: leave the file for another try
    }
    if (imported) file.renameTo(new java.io.File(file.getParentFile(), "restore.done.json"));
  }

  private void sanitize() {
    settings.level = Math.max(1, Math.min(99, settings.level));
    settings.herbYield = Double.isFinite(settings.herbYield) ? Math.max(0,Math.min(50,settings.herbYield)) : 8;
    settings.bark = Math.max(3, Math.min(100, settings.bark));
    if (settings.kinds == null)
      settings.kinds = new LinkedHashSet<>(Arrays.asList("tree", "fruit"));
    if (settings.unlocks == null) settings.unlocks = new LinkedHashSet<>();
    if (settings.crops == null) settings.crops = new LinkedHashMap<>();
    if (settings.excluded == null) settings.excluded = new LinkedHashSet<>();
    if (settings.completed == null) settings.completed = new LinkedHashSet<>();
    if (settings.progress == null) settings.progress = new LinkedHashMap<>();
    if (settings.order == null) settings.order = new ArrayList<>();
    if (settings.unprotected == null) settings.unprotected = new LinkedHashSet<>();
    if (settings.unharvested == null) settings.unharvested = new LinkedHashSet<>();
    // The old all-or-nothing switches become per-crop choices.
    if (!settings.protect) settings.unprotected.addAll(FarmingData.CROPS.keySet());
    if (!settings.harvest) settings.unharvested.addAll(FarmingData.CROPS.keySet());
    settings.protect = true;
    settings.harvest = true;
    if (settings.house == null) settings.house = "brimhaven";
  }

  private <T> T load(String key, Class<T> type, T fallback) {
    try {
      String value = config.getConfiguration(GROUP, key);
      T data = value == null ? null : gson.fromJson(value, type);
      return data == null ? fallback : data;
    } catch (RuntimeException ex) {
      return fallback;
    }
  }

  private void save() {
    config.setConfiguration(GROUP, "settings", gson.toJson(settings));
    if (session == null) config.unsetConfiguration(GROUP, "session");
    else {
      session.elapsed = duration();
      config.setConfiguration(GROUP, "session", gson.toJson(session));
    }
  }

  void updateLevel(int level) {
    clientLevel = level;
    if (session == null && settings.level != level) {
      settings.level = level;
      rebuild();
      save();
    } else renderRoute(); // just refresh the "Farming level" line
  }

  /** Applies unlocks read from the account. Waits for the run to end before changing the route. */
  void updateAccount(AccountScan.Result result) {
    account = result;
    missingChanged.accept(toWithdraw());
    if (session != null) {
      renderKit();
      return;
    }
    boolean changed = false;
    if (result.magic > 0 && settings.magic != result.magic) {
      settings.magic = result.magic;
      changed = true;
    }
    for (Map.Entry<String, Boolean> e : result.unlocks.entrySet())
      changed |=
          e.getValue() ? settings.unlocks.add(e.getKey()) : settings.unlocks.remove(e.getKey());
    if (result.house != null && !result.house.equals(settings.house)) {
      settings.house = result.house;
      changed = true;
    }
    if (changed) {
      save();
      rebuild();
    } else {
      renderKit();
      if (setupStep == 0) renderSetup(); // the Travel page lists what was detected
      // Re-checks the bank-first step as items are withdrawn.
      updateGuide();
    }
  }

  /**
   * Records an in-game planting, compost or gardener payment. The patch completes once every
   * required action has been seen.
   */
  void patchAction(String action, String kind, Set<String> nearby) {
    List<FarmingPlanner.Step> candidates = new ArrayList<>();
    if (guideIndex < route.size()) candidates.add(route.get(guideIndex));
    candidates.addAll(route);
    FarmingPlanner.Step target = null;
    for (FarmingPlanner.Step step : candidates) {
      if (settings.completed.contains(step.patch.id)) continue;
      if (kind != null && !kind.equals(step.patch.kind)) continue;
      if (!Guidance.phases(step, settings).contains(action)) continue;
      if (nearby.contains(step.patch.id)
          || nearby.isEmpty() && guideIndex < route.size() && step == route.get(guideIndex)) {
        target = step;
        break;
      }
    }
    if (target == null) return;
    int at = route.indexOf(target);
    // A health check at the guided patch is read from the patch itself once it has happened.
    if (action.equals("Check health") && at == guideIndex) return;
    // Acting at a different route patch means you've moved on: guide that one from here. The
    // patch you left counts as finished once it was planted (or you'd come back to it later).
    if (guideIndex < route.size() && at != guideIndex && nearby.contains(target.patch.id)) {
      String left = route.get(guideIndex).patch.id;
      if (settings.progress.getOrDefault(left, Collections.emptySet()).contains("Plant"))
        settings.completed.add(left);
      guideIndex = at;
      phaseIndex = 0;
    }
    record(target, action);
  }

  /** Steps that must all be seen before a patch counts as finished. */
  private static final List<String> FINISHING =
      Arrays.asList("Plant", "Protect", "Compost", "Note harvest");

  /** Marks a step as done at a patch, moves the guide past it, and finishes the patch if due. */
  private void record(FarmingPlanner.Step target, String action) {
    Set<String> seen =
        settings.progress.computeIfAbsent(target.patch.id, k -> new LinkedHashSet<>());
    seen.add(action);
    if (action.equals("Plant")) settings.plantedAt.put(target.patch.id, System.currentTimeMillis());
    List<String> phases = Guidance.phases(target, settings);
    boolean done = true;
    for (String phase : FINISHING) done &= !phases.contains(phase) || seen.contains(phase);
    if (done) settings.completed.add(target.patch.id);
    // Composting the empty patch before planting is fine, but the guide stays on clearing and
    // planting; once planted it skips past the compost already done.
    boolean early = action.equals("Compost") && phases.contains("Plant") && !seen.contains("Plant");
    if (!early && guideIndex < route.size() && route.get(guideIndex) == target) {
      int at = phases.indexOf(action);
      for (String phase : seen) at = Math.max(at, phases.indexOf(phase));
      phaseIndex = Math.max(phaseIndex, Math.min(at + 1, phases.size() - 1));
    }
    if (done) phaseIndex = 0;
    // The timer starts itself once the first patch of a fresh run is finished.
    if (done && settings.autoStart && session == null && target == route.get(0)) startRun(true);
    else save();
    // Finishing the last patch, e.g. composting the final herb patch, ends the timed run.
    if (done
        && session != null
        && route.stream().allMatch(x -> settings.completed.contains(x.patch.id))) {
      finishRun();
      return;
    }
    renderRoute();
    updateGuide();
  }

  /**
   * Kit items you aren't carrying or wearing, with how many are short. Pouch runes are checked
   * inside the rune pouch. Empty while logged out.
   */
  private List<String> missing() {
    List<String> result = new ArrayList<>();
    if (account == null || kit == null || route.isEmpty()) return result;
    Map<String, Integer> needed = new LinkedHashMap<>();
    for (String n : kit.wear) needed.put(n, 1);
    needed.putAll(kit.bring);
    for (Map.Entry<String, Integer> e : needed.entrySet()) {
      String n = e.getKey();
      int[] ids = FarmingData.IDS.get(n);
      if (ids == null) continue;
      // An empty bottomless bucket doesn't count: it needs filling with compost first.
      if (n.equals("Bottomless compost bucket")) {
        ids = new int[] {FarmingData.id(n)};
        n = "Filled bottomless compost bucket";
      }
      // Runes count wherever they are: in the rune pouch, the inventory, or both.
      boolean rune = n.endsWith(" rune");
      int have = 0;
      for (int id : ids) {
        have += account.carried.getOrDefault(id, 0);
        if (rune) have += account.pouch.getOrDefault(id, 0);
      }
      int want = e.getValue();
      // Coins aren't counted out: just say to bring some, or more once you're short.
      if (n.equals("Coins")) {
        if (have < want)
          result.add(have == 0 ? "Coins" : "More coins (" + String.format("%,d", want) + " needed)");
        continue;
      }
      if (have < want) result.add(n + (want - have > 1 ? " ×" + (want - have) : ""));
    }
    // Teleport spells in the route only work from the standard spellbook.
    boolean spells = route.stream().anyMatch(s -> Guidance.spell(s.travel) != null);
    if (spells && account.spellbook != 0) result.add(SWITCH_STANDARD);
    if (settings.resurrects(route) && account.spellbook != 3) result.add(SWITCH_ARCEUUS);
    // Spellbook switched with the magic cape: it goes back in the bank (it isn't kit).
    if (!result.contains(SWITCH_ARCEUUS) && !result.contains(SWITCH_STANDARD)) {
      for (int id : FarmingData.IDS.get("Magic cape"))
        if (account.carried.getOrDefault(id, 0) > 0) {
          result.add(DEPOSIT_CAPE);
          break;
        }
    }
    return result;
  }

  static final String SWITCH_STANDARD = "Switch to the standard spellbook";
  static final String SWITCH_ARCEUUS = "Switch to the Arceuus spellbook";
  static final String DEPOSIT_CAPE = "Deposit your magic cape";

  /** Runes kept in the rune pouch (it holds 3 types). */
  private static final List<String> POUCH_RUNES =
      Arrays.asList("Law rune", "Lava rune", "Mist rune", "Soul rune", "Nature rune", "Blood rune");

  /** Before a run starts, the guide stays at the bank until the whole kit is carried. */
  private boolean preparing() {
    // Only once "Prepare run" is pressed: between runs the overlay stays out of the way.
    return prepared
        && !skipBankCheck
        && session == null
        && settings.completed.isEmpty()
        && settings.progress.isEmpty()
        && !missing().isEmpty();
  }

  /** What still needs withdrawing, only while preparing; supplies run down during a run. */
  private List<String> toWithdraw() {
    return preparing() ? missing() : Collections.emptyList();
  }

  /** Lets the player set off without the full kit, until the run is cleared. */
  private boolean skipBankCheck;

  private static final Color WARN = new Color(255, 140, 110);

  /**
   * Resurrect Crops brought a dead patch back: it keeps growing, so there's nothing to pick,
   * plant or compost there this run. The patch is finished.
   */
  void resurrected(String patchId) {
    FarmingPlanner.Step target = null;
    for (FarmingPlanner.Step step : route) if (step.patch.id.equals(patchId)) target = step;
    if (target == null || settings.completed.contains(patchId)) return;
    settings.completed.add(patchId);
    settings.progress.remove(patchId);
    if (guideIndex < route.size() && route.get(guideIndex) == target) phaseIndex = 0;
    save();
    if (session != null
        && route.stream().allMatch(x -> settings.completed.contains(x.patch.id))) {
      finishRun();
      return;
    }
    renderRoute();
    updateGuide();
  }

  /** Celastrus bark chopped at the route's Celastrus patch, for the run's real profit. */
  void barkChopped(int amount) {
    if (session != null) session.bark += amount;
  }

  /** Herb runs start timing on the first herb picked at an unfinished route patch. */
  void herbPicked(Set<String> nearby, int picked) {
    if (session != null) {
      session.herbs += picked;
      return;
    }
    if (!settings.autoStart) return;
    for (int i = 0; i < route.size(); i++) {
      String id = route.get(i).patch.id;
      if (nearby.contains(id) && !settings.completed.contains(id)) {
        guideIndex = i;
        List<String> phases = Guidance.phases(route.get(i), settings);
        phaseIndex = Math.max(0, phases.indexOf("Harvest"));
        startRun(true);
        session.herbs += picked;
        updateGuide();
        return;
      }
    }
  }

  /** The plugin saw a step finish in-game; move the guide on if it is still on that step. */
  void phaseDone(String patchId, String phase) {
    if (guideIndex >= route.size() || !route.get(guideIndex).patch.id.equals(patchId)) return;
    List<String> phases = Guidance.phases(route.get(guideIndex), settings);
    if (phaseIndex >= phases.size() || !phases.get(phaseIndex).equals(phase)) return;
    // Planting and the final noting also count towards finishing the patch.
    if (FINISHING.contains(phase)) {
      record(route.get(guideIndex), phase);
      return;
    }
    phaseIndex = Math.min(phaseIndex + 1, phases.size() - 1);
    // Tree runs start timing on their first health check.
    if (phase.equals("Check health") && settings.autoStart && session == null) startRun(true);
    updateGuide();
  }

  private void clearProgress() {
    settings.completed.clear();
    settings.progress.clear();
  }

  static JPanel column() {
    JPanel p = new Column();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBackground(BG);
    p.setAlignmentX(Component.LEFT_ALIGNMENT);
    return p;
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
      return getParent() instanceof JViewport
          && getParent().getHeight() > getPreferredSize().height;
    }

    @Override
    protected void addImpl(Component c, Object constraints, int index) {
      // Mixed alignments make BoxLayout centre things; tall fields come from spare height.
      if (c instanceof JComponent) {
        JComponent j = (JComponent) c;
        j.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (j instanceof JSpinner || j instanceof JComboBox || j instanceof AbstractButton)
          j.setMaximumSize(new Dimension(Integer.MAX_VALUE, j.getPreferredSize().height));
      }
      super.addImpl(c, constraints, index);
    }
  }

  private static JLabel label(String s) {
    JLabel l = new JLabel(s);
    l.setForeground(INK);
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
  }

  private static JLabel muted(String s) {
    JLabel l = label("<html><div style=\"width:" + CARD_TEXT + "px\">" + html(s) + "</div></html>");
    l.setForeground(MUTED);
    l.setFont(Style.small());
    return l;
  }

  /** Adds a card at its natural height with a small gap below, like Hunter Rumours' list. */
  private static void addCard(JPanel page, JPanel card) {
    card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
    page.add(card);
    page.add(Box.createRigidArea(new Dimension(0, 5)));
  }

  private static String html(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static JScrollPane scroll(JPanel p) {
    JScrollPane s = new JScrollPane(p);
    s.setBorder(BorderFactory.createEmptyBorder());
    s.setViewportBorder(null);
    s.getViewport().setBackground(BG);
    // The same right-hand gap on every tab, scrollbar or not.
    p.setBorder(new EmptyBorder(10, 4, 0, 6));
    s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    Style.scrollBar(s.getVerticalScrollBar());
    s.setPreferredSize(new Dimension(225, 440));
    return s;
  }

  private static String number(double n) {
    return String.format(Locale.UK, "%,.0f", n);
  }

  /** Run time as minutes and seconds, e.g. 5:30 or 72:05. */
  private static String minutes(long ms) {
    long seconds = ms / 1000;
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
  }

  private static String time(long ms) {
    long seconds = ms / 1000;
    return String.format(
        Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
  }

  private void icon(JLabel target, String name) {
    int id = FarmingData.id(name);
    if (id == 0) {
      target.setText("•");
      target.setToolTipText(name);
      return;
    }
    target.setToolTipText(name);
    String key = id + "|full";
    Icon cached = iconCache.get(key);
    if (cached != null) {
      target.setIcon(cached); // no flicker on redraw
      return;
    }
    clientThread.invoke(
        () -> {
          if (disposed) return;
          AsyncBufferedImage image = items.getImage(id);
          Runnable apply =
              () ->
                  SwingUtilities.invokeLater(
                      () -> {
                        ImageIcon icon = new ImageIcon(image);
                        target.setIcon(icon);
                        iconCache.put(key, icon);
                      });
          image.onLoaded(apply);
          apply.run();
        });
  }

  private void rebuild() {
    if (disposed) return;
    route = FarmingPlanner.plan(settings);
    kit = FarmingPlanner.kit(route, settings);
    kitChanged.accept(FarmingPlanner.itemIds(kit, settings));
    sendKitOrder();
    missingChanged.accept(toWithdraw());
    renderRoute();
    renderKit();
    renderSetup();
    renderBests();
    updateYield();
    updateGuide();
    revalidate();
    repaint();
  }

  private String yieldText(String xp, String gp) {
    String text = "<html>Expected XP: " + xp + "<br>Expected GP: " + gp;
    // Optional: everything your recorded runs have made so far.
    if ("true".equals(config.getConfiguration(GROUP, "netTotals"))) {
      long netXp = 0, netGp = 0;
      for (RunRecord r : records) {
        netXp += r.xp;
        if (r.profit != null) netGp += r.profit;
      }
      text += "<br>Net XP: " + number(netXp) + "<br>Net GP: " + (netGp >= 0 ? "+" : "−") + number(Math.abs(netGp));
    }
    return text + "</html>";
  }

  /** Farming XP now, to count each run's XP; 0 until logged in. */
  private long farmingXp;

  void updateXp(long xp) {
    farmingXp = xp;
  }

  /** Redraws the banner, e.g. after Net XP/GP is switched on or off. */
  void refreshBanner() {
    updateYield();
  }

  private void updateYield() {
    yield.setForeground(INK);
    yield.setText(yieldText(number(FarmingPlanner.xp(route, settings)), "loading"));
    yield.setToolTipText(
        "Guide prices. Saplings, protection, compost and removal minus harvested fruit and bark."
            + " Travel charges excluded.");
    List<FarmingPlanner.Step> snapshot = new ArrayList<>(route);
    Settings snapshotSettings = gson.fromJson(gson.toJson(settings), Settings.class);
    double xp = FarmingPlanner.xp(snapshot, snapshotSettings);
    clientThread.invoke(
        () -> {
          if (disposed) return;
          Long net =
              FarmingPlanner.net(
                  snapshot,
                  snapshotSettings,
                  n -> {
                    int id = FarmingData.id(n);
                    return id == 0 ? 0 : items.getItemPrice(id);
                  });
          SwingUtilities.invokeLater(
              () -> {
                if (disposed || !snapshot.equals(route)) return;
                yield.setText(
                    yieldText(
                        number(xp),
                        net == null
                            ? "unavailable"
                            : (net >= 0 ? "+" : "−") + number(Math.abs(net))));
              });
        });
  }

  private void renderRoute() {
    routePage.removeAll();
    // Between runs: choose the run here, then "Prepare run" at the bottom.
    if (!prepared && session == null && !reordering) {
      addRunChoices(routePage);
      routePage.add(Box.createRigidArea(new Dimension(0, 4)));
      routePage.add(guidanceControls);
      routePage.add(Box.createVerticalGlue());
      routePage.revalidate();
      routePage.repaint();
      return;
    }
    routePage.add(guidanceControls);
    routePage.add(Box.createRigidArea(new Dimension(0, 8)));
    if (route.isEmpty()) routePage.add(muted("Choose trees and travel options in Setup."));
    else if (reordering) {
      renderReorder();
      return;
    } else {
      JLabel patches =
          Style.section(route.size() + " patches" + (settings.order.isEmpty() ? "" : " · your order"));
      patches.setToolTipText("Double-click a patch to reorder");
      routePage.add(patches);
    }
    int i = 0;
    for (FarmingPlanner.Step step : route) {
      JPanel row = Style.card();
      row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      boolean done = settings.completed.contains(step.patch.id);
      boolean guided = i == guideIndex && !done;
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
      name.setForeground(done ? Style.DONE_TEXT : INK);
      // Number badge and the travel item's picture on the left, a chevron on the right.
      JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
      left.setOpaque(false);
      left.add(new NumberBadge(i + 1, guided ? 1 : done ? 2 : 0));
      JLabel item = new JLabel();
      item.setPreferredSize(new Dimension(24, 22));
      smallIcon(item, FarmingPlanner.travelItem(step.travel, settings), 24, 21);
      left.add(item);
      JPanel line = new JPanel(new BorderLayout(4, 0));
      line.setOpaque(false);
      line.add(left, BorderLayout.WEST);
      line.add(name, BorderLayout.CENTER);
      line.add(new JLabel(Icons.get("chevron")), BorderLayout.EAST);
      line.setAlignmentX(Component.LEFT_ALIGNMENT);
      final int rowIndex = i;
      // Click a patch to guide it. The order itself is fixed.
      java.awt.event.MouseAdapter pick =
          new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
              // Single click guides this patch; double-click opens the reorder list.
              if (e.getClickCount() >= 2 && session == null) {
                reordering = true;
                renderRoute();
                return;
              }
              guideIndex = rowIndex;
              phaseIndex = 0;
              updateGuide();
            }
          };
      row.addMouseListener(pick);
      name.addMouseListener(pick);
      item.addMouseListener(pick);
      row.add(line);
      Crop crop = settings.crop(step.patch.kind);
      if(step.patch.kind.equals("herb")) name.setToolTipText("Harvest, plant " + crop.sapling + ", apply ultracompost. " + (step.noteBefore ? "Note carried herbs before picking; note again afterwards." : step.noteAfter ? "Note herbs here as needed." : "Carry herbs to the next nearby leprechaun; note here if full."));
      else name.setToolTipText("Check health, " + (settings.harvests(step.patch.kind) && !crop.fruit.isEmpty() ? "harvest, " : "") + "clear and plant " + crop.sapling + (settings.diseaseFree(step.patch) ? ". Disease-free: no payment or compost" : settings.pays(step.patch) ? ". Pay " + crop.paymentQty + " " + crop.payment : ". Use ultracompost"));
      if (done) row.setBackground(DONE);
      addCard(routePage, row);
      i++;
    }
    routePage.add(Box.createVerticalGlue());
    routePage.revalidate();
    routePage.repaint();
  }

  /** A small numbered circle: 0 plain, 1 guided (gold), 2 done (green tick). */
  private static final class NumberBadge extends JComponent {
    private final int number, state;

    NumberBadge(int number, int state) {
      this.number = number;
      this.state = state;
      setPreferredSize(new Dimension(20, 20));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      if (state == 2) {
        Icons.get("check").paintIcon(this, graphics, 2, 2);
        return;
      }
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(state == 1 ? Style.GOLD : new Color(70, 72, 75));
      g.fillOval(1, 1, 18, 18);
      g.setColor(state == 1 ? new Color(40, 30, 10) : INK);
      g.setFont(Style.small());
      String text = String.valueOf(number);
      // Centred on the digits' drawn shape: the RuneScape font's ascent has a lot of empty space.
      java.awt.geom.Rectangle2D shape =
          g.getFont().createGlyphVector(g.getFontRenderContext(), text).getVisualBounds();
      g.drawString(
          text,
          (float) (10 - shape.getX() - shape.getWidth() / 2),
          (float) (10 - shape.getY() - shape.getHeight() / 2));
      g.dispose();
    }
  }

  /** True while the Run tab shows the reorder list instead of the run. */
  private boolean reordering;

  /** A compact list with up/down arrows; travel between patches is recalculated on each move. */
  private void renderReorder() {
    routePage.add(Style.section("Reorder run"));
    JPanel list = Style.card();
    for (int i = 0; i < route.size(); i++) {
      final int at = i;
      JPanel row = new JPanel(new BorderLayout(4, 0));
      row.setOpaque(false);
      row.setBorder(new EmptyBorder(3, 0, 3, 0));
      JLabel name =
          label(
              "<html><div style=\"width:" + (CARD_TEXT - 50) + "px\">"
                  + (i + 1)
                  + ". "
                  + html(route.get(i).patch.name)
                  + " · "
                  + html(route.get(i).patch.kind)
                  + "</div></html>");
      name.setFont(Style.small());
      row.add(name, BorderLayout.CENTER);
      JPanel arrows = new JPanel(new GridLayout(1, 2, 2, 0));
      arrows.setOpaque(false);
      JButton up = new JButton("▲"), down = new JButton("▼");
      for (JButton b : new JButton[] {up, down}) {
        b.setMargin(new Insets(0, 2, 0, 2));
        b.setFont(Style.small());
        b.setFocusPainted(false);
      }
      up.setEnabled(i > 0);
      down.setEnabled(i < route.size() - 1);
      up.addActionListener(e -> movePatch(at, at - 1));
      down.addActionListener(e -> movePatch(at, at + 1));
      arrows.add(up);
      arrows.add(down);
      row.add(arrows, BorderLayout.EAST);
      list.add(row);
    }
    addCard(routePage, list);
    JPanel actions = new JPanel(new GridLayout(1, 2, 5, 0));
    actions.setOpaque(false);
    JButton done = new JButton("Done"), suggested = new JButton("Suggested");
    suggested.setToolTipText("Go back to the plugin's suggested order");
    suggested.setEnabled(!settings.order.isEmpty());
    done.addActionListener(
        e -> {
          reordering = false;
          renderRoute();
        });
    suggested.addActionListener(
        e -> {
          settings.order.clear();
          save();
          rebuild();
        });
    actions.add(done);
    actions.add(suggested);
    actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
    routePage.add(actions);
    routePage.add(Box.createVerticalGlue());
    routePage.revalidate();
    routePage.repaint();
  }

  /** Moves a patch to a new position and keeps that order for later runs. */
  private void movePatch(int from, int to) {
    List<String> ids = new ArrayList<>();
    for (FarmingPlanner.Step step : route) ids.add(step.patch.id);
    String guided = guideIndex < ids.size() ? ids.get(guideIndex) : null;
    ids.add(to, ids.remove(from));
    settings.order = ids;
    if (guided != null) guideIndex = ids.indexOf(guided);
    save();
    rebuild();
  }

  private static void applyDone(JLabel name, boolean done) {
    Map<TextAttribute, Object> a = new HashMap<>();
    a.put(TextAttribute.STRIKETHROUGH, done);
    name.setFont(name.getFont().deriveFont(a));
    name.setForeground(done ? new Color(169, 195, 174) : INK);
  }

  private void renderKit() {
    kitPage.removeAll();
    kitPage.add(Style.section("Wear"));
    JPanel gear = new JPanel(new GridLayout(5, 3, 3, 3));
    gear.setOpaque(false);
    Map<String, String> slots = new HashMap<>();
    for (String n : kit.wear) slots.put(FarmingPlanner.slot(n), n);
    String[] placement = {
      "", "head", "", "cape", "neck", "weapon", "", "body", "", "", "legs", "", "hands", "feet",
      "ring"
    };
    for (String slot : placement) {
      JLabel cell = new JLabel();
      cell.setHorizontalAlignment(SwingConstants.CENTER);
      cell.setPreferredSize(new Dimension(40, 38));
      if (!slot.isEmpty()) {
        cell.setOpaque(true);
        cell.setBackground(SLOT);
        String name = slots.get(slot);
        if (name != null) icon(cell, name);
      }
      gear.add(cell);
    }
    JPanel wearCard = Style.card();
    wearCard.add(gear);
    addCard(kitPage, wearCard);
    kitPage.add(Style.section("Bring"));
    JPanel inv = new JPanel(new GridLayout(0, 4, 3, 3));
    inv.setOpaque(false);
    int count = 0;
    List<Map.Entry<String, Integer>> entries = new ArrayList<>(kit.bring.entrySet());
    entries.sort(Comparator.comparingInt(e -> e.getKey().endsWith("sapling") ? 0 : 1));
    for (Map.Entry<String, Integer> e : entries) {
      String n = e.getKey();
      int repeats = squares(n, e.getValue(), settings);
      if (repeats == 0) continue; // a pouch rune: shown on the pouch
      for (int i = 0; i < repeats; i++) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        if (n.equals("Rune pouch")) cell.add(pouchIcon(cell), BorderLayout.CENTER);
        else {
          JLabel img = new JLabel();
          img.setHorizontalAlignment(SwingConstants.CENTER);
          icon(img, n);
          cell.add(img, BorderLayout.CENTER);
          cell.setToolTipText(n + " × " + e.getValue());
        }
        JLabel qty = new JLabel(repeats == 1 && e.getValue() > 1 ? number(e.getValue()) : " ");
        qty.setFont(Style.small());
        qty.setForeground(MUTED);
        qty.setHorizontalAlignment(SwingConstants.CENTER);
        cell.add(qty, BorderLayout.SOUTH);
        cell.setPreferredSize(new Dimension(38, 46));
        inv.add(cell);
        count++;
      }
    }
    JPanel bringCard = Style.card();
    bringCard.add(inv);
    addCard(kitPage, bringCard);
    kitPage.add(Box.createVerticalGlue());
    kitPage.revalidate();
  }

  /**
   * The rune pouch with small icons of its runes drawn over it. Uses the pouch's real contents when
   * logged in, otherwise the runes the run needs. Hovering lists them.
   */
  private JComponent pouchIcon(JPanel cell) {
    Map<Integer, Integer> runes = new LinkedHashMap<>();
    if (account != null && !account.pouch.isEmpty()) runes.putAll(account.pouch);
    else
      for (String rune : POUCH_RUNES)
        if (kit.bring.containsKey(rune)) runes.put(FarmingData.id(rune), 0);
    BufferedImage[] images = new BufferedImage[runes.size() + 1];
    JComponent icon =
        new JComponent() {
          @Override
          protected void paintComponent(Graphics g) {
            int x = (getWidth() - 36) / 2, y = (getHeight() - 32) / 2;
            if (images[0] != null) g.drawImage(images[0], x, y, null);
            // Runes in rows of three across the bottom of the pouch, at about a third of full size.
            for (int i = 1; i < images.length; i++)
              if (images[i] != null)
                g.drawImage(
                    images[i], x + (i - 1) % 3 * 12, y + 20 - (i - 1) / 3 * 11, 14, 12, null);
          }
        };
    icon.setPreferredSize(new Dimension(36, 32));
    List<Integer> ids = new ArrayList<>();
    ids.add(FarmingData.id("Rune pouch"));
    ids.addAll(runes.keySet());
    clientThread.invoke(
        () -> {
          if (disposed) return;
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
          String tip =
              "<html>Rune pouch"
                  + (account != null && !account.pouch.isEmpty() ? "" : " (needs)")
                  + "<br>"
                  + String.join("<br>", named)
                  + "</html>";
          SwingUtilities.invokeLater(
              () -> {
                cell.setToolTipText(tip);
                icon.repaint();
              });
        });
    return icon;
  }

  private void copy(String value) {
    try {
      Toolkit.getDefaultToolkit()
          .getSystemClipboard()
          .setContents(new StringSelection(value), null);
    } catch (IllegalStateException | HeadlessException ex) {
      JTextArea field = new JTextArea(value, 6, 20);
      field.setLineWrap(true);
      field.setWrapStyleWord(true);
      field.setEditable(false);
      JOptionPane.showMessageDialog(
          this, new JScrollPane(field), "Copy import string", JOptionPane.PLAIN_MESSAGE);
    }
  }

  private void change(Runnable action) {
    if (session != null) return;
    action.run();
    clearProgress();
    save();
    rebuild();
  }

  private JCheckBox check(String text, boolean selected, Consumer<Boolean> apply) {
    JCheckBox b =
        Style.tickbox(new JCheckBox("<html><div style=\"width:" + (CARD_TEXT - 22) + "px\">" + html(text) + "</div></html>", selected));
    b.setOpaque(false);
    b.setForeground(INK);
    b.setAlignmentX(Component.LEFT_ALIGNMENT);
    b.setEnabled(session == null);
    b.addActionListener(e -> change(() -> apply.accept(b.isSelected())));
    return b;
  }

  /** A tickbox row with the item's picture between the box and its text. */
  private JPanel iconCheck(String text, String item, boolean selected, Consumer<Boolean> apply) {
    JCheckBox box = Style.tickbox(new JCheckBox("", selected));
    box.setOpaque(false);
    box.setEnabled(session == null);
    box.addActionListener(e -> change(() -> apply.accept(box.isSelected())));
    JLabel picture = new JLabel();
    picture.setPreferredSize(new Dimension(24, 22));
    smallIcon(picture, item, 24, 21);
    JLabel words =
        label("<html><div style=\"width:" + (CARD_TEXT - 50) + "px\">" + html(text) + "</div></html>");
    words.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    words.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (box.isEnabled()) box.doClick();
          }
        });
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    left.setOpaque(false);
    left.add(box);
    left.add(picture);
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(2, 0, 2, 0));
    row.add(left, BorderLayout.WEST);
    row.add(words, BorderLayout.CENTER);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    return row;
  }

  /**
   * "Protection payment?" and "Harvest?" for one run type. Hovering shows what each costs or earns
   * for this run at guide prices.
   */
  private void addCropOptions(JPanel card, String kind) {
    Crop crop = settings.crop(kind);
    if (crop == null || kind.equals("herb")) return;
    long patches = route.stream().filter(s -> s.patch.kind.equals(kind)).count();
    card.add(Box.createVerticalStrut(6));
    JCheckBox protect = null, harvest = null;
    if (!crop.payment.isEmpty()) {
      protect =
          check(
              "Protection payment?",
              settings.protects(kind),
              v -> {
                if (v) settings.unprotected.remove(kind);
                else settings.unprotected.add(kind);
              });
      card.add(protect);
    }
    // (Not for Calquat: its fruit must be picked before the tree can be dug up.)
    if ((!crop.fruit.isEmpty() || kind.equals("celastrus")) && !kind.equals("calquat")) {
      harvest =
          check(
              "Harvest?",
              settings.harvests(kind),
              v -> {
                if (v) settings.unharvested.remove(kind);
                else settings.unharvested.add(kind);
              });
      card.add(harvest);
    }
    JCheckBox protectBox = protect, harvestBox = harvest;
    clientThread.invoke(
        () -> {
          if (disposed) return;
          long pay = items.getItemPrice(FarmingData.id(crop.payment)) * (long) crop.paymentQty;
          boolean bark = kind.equals("celastrus");
          int count = bark ? (int) Math.round(FarmingPlanner.bark(settings)) : 6;
          String produce = bark ? "Celastrus bark" : crop.fruit;
          long fruit = items.getItemPrice(FarmingData.id(produce)) * (long) count;
          String each = patches == 1 ? "" : " (" + patches + " patches)";
          String payTip =
              "Pay " + crop.paymentQty + " × " + crop.payment + " per patch: −"
                  + number(pay * Math.max(1, patches)) + " GP" + each
                  + ". Without it, ultracompost is used: " + diseaseRisk(crop) + ".";
          String fruitTip =
              (bark ? "Harvest about " : "Pick ") + count + " × " + produce + " per patch: +" + number(fruit * Math.max(1, patches))
                  + " GP" + each + ".";
          SwingUtilities.invokeLater(
              () -> {
                if (protectBox != null) protectBox.setToolTipText(payTip);
                if (harvestBox != null) harvestBox.setToolTipText(fruitTip);
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

  private void unlock(String key, String text, String tip) {
    JCheckBox b =
        check(
            text,
            settings.has(key),
            v -> {
              if (v) settings.unlocks.add(key);
              else settings.unlocks.remove(key);
            });
    b.setToolTipText(tip);
    unlockCard.add(b);
  }

  /** Short name and picture for each detected unlock: {key, name, item}. */
  private static final String[][] UNLOCK_LOOKS = {
    {"herbPriest", "Port Phasmatys", "Ectophial"},
    {"herbTroll", "Troll Stronghold", "Stony basalt"},
    {"herbWeiss", "Weiss", "Icy basalt"},
    {"herbHarmony", "Harmony Island", "Harmony island teleport"},
    {"herbFortis", "Civitas Fortis", "Civitas illa Fortis teleport"},
    {"fortisGlory", "Fortis disease-free", "Civitas illa Fortis teleport"},
    {"attas", "Attas plant", "Magic secateurs"},
    {"kourendEasy", "Kourend easy", "Xeric's talisman"},
    {"kourendHard", "Kourend hard", "Xeric's talisman"},
    {"kandarinMedium", "Kandarin medium", "Camelot teleport"},
    {"kandarinHard", "Kandarin hard", "Camelot teleport"},
    {"kandarinElite", "Kandarin elite", "Camelot teleport"},
    {"herbExplorer", "Explorer’s ring", "Explorer's ring"},
    {"herbArdy", "Ardougne cloak", "Ardougne cloak"},
    {"herbXeric", "Xeric’s talisman", "Xeric's talisman"},
    {"herbEcto", "Ectophial", "Ectophial"},
    {"herbBasalt", "Basalts", "Stony basalt"},
    {"herbTrollheim", "Trollheim tablet", "Trollheim teleport"},
    {"herbHarmonyTab", "Harmony tablet", "Harmony island teleport"},
    {"gnome", "Spirit trees", "Spirit sapling"},
    {"lletya", "Teleport crystal", "Teleport crystal"},
    {"jewellery", "Jewellery box", "Skills necklace"},
    {"spirit", "House spirit tree", "Spirit sapling"},
    {"cape", "Construction cape", "Construction cape"},
    {"farmingCape", "Farming cape", "Farming cape"},
    {"pouch", "Rune pouch", "Rune pouch"},
    {"wealth", "Ring of wealth", "Ring of wealth"},
    {"faladorElite", "Falador elite", "Falador teleport"},
    {"slayer", "Slayer ring", "Slayer ring"},
    {"skills", "Skills necklace", "Skills necklace"},
    {"catherby", "Catherby tablet", "Catherby teleport"},
    {"redirect", "House tablets", "House teleport"},
    {"catherbyPortal", "Catherby portal", "House teleport"},
    {"fairy", "Fairy rings", "Dramen or lunar staff"},
    {"houseFairy", "House fairy ring", "Dramen or lunar staff"},
    {"varlamore", "Auburn Valley", "Pendant of ates"},
    {"kastori", "Kastori", "Pendant of ates"},
    {"pendant", "Pendant of ates", "Pendant of ates"},
    {"quetzal", "Quetzal sites", "Quetzal whistle"},
    {"whistle", "Quetzal whistle", "Quetzal whistle"},
    {"fossil", "Fossil Island", "Digsite pendant"},
    {"digsite", "Digsite pendant", "Digsite pendant"},
    {"summer", "Summer Shore", "Calquat sapling"},
    {"locus", "Locus Oasis", "Teak sapling"},
    {"anglers", "Anglers’ Retreat", "Mahogany sapling"}
  };

  /** Two columns of detected unlocks: a green tick, the item's picture and a short name. */
  private JPanel detectedGrid(String[][] flags, Map<String, Boolean> known, String house) {
    Map<String, String[]> looks = new HashMap<>();
    for (String[] look : UNLOCK_LOOKS) looks.put(look[0], look);
    JPanel grid = new JPanel(new GridLayout(0, 2, 4, 3));
    grid.setOpaque(false);
    for (String[] f : flags) {
      if (!Boolean.TRUE.equals(known.get(f[0]))) continue;
      String[] look = looks.getOrDefault(f[0], new String[] {f[0], f[1], ""});
      grid.add(detectedCell(look[1], look[2], f[1] + ". " + f[2]));
    }
    // Your house's location sits with the rest, as one more detected thing.
    if (house != null)
      grid.add(
          detectedCell(
              Character.toUpperCase(house.charAt(0)) + house.substring(1) + " house",
              "House teleport",
              "Your house portal is in " + house));
    grid.setAlignmentX(Component.LEFT_ALIGNMENT);
    return grid;
  }

  /** One detected unlock: its item picture and a short name (the ✓ is the card's heading). */
  private JPanel detectedCell(String text, String itemName, String tip) {
    JPanel cell = new JPanel(new BorderLayout(3, 0));
    cell.setOpaque(false);
    JLabel item = new JLabel();
    item.setPreferredSize(new Dimension(18, 16));
    if (!itemName.isEmpty()) smallIcon(item, itemName, 18, 16);
    JLabel name = new JLabel("<html><div style=\"width:52px\">" + html(text) + "</div></html>");
    name.setFont(Style.small());
    name.setForeground(INK);
    cell.setToolTipText(tip);
    cell.add(item, BorderLayout.WEST);
    cell.add(name, BorderLayout.CENTER);
    return cell;
  }

  /** The card the Travel step's manual unlock tickboxes go into. */
  private JPanel unlockCard;

  private void renderSetup() {
    setupPage.removeAll();
    JLabel stepTitle =
        label(new String[] {"1 · Travel", "2 · Your kit"}[setupStep]);
    stepTitle.setFont(Style.bold());
    setupPage.add(stepTitle);
    setupPage.add(Box.createVerticalStrut(6));
    if (session != null) setupPage.add(muted("Finish or reset your run to change setup."));
    if (setupStep == 0) {
      renderTravelSetup();
    } else {
      renderKitSetup();
    }
    renderSetupNavigation();
  }

  /**
   * "Your run" on the Run tab: run types with their crops, then which patches. This is what
   * changes from run to run, so it sits right above "Prepare run".
   */
  /**
   * How many squares an item takes in the Kit tab's Bring section: one per sapling or
   * ultracompost, none for runes kept in the rune pouch, one for anything else.
   */
  private static int squares(String name, int quantity, Settings s) {
    if (s.has("pouch") && POUCH_RUNES.contains(name)) return 0;
    return name.endsWith("sapling") || name.equals("Ultracompost") ? quantity : 1;
  }

  /** Squares in the Kit tab's Bring section: the inventory space the run needs. */
  private static int bringSquares(FarmingPlanner.Kit kit, Settings s) {
    int total = 0;
    for (Map.Entry<String, Integer> e : kit.bring.entrySet())
      total += squares(e.getKey(), e.getValue(), s);
    return total;
  }

  /** Whether the Bring section still fits in 28 inventory slots with this run type added. */
  private boolean fitsWith(String kind) {
    Settings copy = gson.fromJson(gson.toJson(settings), Settings.class);
    copy.kinds.add(kind);
    List<FarmingPlanner.Step> plan = FarmingPlanner.plan(copy);
    return bringSquares(FarmingPlanner.kit(plan, copy), copy) <= 28;
  }

  /** The Run tab's patch list is shown (it starts collapsed each session). */
  private boolean patchesOpen;

  private void addRunChoices(JPanel page) {
      page.add(Style.section("Your run"));
      if (clientLevel == 0) page.add(muted("Log in to read your Farming level."));
      if (session != null) page.add(muted("Finish or reset your run to change it."));
      for (String kind : FarmingData.CROPS.keySet()) {
        String title =
            kind.equals("fruit")
                ? "Fruit trees"
                : kind.equals("tree")
                    ? "Trees"
                    : Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
        // One card per run type: tick to include it, then pick the crop from its drop-down.
        JPanel card = Style.card();
        boolean on = settings.kinds.contains(kind);
        card.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(on ? Style.GOLD : Style.CARD_BORDER),
                new EmptyBorder(7, 7, 7, 14))); // keeps the tickbox off the right edge
        JCheckBox include = Style.tickbox(new JCheckBox("", on));
        include.setOpaque(false);
        // Adding this run type would need more than a full inventory: greyed out.
        boolean fits = on || fitsWith(kind);
        include.setEnabled(session == null && fits);
        include.addActionListener(
            e ->
                change(
                    () -> {
                      if (include.isSelected()) settings.kinds.add(kind);
                      else settings.kinds.remove(kind);
                    }));
        // Crop picture, then the name over its patch count, with the tickbox on the right.
        Crop shown = settings.crop(kind) != null ? settings.crop(kind) : FarmingData.CROPS.get(kind).get(0);
        JLabel picture = new JLabel();
        picture.setPreferredSize(new Dimension(26, 24));
        smallIcon(picture, shown.sapling, 26, 23);
        long patches = FarmingData.PATCHES.stream().filter(p -> p.kind.equals(kind)).count();
        JLabel name = label(title);
        name.setFont(Style.bold());
        JLabel count = new JLabel(patches + (patches == 1 ? " patch" : " patches"));
        count.setFont(Style.small());
        count.setForeground(MUTED);
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
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        if (!fits) {
          name.setForeground(Style.DONE_TEXT);
          count.setForeground(Style.DONE_TEXT);
          picture.setEnabled(false);
          String tip = "Too many items needed to fit in your inventory";
          for (JComponent c : new JComponent[] {card, head, name, count, picture, include})
            c.setToolTipText(tip);
        }
        card.add(head);
        if (on) {
          JComboBox<Crop> crops =
              new JComboBox<>(
                  FarmingData.CROPS.get(kind).stream()
                      .filter(c -> c.level <= settings.level)
                      .toArray(Crop[]::new));
          Style.combo(crops);
          crops.setSelectedItem(settings.crop(kind));
          crops.setEnabled(session == null);
          crops.addActionListener(
              e -> {
                Crop c = (Crop) crops.getSelectedItem();
                if (c != null) change(() -> settings.crops.put(kind, c.name));
              });
          card.add(Box.createVerticalStrut(6));
          card.add(crops);
          addCropOptions(card, kind);
        }
        addCard(page, card);
      }
      int slots = kit == null ? 0 : bringSquares(kit, settings);
      if (slots > 28) {
        JLabel over =
            muted("Too many items needed to fit in your inventory");
        over.setForeground(WARN);
        page.add(over);
      }
      long chosen =
          FarmingData.PATCHES.stream()
              .filter(p -> settings.kinds.contains(p.kind) && !settings.excluded.contains(p.id))
              .count();
      // Collapsed until clicked: most runs use every patch.
      // (The arrow is the same as the Records tables' collapse button.)
      Runnable toggle =
          () -> {
            patchesOpen = !patchesOpen;
            renderRoute();
          };
      JPanel heading = new JPanel(new BorderLayout());
      heading.setOpaque(false);
      heading.add(Style.section("Patches", "(" + chosen + " selected)"), BorderLayout.WEST);
      heading.add(
          iconButton(
              patchesOpen ? "▴" : "▾",
              patchesOpen ? "Hide patches" : "Choose which patches to visit",
              MUTED,
              toggle),
          BorderLayout.EAST);
      heading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      heading.addMouseListener(
          new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
              toggle.run();
            }
          });
      heading.setAlignmentX(Component.LEFT_ALIGNMENT);
      heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));
      page.add(heading);
      if (!patchesOpen) return;
      JPanel patchCard = Style.card();
      // The type is only worth naming when more than one is in the run.
      boolean mixed = settings.kinds.size() > 1;
      for (Patch p : FarmingData.PATCHES)
        if (settings.kinds.contains(p.kind)) {
          patchCard.add(
              check(
                  mixed ? p.name + " · " + p.kind : p.name,
                  !settings.excluded.contains(p.id),
                  v -> {
                    if (v) settings.excluded.remove(p.id);
                    else settings.excluded.add(p.id);
                  }));
        }
      if (patchCard.getComponentCount() > 0) addCard(page, patchCard);
  }

  /** Setup page 1: what was detected, and tickboxes for what couldn't be. */
  private void renderTravelSetup() {
      String[][] flags = {
{"herbPriest", "Port Phasmatys unlocked", "Priest in Peril and farm access."},
{"herbTroll", "Troll Stronghold patch", "My Arm’s Big Adventure completed."},
{"herbWeiss", "Weiss patch", "Making Friends with My Arm completed."},
{"herbHarmony", "Harmony Island patch", "Morytania elite diary completed."},
{"herbFortis", "Civitas Fortis patch", "Varlamore farm access."},
{"fortisGlory", "Civitas illa Fortis patch is disease-free", "16,000 Glory in the Fortis Colosseum."},
{"attas", "Attas planted at the Farming Guild", "Detected when you're at the guild. +5% herbs while it lives."},
{"kourendEasy", "Kourend easy diary", "Hosidius herb patch is disease-free."},
{"kourendHard", "Kourend hard diary", "+5% herbs at Hosidius and the Farming Guild."},
{"kandarinMedium", "Kandarin medium diary", "+5% herbs at Catherby."},
{"kandarinHard", "Kandarin hard diary", "+10% herbs at Catherby."},
{"kandarinElite", "Kandarin elite diary", "+15% herbs at Catherby."},
{"herbExplorer", "Explorer’s ring farm teleport", "Requires cabbage teleport."},
{"herbArdy", "Ardougne cloak farm teleport", "Requires farm teleport charges."},
{"herbXeric", "Xeric’s talisman", "Xeric’s Glade teleport available."},
{"herbEcto", "Ectophial", "Ghosts Ahoy completed."},
{"herbBasalt", "Stony and icy basalt", "Bring consumable basalts."},
{"herbTrollheim", "Trollheim tablet", "Requires teleport unlock."},
{"herbHarmonyTab", "Harmony island tablet", "Bring Harmony island teleport."},
        {"gnome", "Spirit tree network", "Tree Gnome Village and Gnome Stronghold access."},
        {
          "lletya",
          "Lletya / eternal teleport crystal",
          "Lletya unlocked. Bring charged or eternal teleport crystal."
        },
        {"jewellery", "House jewellery box", "House entry followed by jewellery teleports."},
        {"spirit", "Spirit tree in house", "House entry followed by a spirit tree."},
        {"cape", "Construction cape", "House portals at Taverley or Brimhaven, plus house entry."},
        {"farmingCape", "Farming cape", "Farming Guild teleport. Requires level 99."},
        {
          "pouch",
          "Standard spellbook rune pouch",
          "Law, lava and mist runes. Selected spells require their Magic levels and quest unlocks."
        },
        {"wealth", "Ring of wealth", "Falador Park teleport. Requires charges."},
        {"faladorElite", "Falador elite diary", "Falador Park tree can't get diseased: no payment or compost."},
        {"slayer", "Slayer ring", "Stronghold Slayer Cave teleport."},
        {"skills", "Skills necklace", "Farming Guild teleport."},
        {
          "catherby",
          "Catherby teleport",
          "Bring a Catherby teleport tablet, or use your house portal."
        },
        {"redirect", "Redirected house tablets", "Taverley and Brimhaven house tablets."},
        {
          "catherbyPortal",
          "Catherby portal or portal nexus in house",
          "Can't be detected: tick if your house can teleport you to Catherby."
        },
        {"fairy", "Fairy rings", "Bring dramen staff unless your unlocks waive it."},
        {
          "houseFairy",
          "Fairy ring in house",
          "Fairy ring or spiritual fairy tree in your house: fairy trips start with a house teleport."
        },
        {"varlamore", "Auburn Valley unlocked", "Separate from Pendant of ates ownership."},
        {"kastori", "Kastori unlocked", "Separate from Pendant of ates ownership."},
        {"pendant", "Pendant of ates", "Nemus Retreat and Kastori teleport options."},
        {"quetzal", "Quetzal landing sites", "Required landing sites must already be built."},
        {"whistle", "Quetzal whistle", "Bring a charged whistle."},
        {"fossil", "Fossil Island unlocked", "Requires Bone Voyage."},
        {"digsite", "Digsite pendant / mushtree", "Fossil Island and Mushroom Meadow unlocked."},
        {"summer", "Summer Shore unlocked", "Troubled Tortugans, CJQ and 50 Agility."},
        {"locus", "Locus Oasis unlocked", "Ribbiting Tale completed."},
        {"anglers", "Anglers’ Retreat rowboat", "Rowboat built, 51 Sailing and 50 Construction."}
      };
      Map<String, Boolean> known =
          account == null ? Collections.emptyMap() : account.unlocks;
      List<String> found = new ArrayList<>();
      for (String[] f : flags) if (Boolean.TRUE.equals(known.get(f[0]))) found.add(f[1]);
      if (account == null)
        setupPage.add(
            muted("Log in to detect travel options from your quests, diaries, bank and gear."));
      else setupPage.add(Style.section("Choose what can’t be detected"));
      unlockCard = Style.card();
      for (String[] f : flags) if (!known.containsKey(f[0])) unlock(f[0], f[1], f[2]);
      if (account == null || account.house == null) {
        unlockCard.add(Box.createVerticalStrut(6));
        unlockCard.add(muted("House location"));
        JComboBox<String> house =
            new JComboBox<>(new String[] {"brimhaven", "taverley", "rimmington", "other"});
        Style.combo(house);
        house.setSelectedItem(settings.house);
        house.setEnabled(session == null);
        house.addActionListener(
            e -> change(() -> settings.house = (String) house.getSelectedItem()));
        unlockCard.add(house);
      }
      addCard(setupPage, unlockCard);
      // What the account already has, below the choices: there's nothing to do with these.
      if (account != null) {
        setupPage.add(Style.section("Detected on your account"));
        JPanel detected = Style.card();
        if (found.isEmpty() && account.house == null) detected.add(muted("Nothing yet."));
        else detected.add(detectedGrid(flags, known, account.house));
        if (!account.bankSeen)
          detected.add(muted("Open your bank once so items stored there can be detected."));
        addCard(setupPage, detected);
      }
  }

  /** Setup page 2: outfit, tools and Resurrect Crops, and the yield estimates. */
  private void renderKitSetup() {
      setupPage.add(Style.section("Options"));
      JPanel options = Style.card();
      options.add(
          iconCheck(
              "Farmer’s outfit (otherwise Graceful)",
              "Farmer's jacket",
              settings.farmer,
              v -> settings.farmer = v));
      options.add(
          iconCheck(
              "Bottomless ultracompost bucket",
              "Bottomless compost bucket",
              settings.bucket,
              v -> settings.bucket = v));
      options.add(
          iconCheck("Magic secateurs", "Magic secateurs", settings.secateurs, v -> settings.secateurs = v));
      options.add(
          iconCheck(
              "Resurrect Crops on dead herbs (Arceuus, 78 Magic)",
              "Soul rune",
              settings.resurrect,
              v -> settings.resurrect = v));
      if (settings.resurrect && !settings.resurrects(route)) {
        String why =
            settings.magic > 0 && settings.magic < 78
                ? "Needs 78 Magic."
                : Settings.usesSpells(route)
                    ? "Off for this route: it casts standard spellbook teleports."
                    : route.stream().noneMatch(st -> st.patch.kind.equals("herb"))
                        ? "Only used on herb runs."
                        : settings.magic == 0 ? "Log in to check your Magic level." : "";
        if (!why.isEmpty()) options.add(muted(why));
      }
      options.add(
          iconCheck(
              "Stamina potion (runs of 6+ patches)",
              "Stamina potion(4)",
              settings.stamina,
              v -> settings.stamina = v));
      addCard(setupPage, options);
      setupPage.add(Style.section("Estimates"));
      JPanel estimates = Style.card();
      // Herbs per patch is worked out with the OSRS wiki's herb calculator formula.
      List<FarmingPlanner.Step> herbSteps = new ArrayList<>();
      for (FarmingPlanner.Step step : route) if (step.patch.kind.equals("herb")) herbSteps.add(step);
      if (!herbSteps.isEmpty()) {
        boolean cape = FarmingPlanner.farmingCapeWorn(route, settings);
        double total = 0;
        for (FarmingPlanner.Step step : herbSteps)
          total += FarmingPlanner.expectedHerbs(step.patch, settings, cape);
        JLabel herbs =
            muted(
                String.format(
                    Locale.ROOT, "Herbs per patch: %.1f on average", total / herbSteps.size()));
        herbs.setForeground(INK);
        herbs.setToolTipText(
            "<html>OSRS wiki herb calculator: your Farming level, ultracompost,"
                + (settings.secateurs ? " magic secateurs," : "")
                + (cape ? " farming cape," : "")
                + " diary bonuses<br>(Kandarin at Catherby, Kourend at Hosidius and the guild)"
                + " and disease-free patches.<br>Dead patches are counted in.</html>");
        estimates.add(herbs);
        estimates.add(Box.createVerticalStrut(6));
      }
      if (route.stream().anyMatch(x -> x.patch.kind.equals("celastrus"))) {
        JLabel bark =
            muted(
                String.format(
                    Locale.ROOT, "Celastrus bark per patch: %.1f on average", FarmingPlanner.bark(settings)));
        bark.setForeground(INK);
        bark.setToolTipText(
            "From your Farming level, as in the OSRS wiki's money making guide (8.8 at 99)");
        estimates.add(bark);
      }
      if (estimates.getComponentCount() > 0) addCard(setupPage, estimates);
  }

  /** Back / Next under the Setup pages; "Done" on the last goes back to the Run tab. */
  private void renderSetupNavigation() {
    JPanel navigation = new JPanel(new GridLayout(1, 2, 5, 5));
    navigation.setOpaque(false);
    JButton back = new JButton("Back"), next = new JButton(setupStep == 1 ? "Done" : "Next");
    back.setEnabled(setupStep > 0);
    back.addActionListener(
        e -> {
          setupStep--;
          renderSetup();
        });
    next.addActionListener(
        e -> {
          if (setupStep < 1) {
            setupStep++;
            renderSetup();
          } else {
            save();
            tabs.setSelectedIndex(0);
          }
        });
    navigation.add(back);
    navigation.add(next);
    setupPage.add(Box.createVerticalStrut(10));
    navigation.setMaximumSize(
        new Dimension(Integer.MAX_VALUE, navigation.getPreferredSize().height));
    setupPage.add(navigation);
    setupPage.add(Box.createVerticalGlue());
    setupPage.revalidate();
    setupPage.repaint();
  }

  private long duration() {
    return elapsed + (running ? (System.nanoTime() - nanoStart) / 1_000_000 : 0);
  }

  private void updateGuide() {
    if (disposed) return;
    // Prepared but nothing done yet: the bank button is wanted until the first step of the run.
    preparingChanged.accept(
        prepared
            && session == null
            && settings.completed.isEmpty()
            && settings.progress.isEmpty());
    if (guideIndex >= route.size()) guideIndex = 0;
    if (!route.isEmpty() && settings.completed.contains(route.get(guideIndex).patch.id)) {
      guideIndex = 0;
      while (guideIndex < route.size()
          && settings.completed.contains(route.get(guideIndex).patch.id)) guideIndex++;
      phaseIndex = 0;
    }
    guidanceControls.removeAll();
    if (guideIndex >= route.size() || route.isEmpty()) {
      guideChanged.accept(null);
      guidanceControls.add(muted(route.isEmpty() ? "No route selected" : "All patches checked"));
      guidanceControls.setMaximumSize(
          new Dimension(Integer.MAX_VALUE, guidanceControls.getPreferredSize().height));
      renderRoute();
      return;
    }
    if (!prepared && session == null) {
      // Nothing is guided or highlighted until you choose to start preparing a run.
      guideChanged.accept(null);
      missingChanged.accept(Collections.emptyList());
      // A picture of what's growing beside the heading and when it'll be ready.
      JLabel picture = new JLabel();
      picture.setPreferredSize(new Dimension(34, 32));
      Crop growing = settings.crop(route.get(0).patch.kind);
      if (growing != null) smallIcon(picture, growing.sapling, 34, 30);
      JPanel words = column();
      words.setOpaque(false);
      JLabel next = Style.section("Next run");
      next.setBorder(new EmptyBorder(0, 0, 3, 0));
      words.add(next);
      String idle = idleMessage();
      JLabel when =
          label(
              "<html><div style=\"width:" + (CARD_TEXT - 42) + "px\">"
                  + html(idle == null ? "Press Prepare run when you're ready." : idle)
                  + "</div></html>");
      when.setFont(Style.small());
      words.add(when);
      JPanel top = new JPanel(new BorderLayout(8, 0));
      top.setOpaque(false);
      top.add(picture, BorderLayout.WEST);
      top.add(words, BorderLayout.CENTER);
      top.setAlignmentX(Component.LEFT_ALIGNMENT);
      guidanceControls.add(top);
      guidanceControls.add(Box.createVerticalStrut(8));
      JButton prepare = Style.primary("Prepare run", null);
      prepare.setToolTipText("Show the bank kit check, guide and highlights for this run");
      prepare.addActionListener(
          e -> {
            prepared = true;
            updateGuide();
          });
      guidanceControls.add(prepare);
      guidanceControls.setMaximumSize(
          new Dimension(Integer.MAX_VALUE, guidanceControls.getPreferredSize().height));
      renderRoute();
      return;
    }
    List<String> withdraw = toWithdraw();
    missingChanged.accept(withdraw);
    if (!withdraw.isEmpty()) {
      // Bank first: no teleport is guided or highlighted until the kit is carried.
      guideChanged.accept(null);
      JLabel now = Style.section("Withdraw from the bank");
      now.setBorder(new EmptyBorder(0, 0, 3, 0));
      guidanceControls.add(now);
      for (String item : withdraw) {
        JLabel line = muted("• " + item);
        line.setForeground(WARN);
        guidanceControls.add(line);
      }
      guidanceControls.add(Box.createVerticalStrut(6));
      JButton anyway = new JButton("Set off anyway");
      anyway.setToolTipText("Skip the kit check until this run is cleared");
      anyway.addActionListener(
          e -> {
            skipBankCheck = true;
            updateGuide();
          });
      guidanceControls.add(anyway);
      guidanceControls.setMaximumSize(
          new Dimension(Integer.MAX_VALUE, guidanceControls.getPreferredSize().height));
      renderRoute();
      return;
    }
    FarmingPlanner.Step step = route.get(guideIndex);
    List<String> phases = Guidance.phases(step, settings);
    phaseIndex = Math.max(0, Math.min(phaseIndex, phases.size() - 1));
    Guidance guide =
        new Guidance(step, settings, guideIndex + 1, route.size(), phases.get(phaseIndex));
    guide.herbsPerPatch =
        (int)
            Math.ceil(
                FarmingPlanner.expectedHerbs(
                    step.patch, settings, FarmingPlanner.farmingCapeWorn(route, settings)));
    if (guide.herbsPerPatch <= 0) guide.herbsPerPatch = 8;
    guide.resurrect = settings.resurrects(route);
    for (int n = guideIndex + 1; n < route.size(); n++)
      if (!settings.completed.contains(route.get(n).patch.id)) {
        guide.nextDestination = route.get(n).patch.name;
        guide.nextTravel = route.get(n).travel.text;
        // Its teleport item, to show in orange (not the staff or pouch: nothing to click there).
        List<String> teleport = new ArrayList<>(route.get(n).travel.items);
        teleport.addAll(route.get(n).travel.gear);
        teleport.removeIf(item -> item.contains("staff") || item.equals("Rune pouch"));
        guide.nextTravelItems = teleport;
        break;
      }
    guideChanged.accept(guide);
    JLabel now = Style.section("Now · patch " + (guideIndex + 1) + " of " + route.size());
    now.setBorder(new EmptyBorder(0, 0, 3, 0));
    guidanceControls.add(now);
    JLabel heading =
        label(
            "<html><div style=\"width:" + CARD_TEXT + "px\">"
                + html(guide.destination)
                // Travelling: the grey line below already says how to get there.
                + (guide.phase.equals("Travel")
                    ? ""
                    : " · " + html(guide.labels.get(guide.phaseIndex)))
                + "</div></html>");
    heading.setFont(Style.bold());
    guidanceControls.add(heading);
    guidanceControls.add(muted(guide.action));
    guidanceControls.add(Box.createVerticalStrut(6));
    JPanel actions = new JPanel(new GridLayout(1, 3, 3, 0));
    actions.setOpaque(false);
    JButton back = new JButton("Back"), next = new JButton("Next"), done = new JButton("Done");
    back.setToolTipText("Previous action");
    next.setToolTipText("Next action");
    done.setToolTipText("Mark this patch complete and guide the next patch");
    back.setEnabled(phaseIndex > 0);
    next.setEnabled(phaseIndex < phases.size() - 1);
    back.addActionListener(
        e -> {
          phaseIndex--;
          updateGuide();
        });
    next.addActionListener(
        e -> {
          phaseIndex++;
          updateGuide();
        });
    done.addActionListener(
        e -> {
          settings.completed.add(step.patch.id);
          phaseIndex = 0;
          save();
          renderRoute();
          updateGuide();
        });
    actions.add(back);
    actions.add(next);
    actions.add(done);
    actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
    guidanceControls.add(actions);
    guidanceControls.setMaximumSize(
        new Dimension(Integer.MAX_VALUE, guidanceControls.getPreferredSize().height));
    renderRoute();
  }

  private void startRun(boolean keepProgress) {
    if (route.isEmpty()) return;
    prepared = true;
    if (session == null) {
      session = new Session();
      session.startXp = farmingXp;
      session.signature = FarmingPlanner.signature(route, kit, settings);
      session.label =
          String.join(" + ", settings.kinds)
              + " · "
              + route.size()
              + " patches · "
              + (settings.farmer ? "Farmer" : "Graceful");
      session.setup =
          String.join(", ", kit.wear)
              + ". "
              + (settings.unprotected.isEmpty()
                  ? "Gardener protection"
                  : "No protection for " + String.join(", ", settings.unprotected))
              + ", "
              + (settings.unharvested.isEmpty()
                  ? "harvesting"
                  : "not harvesting " + String.join(", ", settings.unharvested))
              + ", "
              + (settings.bucket ? "bottomless bucket" : "loose ultracompost");
      session.patches = route.size();
      if (!keepProgress) {
        clearProgress();
        guideIndex = 0;
        phaseIndex = 0;
      }
      elapsed = 0;
      renderRoute();
      renderSetup();
      updateGuide();
    }
    if (!running) {
      nanoStart = System.nanoTime();
      running = true;
    }
    tabs.setSelectedIndex(0);
    save();
    updateTimer();
  }

  private void pauseRun() {
    if (running) {
      elapsed = duration();
      running = false;
      save();
      updateTimer();
    }
  }

  private void finishRun() {
    if (session == null || duration() < 1000) return;
    RunRecord r = new RunRecord();
    r.signature = session.signature;
    r.label = session.label;
    r.setup = session.setup;
    r.date = Instant.now().toString();
    r.milliseconds = duration();
    r.patches = session.patches;
    r.completed = (int) route.stream().filter(x -> settings.completed.contains(x.patch.id)).count();
    r.herbs = session.herbs;
    r.bark = session.bark;
    if (session.startXp > 0 && farmingXp > session.startXp) r.xp = farmingXp - session.startXp;
    records.add(r);
    if (records.size() > 500) records.remove(0);
    latestRecord = r;
    revealLatest = true;
    config.setConfiguration(GROUP, "records", gson.toJson(records));
    // Profit at guide prices for the patches done, using the herbs actually picked.
    List<FarmingPlanner.Step> done = new ArrayList<>();
    for (FarmingPlanner.Step s : route) if (settings.completed.contains(s.patch.id)) done.add(s);
    Settings priced = gson.fromJson(gson.toJson(settings), Settings.class);
    long herbPatches = done.stream().filter(s -> s.patch.kind.equals("herb")).count();
    if (herbPatches > 0) priced.herbActual = (double) r.herbs / herbPatches;
    // Celastrus bark actually chopped this run, when counted.
    if (r.bark > 0) priced.bark = r.bark;
    clientThread.invoke(
        () -> {
          Long profit =
              FarmingPlanner.net(
                  done,
                  priced,
                  n -> {
                    int id = FarmingData.id(n);
                    return id == 0 ? 0 : items.getItemPrice(id);
                  });
          SwingUtilities.invokeLater(
              () -> {
                r.profit = profit;
                config.setConfiguration(GROUP, "records", gson.toJson(records));
                renderBests();
              });
        });
    resetRun();
    renderBests();
    tabs.setSelectedIndex(3);
    onFinish.run();
  }

  /** True once "Prepare run" is pressed; cleared when the run is finished or cleared. */
  private boolean prepared;

  private Consumer<String> idleChanged = m -> {};

  private Consumer<String> timeChanged = t -> {};

  /** Receives the run timer's text ("05:32"), or null when no run is being timed. */
  void setTimeListener(Consumer<String> timeChanged) {
    this.timeChanged = timeChanged;
  }

  /** Receives the between-runs overlay line, e.g. when the plants will be ready. */
  void setIdleMessage(Consumer<String> idleChanged) {
    this.idleChanged = idleChanged;
  }

  /**
   * Between runs: when this route's crops will all be grown, using the planting times this plugin
   * saw and RuneLite Time Tracking's growth rates. Null if nothing has been planted yet.
   */
  private String idleMessage() {
    if (prepared || session != null) return null;
    long ready = 0;
    for (FarmingPlanner.Step step : route) {
      Long planted = settings.plantedAt.get(step.patch.id);
      Crop crop = settings.crop(step.patch.kind);
      if (planted == null || crop == null) continue;
      ready = Math.max(ready, FarmingData.readyAt(crop.name, planted));
    }
    if (ready == 0) return null;
    if (ready <= System.currentTimeMillis())
      return "Your patches be ready!";
    String at =
        java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(ready), java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    return "The plants still be growin' - come back at " + at;
  }

  /** Run types whose Records table is in edit (delete) mode. */
  private final Set<String> editingTables = new HashSet<>();

  /** The run just finished; its row glows in Records. */
  private RunRecord latestRecord;

  /** Averages and totals for one run type. */
  private JPanel statsGrid(List<RunRecord> runs, boolean herbs) {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"Runs", String.valueOf(runs.size())});
    // Struck-off times are left out of everything time-based.
    List<RunRecord> timed = new ArrayList<>();
    for (RunRecord r : runs) if (!r.timeVoid) timed.add(r);
    if (!timed.isEmpty())
      rows.add(
          new String[] {
            "Average time",
            minutes((long) timed.stream().mapToLong(r -> r.milliseconds).average().orElse(0))
          });
    if (herbs) {
      List<RunRecord> counted = new ArrayList<>();
      for (RunRecord r : runs) if (r.herbs > 0 && r.completed > 0) counted.add(r);
      if (!counted.isEmpty()) {
        int total = counted.stream().mapToInt(r -> r.herbs).sum();
        int patches = counted.stream().mapToInt(r -> r.completed).sum();
        rows.add(new String[] {"Total herbs", number(total)});
        rows.add(
            new String[] {
              "Herbs / patch", String.format(Locale.ROOT, "%.1f", total / (double) patches)
            });
      }
    }
    List<RunRecord> priced = new ArrayList<>();
    for (RunRecord r : runs) if (r.profit != null) priced.add(r);
    if (!priced.isEmpty()) {
      long total = priced.stream().mapToLong(r -> r.profit).sum();
      rows.add(new String[] {"Total profit", gp(total)});
      rows.add(new String[] {"Profit / run", gp(total / priced.size())});
      // GP / hour only from runs with a real time.
      long timedProfit = 0, time = 0;
      for (RunRecord r : priced)
        if (!r.timeVoid) {
          timedProfit += r.profit;
          time += r.milliseconds;
        }
      if (time > 0) rows.add(new String[] {"GP / hour", gp(timedProfit * 3_600_000L / time)});
    }
    return grid(rows);
  }

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

  /**
   * A run's type with its parts in a fixed order, so "tree + fruit" and "fruit + tree" are one
   * group.
   */
  private static String runType(RunRecord r) {
    if (r.label == null) return "other";
    String[] parts = r.label.split(" · ")[0].split(" \\+ ");
    Arrays.sort(parts);
    return String.join(" + ", parts);
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
      name.setForeground(MUTED);
      JLabel value = new JLabel(row[1], SwingConstants.RIGHT);
      value.setFont(Style.small());
      // Gains in green, losses in red, everything else plain.
      value.setForeground(
          row[1].startsWith("+") ? PROFIT : row[1].startsWith("−") ? new Color(230, 90, 80) : INK);
      line.add(name, BorderLayout.WEST);
      line.add(value, BorderLayout.EAST);
      line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
      grid.add(line);
    }
    return grid;
  }

  private static String gp(long gp) {
    return (gp >= 0 ? "+" : "−") + shortGp(Math.abs(gp));
  }

  /** 1,234 → 1.2k, 1,234,567 → 1.2m. */
  private static String shortGp(long gp) {
    if (gp >= 1_000_000) return String.format(Locale.ROOT, "%.1fm", gp / 1_000_000.0);
    if (gp >= 1_000) return String.format(Locale.ROOT, "%.1fk", gp / 1_000.0);
    return String.valueOf(gp);
  }

  private void resetRun() {
    running = false;
    elapsed = 0;
    session = null;
    skipBankCheck = false;
    prepared = false;
    clearProgress();
    guideIndex = 0;
    phaseIndex = 0;
    save();
    if (account != null) updateAccount(account);
    rebuild();
    updateTimer();
  }

  private void updateTimer() {
    clock.setText(time(duration()));
    timeChanged.accept(session == null ? null : time(duration()));
    // Keep the between-runs overlay line current, e.g. once the plants are grown.
    idleChanged.accept(idleMessage());
    start.setEnabled(!running && !route.isEmpty());
    pause.setEnabled(session != null);
    pause.setText(running || session == null ? "Pause" : "Resume");
    finish.setEnabled(session != null && duration() >= 1000);
    // Also after Prepare run, before anything has started: back to between runs.
    reset.setEnabled(
        prepared
            || session != null
            || !settings.completed.isEmpty()
            || !settings.progress.isEmpty());
    reset.setToolTipText("Abandon this run: clears its progress and timer without saving a time");
  }

  private void renderBests() {
    bestPage.removeAll();
    addLeaderboardBanner();
    // One table per run type (e.g. "herb", "tree + fruit"), herb runs first.
    Map<String, List<RunRecord>> groups =
        new TreeMap<>(
            Comparator.comparing((String t) -> !t.equals("herb")).thenComparing(t -> t));
    for (RunRecord r : records) {
      String type = runType(r);
      groups.computeIfAbsent(type, k -> new ArrayList<>()).add(r);
    }
    // The run types you do most come first (ties keep herbs, then name order).
    List<Map.Entry<String, List<RunRecord>>> bySize = new ArrayList<>(groups.entrySet());
    bySize.sort(Comparator.comparingInt(e -> -e.getValue().size()));
    Map<String, List<RunRecord>> sorted = new LinkedHashMap<>();
    for (Map.Entry<String, List<RunRecord>> e : bySize) sorted.put(e.getKey(), e.getValue());
    groups = sorted;
    if (groups.isEmpty())
      bestPage.add(muted("Finish a timed run to create your first leaderboard."));
    // Only the first run type starts open; the rest show their best run until expanded.
    if (!tablesInitialised && !groups.isEmpty()) {
      tablesInitialised = true;
      collapsedTables.addAll(groups.keySet());
      collapsedTables.remove(groups.keySet().iterator().next());
    }
    for (Map.Entry<String, List<RunRecord>> group : groups.entrySet()) {
      List<RunRecord> runs = group.getValue();
      // Most patches first, then fastest; runs with a struck-off time go to the bottom.
      runs.sort(
          Comparator.comparing((RunRecord r) -> r.timeVoid)
              .thenComparingInt(r -> -r.completed)
              .thenComparingLong(r -> r.milliseconds));
      String type = group.getKey();
      // The run you just finished: open its table, and show more rows if it's below the top N.
      if (revealLatest && latestRecord != null && runs.contains(latestRecord)) {
        collapsedTables.remove(type);
        if (runs.indexOf(latestRecord) >= recordsShown()) expandedTables.add(type);
        revealLatest = false;
      }
      boolean herbs = type.equals("herb");
      boolean editing = editingTables.contains(type);
      boolean collapsed = collapsedTables.contains(type);
      bestPage.add(recordsHeader(type, herbs, editing, collapsed));
      JPanel card = Style.card();
      if (collapsed) {
        // Collapsed: just the best run, inline.
        RunRecord best = runs.get(0);
        // Collapsed: just how many runs and what they made, on one line.
        long profit = 0;
        boolean priced = false;
        for (RunRecord r : runs)
          if (r.profit != null) {
            profit += r.profit;
            priced = true;
          }
        // Same icons as the stats: runs, then total profit.
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel count =
            new JLabel(runs.size() + (runs.size() == 1 ? " run" : " runs"), Icons.get("runs"), JLabel.LEFT);
        count.setIconTextGap(5);
        count.setFont(Style.small());
        count.setForeground(INK);
        line.add(count);
        if (priced) {
          JLabel total = new JLabel(gp(profit) + " GP", Icons.get("profit"), JLabel.LEFT);
          total.setIconTextGap(5);
          total.setFont(Style.small());
          total.setForeground(profit >= 0 ? PROFIT : new Color(230, 90, 80));
          total.setBorder(new EmptyBorder(0, 14, 0, 0));
          line.add(total);
        }
        if (!best.timeVoid)
          line.setToolTipText("Best: " + best.completed + " patches in " + minutes(best.milliseconds));
        card.add(line);
      } else {
        card.add(recordsTable(type, runs, herbs, editing));
        card.add(Box.createVerticalStrut(8));
        card.add(statsGrid(runs, herbs));
      }
      addCard(bestPage, card);
    }
    bestPage.add(Box.createVerticalGlue());
    bestPage.revalidate();
    bestPage.repaint();
  }

  private static final Color PROFIT = new Color(120, 220, 110), LATEST = new Color(150, 230, 120);

  /** The icon for a run type: a grimy herb for herbs, else the sapling of its first crop. */
  private String groupIcon(String type) {
    if (type.startsWith("herb")) return "Grimy ranarr weed";
    String kind = type.split(" \\+ ")[0];
    Crop crop = FarmingData.CROPS.containsKey(kind) ? settings.crop(kind) : null;
    return crop == null ? "Magic sapling" : crop.sapling;
  }

  /** An item image scaled to fit, so it isn't cropped in small spaces. */
  private void smallIcon(JLabel target, String name, int w, int h) {
    int id = FarmingData.id(name);
    if (id == 0) return;
    target.setToolTipText(name);
    // Already loaded once: set it now, so redrawing the tab doesn't make the pictures flicker.
    String key = id + "|" + w + "|" + h;
    Icon cached = iconCache.get(key);
    if (cached != null) {
      target.setIcon(cached);
      return;
    }
    clientThread.invoke(
        () -> {
          if (disposed) return;
          AsyncBufferedImage image = items.getImage(id);
          Runnable apply =
              () ->
                  SwingUtilities.invokeLater(
                      () -> {
                        ImageIcon icon =
                            new ImageIcon(
                                net.runelite.client.util.ImageUtil.resizeImage(image, w, h, true));
                        target.setIcon(icon);
                        iconCache.put(key, icon);
                      });
          image.onLoaded(apply);
          apply.run();
        });
  }

  /** Item pictures already loaded, by "id|width|height" (Swing thread only). */
  private final Map<String, Icon> iconCache = new HashMap<>();

  private boolean tablesInitialised;
  private final Set<String> collapsedTables = new HashSet<>();

  /** Gold heading with a crop icon, plus the edit pencil and the expand/collapse chevron. */
  private JPanel recordsHeader(String type, boolean herbs, boolean editing, boolean collapsed) {
    JPanel header = new JPanel(new BorderLayout(6, 0));
    header.setOpaque(false);
    header.setBorder(new EmptyBorder(6, 0, 3, 0));
    JLabel icon = new JLabel();
    icon.setPreferredSize(new Dimension(24, 22));
    smallIcon(icon, groupIcon(type), 22, 20);
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
    if (!collapsed)
      buttons.add(
          iconButton(
              editing ? "✓" : "✎",
              editing ? "Done editing" : "Edit: remove times or delete runs",
              editing ? Style.GOLD : MUTED,
              () -> {
                if (!editingTables.remove(type)) editingTables.add(type);
                renderBests();
              }));
    buttons.add(
        iconButton(
            collapsed ? "▾" : "▴",
            collapsed ? "Show all runs" : "Collapse",
            INK,
            () -> {
              if (!collapsedTables.remove(type)) collapsedTables.add(type);
              renderBests();
            }));
    header.add(buttons, BorderLayout.EAST);
    // Clicking the heading itself also expands or collapses.
    title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    title.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (!collapsedTables.remove(type)) collapsedTables.add(type);
            renderBests();
          }
        });
    header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
    return header;
  }

  private static JButton iconButton(String text, String tip, Color color, Runnable action) {
    JButton b = new JButton(text);
    b.setToolTipText(tip);
    b.setFont(b.getFont().deriveFont(14f));
    b.setForeground(color);
    b.setBorderPainted(false);
    b.setContentAreaFilled(false);
    b.setFocusPainted(false);
    b.setMargin(new Insets(0, 2, 0, 2));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addActionListener(e -> action.run());
    return b;
  }

  private static JLabel cell(String text, Color color, boolean bold) {
    JLabel l = new JLabel(text, SwingConstants.CENTER);
    l.setFont(bold ? Style.bold() : Style.small());
    l.setForeground(color);
    return l;
  }

  /** A run's finish time in local time, e.g. "12 Oct 2026, 14:05". */
  private static String runDate(String iso) {
    try {
      return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)
          .format(Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()));
    } catch (RuntimeException ex) {
      return iso == null ? "" : iso.substring(0, Math.min(10, iso.length()));
    }
  }

  /** Centred columns; the latest run's row is shaded green with a bar on the left. */
  private JPanel recordsTable(String type, List<RunRecord> runs, boolean herbs, boolean editing) {
    JPanel table = new JPanel();
    table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
    table.setOpaque(false);
    List<String> headings = new ArrayList<>();
    if (herbs) headings.add("Herbs");
    headings.addAll(Arrays.asList("Patches", "Time", editing ? "" : "Profit"));
    JPanel head = new JPanel(new GridLayout(1, headings.size(), 2, 0));
    head.setOpaque(false);
    head.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Style.CARD_BORDER),
            new EmptyBorder(0, 3, 4, 0)));
    for (String h : headings) head.add(cell(h, INK, false));
    table.add(head);
    int limit = expandedTables.contains(type) ? Integer.MAX_VALUE : recordsShown();
    int shown = 0;
    for (RunRecord r : runs) {
      if (++shown > limit) break;
      boolean latest = r == latestRecord;
      JPanel row = new JPanel(new GridLayout(1, headings.size(), 2, 0));
      row.setOpaque(latest);
      row.setBackground(new Color(45, 70, 45));
      row.setBorder(
          latest
              ? BorderFactory.createCompoundBorder(
                  BorderFactory.createMatteBorder(0, 3, 0, 0, LATEST), new EmptyBorder(3, 0, 3, 0))
              : new EmptyBorder(3, 3, 3, 0));
      row.setToolTipText((latest ? "Latest run · " : "") + runDate(r.date));
      Color ink = latest ? LATEST : MUTED;
      if (herbs) row.add(cell(String.valueOf(r.herbs), ink, false));
      row.add(cell(String.valueOf(r.completed), ink, false));
      JLabel time = cell(r.timeVoid ? "–" : minutes(r.milliseconds), ink, false);
      if (r.timeVoid) time.setToolTipText("Time removed (" + minutes(r.milliseconds) + " recorded)");
      row.add(time);
      if (editing) {
        // Strike off the time (or bring it back), or delete the whole run.
        JButton clock =
            iconButton(
                "",
                r.timeVoid ? "Restore this run's time" : "Remove this run's time (keeps herbs and profit)",
                MUTED,
                () -> {
                  r.timeVoid = !r.timeVoid;
                  config.setConfiguration(GROUP, "records", gson.toJson(records));
                  renderBests();
                });
        clock.setIcon(r.timeVoid ? Icons.get("time") : Icons.get("noTime"));
        JButton delete = iconButton("✕", "Delete this run", new Color(230, 80, 70), () -> {
          records.remove(r);
          if (latestRecord == r) latestRecord = null;
          config.setConfiguration(GROUP, "records", gson.toJson(records));
          renderBests();
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        actions.setOpaque(false);
        actions.add(clock);
        actions.add(delete);
        row.add(actions);
      } else
      {
        JLabel profit =
            cell(
                r.profit == null ? "–" : gp(r.profit),
                r.profit == null ? MUTED : r.profit >= 0 ? PROFIT : new Color(230, 90, 80),
                false);
        if (r.profit == null)
          profit.setToolTipText("Not recorded (runs from before profit tracking)");
        row.add(profit);
      }
      row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
      table.add(row);
    }
    // More runs than shown: a link to reveal the rest (or hide them again).
    if (runs.size() > recordsShown()) {
      boolean expanded = expandedTables.contains(type);
      JButton more =
          iconButton(
              expanded ? "Show fewer" : "Show " + (runs.size() - recordsShown()) + " more",
              null,
              Style.GOLD,
              () -> {
                if (!expandedTables.remove(type)) expandedTables.add(type);
                renderBests();
              });
      // A proper button, centred under the table.
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
      centre.setMaximumSize(new Dimension(Integer.MAX_VALUE, centre.getPreferredSize().height));
      table.add(centre);
    }
    table.setAlignmentX(Component.LEFT_ALIGNMENT);
    return table;
  }

  /** Set when a run finishes, so Records opens the table that run is in. */
  private boolean revealLatest;

  /** Records tables expanded past the "Records rows shown" limit. */
  private final Set<String> expandedTables = new HashSet<>();

  /**
   * At the top of Records until dismissed: register interest in online leaderboards. It opens a
   * Google Form in your browser, so the plugin itself sends nothing anywhere.
   */
  private void addLeaderboardBanner() {
    if ("true".equals(config.getConfiguration(GROUP, "leaderboardDismissed"))) return;
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
            MUTED,
            () -> {
              config.setConfiguration(GROUP, "leaderboardDismissed", "true");
              renderBests();
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
    addCard(bestPage, banner);
  }

  /** Rows per Records table before "Show more" (the plugin's config, default 10). */
  private int recordsShown() {
    try {
      String value = config.getConfiguration(GROUP, "recordsShown");
      return value == null ? 10 : Math.max(1, Integer.parseInt(value));
    } catch (NumberFormatException ex) {
      return 10;
    }
  }

  void dispose() {
    guideChanged.accept(null);
    disposed = true;
    pauseRun();
    ticker.stop();
    save();
  }
}