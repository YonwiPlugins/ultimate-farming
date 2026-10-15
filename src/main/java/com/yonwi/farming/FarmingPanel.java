package com.yonwi.farming;

import static com.yonwi.farming.PanelWidgets.CARD_TEXT;
import static com.yonwi.farming.PanelWidgets.column;
import static com.yonwi.farming.PanelWidgets.html;
import static com.yonwi.farming.PanelWidgets.number;
import static com.yonwi.farming.PanelWidgets.scroll;
import static com.yonwi.farming.PanelWidgets.time;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar. Holds the run being planned or done (settings, route, kit, progress and timer),
 * saves it in the RuneLite profile, and tells the plugin what to guide. Each tab draws itself.
 */
final class FarmingPanel extends PluginPanel {
  static final String GROUP = "yonwi-farming-companion";
  static final String SWITCH_STANDARD = "Switch to the standard spellbook";
  static final String SWITCH_ARCEUUS = "Switch to the Arceuus spellbook";
  static final String DEPOSIT_CAPE = "Deposit your magic cape";

  private static final int RUN_TAB = 0;
  private static final int RECORDS_TAB = 3;
  private static final String[][] TABS = {
    {"Run", "run"}, {"Kit", "kit"}, {"Setup", "setup"}, {"Records", "records"}
  };
  /** Steps that must all be seen before a patch counts as finished. */
  private static final List<String> FINISHING = Arrays.asList("Plant", "Protect", "Compost", "Note harvest");
  private static final int MAX_RECORDS = 500;
  /** Runs shorter than this can't be finished (a misclick). */
  private static final long SHORTEST_RUN_MS = 1000;
  private static final int TIMER_REFRESH_MS = 250;
  private static final int ARCEUUS = 3;

  private final ItemManager items;
  private final ClientThread clientThread;
  private final ConfigManager config;
  private final Gson gson;
  private final Consumer<Guidance> guideChanged;
  private final Consumer<Set<Integer>> kitChanged;
  private final Consumer<List<String>> missingChanged;

  private final ItemPictures pictures;
  private final GuideCard guideCard;
  private final RunTab runTab;
  private final KitTab kitTab;
  private final SetupTab setupTab;
  private final RecordsTab recordsTab;
  private final JTabbedPane tabs = new JTabbedPane();
  private final JLabel clock = new JLabel("00:00:00");
  private final JLabel yield = new JLabel();
  private final JButton start = Style.primary("Start", null);
  private final JButton pause = new JButton("Pause");
  private final JButton finish = new JButton("Finish");
  private final JButton reset = new JButton("Abandon");
  private final Timer ticker;

  private Settings settings;
  private List<FarmingPlanner.Step> route = new ArrayList<>();
  private FarmingPlanner.Kit kit;
  private List<RunRecord> records = new ArrayList<>();
  private RunRecord.Session session;
  private AccountScan.Result account;
  private int guideIndex;
  private int phaseIndex;
  /** True once "Prepare run" is pressed; cleared when the run is finished or cleared. */
  private boolean prepared;
  /** Lets the player set off without the full kit, until the run is cleared. */
  private boolean skipBankCheck;
  private long elapsed;
  private long nanoStart;
  private boolean running;
  private boolean disposed;
  private int clientLevel;
  /** Farming XP now, to count each run's XP; 0 until logged in. */
  private long farmingXp;

  /** Runs when a timed run is finished and recorded, e.g. for fireworks. */
  private Runnable onFinish = () -> {};
  /** Receives the kit's item names in Kit tab order whenever the kit changes. */
  private Consumer<List<String>> kitOrder = names -> {};
  /** Receives how many of each kit item to bring, for the bank filter's counts. */
  private Consumer<Map<String, Integer>> kitCounts = counts -> {};
  /** Told whether a run is prepared but not yet begun (the bank button shows only then). */
  private Consumer<Boolean> preparingChanged = preparing -> {};
  /** Receives the between-runs overlay line, e.g. when the plants will be ready. */
  private Consumer<String> idleChanged = message -> {};
  /** Receives the run timer's text ("05:32"), or null when no run is being timed. */
  private Consumer<String> timeChanged = time -> {};

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
    pictures = new ItemPictures(items, thread, () -> account == null ? null : account.owned, () -> disposed);
    guideCard = new GuideCard(pictures);
    runTab = new RunTab(this, items, thread);
    kitTab = new KitTab(this, items, thread);
    setupTab = new SetupTab(this);
    recordsTab = new RecordsTab(this, config);
    setLayout(new BorderLayout());
    setBackground(Style.BG);
    setBorder(new EmptyBorder(8, 6, 8, 6));
    loadState();
    add(body(), BorderLayout.CENTER);
    add(timerBar(), BorderLayout.SOUTH);
    ticker = new Timer(TIMER_REFRESH_MS, e -> updateTimer());
    ticker.start();
    rebuild();
    updateTimer();
    // (Your run is chosen on the Run tab, so it opens there even the first time.)
  }

  /** The banner (title and expected XP and GP), then the four tabs. */
  private JPanel body() {
    JPanel body = column();
    Image leaf = Icons.leaf().getScaledInstance(26, 26, Image.SCALE_SMOOTH);
    JPanel hero = Style.hero("Ultimate Farming", yield, new ImageIcon(leaf), null);
    hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
    body.add(hero);
    body.add(Box.createVerticalStrut(8));
    JPanel[] pages = {runTab.page(), kitTab.page(), setupTab.page(), recordsTab.page()};
    for (int i = 0; i < TABS.length; i++) tabs.addTab(TABS[i][0], Icons.get(TABS[i][1]), scroll(pages[i]));
    // Icon above small text keeps all four tabs on one row, always in the same order.
    for (int i = 0; i < TABS.length; i++) {
      JLabel tab = new JLabel(TABS[i][0], Icons.get(TABS[i][1]), SwingConstants.CENTER);
      tab.setVerticalTextPosition(SwingConstants.BOTTOM);
      tab.setHorizontalTextPosition(SwingConstants.CENTER);
      tab.setIconTextGap(1);
      tab.setFont(Style.small());
      tab.setForeground(Style.INK);
      tabs.setTabComponentAt(i, tab);
    }
    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == RECORDS_TAB) recordsTab.render();
          styleTabs();
        });
    styleTabs();
    body.add(tabs);
    return body;
  }

  /** The run timer with Auto-start, then Start, Pause, Finish and Abandon. */
  private JPanel timerBar() {
    JPanel bottom = column();
    // The same gap above the timer as between the timer and the buttons.
    bottom.add(Box.createVerticalStrut(5));
    clock.setForeground(Style.INK);
    // The RuneScape bold font, like the rest of the sidebar.
    clock.setFont(Style.bold().deriveFont(22f));
    JPanel timeRow = new JPanel(new BorderLayout(6, 0));
    timeRow.setOpaque(false);
    timeRow.add(clock, BorderLayout.WEST);
    JCheckBox autoStart = Style.tickbox(new JCheckBox("Auto-start", settings.autoStart));
    autoStart.setOpaque(false);
    autoStart.setForeground(Style.MUTED);
    autoStart.setToolTipText("Start the timer when the first patch is finished, or the first herb is picked");
    autoStart.addActionListener(
        e -> {
          settings.autoStart = autoStart.isSelected();
          save();
        });
    timeRow.add(autoStart, BorderLayout.EAST);
    PanelWidgets.fixHeight(timeRow);
    bottom.add(timeRow);
    bottom.add(Box.createVerticalStrut(5));
    JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
    buttons.add(start);
    buttons.add(pause);
    buttons.add(finish);
    buttons.add(reset);
    bottom.add(buttons);
    start.addActionListener(e -> startRun(false));
    pause.addActionListener(e -> pauseOrResume());
    finish.addActionListener(e -> finishRun());
    reset.addActionListener(e -> resetRun());
    start.setIcon(Icons.get("play"));
    pause.setIcon(Icons.get("pause"));
    finish.setIcon(Icons.get("flag"));
    reset.setIcon(Icons.get("trash"));
    return bottom;
  }

  /** The open tab's icon and name in gold, the rest in white. */
  private void styleTabs() {
    for (int i = 0; i < TABS.length && i < tabs.getTabCount(); i++) {
      Component component = tabs.getTabComponentAt(i);
      if (!(component instanceof JLabel)) continue;
      JLabel tab = (JLabel) component;
      boolean open = i == tabs.getSelectedIndex();
      ImageIcon icon = Icons.get(TABS[i][1]);
      tab.setIcon(open ? Icons.tint(icon, Style.GOLD) : Icons.tint(icon, Style.INK));
      tab.setForeground(open ? Style.GOLD : Style.INK);
    }
  }

  // ---- For the tabs ----

  Settings settings() {
    return settings;
  }

  List<FarmingPlanner.Step> route() {
    return route;
  }

  FarmingPlanner.Kit kit() {
    return kit;
  }

  AccountScan.Result account() {
    return account;
  }

  List<RunRecord> records() {
    return records;
  }

  ItemPictures pictures() {
    return pictures;
  }

  GuideCard guideCard() {
    return guideCard;
  }

  int guideIndex() {
    return guideIndex;
  }

  int clientLevel() {
    return clientLevel;
  }

  boolean prepared() {
    return prepared;
  }

  /** A timed run has started (and not been finished or abandoned). */
  boolean runStarted() {
    return session != null;
  }

  boolean isDisposed() {
    return disposed;
  }

  /** Guide this patch of the route, from its first step. */
  void guidePatch(int index) {
    guideIndex = index;
    phaseIndex = 0;
    updateGuide();
  }

  /** Keeps a new patch order for later runs, still guiding the same patch. */
  void setOrder(List<String> ids) {
    String guided = guideIndex < route.size() ? route.get(guideIndex).patch.id : null;
    settings.order = ids;
    if (guided != null) guideIndex = ids.indexOf(guided);
    save();
    rebuild();
  }

  /** Back to the plugin's suggested order. */
  void useSuggestedOrder() {
    settings.order.clear();
    save();
    rebuild();
  }

  /** Whether the run still fits in the inventory with this run type added. */
  boolean fitsWith(String kind) {
    return InventorySpace.fitsWith(settings, kind, gson);
  }

  /** Setup is done: back to the Run tab. */
  void setupDone() {
    save();
    tabs.setSelectedIndex(RUN_TAB);
  }

  void saveRecords() {
    config.setConfiguration(GROUP, "records", gson.toJson(records));
  }

  /** Changes a setting between runs: progress starts over and everything is redrawn. */
  void change(Runnable action) {
    if (session != null) return;
    action.run();
    clearProgress();
    save();
    rebuild();
  }

  /** A tickbox for a setting; it can't be changed during a run. */
  JCheckBox settingBox(String text, boolean selected, Consumer<Boolean> apply) {
    JCheckBox box =
        Style.tickbox(
            new JCheckBox(
                "<html><div style=\"width:" + (CARD_TEXT - 22) + "px\">" + html(text) + "</div></html>", selected));
    box.setOpaque(false);
    box.setForeground(Style.INK);
    box.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.setEnabled(session == null);
    box.addActionListener(e -> change(() -> apply.accept(box.isSelected())));
    return box;
  }

  // ---- Listeners the plugin sets ----

  void setOnFinish(Runnable onFinish) {
    this.onFinish = onFinish;
  }

  void setKitOrder(Consumer<List<String>> kitOrder) {
    this.kitOrder = kitOrder;
    sendKitOrder();
  }

  void setKitCounts(Consumer<Map<String, Integer>> kitCounts) {
    this.kitCounts = kitCounts;
    sendKitOrder();
  }

  void setPreparing(Consumer<Boolean> preparingChanged) {
    this.preparingChanged = preparingChanged;
    updateGuide();
  }

  void setTimeListener(Consumer<String> timeChanged) {
    this.timeChanged = timeChanged;
  }

  void setIdleMessage(Consumer<String> idleChanged) {
    this.idleChanged = idleChanged;
  }

  private void sendKitOrder() {
    if (kit == null) return;
    List<String> names = new ArrayList<>(kit.wear);
    List<String> bring = new ArrayList<>(kit.bring.keySet());
    bring.sort(Comparator.comparingInt(name -> name.endsWith("sapling") ? 0 : 1));
    names.addAll(bring);
    if (settings.level == 99 && !names.contains("Farming cape")) names.add("Farming cape");
    kitOrder.accept(names);
    // Worn items count too (one each), so the bank can tick them off as well.
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String worn : kit.wear) counts.put(worn, 1);
    counts.putAll(kit.bring);
    kitCounts.accept(counts);
  }

  // ---- Saved state ----

  /** Reads settings, records and any unfinished run from the active RuneLite profile. */
  private void loadState() {
    settings = load("settings", Settings.class, new Settings());
    sanitize();
    records = new ArrayList<>();
    RunRecord[] saved = load("records", RunRecord[].class, new RunRecord[0]);
    for (RunRecord record : saved)
      if (record != null && record.signature != null && record.milliseconds > 0) records.add(record);
    elapsed = 0;
    session = load("session", RunRecord.Session.class, null);
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
    runTab.reset();
    guideIndex = 0;
    phaseIndex = 0;
    setupTab.reset();
    recordsTab.reset();
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
    File file = new File(RuneLite.RUNELITE_DIR, "ultimate-farming/restore.json");
    if (!file.isFile()) return;
    ConfigProfile profile = config.getProfile();
    boolean imported = false;
    try (Reader in = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      Restore restore = gson.fromJson(in, Restore.class);
      if (restore != null
          && restore.records != null
          && profile != null
          && profile.getName().equals(restore.profile)) {
        mergeRestored(restore);
        imported = true;
      }
    } catch (IOException | RuntimeException ex) {
      return; // unreadable: leave the file for another try
    }
    if (imported) file.renameTo(new File(file.getParentFile(), "restore.done.json"));
  }

  private void mergeRestored(Restore restore) {
    Set<String> dates = new HashSet<>();
    for (RunRecord record : records) dates.add(record.date);
    for (RunRecord record : restore.records)
      if (record != null && record.signature != null && record.milliseconds > 0 && dates.add(record.date))
        records.add(record);
    records.sort(Comparator.comparing(record -> record.date == null ? "" : record.date));
    if (settings.order.isEmpty() && restore.order != null) settings.order = new ArrayList<>(restore.order);
    saveRecords();
    config.setConfiguration(GROUP, "settings", gson.toJson(settings));
  }

  /** Fills in anything missing or out of range in saved settings, and upgrades old ones. */
  private void sanitize() {
    settings.level = Math.max(1, Math.min(99, settings.level));
    settings.herbYield = Double.isFinite(settings.herbYield) ? Math.max(0, Math.min(50, settings.herbYield)) : 8;
    settings.bark = Math.max(3, Math.min(100, settings.bark));
    if (settings.kinds == null) settings.kinds = new LinkedHashSet<>(Arrays.asList("tree", "fruit"));
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

  // ---- What the plugin reports ----

  void updateLevel(int level) {
    clientLevel = level;
    if (session == null && settings.level != level) {
      settings.level = level;
      rebuild();
      save();
    } else runTab.render(); // just refresh the "Farming level" line
  }

  void updateXp(long xp) {
    farmingXp = xp;
  }

  /** Applies unlocks read from the account. Waits for the run to end before changing the route. */
  void updateAccount(AccountScan.Result result) {
    account = result;
    missingChanged.accept(toWithdraw());
    if (session != null) {
      kitTab.render();
      return;
    }
    if (applyUnlocks(result)) {
      save();
      rebuild();
      return;
    }
    kitTab.render();
    if (setupTab.showsTravel()) setupTab.render(); // the Travel page lists what was detected
    // Re-checks the bank-first step as items are withdrawn.
    updateGuide();
  }

  /** Copies the account's Magic level, unlocks and house into the settings; true if any changed. */
  private boolean applyUnlocks(AccountScan.Result result) {
    boolean changed = false;
    if (result.magic > 0 && settings.magic != result.magic) {
      settings.magic = result.magic;
      changed = true;
    }
    for (Map.Entry<String, Boolean> unlock : result.unlocks.entrySet())
      changed |=
          unlock.getValue() ? settings.unlocks.add(unlock.getKey()) : settings.unlocks.remove(unlock.getKey());
    if (result.house != null && !result.house.equals(settings.house)) {
      settings.house = result.house;
      changed = true;
    }
    return changed;
  }

  /**
   * Records an in-game planting, compost or gardener payment. The patch completes once every
   * required action has been seen.
   */
  void patchAction(String action, String kind, Set<String> nearby) {
    FarmingPlanner.Step target = patchActedOn(action, kind, nearby);
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

  /** The unfinished route patch an action was seen at: one you're beside, else the guided one. */
  private FarmingPlanner.Step patchActedOn(String action, String kind, Set<String> nearby) {
    List<FarmingPlanner.Step> candidates = new ArrayList<>();
    if (guideIndex < route.size()) candidates.add(route.get(guideIndex));
    candidates.addAll(route);
    for (FarmingPlanner.Step step : candidates) {
      if (settings.completed.contains(step.patch.id)) continue;
      if (kind != null && !kind.equals(step.patch.kind)) continue;
      if (!Guidance.phases(step, settings).contains(action)) continue;
      if (nearby.contains(step.patch.id)
          || nearby.isEmpty() && guideIndex < route.size() && step == route.get(guideIndex)) return step;
    }
    return null;
  }

  /** Marks a step as done at a patch, moves the guide past it, and finishes the patch if due. */
  private void record(FarmingPlanner.Step target, String action) {
    Set<String> seen = settings.progress.computeIfAbsent(target.patch.id, k -> new LinkedHashSet<>());
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
    if (done && session != null && allPatchesDone()) {
      finishRun();
      return;
    }
    runTab.render();
    updateGuide();
  }

  private boolean allPatchesDone() {
    return route.stream().allMatch(step -> settings.completed.contains(step.patch.id));
  }

  /** A route teleport was used: the run is under way, whatever the kit now lacks. */
  void setOff() {
    if (skipBankCheck || !prepared) return;
    skipBankCheck = true;
    updateGuide();
  }

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
    if (session != null && allPatchesDone()) {
      finishRun();
      return;
    }
    runTab.render();
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

  // ---- The kit check ----

  /**
   * Kit items you aren't carrying or wearing, with how many are short. Pouch runes are checked
   * inside the rune pouch. Empty while logged out.
   */
  private List<String> missing() {
    List<String> result = new ArrayList<>();
    if (account == null || kit == null || route.isEmpty()) return result;
    Map<String, Integer> needed = new LinkedHashMap<>();
    for (String worn : kit.wear) needed.put(worn, 1);
    needed.putAll(kit.bring);
    for (Map.Entry<String, Integer> item : needed.entrySet()) addShortfall(result, item.getKey(), item.getValue());
    // Teleport spells in the route only work from the standard spellbook.
    boolean spells = route.stream().anyMatch(step -> Guidance.spell(step.travel) != null);
    if (spells && account.spellbook != 0) result.add(SWITCH_STANDARD);
    if (settings.resurrects(route) && account.spellbook != ARCEUUS) result.add(SWITCH_ARCEUUS);
    // Spellbook switched with the magic cape: it goes back in the bank (it isn't kit).
    if (!result.contains(SWITCH_ARCEUUS) && !result.contains(SWITCH_STANDARD))
      for (int id : FarmingData.IDS.get("Magic cape"))
        if (account.carried.getOrDefault(id, 0) > 0) {
          result.add(DEPOSIT_CAPE);
          break;
        }
    return result;
  }

  /** Adds a line for one kit item if you're carrying fewer than the run needs. */
  private void addShortfall(List<String> result, String name, int want) {
    int[] ids = FarmingData.IDS.get(name);
    if (ids == null) return;
    // An empty bottomless bucket doesn't count: it needs filling with compost first.
    if (name.equals("Bottomless compost bucket")) {
      ids = new int[] {FarmingData.id(name)};
      name = "Filled bottomless compost bucket";
    }
    // Runes count wherever they are: in the rune pouch, the inventory, or both.
    boolean rune = name.endsWith(" rune");
    int have = 0;
    for (int id : ids) {
      have += account.carried.getOrDefault(id, 0);
      if (rune) have += account.pouch.getOrDefault(id, 0);
    }
    if (have >= want) return;
    // Coins aren't counted out: just say to bring some, or more once you're short.
    if (name.equals("Coins")) result.add(have == 0 ? "Coins" : "More coins (" + String.format("%,d", want) + " needed)");
    else result.add(name + (want - have > 1 ? " ×" + (want - have) : ""));
  }

  /** Before a run starts, the guide stays at the bank until the whole kit is carried. */
  private boolean preparing() {
    // Only once "Prepare run" is pressed: between runs the overlay stays out of the way.
    return prepared
        && !skipBankCheck
        && session == null
        && settings.completed.isEmpty()
        && settings.progress.isEmpty()
        // Under way (e.g. landed at the first patch, its ring now out of charges): no going back.
        && guideIndex == 0
        && phaseIndex == 0
        && !missing().isEmpty();
  }

  /** What still needs withdrawing, only while preparing; supplies run down during a run. */
  private List<String> toWithdraw() {
    return preparing() ? missing() : Collections.emptyList();
  }

  // ---- Drawing ----

  private void rebuild() {
    if (disposed) return;
    route = FarmingPlanner.plan(settings);
    kit = FarmingPlanner.kit(route, settings);
    kitChanged.accept(FarmingPlanner.itemIds(kit, settings));
    sendKitOrder();
    missingChanged.accept(toWithdraw());
    runTab.render();
    kitTab.render();
    setupTab.render();
    recordsTab.render();
    updateYield();
    updateGuide();
    revalidate();
    repaint();
  }

  /** Redraws the banner, e.g. after Net XP/GP is switched on or off. */
  void refreshBanner() {
    updateYield();
  }

  private String yieldText(String xp, String gp) {
    String text = "<html>Expected XP: " + xp + "<br>Expected GP: " + gp;
    // Optional: everything your recorded runs have made so far.
    if ("true".equals(config.getConfiguration(GROUP, "netTotals"))) {
      long netXp = 0;
      long netGp = 0;
      for (RunRecord record : records) {
        netXp += record.xp;
        if (record.profit != null) netGp += record.profit;
      }
      text += "<br>Net XP: " + number(netXp) + "<br>Net GP: " + (netGp >= 0 ? "+" : "−") + number(Math.abs(netGp));
    }
    return text + "</html>";
  }

  /** The banner's expected XP now, and expected GP once guide prices are read. */
  private void updateYield() {
    yield.setForeground(Style.INK);
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
          Long net = FarmingPlanner.net(snapshot, snapshotSettings, this::guidePrice);
          SwingUtilities.invokeLater(
              () -> {
                if (disposed || !snapshot.equals(route)) return;
                String gp = net == null ? "unavailable" : (net >= 0 ? "+" : "−") + number(Math.abs(net));
                yield.setText(yieldText(number(xp), gp));
              });
        });
  }

  /** An item's guide price by name, or 0. Client thread. */
  private long guidePrice(String name) {
    int id = FarmingData.id(name);
    return id == 0 ? 0 : items.getItemPrice(id);
  }

  // ---- The guide ----

  /** Works out what to guide now and tells the plugin; redraws the guide card. */
  private void updateGuide() {
    if (disposed) return;
    // Prepared but nothing done yet: the bank button is wanted until the first step of the run.
    preparingChanged.accept(
        prepared && session == null && settings.completed.isEmpty() && settings.progress.isEmpty());
    skipFinishedPatches();
    if (guideIndex >= route.size() || route.isEmpty()) {
      guideChanged.accept(null);
      guideCard.showMessage(route.isEmpty() ? "No route selected" : "All patches checked");
    } else if (!prepared && session == null) {
      // Nothing is guided or highlighted until you choose to start preparing a run.
      guideChanged.accept(null);
      missingChanged.accept(Collections.emptyList());
      Crop growing = settings.crop(route.get(0).patch.kind);
      guideCard.showNextRun(idleMessage(), growing == null ? null : growing.sapling, this::prepare);
    } else showStepOrBank();
    runTab.render();
  }

  /** Off a finished patch: on to the first one not yet done. */
  private void skipFinishedPatches() {
    if (guideIndex >= route.size()) guideIndex = 0;
    if (route.isEmpty() || !settings.completed.contains(route.get(guideIndex).patch.id)) return;
    guideIndex = 0;
    while (guideIndex < route.size() && settings.completed.contains(route.get(guideIndex).patch.id)) guideIndex++;
    phaseIndex = 0;
  }

  private void showStepOrBank() {
    List<String> withdraw = toWithdraw();
    missingChanged.accept(withdraw);
    if (!withdraw.isEmpty()) {
      // Bank first: no teleport is guided or highlighted until the kit is carried.
      guideChanged.accept(null);
      guideCard.showWithdraw(withdraw, this::setOffAnyway);
      return;
    }
    FarmingPlanner.Step step = route.get(guideIndex);
    List<String> phases = Guidance.phases(step, settings);
    phaseIndex = Math.max(0, Math.min(phaseIndex, phases.size() - 1));
    Guidance guide = guidanceFor(step, phases.get(phaseIndex));
    guideChanged.accept(guide);
    guideCard.showStep(guide, route.size(), () -> movePhase(-1), () -> movePhase(1), () -> markPatchDone(step));
  }

  /** Everything the plugin needs about the guided step, the one before and the one after. */
  private Guidance guidanceFor(FarmingPlanner.Step step, String phase) {
    Guidance guide = new Guidance(step, settings, guideIndex + 1, route.size(), phase);
    boolean cape = FarmingPlanner.farmingCapeWorn(route, settings);
    guide.herbsPerPatch = herbCount(FarmingPlanner.expectedHerbs(step.patch, settings, cape));
    guide.herbsIfAlive = herbCount(FarmingPlanner.expectedHerbsIfAlive(step.patch, settings, cape));
    if (guideIndex > 0) {
      guide.prevPatchId = route.get(guideIndex - 1).patch.id;
      guide.prevKind = route.get(guideIndex - 1).patch.kind;
    }
    guide.resurrect = settings.resurrects(route);
    for (int next = guideIndex + 1; next < route.size(); next++)
      if (!settings.completed.contains(route.get(next).patch.id)) {
        setNextPatch(guide, route.get(next), cape);
        break;
      }
    return guide;
  }

  private void setNextPatch(Guidance guide, FarmingPlanner.Step next, boolean cape) {
    guide.nextDestination = next.patch.name;
    guide.nextTravel = next.travel.text;
    guide.nextPatchId = next.patch.id;
    guide.nextKind = next.patch.kind;
    guide.nextHerbsIfAlive = herbCount(FarmingPlanner.expectedHerbsIfAlive(next.patch, settings, cape));
    // Its teleport item, to show in orange (not the staff or pouch: nothing to click there).
    List<String> teleport = new ArrayList<>(next.travel.items);
    teleport.addAll(next.travel.gear);
    teleport.removeIf(item -> item.contains("staff") || item.equals("Rune pouch"));
    guide.nextTravelItems = teleport;
  }

  /** Expected herbs rounded up, or 8 when unknown. */
  private static int herbCount(double expected) {
    int count = (int) Math.ceil(expected);
    return count <= 0 ? 8 : count;
  }

  private void prepare() {
    prepared = true;
    updateGuide();
  }

  private void setOffAnyway() {
    skipBankCheck = true;
    updateGuide();
  }

  private void movePhase(int by) {
    phaseIndex += by;
    updateGuide();
  }

  private void markPatchDone(FarmingPlanner.Step step) {
    settings.completed.add(step.patch.id);
    phaseIndex = 0;
    save();
    runTab.render();
    updateGuide();
  }

  // ---- The timed run ----

  private long duration() {
    return elapsed + (running ? (System.nanoTime() - nanoStart) / 1_000_000 : 0);
  }

  private void startRun(boolean keepProgress) {
    if (route.isEmpty()) return;
    prepared = true;
    if (session == null) {
      session = newSession();
      if (!keepProgress) {
        clearProgress();
        guideIndex = 0;
        phaseIndex = 0;
      }
      elapsed = 0;
      runTab.render();
      setupTab.render();
      updateGuide();
    }
    if (!running) {
      nanoStart = System.nanoTime();
      running = true;
    }
    tabs.setSelectedIndex(RUN_TAB);
    save();
    updateTimer();
  }

  private RunRecord.Session newSession() {
    RunRecord.Session started = new RunRecord.Session();
    started.startXp = farmingXp;
    started.signature = FarmingPlanner.signature(route, kit, settings);
    started.label =
        String.join(" + ", settings.kinds) + " · " + route.size() + " patches · "
            + (settings.farmer ? "Farmer" : "Graceful");
    started.setup =
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
    started.patches = route.size();
    return started;
  }

  private void pauseOrResume() {
    if (running) pauseRun();
    else if (session != null) {
      running = true;
      nanoStart = System.nanoTime();
      updateTimer();
    }
  }

  private void pauseRun() {
    if (!running) return;
    elapsed = duration();
    running = false;
    save();
    updateTimer();
  }

  private void finishRun() {
    if (session == null || duration() < SHORTEST_RUN_MS) return;
    RunRecord record = new RunRecord();
    record.signature = session.signature;
    record.label = session.label;
    record.setup = session.setup;
    record.date = Instant.now().toString();
    record.milliseconds = duration();
    record.patches = session.patches;
    record.completed = (int) route.stream().filter(step -> settings.completed.contains(step.patch.id)).count();
    record.herbs = session.herbs;
    record.bark = session.bark;
    if (session.startXp > 0 && farmingXp > session.startXp) record.xp = farmingXp - session.startXp;
    records.add(record);
    if (records.size() > MAX_RECORDS) records.remove(0);
    recordsTab.showLatest(record);
    saveRecords();
    priceRecord(record);
    resetRun();
    recordsTab.render();
    tabs.setSelectedIndex(RECORDS_TAB);
    onFinish.run();
  }

  /** Profit at guide prices for the patches done, using the herbs actually picked. */
  private void priceRecord(RunRecord record) {
    List<FarmingPlanner.Step> done = new ArrayList<>();
    for (FarmingPlanner.Step step : route) if (settings.completed.contains(step.patch.id)) done.add(step);
    Settings priced = gson.fromJson(gson.toJson(settings), Settings.class);
    long herbPatches = done.stream().filter(step -> step.patch.kind.equals("herb")).count();
    if (herbPatches > 0) priced.herbActual = (double) record.herbs / herbPatches;
    // Celastrus bark actually chopped this run, when counted.
    if (record.bark > 0) priced.bark = record.bark;
    clientThread.invoke(
        () -> {
          Long profit = FarmingPlanner.net(done, priced, this::guidePrice);
          SwingUtilities.invokeLater(
              () -> {
                record.profit = profit;
                saveRecords();
                recordsTab.render();
              });
        });
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
    finish.setEnabled(session != null && duration() >= SHORTEST_RUN_MS);
    // Also after Prepare run, before anything has started: back to between runs.
    reset.setEnabled(prepared || session != null || !settings.completed.isEmpty() || !settings.progress.isEmpty());
    reset.setToolTipText("Abandon this run: clears its progress and timer without saving a time");
  }

  /**
   * Between runs: when this route's crops will all be grown. From what RuneLite's Time Tracking
   * last saw at each patch, else the planting time this plugin saw (patches planted before either
   * was watching have neither). Null if nothing is known.
   */
  private String idleMessage() {
    if (prepared || session != null) return null;
    long ready = 0;
    for (FarmingPlanner.Step step : route) {
      Long at = TimeTrackingData.readyAt(config, step.patch.id);
      Long planted = settings.plantedAt.get(step.patch.id);
      Crop crop = settings.crop(step.patch.kind);
      if (at == null && planted != null && crop != null) at = FarmingData.readyAt(crop.name, planted);
      if (at != null) ready = Math.max(ready, at);
    }
    if (ready == 0) return null;
    if (ready <= System.currentTimeMillis()) return "Your patches be ready!";
    String at =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ready), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"));
    return "The plants still be growin' - come back at " + at;
  }

  void dispose() {
    guideChanged.accept(null);
    disposed = true;
    pauseRun();
    ticker.stop();
    save();
  }
}
