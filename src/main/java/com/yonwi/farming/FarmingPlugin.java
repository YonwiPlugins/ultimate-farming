package com.yonwi.farming;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Starts and stops everything, and passes the game's events to the part that handles them. The
 * sidebar (on Swing's thread) chooses the run; the parts below (on the client thread) guide it.
 */
@PluginDescriptor(
    name = "Ultimate Farming",
    description = "Mixed tree routes, farming kit and personal run times",
    tags = {"farming", "tree", "fruit", "route"})
@PluginDependency(BankTagsPlugin.class)
public class FarmingPlugin extends Plugin {
  /** How long the "plants still be growin'" line stays on the overlay after a run. */
  private static final long IDLE_SHOWN_MS = 15_000;
  /** Our own key for the fireworks animation on the player. */
  private static final int FIREWORKS_KEY = 0x0F4A11;

  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private ItemManager itemManager;
  @Inject private ConfigManager configManager;
  @Inject private ClientToolbar toolbar;
  @Inject private Gson gson;
  @Inject private OverlayManager overlays;
  @Inject private FarmingConfig config;

  @Inject private FarmingInfoOverlay infoOverlay;
  @Inject private FarmingHighlightOverlay highlightOverlay;
  @Inject private FarmingInventoryOverlay inventoryOverlay;
  @Inject private FarmingDebugOverlay debugOverlay;
  @Inject private FarmingAlertOverlay alertOverlay;
  @Inject private FarmingTimerOverlay timerOverlay;

  @Inject private DebugLog debug;
  @Inject private DebugTools debugTools;
  @Inject private SceneTracker scene;
  @Inject private AccountItems account;
  @Inject private KitTracker kit;
  @Inject private BankButton bankButton;
  @Inject private SpellbookSwitch spellbook;
  @Inject private GuidedPatch patch;
  @Inject private StepProgress progress;
  @Inject private Targets targets;
  @Inject private HarvestTracker harvest;
  @Inject private NotingAdvice noting;
  @Inject private RouteLine route;
  @Inject private MenuSwaps menuSwaps;
  @Inject private FairyRingLog fairyRingLog;
  @Inject private PatchEvents patchEvents;

  private volatile Guidance guidance;
  private FarmingPanel panel;
  private NavigationButton navigation;
  private volatile boolean active;
  /** Between runs: when the plants will be ready, or that they are. */
  private volatile String idleMessage;
  /** When the idle line leaves the overlay. */
  private volatile long idleShownUntil;
  /** The run timer's text while a run is timed, else null (for the optional timer overlay). */
  private volatile String runTime;

  @Provides
  FarmingConfig config(ConfigManager manager) {
    return manager.getConfig(FarmingConfig.class);
  }

  /** The step being guided, or null between runs (or before the bank). */
  Guidance guide() {
    return guidance;
  }

  boolean isActive() {
    return active;
  }

  FarmingConfig config() {
    return config;
  }

  DebugLog debug() {
    return debug;
  }

  DebugTools debugTools() {
    return debugTools;
  }

  SceneTracker scene() {
    return scene;
  }

  AccountItems account() {
    return account;
  }

  KitTracker kit() {
    return kit;
  }

  BankButton bankButton() {
    return bankButton;
  }

  SpellbookSwitch spellbook() {
    return spellbook;
  }

  GuidedPatch patch() {
    return patch;
  }

  StepProgress progress() {
    return progress;
  }

  Targets targets() {
    return targets;
  }

  HarvestTracker harvest() {
    return harvest;
  }

  NotingAdvice noting() {
    return noting;
  }

  RouteLine route() {
    return route;
  }

  String runTime() {
    return runTime;
  }

  /** Between runs: shown briefly after a run, then the overlay gets out of the way; the sidebar keeps it. */
  String idleMessage() {
    return System.currentTimeMillis() < idleShownUntil ? idleMessage : null;
  }

  /** Runs something on the sidebar, on Swing's thread, while the plugin and sidebar are up. */
  void toPanel(Consumer<FarmingPanel> action) {
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) action.accept(panel);
        });
  }

  @Override
  protected void startUp() {
    active = true;
    overlays.add(infoOverlay);
    overlays.add(highlightOverlay);
    overlays.add(inventoryOverlay);
    overlays.add(debugOverlay);
    overlays.add(alertOverlay);
    overlays.add(timerOverlay);
    bankButton.registerFilter();
    clientThread.invoke(this::seedScene);
    SwingUtilities.invokeLater(
        () -> {
          if (!active) return;
          panel = createPanel();
          navigation =
              NavigationButton.builder()
                  .tooltip("Ultimate Farming")
                  .priority(6)
                  .icon(Icons.leaf())
                  .panel(panel)
                  .build();
          toolbar.addNavigation(navigation);
          refreshLevel();
        });
  }

  private FarmingPanel createPanel() {
    FarmingPanel created =
        new FarmingPanel(
            itemManager,
            clientThread,
            configManager,
            gson,
            step -> {
              guidance = step;
              // Redraw targets straight away instead of waiting for the next game tick.
              clientThread.invoke(targets::refresh);
            },
            kit::setItems,
            kit::setMissing);
    created.setOnFinish(this::celebrate);
    created.setIdleMessage(message -> idleMessage = message);
    created.setTimeListener(time -> runTime = time);
    created.setKitOrder(this::kitOrderChanged);
    created.setKitCounts(kit::setCounts);
    created.setPreparing(bankButton::setPreparing);
    return created;
  }

  /** The run changed while the bank shows the kit: show the new kit, not the old one. */
  private void kitOrderChanged(List<String> names) {
    if (!kit.setOrder(names)) return;
    clientThread.invokeLater(
        () -> {
          if (active && bankButton.filterOpen() && bankButton.bankOpen()) bankButton.refreshFilter();
        });
  }

  @Override
  protected void shutDown() {
    active = false;
    guidance = null;
    bankButton.unregisterFilter();
    clientThread.invoke(bankButton::remove);
    overlays.remove(infoOverlay);
    overlays.remove(highlightOverlay);
    overlays.remove(inventoryOverlay);
    overlays.remove(debugOverlay);
    overlays.remove(alertOverlay);
    overlays.remove(timerOverlay);
    clientThread.invoke(
        () -> {
          scene.clear();
          targets.clear();
        });
    SwingUtilities.invokeLater(
        () -> {
          if (navigation != null) toolbar.removeNavigation(navigation);
          if (panel != null) panel.dispose();
          navigation = null;
          panel = null;
        });
  }

  private void seedScene() {
    if (active) scene.seed();
  }

  /** Sends your Farming level and XP to the sidebar. */
  private void refreshLevel() {
    clientThread.invoke(
        () -> {
          if (!active || client.getGameState() != GameState.LOGGED_IN) return;
          int level = client.getRealSkillLevel(Skill.FARMING);
          long xp = client.getSkillExperience(Skill.FARMING);
          toPanel(
              sidebar -> {
                sidebar.updateXp(xp);
                sidebar.updateLevel(level);
              });
        });
  }

  /** Level-up style fireworks on your character when a run is finished. Only you see them. */
  private void celebrate() {
    idleShownUntil = System.currentTimeMillis() + IDLE_SHOWN_MS;
    clientThread.invoke(
        () -> {
          Player player = client.getLocalPlayer();
          if (player != null) player.createSpotAnim(FIREWORKS_KEY, SpotanimID.LEVELUP_99_ANIM, 0, 0);
        });
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    targets.refresh();
    harvest.countSackHerbs();
    patchEvents.watchGardenerDialog();
    progress.check();
    if (config.debug()) {
      debugTools.scan();
      debugTools.trackLeg();
    }
    // Nothing guided (between runs, bank first): don't leave a stale Shortest Path route.
    if (guidance == null) {
      route.clearShortestPath();
      progress.clearRun();
    }
    spellbook.refresh();
    account.onTick();
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged e) {
    GameState state = e.getGameState();
    if (state == GameState.LOGGED_IN) {
      refreshLevel();
      seedScene();
      account.scanSoon();
    } else if (state == GameState.LOADING || state == GameState.LOGIN_SCREEN) {
      progress.sceneLoaded();
      if (state == GameState.LOGIN_SCREEN) {
        account.forgetBank();
        harvest.reset();
      }
      scene.clear();
      targets.clear();
    }
  }

  /**
   * Switching RuneLite profile doesn't restart the plugin, so the sidebar would otherwise keep the
   * old profile's records and settings and save them over the new one's.
   */
  @Subscribe
  public void onProfileChanged(ProfileChanged e) {
    guidance = null;
    toPanel(FarmingPanel::reload);
    clientThread.invoke(account::scanSoon);
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged e) {
    if (!FarmingPanel.GROUP.equals(e.getGroup())) return;
    if ("netTotals".equals(e.getKey())) toPanel(FarmingPanel::refreshBanner);
    if ("attasWarning".equals(e.getKey())) toPanel(FarmingPanel::refreshRun);
  }

  @Subscribe
  public void onStatChanged(StatChanged e) {
    if (e.getSkill() != Skill.FARMING) return;
    harvest.farmingXpChanged(e.getXp());
    refreshLevel();
  }

  @Subscribe
  public void onGameObjectSpawned(GameObjectSpawned e) {
    scene.remember(e.getGameObject());
  }

  @Subscribe
  public void onGameObjectDespawned(GameObjectDespawned e) {
    scene.forget(e.getGameObject());
  }

  @Subscribe
  public void onGroundObjectSpawned(GroundObjectSpawned e) {
    scene.remember(e.getGroundObject());
  }

  @Subscribe
  public void onGroundObjectDespawned(GroundObjectDespawned e) {
    scene.forget(e.getGroundObject());
  }

  @Subscribe
  public void onDecorativeObjectSpawned(DecorativeObjectSpawned e) {
    scene.remember(e.getDecorativeObject());
  }

  @Subscribe
  public void onDecorativeObjectDespawned(DecorativeObjectDespawned e) {
    scene.forget(e.getDecorativeObject());
  }

  @Subscribe
  public void onNpcSpawned(NpcSpawned e) {
    scene.addNpc(e.getNpc());
  }

  @Subscribe
  public void onNpcDespawned(NpcDespawned e) {
    scene.removeNpc(e.getNpc());
  }

  @Subscribe
  public void onPostClientTick(PostClientTick e) {
    Guidance step = guidance;
    if (step == null || !step.phase.equals("Travel") && !progress.houseReturn()) return;
    menuSwaps.colourDestination(step);
    fairyRingLog.highlight(step);
  }

  // Runs after RuneLite's Menu Entry Swapper (higher priority runs first), whose own Ardougne
  // cloak and construction cape swaps otherwise overrode these.
  @Subscribe(priority = -10)
  public void onPostMenuSort(PostMenuSort e) {
    menuSwaps.swap();
  }

  @Subscribe
  public void onScriptPostFired(ScriptPostFired e) {
    if (e.getScriptId() == ScriptID.BANKMAIN_BUILD) bankButton.update();
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded e) {
    if (e.getGroupId() == InterfaceID.BANKMAIN) bankButton.bankLoaded();
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed e) {
    if (e.getGroupId() == InterfaceID.BANKMAIN) bankButton.bankClosed();
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged e) {
    int container = e.getContainerId();
    if (container == InventoryID.BANK) {
      account.bankChanged(e.getItemContainer());
      harvest.bankSeen();
    }
    if (container == InventoryID.BANK || container == InventoryID.INV || container == InventoryID.WORN)
      account.scanSoon();
    if (container == InventoryID.INV) harvest.inventoryChanged(e.getItemContainer());
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged e) {
    if (e.getVarbitId() == VarbitID.SPELLBOOK) {
      account.scanSoon();
      spellbook.spellbookChanged();
    }
    if (KitTracker.isPouchVarbit(e.getVarbitId())) account.scanSoon();
    // The anima patch changed (planted, grown or died) while you're at the guild.
    if (e.getVarbitId() == VarbitID.FARMING_TRANSMIT_M) account.scanSoon();
  }

  @Subscribe
  public void onChatMessage(ChatMessage e) {
    patchEvents.gameMessage(e);
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked e) {
    debugTools.clicked();
    debug.log(
        "click '" + Text.removeTags(e.getMenuOption()) + "' on '" + Text.removeTags(e.getMenuTarget())
            + "' action " + e.getMenuAction() + " id " + e.getId() + " param0 " + e.getParam0()
            + " param1 " + e.getParam1() + " (group " + (e.getParam1() >>> 16) + " child "
            + (e.getParam1() & 0xFFFF) + ") item " + e.getItemId());
    Guidance step = guidance;
    // Anything done to the guided patch (pick, rake, use an item on it…): no more line to it.
    TileObject worked = patch.object();
    if (worked != null && step != null && e.getId() == worked.getId()) progress.setWorkedPatch(step.patchId);
    String option = Text.removeTags(e.getMenuOption()).toLowerCase(Locale.ROOT);
    String target = Text.removeTags(e.getMenuTarget()).toLowerCase(Locale.ROOT);
    // The magic cape's Spellbook option: a change of spellbook soon after counts as one of the
    // 5 a day.
    if (option.equals("spellbook") && (target.contains("magic cape") || target.contains("max cape")))
      spellbook.capeClicked();
    if (step == null) return;
    progress.travelClicked(e, step, option, target);
    if (e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
        || e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_NPC) targets.itemUsed(step);
    // Checking a tree at another route patch moves the guide there (you've skipped ahead).
    if (option.equals("check-health")) patchEvents.patchAction("Check health", null);
    if (target.contains("resurrect crops")) progress.resurrectCast(step);
    targets.routeClicked(step, option, target);
  }
}
