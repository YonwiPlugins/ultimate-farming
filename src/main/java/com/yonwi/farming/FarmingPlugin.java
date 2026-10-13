package com.yonwi.farming;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import net.runelite.client.util.Text;

@PluginDescriptor(
    name = "Ultimate Farming",
    description = "Mixed tree routes, farming kit and personal run times",
    tags = {"farming", "tree", "fruit", "route"})
@PluginDependency(BankTagsPlugin.class)
public class FarmingPlugin extends Plugin {
  private static final String BANK_TAG = "ultimate farming";
  @Inject private TagManager tagManager;
  @Inject private BankTagsService bankTags;
  @Inject private LayoutManager layoutManager;
  /** Kit item names in Kit tab order (wear, then bring), for the bank layout. */
  private volatile List<String> kitOrder = Collections.emptyList();
  private volatile Map<String, Integer> kitCounts = Collections.emptyMap();

  /** True while the bank shows this plugin's kit filter. */
  boolean kitFilterOpen() {
    return BANK_TAG.equals(bankTags.getActiveTag());
  }

  /** How many of this item ID the run needs, or 0. */
  int kitCount(int id) {
    String name = kitName(id);
    return name == null ? 0 : kitCounts.get(name);
  }

  /**
   * Before a run: rune item IDs the run still needs that you aren't carrying (pouch + inventory),
   * when your rune pouch is in the inventory, so it can be set up. Empty otherwise.
   */
  Set<Integer> pouchShortfall() {
    if (guidance != null || missing.isEmpty()) return Collections.emptySet();
    Set<Integer> pouch = itemIds(Collections.singletonList("Rune pouch"));
    ItemContainer inv = client.getItemContainer(InventoryID.INV);
    boolean carried = false;
    if (inv != null) for (Item item : inv.getItems()) carried |= pouch.contains(item.getId());
    if (!carried) return Collections.emptySet();
    Set<Integer> shortRunes = new HashSet<>();
    for (String name : kitCounts.keySet()) {
      if (!name.endsWith(" rune")) continue;
      int id = FarmingData.id(name);
      if (id > 0 && kitRemaining(id) > 0) shortRunes.add(id);
    }
    return shortRunes;
  }

  /** Rune item IDs the run needs, whether or not you have them yet. */
  Set<Integer> runesNeeded() {
    Set<Integer> ids = new HashSet<>();
    for (String name : kitCounts.keySet())
      if (name.endsWith(" rune")) {
        int id = FarmingData.id(name);
        if (id > 0) ids.add(id);
      }
    return ids;
  }

  /** One line telling you what to do with the rune pouch at the bank, or null. */
  private volatile String pouchHint;

  void setPouchHint(String hint) {
    pouchHint = hint;
  }

  String pouchHint() {
    return pouchShortfall().isEmpty() ? null : pouchHint;
  }

  /** Picking herbs right now, with the magic secateurs in the inventory instead of wielded. */
  boolean secateursAlert() {
    Guidance step = guidance;
    return step != null
        && step.phase.equals("Harvest")
        && step.items.contains("Magic secateurs")
        && !deadPatch
        && secateursToWield();
  }

  /** Harvesting is now or next, the secateurs are wanted, and they're in the inventory unworn. */
  boolean secateursToWield() {
    Guidance step = guidance;
    if (step == null) return false;
    boolean harvestSoon =
        step.phase.equals("Harvest") && step.items.contains("Magic secateurs")
            || "Harvest".equals(step.nextPhase) && step.nextItems.contains("Magic secateurs");
    if (!harvestSoon) return false;
    Set<Integer> ids = itemIds(Collections.singletonList("Magic secateurs"));
    ItemContainer inv = client.getItemContainer(InventoryID.INV);
    if (inv == null) return false;
    for (Item item : inv.getItems()) if (ids.contains(item.getId())) return true;
    return false;
  }

  /** The kit item name this item ID counts as, or null. */
  private String kitName(int id) {
    for (Map.Entry<String, Integer> e : kitCounts.entrySet())
      for (int known : FarmingData.IDS.getOrDefault(e.getKey(), new int[0]))
        if (known == id) return e.getKey();
    return null;
  }

  /**
   * How many more of this kit item you still need to take out of the bank: what the run needs
   * minus what's in your inventory, worn, or (for runes) in the rune pouch. -1 if it isn't kit.
   */
  int kitRemaining(int id) {
    String name = kitName(id);
    if (name == null) return -1;
    int need = kitCounts.get(name);
    int have = 0;
    Set<Integer> ids = new HashSet<>();
    for (int known : FarmingData.IDS.getOrDefault(name, new int[0])) ids.add(known);
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer items = client.getItemContainer(container);
      if (items == null) continue;
      for (Item item : items.getItems())
        if (item.getId() > 0 && ids.contains(itemManager.canonicalize(item.getId())))
          have += item.getQuantity();
    }
    if (name.endsWith(" rune"))
      for (Map.Entry<Integer, Integer> rune : pouchRunes().entrySet())
        if (ids.contains(rune.getKey())) have += rune.getValue();
    return Math.max(0, need - have);
  }

  /**
   * One slot per kit item, in Kit tab order. Each slot uses the variant you actually have in the
   * bank or inventory (e.g. your ring of wealth's charge), so it shows that item or its
   * placeholder.
   */
  private int[] kitLayout() {
    Set<Integer> have = new HashSet<>();
    if (bank != null) have.addAll(bank);
    ItemContainer inv = client.getItemContainer(InventoryID.INV);
    if (inv != null) for (Item i : inv.getItems()) have.add(itemManager.canonicalize(i.getId()));
    List<Integer> slots = new ArrayList<>(), seeds = new ArrayList<>();
    Set<Integer> hidden = new HashSet<>();
    List<String> names = new ArrayList<>(kitOrder);
    if (capeNeeded) names.add("Magic cape"); // to switch spellbook before the run
    for (String name : names) {
      int[] ids = FarmingData.IDS.getOrDefault(name, new int[0]);
      if (ids.length == 0) continue;
      int chosen = FarmingData.id(name) > 0 ? FarmingData.id(name) : ids[0];
      for (int id : ids)
        if (have.contains(id)) {
          chosen = id;
          break;
        }
      boolean seed = name.endsWith(" seed") || name.endsWith(" sapling");
      if (!slots.contains(chosen) && !seeds.contains(chosen)) (seed ? seeds : slots).add(chosen);
      // One quetzal whistle is enough: show the best you own and hide the rest (IDS lists the
      // best first).
      if (name.equals("Quetzal whistle") || name.equals("Axe"))
        for (int id : ids) if (id != chosen) hidden.add(id);
    }
    hiddenVariants = hidden;
    // Seeds and saplings start the second row (slot 9): the first row is the outfit you put on,
    // so they're the first thing withdrawn into the inventory.
    if (!seeds.isEmpty()) {
      int at = Math.min(8, slots.size());
      while (at < 8) {
        slots.add(-1);
        at++;
      }
      slots.addAll(8, seeds);
    }
    return slots.stream().mapToInt(Integer::intValue).toArray();
  }
  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private ItemManager itemManager;
  @Inject private ConfigManager configManager;
  @Inject private ClientToolbar toolbar;
  @Inject private Gson gson;
  @Inject private OverlayManager overlays;
  @Inject private FarmingInfoOverlay infoOverlay;
  @Inject private FarmingHighlightOverlay highlightOverlay;
  @Inject private FarmingInventoryOverlay inventoryOverlay;
  @Inject private FarmingDebugOverlay debugOverlay;
  @Inject private FarmingAlertOverlay alertOverlay;
  @Inject private FarmingConfig config;
  @Inject private net.runelite.client.eventbus.EventBus eventBus;
  @Inject private net.runelite.client.plugins.PluginManager pluginManager;
  private volatile Guidance guidance;
  private final Set<TileObject> sceneObjects = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<NPC> sceneNpcs = Collections.newSetFromMap(new IdentityHashMap<>());
  private List<TileObject> objectHighlights = Collections.emptyList();
  private List<NPC> npcHighlights = Collections.emptyList();
  private List<TileObject> nextObjects = Collections.emptyList();
  private List<NPC> nextNpcs = Collections.emptyList();
  private WorldPoint lastLocation;
  private Set<Integer> bank;
  private boolean scanDue;
  private int ticks;
  private String lastDialog;
  /** The guided patch object, found each tick while nearby. */
  private TileObject patchObject;
  /** Whether the guided patch currently offers "Rake", i.e. has weeds to clear. */
  private volatile boolean needsRake;
  /** Tree types whose gardener clears the old tree for a fee. */
  static final Set<String> GARDENER_REMOVAL =
      new HashSet<>(Arrays.asList("tree", "fruit", "redwood", "hardwood"));
  private int grimyCount = -1, barkCount = -1;
  /** Unnoted grimy herbs, whose count rising means a herb was just picked. */
  private static final Set<Integer> GRIMY = new HashSet<>();

  static Set<Integer> grimyHerbs() {
    return GRIMY;
  }

  static {
    for (Crop c : FarmingData.CROPS.get("herb"))
      for (int id : FarmingData.IDS.getOrDefault(c.fruit, new int[0])) GRIMY.add(id);
  }
  private volatile Set<Integer> kitItems = Collections.emptySet();
  private volatile boolean bankUnknown;
  private Widget bankButton, bankIcon;
  private volatile List<String> missing = Collections.emptyList();

  @Provides
  FarmingConfig config(ConfigManager manager) {
    return manager.getConfig(FarmingConfig.class);
  }

  Guidance guide() {
    return guidance;
  }

  List<TileObject> highlightObjects() {
    return objectHighlights;
  }

  List<NPC> highlightNpcs() {
    return npcHighlights;
  }

  /** Targets for the substep after the current one, drawn in orange. */
  List<TileObject> nextObjects() {
    return nextObjects;
  }

  List<NPC> nextNpcs() {
    return nextNpcs;
  }

  private NPC debugGardener;
  private final java.util.Deque<String> events = new ArrayDeque<>();

  /** Remembers the last few detected events for the debug box. */
  private void event(String text) {
    String line = text.length() > 40 ? text.substring(0, 40) + "…" : text;
    if (line.equals(events.peekFirst())) return;
    events.addFirst(line);
    while (events.size() > 4) events.removeLast();
  }

  /** What the plugin currently believes, for the debug box. Called on the client thread. */
  List<String> debugLines() {
    List<String> lines = new ArrayList<>();
    Guidance step = guidance;
    Player player = client.getLocalPlayer();
    if (step == null) lines.add("No step (bank first or no route)");
    else {
      lines.add(
          step.patchId + " · " + step.phase + " (" + (step.phaseIndex + 1) + "/"
              + step.phases.size() + ") next " + step.nextPhase);
      lines.add("travel multi-leg " + multiLeg(step.travel) + " · rake " + needsRake);
      if (player != null) lines.add("arrived " + arrived(step.patchId, player));
      if (step.phase.startsWith("Note"))
        lines.add("unnoted held " + holdingUnnoted(itemIds(step.items)));
    }
    if (player != null) {
      WorldPoint p = player.getWorldLocation();
      lines.add(
          "you " + p.getX() + "," + p.getY() + "," + p.getPlane() + " region " + p.getRegionID()
              + (client.getTopLevelWorldView().isInstance() ? " (instance)" : ""));
    }
    TileObject patch = patchObject;
    if (patch == null) lines.add("patch: not found");
    else {
      ObjectComposition base = client.getObjectDefinition(patch.getId());
      ObjectComposition c = base.getImpostorIds() != null ? base.getImpostor() : base;
      lines.add(
          "patch " + patch.getId() + " -> "
              + (c == null ? "?" : c.getId() + " '" + c.getName() + "'"));
      if (c != null) {
        List<String> actions = new ArrayList<>();
        for (String a : c.getActions()) if (a != null) actions.add(a);
        lines.add("actions " + String.join("/", actions));
      }
    }
    lines.add(
        "gardener " + (debugGardener == null ? "none" : debugGardener.getId()) + " · lep "
            + (npcHighlights.isEmpty() && nextNpcs.isEmpty() ? "-" : "lit"));
    // While a quetzal map is open, list its destination icons so the highlight can be checked.
    for (int id : new int[] {InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.QuetzalMenu.ICONS}) {
      Widget icons = client.getWidget(id);
      Widget[] children = icons == null || icons.isHidden() ? null : icons.getDynamicChildren();
      if (children == null) continue;
      List<String> names = new ArrayList<>();
      for (Widget w : children)
        if (w != null && w.getName() != null && !w.getName().isEmpty())
          names.add(Text.removeTags(w.getName()));
      lines.add("quetzal icons: " + String.join(", ", names));
    }
    if (!overlayHint.isEmpty()) lines.add("hint: " + overlayHint);
    for (String e : events) lines.add("> " + e);
    return lines;
  }

  /** Item IDs (all charge and tier variants) used by the guided action. */
  Set<Integer> itemIds(Guidance step) {
    // Clearing a tree yourself: chop it while it stands (axe used from the inventory, nothing to
    // highlight), then the spade on the stump or bare tree.
    if (step.phase.equals("Clear") && selfClear(step) && !patchChoppable && patchObject != null)
      return itemIds(Collections.singletonList("Spade"));
    return usableIds(step.items);
  }

  /** Whether you clear this patch yourself (no gardener removal), e.g. Celastrus and Calquat. */
  boolean selfClear(Guidance step) {
    return !GARDENER_REMOVAL.contains(step.kind) || deadPatch;
  }

  /** The guided tree can still be chopped down (before it's a stump). */
  private volatile boolean patchChoppable;

  /** Whether an object (resolved through its impostor) has this right-click option. */
  private boolean offers(TileObject object, String option) {
    if (object == null) return false;
    ObjectComposition c = client.getObjectDefinition(object.getId());
    if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
    if (c == null) return false;
    for (String action : c.getActions()) if (option.equalsIgnoreCase(action)) return true;
    return false;
  }

  /** The inventory overlay's last decision, shown in the debug box. */
  private volatile String overlayHint = "";

  void setOverlayHint(String hint) {
    overlayHint = hint;
  }

  /** The guided patch has weeds: raking it is a single click on the patch. */
  boolean needsRake() {
    return needsRake;
  }

  /** Item IDs to highlight, leaving the rake out unless the patch actually has weeds. */
  Set<Integer> usableIds(List<String> names) {
    List<String> wanted = new ArrayList<>(names);
    if (!needsRake) wanted.remove("Rake");
    return itemIds(wanted);
  }

  static Set<Integer> itemIds(List<String> names) {
    Set<Integer> ids = new HashSet<>();
    for (String raw : names)
      for (int id : FarmingData.IDS.getOrDefault(FarmingPlanner.normalize(raw), new int[0]))
        ids.add(id);
    return ids;
  }

  /** Whether the player has one of these items selected, ready to use on a patch. */
  boolean selectedItem(Set<Integer> ids) {
    if (!client.isWidgetSelected()) return false;
    Widget selected = client.getSelectedWidget();
    return selected != null && ids.contains(selected.getItemId());
  }

  List<String> missingItems() {
    return missing;
  }

  boolean bankUnknown() {
    return bankUnknown;
  }

  private FarmingPanel panel;
  private NavigationButton navigation;
  private volatile boolean active;

  @Override
  protected void startUp() {
    active = true;
    overlays.add(infoOverlay);
    overlays.add(highlightOverlay);
    overlays.add(inventoryOverlay);
    overlays.add(debugOverlay);
    overlays.add(alertOverlay);
    tagManager.registerTag(
        BANK_TAG,
        id ->
            !hiddenVariants.contains(id)
                && (kitItems.contains(id) || capeNeeded && MAGIC_CAPES.contains(id)));
    clientThread.invoke(this::seedScene);
    SwingUtilities.invokeLater(
        () -> {
          if (!active) return;
          panel =
              new FarmingPanel(
                  itemManager,
                  clientThread,
                  configManager,
                  gson,
                  g -> {
                    guidance = g;
                    // Redraw targets straight away instead of waiting for the next game tick.
                    clientThread.invoke(this::refreshHighlights);
                  },
                  ids -> kitItems = ids,
                  list -> {
                    boolean was = !missing.isEmpty();
                    missing = list;
                    // The bank button only shows while preparing: add or remove it as that changes.
                    if (was != !list.isEmpty()) clientThread.invokeLater(this::updateBankButton);
                  });
          panel.setOnFinish(this::celebrate);
          panel.setIdleMessage(m -> idleMessage = m);
          panel.setKitOrder(
              names -> {
                boolean changed = !names.equals(kitOrder);
                kitOrder = names;
                // The run changed while the bank shows the kit: show the new kit, not the old one.
                if (changed)
                  clientThread.invokeLater(
                      () -> {
                        if (active && kitFilterOpen() && client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null)
                          refreshKitFilter();
                      });
              });
          panel.setKitCounts(counts -> kitCounts = counts);
          navigation =
              NavigationButton.builder()
                  .tooltip("Ultimate Farming")
                  .priority(6)
                  .icon(leafIcon())
                  .panel(panel)
                  .build();
          toolbar.addNavigation(navigation);
          refreshLevel();
        });
  }

  /** A green leaf filling the whole 16px sidebar slot, corner to corner. */
  static BufferedImage leafIcon() {
    BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = icon.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    Path2D leaf = new Path2D.Double();
    leaf.moveTo(1.5, 14.5);
    leaf.curveTo(0.5, 6, 6, 0.5, 15.5, 0.5);
    leaf.curveTo(15.5, 10, 10, 15.5, 1.5, 14.5);
    leaf.closePath();
    g.setPaint(
        new GradientPaint(2, 14, new Color(62, 132, 58), 14, 2, new Color(152, 214, 104)));
    g.fill(leaf);
    g.setColor(new Color(36, 84, 38));
    g.setStroke(new BasicStroke(0.9f));
    g.draw(leaf);
    g.setColor(new Color(214, 240, 170, 220));
    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(new java.awt.geom.QuadCurve2D.Double(0.5, 15.5, 7, 9.5, 13.5, 2.5));
    g.dispose();
    return icon;
  }

  private void refreshLevel() {
    clientThread.invoke(
        () -> {
          if (!active || client.getGameState() != GameState.LOGGED_IN) return;
          int level = client.getRealSkillLevel(Skill.FARMING);
          long xp = client.getSkillExperience(Skill.FARMING);
          SwingUtilities.invokeLater(
              () -> {
                if (!active || panel == null) return;
                panel.updateXp(xp);
                panel.updateLevel(level);
              });
        });
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged e) {
    if (e.getGameState() == GameState.LOGGED_IN) {
      refreshLevel();
      seedScene();
      scanDue = true;
    } else if (e.getGameState() == GameState.LOADING
        || e.getGameState() == GameState.LOGIN_SCREEN) {
      if (e.getGameState() == GameState.LOGIN_SCREEN) {
        bank = null;
        grimyCount = -1;
      }
      sceneObjects.clear();
      sceneNpcs.clear();
      objectHighlights = Collections.emptyList();
      npcHighlights = Collections.emptyList();
    }
  }

  /**
   * Switching RuneLite profile doesn't restart the plugin, so the sidebar would otherwise keep the
   * old profile's records and settings and save them over the new one's.
   */
  @Subscribe
  public void onProfileChanged(net.runelite.client.events.ProfileChanged e) {
    guidance = null;
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) panel.reload();
        });
    clientThread.invoke(() -> scanDue = true);
  }

  @Subscribe
  public void onConfigChanged(net.runelite.client.events.ConfigChanged e) {
    if (!FarmingPanel.GROUP.equals(e.getGroup()) || !"netTotals".equals(e.getKey())) return;
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) panel.refreshBanner();
        });
  }

  @Subscribe
  public void onStatChanged(StatChanged e) {
    if (e.getSkill() == Skill.FARMING) refreshLevel();
  }

  @Override
  protected void shutDown() {
    active = false;
    guidance = null;
    tagManager.unregisterTag(BANK_TAG);
    clientThread.invoke(
        () -> {
          if (BANK_TAG.equals(bankTags.getActiveTag())) bankTags.closeBankTag();
          Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
          if (bankButton != null && universe != null) {
            bankButton.setHidden(true);
            if (bankIcon != null) bankIcon.setHidden(true);
            universe.revalidate();
          }
          bankButton = null;
        });
    overlays.remove(infoOverlay);
    overlays.remove(highlightOverlay);
    overlays.remove(inventoryOverlay);
    overlays.remove(debugOverlay);
    overlays.remove(alertOverlay);
    clientThread.invoke(
        () -> {
          sceneObjects.clear();
          sceneNpcs.clear();
          objectHighlights = Collections.emptyList();
          npcHighlights = Collections.emptyList();
        });
    SwingUtilities.invokeLater(
        () -> {
          if (navigation != null) toolbar.removeNavigation(navigation);
          if (panel != null) panel.dispose();
          navigation = null;
          panel = null;
        });
  }

  private void remember(TileObject object) {
    if (object == null) return;
    ObjectComposition c = client.getObjectDefinition(object.getId());
    if (c == null) return;
    String n = c.getName();
    if (c.getImpostorIds() != null
        || n != null
            && (n.toLowerCase(Locale.ROOT).contains("tree")
                || n.toLowerCase(Locale.ROOT).contains("patch")
                || n.toLowerCase(Locale.ROOT).contains("fairy ring")
                || n.toLowerCase(Locale.ROOT).contains("railing")
                || n.toLowerCase(Locale.ROOT).contains("portal")
                || n.toLowerCase(Locale.ROOT).contains("jewellery box")
                || n.toLowerCase(Locale.ROOT).contains("altar"))) sceneObjects.add(object);
  }

  private void seedScene() {
    if (!active || client.getGameState() != GameState.LOGGED_IN) return;
    sceneObjects.clear();
    sceneNpcs.clear();
    for (NPC npc : client.getTopLevelWorldView().npcs()) sceneNpcs.add(npc);
    Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
    for (Tile[][] plane : tiles)
      for (Tile[] column : plane)
        for (Tile tile : column)
          if (tile != null) {
            remember(tile.getGroundObject());
            remember(tile.getDecorativeObject());
            for (GameObject object : tile.getGameObjects()) remember(object);
          }
  }

  @Subscribe
  public void onGameObjectSpawned(GameObjectSpawned e) {
    remember(e.getGameObject());
  }

  @Subscribe
  public void onGameObjectDespawned(GameObjectDespawned e) {
    sceneObjects.remove(e.getGameObject());
  }

  @Subscribe
  public void onGroundObjectSpawned(GroundObjectSpawned e) {
    remember(e.getGroundObject());
  }

  @Subscribe
  public void onGroundObjectDespawned(GroundObjectDespawned e) {
    sceneObjects.remove(e.getGroundObject());
  }

  @Subscribe
  public void onDecorativeObjectSpawned(DecorativeObjectSpawned e) {
    remember(e.getDecorativeObject());
  }

  @Subscribe
  public void onDecorativeObjectDespawned(DecorativeObjectDespawned e) {
    sceneObjects.remove(e.getDecorativeObject());
  }

  @Subscribe
  public void onNpcSpawned(NpcSpawned e) {
    sceneNpcs.add(e.getNpc());
  }

  @Subscribe
  public void onNpcDespawned(NpcDespawned e) {
    sceneNpcs.remove(e.getNpc());
  }

  @Subscribe
  public void onGameTick(GameTick event) {
    refreshHighlights();
    watchGardenerDialog();
    checkProgress();
    // Nothing guided (between runs, bank first): don't leave a stale Shortest Path route.
    if (guidance == null) {
      shortestPath(null);
      resurrectTried.clear();
    }
    refreshSpellbookSwitch();
    if (scanDue || ++ticks % 100 == 0) scanAccount();
  }

  /**
   * Moves the guide on by itself each tick from the game's state: Travel ends on a teleport or on
   * arrival, patch steps from what the patch currently is, and noting once nothing is left unnoted.
   */
  private void checkProgress() {
    Guidance step = guidance;
    Player player = client.getLocalPlayer();
    if (step == null || player == null) return;
    WorldPoint here = player.getWorldLocation(), before = lastLocation;
    lastLocation = here;
    if (step.phase.equals("Travel")) {
      // A jump of many tiles in one tick is a teleport, so the teleport item is used up. Landing
      // in the house (an instance) doesn't count: the house portal is still to come.
      boolean teleported =
          before != null
              && (before.getPlane() != here.getPlane() || before.distanceTo(here) > 30)
              && !client.getTopLevelWorldView().isInstance();
      // A second leg after the teleport (guide, boat, quetzal...) means only arriving counts.
      if (!step.patchId.equals(jumpPatch)) {
        jumpPatch = step.patchId;
        jumped = false;
      }
      // After the teleport out (e.g. through the fairy ring), the ring or tree is no longer the
      // thing to click: the route to the patch is. Only a jump right after using a fairy ring,
      // spirit tree, portal or box counts: popping out to the bank and back mustn't lose the
      // house route.
      if (teleported && usedTransport) jumped = true;
      if (teleported) usedTransport = false;
      if (teleported && !multiLeg(step.travel) || arrived(step.patchId, player)) phaseDone(step);
      return;
    }
    if (step.phase.startsWith("Note")) {
      // Noting is done once no unnoted produce is left in the inventory.
      if (!holdingUnnoted(itemIds(step.items))) phaseDone(step);
      return;
    }
    // Every other step is read from what the patch is right now, not from what was clicked.
    TileObject patch = patchObject;
    if (patch == null) return;
    ObjectComposition c = client.getObjectDefinition(patch.getId());
    if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
    if (c == null || c.getName() == null) return;
    // Empty and weedy patches are named "... patch"; grown, growing and dead crops are not.
    boolean empty = c.getName().toLowerCase(Locale.ROOT).contains("patch");
    boolean pick = false, check = false, clearDead = false;
    for (String action : c.getActions())
      if (action != null) {
        String a = action.toLowerCase(Locale.ROOT);
        pick |= a.startsWith("pick") || a.startsWith("harvest");
        check |= a.equals("check-health");
        clearDead |= a.equals("clear");
      }
    // Dead crops (herbs or trees) have to be cleared by you; no gardener will do it.
    // Dead only by name ("Dead herbs", "Dead … tree"): a healthy grown Calquat also offers Clear.
    boolean deadName = c.getName().toLowerCase(Locale.ROOT).contains("dead");
    clearDead &= deadName;
    deadPatch = deadName;
    // Resurrect Crops cast here: growing again means it worked and the patch is finished; a
    // failed cast clears the patch, and the normal plant and compost steps follow.
    if (step.patchId.equals(resurrectCastOn) && !deadPatch && !empty && !pick && !check) {
      event("resurrected: " + step.patchId);
      resurrectCastOn = null;
      String id = step.patchId;
      SwingUtilities.invokeLater(
          () -> {
            if (active && panel != null) panel.resurrected(id);
          });
      return;
    }
    if (step.patchId.equals(resurrectCastOn) && empty) resurrectCastOn = null;
    boolean done;
    switch (step.phase) {
      case "Harvest":
        // Dead herbs must be cleared first; that is shown as "Clear the dead herbs".
        done = !pick && !clearDead;
        break;
      case "Check health":
        done = !check;
        break;
      case "Clear":
        done = empty;
        break;
      case "Plant":
        // A crop is now growing: not empty, nothing to pick, check or clear.
        done = !empty && !pick && !check && !clearDead;
        break;
      default:
        done = false;
    }
    // Only trust a reading that holds for two ticks: just after a teleport the scene is still
    // loading and the wrong object, or a half-loaded one, can briefly be found.
    String reading = step.patchId + step.phase + patch.getWorldLocation() + c.getId() + done;
    readingTicks = reading.equals(lastReading) ? readingTicks + 1 : 0;
    lastReading = reading;
    if (done && readingTicks >= 1) phaseDone(step);
  }

  private String lastReading;
  private int readingTicks;
  /** The guided herb patch has died and needs clearing before anything else. */
  private volatile boolean deadPatch;

  // ---- Switching spellbook before a run (magic cape or the house altar) ----

  static final int CAPE_SWAPS_PER_DAY = 5;
  private static final Set<Integer> MAGIC_CAPES = itemIds(Collections.singletonList("Magic cape"));
  /** Tick the magic cape's Spellbook option was clicked, or -1. */
  private int capeSwapClickedAt = -1;
  /** Spare variants kept out of the bank filter, e.g. a basic whistle when you own a better one. */
  private volatile Set<Integer> hiddenVariants = Collections.emptySet();
  /** Everything owned (bank, inventory, worn), from the last account scan. */
  private volatile Set<Integer> ownedItems = Collections.emptySet();
  /** Show the magic cape in the bank filter, to withdraw for a spellbook switch. */
  private volatile boolean capeNeeded;
  /** "cape" or "altar" when that's what to click to switch, else null. */
  private volatile String switchMethod;
  /** One line for the overlay saying how to switch, or null. */
  private volatile String switchHint;
  /** The house altar to click, when that's the way to switch. */
  private volatile List<TileObject> switchObjects = Collections.emptyList();

  /** The magic cape's menu title, "Choose spellbook: (4/5 left)", says how many changes are left. */
  private void readSpellbookDialog() {
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (options == null || options.isHidden() || options.getDynamicChildren() == null) return;
    for (Widget line : options.getDynamicChildren()) {
      String text = line == null || line.getText() == null ? "" : Text.removeTags(line.getText());
      java.util.regex.Matcher left =
          java.util.regex.Pattern.compile("Choose spellbook: \\((\\d+)/(\\d+) left\\)").matcher(text);
      if (left.find()) {
        int used = Integer.parseInt(left.group(2)) - Integer.parseInt(left.group(1));
        if (used != capeSwapsUsed()) setCapeSwapsUsed(used);
        return;
      }
    }
  }

  /** The magic cape's spellbook changes used today. The game's day starts at 00:00 UTC. */
  int capeSwapsUsed() {
    String saved = configManager.getRSProfileConfiguration(FarmingPanel.GROUP, "capeSwaps");
    String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
    if (saved == null || !saved.startsWith(today + ":")) return 0;
    try {
      return Integer.parseInt(saved.substring(today.length() + 1));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private void setCapeSwapsUsed(int used) {
    String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
    int clamped = Math.max(0, Math.min(CAPE_SWAPS_PER_DAY, used));
    configManager.setRSProfileConfiguration(FarmingPanel.GROUP, "capeSwaps", today + ":" + clamped);
  }

  /** The spellbook the run needs and you're not on: 0 standard, 3 Arceuus, or -1 if fine. */
  int spellbookWanted() {
    List<String> list = missing;
    if (list.contains(FarmingPanel.SWITCH_ARCEUUS)) return 3;
    if (list.contains(FarmingPanel.SWITCH_STANDARD)) return 0;
    return -1;
  }

  /** "arceuus" or "standard", for matching menu and dialogue options. */
  String spellbookWord() {
    int wanted = spellbookWanted();
    return wanted == 3 ? "arceuus" : wanted == 0 ? "standard" : null;
  }

  String switchMethod() {
    return switchMethod;
  }

  String switchHint() {
    return switchHint;
  }

  List<TileObject> switchObjects() {
    return switchObjects;
  }

  /**
   * Works out how to switch spellbook: the magic cape if you're carrying it and have changes left
   * today, the altar when you're in your house, otherwise what to fetch first.
   */
  private void refreshSpellbookSwitch() {
    boolean before = capeNeeded;
    computeSpellbookSwitch();
    // The cape comes and goes from the bank filter: refresh it if it's showing.
    if (before != capeNeeded && kitFilterOpen()) refreshKitFilter();
  }

  /** Re-applies the kit filter so the bank shows exactly what's needed now. */
  private void refreshKitFilter() {
    layoutManager.saveLayout(new Layout(BANK_TAG, kitLayout()));
    bankTags.closeBankTag();
    bankTags.openBankTag(BANK_TAG, 0);
    styleBankButton();
  }

  private void computeSpellbookSwitch() {
    int wanted = spellbookWanted();
    Player player = client.getLocalPlayer();
    if (wanted < 0 || player == null || client.getVarbitValue(VarbitID.SPELLBOOK) == wanted) {
      switchMethod = null;
      switchHint = null;
      switchObjects = Collections.emptyList();
      capeNeeded = false;
      // Switched with the magic cape: the bank is next, to put the cape back.
      Widget bankOpen = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
      if (player != null
          && missing.contains(FarmingPanel.DEPOSIT_CAPE)
          && (bankOpen == null || bankOpen.isHidden())) {
        TileObject booth = nearestObject(player, 25, "bank booth", "bank chest", "bank deposit box");
        if (booth != null) switchObjects = Collections.singletonList(booth);
      }
      return;
    }
    String book = wanted == 3 ? "Arceuus" : "standard";
    int left = CAPE_SWAPS_PER_DAY - capeSwapsUsed();
    boolean carried = false;
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer items = client.getItemContainer(container);
      if (items != null)
        for (Item item : items.getItems()) carried |= MAGIC_CAPES.contains(item.getId());
    }
    boolean owned = false;
    for (int id : MAGIC_CAPES) owned |= ownedItems.contains(id);
    TileObject altar =
        client.getTopLevelWorldView().isInstance()
            ? nearestObject(player, 40, "altar of the occult", "occult altar", "dark altar")
            : null;
    readSpellbookDialog();
    capeNeeded = !carried && owned && left > 0;
    if (carried && left > 0) {
      switchMethod = "cape";
      switchObjects = Collections.emptyList();
      switchHint = "Magic cape → Spellbook → " + book + " (" + left + " left today)";
    } else if (altar != null) {
      switchMethod = "altar";
      switchObjects = Collections.singletonList(altar);
      switchHint = "Use the altar to switch to " + book;
    } else {
      switchMethod = null;
      switchObjects = Collections.emptyList();
      switchHint =
          capeNeeded
              ? "Withdraw your magic cape to switch to " + book + " (" + left + " left today)"
              : "Teleport to your house and use the altar to switch to " + book;
    }
  }

  /** The patch Resurrect Crops was last cast on, until its result shows. */
  private String resurrectCastOn;

  /** Patches Resurrect Crops has been tried on this run (a crop can only be revived once). */
  private final Set<String> resurrectTried = new HashSet<>();

  /** Cast Resurrect Crops now: on, dead herbs at the guided patch, not tried here yet. */
  boolean resurrectNow() {
    Guidance step = guidance;
    return step != null
        && step.resurrect
        && step.kind.equals("herb")
        && step.phase.equals("Harvest")
        && deadPatch
        && !resurrectTried.contains(step.patchId);
  }

  boolean deadPatch() {
    return deadPatch;
  }

  private boolean holdingUnnoted(Set<Integer> ids) {
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    if (inventory == null) return false;
    for (Item item : inventory.getItems()) if (ids.contains(item.getId())) return true;
    return false;
  }

  private static final List<String> SECOND_LEGS =
      Arrays.asList(
          "follow", "boat", "charter", "rowboat", "barge", "mushtree", "quetzal to", "spirit tree",
          "fairy ring");

  /** Travel that needs another transport after the first teleport, e.g. "…; follow Mountain Guide". */
  static boolean multiLeg(String travel) {
    int arrow = travel.indexOf('→');
    String after = (arrow < 0 ? travel : travel.substring(arrow + 1)).toLowerCase(Locale.ROOT);
    for (String leg : SECOND_LEGS) if (after.contains(leg)) return true;
    return false;
  }

  /** In the patch's herb region, or its gardener is anywhere in the loaded scene. */
  private boolean arrived(String patchId, Player player) {
    // Known patch position: within 10 tiles, so a nearby bank (e.g. Falador east) isn't "there".
    // Tree Gnome Village allows 20: through the maze counts, not just its spirit tree.
    WorldPoint at = TargetData.PATCH_POINTS.get(patchId);
    int radius = patchId.equals("village") ? 20 : 10;
    if (at != null && !patchId.startsWith("herb"))
      return at.getPlane() == player.getWorldLocation().getPlane()
          && at.distanceTo(player.getWorldLocation()) <= radius;
    if (TargetData.HERB_REGIONS
        .getOrDefault(patchId, Collections.emptySet())
        .contains(player.getWorldLocation().getRegionID())) return true;
    int gardener = TargetData.GARDENERS.getOrDefault(patchId, -1);
    for (NPC n : sceneNpcs) if (n.getId() == gardener) return true;
    return false;
  }

  /**
   * Before a run that needs another spellbook: the magic cape's Spellbook, the altar's option for
   * that book, or (when the house is the way) the Construction cape's or tablet's Tele to POH.
   */
  private void swapForSpellbookSwitch() {
    String word = spellbookWord();
    if (word == null) return;
    MenuEntry[] entries = client.getMenuEntries();
    String[][] wanted = {
      {"magic cape", "spellbook"},
      {"max cape", "spellbook"},
      {"altar of the occult", word}, // options: Venerate, Standard, Ancient, Lunar (+ Arceuus)
      {"occult altar", word},
      {"dark altar", word},
      {"dark altar", "venerate"},
      {"construct", "tele to poh"}, // the game names it "Construct. cape(t)"
      {"teleport to house", "break"}
    };
    for (String[] want : wanted) {
      if (want[0].equals("construct") || want[0].equals("teleport to house")) {
        if (!"house".equals(switchRoute())) continue;
      }
      for (MenuEntry entry : entries) {
        String option = Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT);
        String target = Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT);
        if (target.contains(want[0]) && option.equals(want[1])) {
          moveToLeftClick(entries, entry);
          return;
        }
      }
    }
  }

  /** How an item's name starts in game menus, lower case (some are abbreviated there). */
  private static String menuName(String itemName) {
    String name = itemName.toLowerCase(Locale.ROOT);
    return name.equals("construction cape") ? "construct. cape" : name;
  }

  /**
   * With the fairy ring's dials open, scrolls the travel log so this route's code is at the top
   * and colours it green, the way the Shortest Path plugin does (ShortestPathPlugin
   * .scrollFairyRingPanel, BSD-2-Clause).
   */
  /**
   * In a right-click submenu, e.g. the construction cape's Teleport list, the route's destination
   * and the option that opens it are drawn in the highlight colour.
   */
  private void colourMenuDestination(Guidance step) {
    if (!client.isMenuOpen()) return;
    String word = FarmingInventoryOverlay.destination(step.travel).split(" ")[0];
    if (word.length() < 4) return;
    String colour =
        String.format("<col=%06x>", FarmingInventoryOverlay.TARGET.getRGB() & 0xFFFFFF);
    for (MenuEntry entry : client.getMenu().getMenuEntries()) {
      net.runelite.api.Menu sub = entry.getSubMenu();
      if (sub == null) continue;
      for (MenuEntry choice : sub.getMenuEntries()) {
        String option = choice.getOption();
        if (option == null || option.startsWith("<col=")) continue;
        if (!option.toLowerCase(Locale.ROOT).contains(word)) continue;
        choice.setOption(colour + option + "</col>");
        if (!entry.getOption().startsWith("<col="))
          entry.setOption(colour + entry.getOption() + "</col>");
      }
    }
  }

  @Subscribe
  public void onPostClientTick(PostClientTick e) {
    Guidance step = guidance;
    if (step == null || !step.phase.equals("Travel")) return;
    colourMenuDestination(step);
    String code = FarmingPlanner.fairyCode(step.travel);
    Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
    if (code == null || contents == null || contents.isHidden()) return;
    Widget entry = null;
    Widget faves = client.getWidget(InterfaceID.FairyringsLog.FAVES);
    if (faves != null && faves.getStaticChildren() != null)
      for (Widget w : faves.getStaticChildren())
        if (w != null && code.equals(Text.removeTags(String.valueOf(w.getText())))) entry = w;
    if (entry == null && contents.getDynamicChildren() != null)
      for (Widget w : contents.getDynamicChildren())
        if (w != null && code.equals(Text.removeTags(String.valueOf(w.getText())))) entry = w;
    if (entry == null) return;
    entry.setTextColor(0x00FF00);
    int scrollY = Math.min(entry.getRelativeY(), contents.getScrollHeight() - contents.getHeight());
    if (scrollY < 0 || contents.getScrollY() == scrollY) return;
    contents.setScrollY(scrollY);
    contents.revalidateScroll();
    client.runScript(
        ScriptID.UPDATE_SCROLLBAR,
        InterfaceID.FairyringsLog.SCROLLBAR,
        InterfaceID.FairyringsLog.CONTENTS,
        scrollY);
  }

  /** Item options that aren't a teleport. */
  private static final Set<String> TELEPORT_NOT =
      new HashSet<>(
          Arrays.asList(
              "wear", "wield", "remove", "examine", "drop", "use", "check", "check-charges",
              "uncharge", "configure", "open", "empty", "fill", "destroy", "cancel", "walk here"));

  /** Tick a teleport was clicked on this step, and which patch's step it was. */
  private int teleportClickedAt = -100;

  private String teleportClickedPatch;

  /** A teleport for the current Travel step was clicked in the last few ticks. */
  boolean teleportClicked() {
    Guidance step = guidance;
    return step != null
        && step.patchId.equals(teleportClickedPatch)
        && client.getTickCount() - teleportClickedAt <= 8;
  }

  /** Whether you've just used a fairy ring, spirit tree, portal or box. */
  private boolean usedTransport;

  /** "cape", "altar", "house" (go home first) or "bank" (withdraw the cape), or null. */
  String switchRoute() {
    if (switchHint == null) return null;
    if (switchMethod != null) return switchMethod;
    return capeNeeded ? "bank" : "house";
  }

  private void phaseDone(Guidance step) {
    event("step done: " + step.phase);
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) panel.phaseDone(step.patchId, step.phase);
        });
  }

  /**
   * While travelling, makes the needed option the left-click one: the cloak or ring teleport, the
   * fairy ring's Last-destination (when it's already this code) or Configure, and the spirit
   * tree's Travel (Tree on a spiritual fairy tree). Option names are from RuneLite's Menu Entry
   * Swapper.
   */
  // Runs after RuneLite's Menu Entry Swapper (higher priority runs first), whose own Ardougne
  // cloak and construction cape swaps otherwise overrode these.
  @Subscribe(priority = -10)
  public void onPostMenuSort(PostMenuSort e) {
    // The rune pouch doesn't hold what the run needs: Configure is the left-click.
    if (guidance == null && !client.isMenuOpen() && !pouchShortfall().isEmpty()) {
      MenuEntry[] entries = client.getMenuEntries();
      for (MenuEntry entry : entries) {
        String option = Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT);
        String target = Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT);
        if (target.contains("rune pouch") && option.equals("configure")) {
          moveToLeftClick(entries, entry);
          return;
        }
      }
    }
    if (guidance == null && switchHint != null && !client.isMenuOpen()) {
      swapForSpellbookSwitch();
      return;
    }
    Guidance step = guidance;
    if (step == null || !step.phase.equals("Travel") || client.isMenuOpen()) return;
    String travel = step.travel.toLowerCase(Locale.ROOT);
    String code = FarmingPlanner.fairyCode(step.travel);
    List<String[]> wanted = new ArrayList<>(); // {target contains, option contains}, best first
    if (step.travel.startsWith("Ardougne cloak"))
      wanted.add(new String[] {"ardougne cloak", "farm"});
    if (step.travel.startsWith("Explorer's ring"))
      wanted.add(new String[] {"explorer's ring", "teleport"});
    // The Construction cape's own portals (Taverley, Brimhaven) are under "Teleport"; its
    // "Tele to POH" is only for trips through your house.
    // (In menus the game calls it "Construct. cape(t)", so match on "construct".)
    if (step.travel.startsWith("Construction cape"))
      wanted.add(new String[] {"construct", "teleport"});
    // Eternal or charged teleport crystal: its "Lletya" option.
    if (travel.contains("lletya")) wanted.add(new String[] {"teleport crystal", "lletya"});
    // Standard spells: "Cast", not an alternative like Varrock's Grand Exchange.
    String spell = step.spell;
    if (spell != null && !spell.equals("House"))
      wanted.add(new String[] {spell.toLowerCase(Locale.ROOT) + " teleport", "cast"});
    if (code != null) {
      wanted.add(new String[] {"fairy", "last-destination (" + code.toLowerCase(Locale.ROOT)});
      wanted.add(new String[] {"fairy", "configure"});
    }
    if (travel.contains("spirit tree")) {
      wanted.add(new String[] {"spiritual fairy tree", "tree"});
      wanted.add(new String[] {"spirit tree", "travel"});
    }
    MenuEntry[] entries = client.getMenuEntries();
    // Any other teleport item on this step (talisman, capes, jewellery…): prefer an option naming
    // the destination, e.g. "Falador Park" or "Xeric's Glade", then Teleport / Tele to POH, then Rub.
    int arrow = step.travel.indexOf('→');
    String place =
        arrow < 0
            ? ""
            : travel.substring(arrow + 1).split("[,;]| then ")[0].replace("→", "").trim();
    MenuEntry best = null;
    int bestRank = 9;
    for (MenuEntry entry : entries) {
      String option = Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT);
      String target = Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT);
      boolean ours = false;
      for (String item : step.items)
        ours |= target.startsWith(menuName(FarmingPlanner.normalize(item)));
      // Objects and NPCs on the way: house spirit tree, portal nexus, jewellery box, the Tree
      // Gnome Village railing and Elkoy.
      for (String thing :
          new String[] {
            "spirit tree", "portal nexus", "jewellery box", "railing", "elkoy", "mountain guide"
          })
        ours |= target.contains(thing) && travel.contains(thing.split(" ")[0]);
      ours |= target.contains("portal") && travel.contains("portal");
      if (!ours) continue;
      String destination = FarmingInventoryOverlay.destination(step.travel);
      // House routes (house fairy ring, spirit tree, jewellery box, nexus) want "Tele to POH",
      // not the cape's portal list.
      boolean toHouse =
          travel.startsWith("house ") || travel.startsWith("poh ") || travel.contains("your house");
      int rank =
          toHouse && option.equals("tele to poh")
              ? 0
              : !place.isEmpty() && option.contains(place)
                  || destination.length() > 3 && option.contains(destination)
                  // Worn jewellery shortens it: the worn ring's "Falador" for Falador Park.
                  || option.length() > 3 && !place.isEmpty() && place.startsWith(option)
              ? 0
              : Arrays.asList(
                          "teleport",
                          "farm teleport",
                          "tele to poh",
                          "travel",
                          "teleport menu",
                          "squeeze-through",
                          "follow")
                      .contains(option)
                  ? 1
                  : option.equals("rub") ? 2 : 9;
      if (rank < bestRank) {
        best = entry;
        bestRank = rank;
      }
    }
    // Exact swaps first (worn items use the destination as the option, e.g. "Ardougne Farm").
    for (String[] want : wanted)
      for (int i = 0; i < entries.length; i++) {
        MenuEntry entry = entries[i];
        String option = Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT);
        String target = Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT);
        boolean exact = want[1].equals("teleport") || want[1].equals("tree");
        if (!target.contains(want[0])
            || !(exact ? option.equals(want[1]) : option.contains(want[1]))) continue;
        moveToLeftClick(entries, entry);
        return;
      }
    if (best != null) moveToLeftClick(entries, best);
  }

  /** The last menu entry is the left-click option. */
  private void moveToLeftClick(MenuEntry[] entries, MenuEntry entry) {
    List<MenuEntry> list = new ArrayList<>(Arrays.asList(entries));
    list.remove(entry);
    list.add(entry);
    // Item ops 4 and 5 (e.g. the cloak's Farm Teleport, the cape's Tele to POH) are
    // CC_OP_LOW_PRIORITY, which the game never uses as the left-click: it opens the menu or picks
    // the next option instead. Menu Entry Swapper raises them to CC_OP for the same reason.
    if (entry.getType() == MenuAction.CC_OP_LOW_PRIORITY) entry.setType(MenuAction.CC_OP);
    // A deprioritised entry on top also makes a left-click open the menu.
    entry.setDeprioritized(false);
    event("left-click: " + Text.removeTags(entry.getOption()) + " " + Text.removeTags(entry.getTarget()));
    client.setMenuEntries(list.toArray(new MenuEntry[0]));
  }

  /** Level-up style fireworks on your character when a run is finished. Only you see them. */
  private void celebrate() {
    idleShownUntil = System.currentTimeMillis() + 15_000;
    clientThread.invoke(
        () -> {
          Player player = client.getLocalPlayer();
          if (player != null) player.createSpotAnim(0x0F4A11, SpotanimID.LEVELUP_99_ANIM, 0, 0);
        });
  }

  /** The fairy ring code to dial on this Travel step, or null. */
  String fairyCode() {
    Guidance step = guidance;
    return step == null || !step.phase.equals("Travel")
        ? null
        : FarmingPlanner.fairyCode(step.travel);
  }

  @Subscribe
  public void onScriptPostFired(ScriptPostFired e) {
    if (e.getScriptId() == ScriptID.BANKMAIN_BUILD) updateBankButton();
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded e) {
    if (e.getGroupId() != InterfaceID.BANKMAIN) return;
    // Work out which spare variants to hide (e.g. a second quetzal whistle) before the bank shows
    // the filter, including when it was left open from last time.
    kitLayout();
    clientThread.invokeLater(
        () -> {
          updateBankButton();
          // Left open from last time: rebuild it from the current kit, as the run may have changed.
          if (preparingRun() && kitFilterOpen()) refreshKitFilter();
        });
  }

  /** "Prepare run" pressed and the kit isn't all withdrawn yet: the only time the bank needs us. */
  private boolean preparingRun() {
    return !missing.isEmpty();
  }

  /**
   * The bank button is only there while preparing a run; otherwise it's removed, along with the
   * kit filter if it was left showing, so the bank is untouched while you do other things.
   */
  private void updateBankButton() {
    Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (universe == null) return;
    if (preparingRun()) {
      addBankButton();
      if (bankButton != null) bankButton.setHidden(false);
      if (bankIcon != null) bankIcon.setHidden(false);
      styleBankButton();
      return;
    }
    if (kitFilterOpen()) bankTags.closeBankTag();
    if (bankButton != null) bankButton.setHidden(true);
    if (bankIcon != null) bankIcon.setHidden(true);
    universe.revalidate();
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed e) {
    if (e.getGroupId() == InterfaceID.BANKMAIN) bankButton = bankIcon = null;
  }

  /** A sapling button in the bank's top-left corner that shows every item the run needs. */
  private void addBankButton() {
    Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (universe == null) return;
    Widget[] children = universe.getDynamicChildren();
    if (bankButton != null && children != null && Arrays.asList(children).contains(bankButton))
      return;
    // Quest Helper's exact button: 25px square at (408, 5) with a 19px icon inset by 3.
    bankButton = universe.createChild(-1, WidgetType.GRAPHIC);
    bankIcon = universe.createChild(-1, WidgetType.GRAPHIC);
    for (Widget w : new Widget[] {bankButton, bankIcon}) {
      int inset = w == bankIcon ? 3 : 0;
      w.setOriginalX(408 + inset);
      w.setOriginalY(5 + inset);
      w.setOriginalWidth(25 - 2 * inset);
      w.setOriginalHeight(25 - 2 * inset);
    }
    bankIcon.setSpriteId(SpriteID.SKILL_FARMING);
    bankButton.setAction(0, "View Ultimate Farming items");
    bankButton.setName("<col=ff9040>Ultimate Farming</col>");
    bankButton.setHasListener(true);
    bankButton.setNoClickThrough(true);
    bankButton.setOnMouseOverListener(
        (JavaScriptCallback) ev -> bankButton.setSpriteId(SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED));
    bankButton.setOnMouseLeaveListener((JavaScriptCallback) ev -> styleBankButton());
    bankButton.setOnOpListener(
        (JavaScriptCallback)
            ev -> {
              if (BANK_TAG.equals(bankTags.getActiveTag())) bankTags.closeBankTag();
              else {
                // A fixed layout keeps every slot: a withdrawn item leaves a placeholder instead
                // of the rest shifting left.
                layoutManager.saveLayout(new Layout(BANK_TAG, kitLayout()));
                bankTags.openBankTag(BANK_TAG, 0);
              }
              styleBankButton();
            });
    styleBankButton();
    bankButton.revalidate();
    bankIcon.revalidate();
  }

  /** The button stays pressed while the farming items are shown. */
  private void styleBankButton() {
    if (bankButton == null) return;
    bankButton.setSpriteId(
        BANK_TAG.equals(bankTags.getActiveTag())
            ? SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
            : SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL);
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged e) {
    int container = e.getContainerId();
    if (container == InventoryID.BANK) {
      bank = new LinkedHashSet<>();
      for (Item item : e.getItemContainer().getItems())
        if (item.getId() > 0 && item.getQuantity() > 0)
          bank.add(itemManager.canonicalize(item.getId()));
      StringBuilder b = new StringBuilder();
      for (int id : bank) b.append(b.length() == 0 ? "" : ",").append(id);
      configManager.setRSProfileConfiguration(FarmingPanel.GROUP, "bank", b.toString());
    }
    if (container == InventoryID.BANK || container == InventoryID.INV || container == InventoryID.WORN)
      scanDue = true;
    if (container == InventoryID.INV) {
      int count = 0;
      for (Item item : e.getItemContainer().getItems())
        if (GRIMY.contains(item.getId())) count += item.getQuantity();
      if (grimyCount >= 0 && count > grimyCount) {
        event("herb picked: " + grimyCount + " -> " + count);
        herbPicked(count - grimyCount);
      }
      grimyCount = count;
      // Celastrus bark gained while guided at the Celastrus patch counts towards real profit.
      int bark = 0;
      for (Item item : e.getItemContainer().getItems())
        if (item.getId() == net.runelite.api.gameval.ItemID.CELASTRUS_WOOD) bark += item.getQuantity();
      Guidance step = guidance;
      if (barkCount >= 0 && bark > barkCount && step != null && step.kind.equals("celastrus")) {
        int gained = bark - barkCount;
        event("bark chopped: +" + gained);
        SwingUtilities.invokeLater(
            () -> {
              if (active && panel != null) panel.barkChopped(gained);
            });
      }
      barkCount = bark;
    }
  }

  /** Picking a herb at a route patch starts a herb run's timer. */
  private void herbPicked(int picked) {
    Player player = client.getLocalPlayer();
    if (player == null) return;
    Set<String> nearby = new HashSet<>();
    int region = player.getWorldLocation().getRegionID();
    for (Map.Entry<String, Set<Integer>> e : TargetData.HERB_REGIONS.entrySet())
      if (e.getValue().contains(region)) nearby.add(e.getKey());
    if (nearby.isEmpty()) return;
    // Picking has started here: from now on, noting is only suggested if the inventory fills.
    Guidance step = guidance;
    if (step != null && nearby.contains(step.patchId)) pickingPatch = step.patchId;
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) panel.herbPicked(nearby, picked);
        });
  }

  /** Reads quests, diaries and owned items, then tells the sidebar which unlocks are known. */
  private void scanAccount() {
    scanDue = false;
    if (!active || client.getGameState() != GameState.LOGGED_IN) return;
    if (bank == null) {
      String saved = configManager.getRSProfileConfiguration(FarmingPanel.GROUP, "bank");
      if (saved != null) {
        bank = new LinkedHashSet<>();
        for (String id : saved.split(","))
          if (!id.isEmpty()) bank.add(Integer.parseInt(id));
      }
    }
    Set<Integer> owned = new HashSet<>();
    Map<Integer, Integer> carried = new HashMap<>();
    if (bank != null) owned.addAll(bank);
    for (int container : new int[] {InventoryID.INV, InventoryID.WORN}) {
      ItemContainer items = client.getItemContainer(container);
      if (items == null) continue;
      for (Item item : items.getItems())
        if (item.getId() > 0) {
          // Notes count as the item itself, e.g. noted protection payments.
          int id = itemManager.canonicalize(item.getId());
          owned.add(id);
          carried.merge(id, item.getQuantity(), Integer::sum);
        }
    }
    bankUnknown = bank == null;
    ownedItems = owned;
    // Skill levels for Agility shortcuts on walking routes.
    Map<String, Integer> levels = new HashMap<>();
    for (Skill skill : Skill.values()) levels.put(skill.getName(), client.getRealSkillLevel(skill));
    WorldPath.setLevels(levels);
    AccountScan.Result result = AccountScan.scan(client, owned, bank != null);
    result.carried = carried;
    result.pouch = pouchRunes();
    result.spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) panel.updateAccount(result);
        });
  }

  private static final int[][] POUCH_SLOTS = {
    {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
    {VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
    {VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
    {VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
    {VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5},
    {VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6}
  };

  /** Rune item IDs and amounts inside the rune pouch, read from its varbits. */
  Map<Integer, Integer> pouchRunes() {
    Map<Integer, Integer> runes = new LinkedHashMap<>();
    EnumComposition types = client.getEnum(EnumID.RUNEPOUCH_RUNE);
    for (int[] slot : POUCH_SLOTS) {
      int type = client.getVarbitValue(slot[0]), amount = client.getVarbitValue(slot[1]);
      if (type <= 0 || amount <= 0 || types == null) continue;
      int id = types.getIntValue(type);
      if (id > 0) runes.merge(id, amount, Integer::sum);
    }
    return runes;
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged e) {
    if (e.getVarbitId() == VarbitID.SPELLBOOK) {
      scanDue = true;
      if (capeSwapClickedAt >= 0 && client.getTickCount() - capeSwapClickedAt < 100) {
        capeSwapClickedAt = -1;
        setCapeSwapsUsed(capeSwapsUsed() + 1);
        event("magic cape swaps used today: " + capeSwapsUsed());
      }
    }
    for (int[] slot : POUCH_SLOTS)
      if (e.getVarbitId() == slot[0] || e.getVarbitId() == slot[1]) scanDue = true;
  }

  @Subscribe
  public void onChatMessage(ChatMessage e) {
    if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM) return;
    String m = Text.removeTags(e.getMessage()).toLowerCase(Locale.ROOT);
    if (m.contains("spellbook")) {
      // The game says "You have changed your spellbook 1/5 times today." after each change.
      event("msg: " + m);
      java.util.regex.Matcher used =
          java.util.regex.Pattern.compile("changed your spellbook (\\d+)/\\d+ times today").matcher(m);
      if (used.find()) {
        setCapeSwapsUsed(Integer.parseInt(used.group(1)));
        capeSwapClickedAt = -1; // counted from the message, not the varbit change
      }
    }
    if (m.contains("already resurrected")) {
      Guidance step = guidance;
      if (step != null) resurrectTried.add(step.patchId);
    }
    if (!m.contains("patch")) return;
    event("msg: " + m);
    if (m.startsWith("you plant ")) patchAction("Plant", patchKind(m));
    // Only "You treat the … patch": inspecting says "has been treated", which isn't you composting.
    else if (m.startsWith("you treat") && m.contains("compost")) patchAction("Compost", patchKind(m));
  }

  /** Gardeners confirm protection in dialogue rather than in chat. */
  private void watchGardenerDialog() {
    Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
    String value = text == null || text.isHidden() ? null : text.getText();
    if (Objects.equals(value, lastDialog)) return;
    lastDialog = value;
    if (value == null) return;
    String m = Text.removeTags(value).toLowerCase(Locale.ROOT);
    if (m.contains("leave it with me") || m.contains("already looking after")) {
      event("gardener: " + m);
      patchAction("Protect", null);
    }
  }

  private static String patchKind(String message) {
    for (String kind : new String[] {"herb", "calquat", "celastrus", "redwood", "hardwood"})
      if (message.contains(kind)) return kind;
    if (message.contains("fruit tree")) return "fruit";
    if (message.contains("tree patch")) return "tree";
    return null;
  }

  /** Reports an observed planting, compost or payment with the patches the player is beside. */
  private void patchAction(String action, String kind) {
    Player player = client.getLocalPlayer();
    if (player == null) return;
    Set<String> nearby = new HashSet<>();
    int region = player.getWorldLocation().getRegionID();
    for (Map.Entry<String, Set<Integer>> e : TargetData.HERB_REGIONS.entrySet())
      if (e.getValue().contains(region)) nearby.add(e.getKey());
    for (Map.Entry<String, Integer> e : TargetData.GARDENERS.entrySet())
      for (NPC n : sceneNpcs)
        if (n.getId() == e.getValue()
            && n.getWorldLocation().distanceTo(player.getWorldLocation()) <= 20)
          nearby.add(e.getKey());
    SwingUtilities.invokeLater(
        () -> {
          if (active && panel != null) panel.patchAction(action, kind, nearby);
        });
  }

  private void refreshHighlights() {
    patchObject = null;
    // deadPatch keeps the last reading until checkPatch reads the patch again, so the targets
    // chosen below already know a dead crop is there.
    objectHighlights = Collections.emptyList();
    npcHighlights = Collections.emptyList();
    nextObjects = Collections.emptyList();
    nextNpcs = Collections.emptyList();
    Guidance step = guidance;
    Player player = client.getLocalPlayer();
    if (step == null || player == null) return;
    int gardenerId = TargetData.GARDENERS.getOrDefault(step.patchId, -1);
    NPC gardener = null;
    for (NPC n : sceneNpcs)
      if (n.getId() == gardenerId
          && n.getWorldLocation().distanceTo(player.getWorldLocation()) <= 32) {
        gardener = n;
        break;
      }
    // Find the patch every tick, so step completion can be read whatever is highlighted.
    TileObject patch = null;
    if (step.kind.equals("herb")) {
      // The patch is the farming object (one that changes with growth) at the patch's position.
      WorldPoint at = TargetData.HERB_POINTS.get(step.patchId);
      int closest = 2;
      if (at != null)
        for (TileObject o : sceneObjects) {
          if (client.getObjectDefinition(o.getId()).getImpostorIds() == null) continue;
          int d = o.getWorldLocation().distanceTo(at);
          if (d < closest) {
            patch = o;
            closest = d;
          }
        }
    }
    // Trees with a known position: the farming object there, found as soon as it's loaded and
    // whether or not there's a gardener (Summer Shore's Calquat has none).
    WorldPoint treeAt = step.kind.equals("herb") ? null : TargetData.PATCH_POINTS.get(step.patchId);
    if (treeAt != null) {
      int closest = 4;
      for (TileObject o : sceneObjects) {
        if (o.getPlane() != treeAt.getPlane()) continue;
        int d = o.getWorldLocation().distanceTo(treeAt);
        if (d >= closest) continue;
        ObjectComposition c = client.getObjectDefinition(o.getId());
        if (c == null || c.getImpostorIds() == null) continue;
        c = c.getImpostor();
        if (c == null || !TargetData.patchLike(c, step.kind)) continue;
        patch = o;
        closest = d;
      }
    }
    if (patch == null && !step.kind.equals("herb") && gardener != null) {
      int closest = 15;
      for (TileObject o : sceneObjects) {
        int d = o.getWorldLocation().distanceTo(gardener.getWorldLocation());
        if (d > closest) continue;
        ObjectComposition c = client.getObjectDefinition(o.getId());
        if (c.getImpostorIds() != null) c = c.getImpostor();
        if (c == null || !TargetData.patchLike(c, step.kind)) continue;
        if (d < closest) {
          patch = o;
          closest = d;
        }
      }
    }
    patchObject = patch;
    if (patch == null) deadPatch = false;
    patchChoppable = offers(patch, "chop") || offers(patch, "chop down");
    debugGardener = gardener;
    needsRake = false;
    if (patch != null) {
      ObjectComposition c = client.getObjectDefinition(patch.getId());
      if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
      if (c != null)
        for (String action : c.getActions()) needsRake |= "rake".equalsIgnoreCase(action);
    }
    // The leprechaun nearest the patch, or failing that the gardener or the player.
    WorldPoint anchor =
        patch != null
            ? patch.getWorldLocation()
            : gardener != null ? gardener.getWorldLocation() : player.getWorldLocation();
    NPC leprechaun = null;
    if (anchor != null) {
      int closest = 33;
      for (NPC n : sceneNpcs)
        if (n.getName() != null && n.getName().toLowerCase(Locale.ROOT).contains("leprechaun")) {
          int d = n.getWorldLocation().distanceTo(anchor);
          if (d < closest) {
            leprechaun = n;
            closest = d;
          }
        }
    }
    Object current =
        step.phase.equals("Travel")
            ? transportObject(step, player)
            : target(step, step.phase, gardener, patch, leprechaun);
    Object next =
        step.nextPhase == null ? null : target(step, step.nextPhase, gardener, patch, leprechaun);
    if (current instanceof TileObject)
      objectHighlights = Collections.singletonList((TileObject) current);
    if (current instanceof NPC) npcHighlights = Collections.singletonList((NPC) current);
    // The next step's target is only shown in orange when it isn't already the current one.
    if (next != current && next instanceof TileObject)
      nextObjects = Collections.singletonList((TileObject) next);
    if (next != current && next instanceof NPC) nextNpcs = Collections.singletonList((NPC) next);
    noteAdvice = noteAdvice(step, player, patch);
    // When noting is advised, the leprechaun is what to click.
    if (noteAdvice != null && leprechaun != null) {
      // Noting comes first: just the leprechaun (herbs, then him), nothing else lit up.
      npcHighlights = Collections.singletonList(leprechaun);
      objectHighlights = Collections.emptyList();
      nextObjects = Collections.emptyList();
      nextNpcs = Collections.emptyList();
    }
    path = walkingPath(step, player);
  }

  private volatile String idleMessage;

  /** Between runs: when the plants will be ready, or that they are. */
  String idleMessage() {
    // Shown briefly after a run, then the overlay gets out of the way; the sidebar keeps it.
    return System.currentTimeMillis() < idleShownUntil ? idleMessage : null;
  }

  /** When the "plants still be growin'" line leaves the overlay. */
  private volatile long idleShownUntil;

  /** Tiles to walk from you to whatever you click next, or the patch itself. */
  private volatile List<WorldPoint> path = Collections.emptyList();

  List<WorldPoint> path() {
    return path;
  }

  private List<WorldPoint> walkingPath(Guidance step, Player player) {
    WorldPoint from = player.getWorldLocation();
    // In the house (an instance) Shortest Path has no map, so our own route over the loaded
    // rooms leads to the portal, nexus, jewellery box, fairy ring or spirit tree.
    if (client.getTopLevelWorldView().isInstance()) {
      shortestPath(null);
      if (objectHighlights.isEmpty()) return Collections.emptyList();
      WorldPoint target = objectHighlights.get(0).getWorldLocation();
      if (target.distanceTo(from) <= 1) return Collections.emptyList();
      return TilePath.find(client.getTopLevelWorldView(), from, target);
    }
    // Noting herbs: the leprechaun is the destination, not the patch.
    boolean noting = noteAdvice != null || step.phase.startsWith("Note");
    WorldPoint to = null;
    if (!npcHighlights.isEmpty()) to = npcHighlights.get(0).getWorldLocation();
    else if (!objectHighlights.isEmpty()) to = objectHighlights.get(0).getWorldLocation();
    else if (!step.phase.equals("Travel") || arrivedLeg(step))
      to = TargetData.PATCH_POINTS.get(step.patchId);
    // Patches have fixed spots; prefer them over a moving NPC so Shortest Path isn't re-asked.
    WorldPoint fixed = TargetData.PATCH_POINTS.get(step.patchId);
    // (While travelling, a highlighted guide or object on the way wins: e.g. the Mountain Guide.)
    if (fixed != null && (!noting || to == null)
        && (objectHighlights.isEmpty() && npcHighlights.isEmpty() || !step.phase.equals("Travel"))
        && (!step.phase.equals("Travel") || arrivedLeg(step))) to = fixed;
    // The target's full size, so the route goes to its nearest side (trees are often 2×2+).
    int w = 1, h = 1;
    if (!npcHighlights.isEmpty() && to != null && to.equals(npcHighlights.get(0).getWorldLocation())) {
      NPCComposition npc = npcHighlights.get(0).getComposition();
      w = h = npc == null ? 1 : Math.max(1, npc.getSize());
    } else {
      TileObject object = !objectHighlights.isEmpty() ? objectHighlights.get(0) : patchObject;
      if (object != null && to != null && object.getWorldLocation().distanceTo(to) <= 3) {
        to = object.getWorldLocation();
        if (object instanceof GameObject) {
          w = ((GameObject) object).sizeX();
          h = ((GameObject) object).sizeY();
        } else {
          // Herb patches aren't GameObjects: take the size from the object's definition, so the
          // route stops at the patch's edge instead of walking into it.
          ObjectComposition def = client.getObjectDefinition(object.getId());
          if (def != null) {
            w = Math.max(1, def.getSizeX());
            h = Math.max(1, def.getSizeY());
          }
        }
      }
    }
    shortestPath(null); // never drive the Shortest Path plugin any more
    // Already beside it: no line.
    if (to == null || to.getPlane() != from.getPlane() || besideArea(from, to, w, h))
      return Collections.emptyList();
    // Our own route over the whole map (Shortest Path's collision data, doors, gates and Agility
    // shortcuts), so fences beyond the loaded area count too. The loaded-scene route is the
    // fallback while the map loads.
    List<WorldPoint> route = worldRoute(from, to, w, h);
    if (!route.isEmpty()) return route;
    return TilePath.find(client.getTopLevelWorldView(), from, to);
  }

  /** Whether a tile is on or next to a w × h area whose south-west corner is {@code corner}. */
  private static boolean besideArea(WorldPoint tile, WorldPoint corner, int w, int h) {
    int dx = Math.max(0, Math.max(corner.getX() - tile.getX(), tile.getX() - (corner.getX() + w - 1)));
    int dy = Math.max(0, Math.max(corner.getY() - tile.getY(), tile.getY() - (corner.getY() + h - 1)));
    return dx + dy <= 1; // on it, or beside a side (corners don't count)
  }

  /** The last whole-map route and where it was going, reused while you walk along it. */
  private List<WorldPoint> worldRoute = Collections.emptyList();
  private WorldPoint worldRouteTarget;

  /**
   * The whole-map route to {@code to}: the cached one, trimmed to where you are, while you're on
   * it; otherwise a fresh search.
   */
  private List<WorldPoint> worldRoute(WorldPoint from, WorldPoint to, int w, int h) {
    if (!WorldPath.ready()) return Collections.emptyList(); // still loading the map
    if (to.equals(worldRouteTarget)) {
      for (int i = 0; i < worldRoute.size(); i++)
        if (worldRoute.get(i).distanceTo(from) <= 1)
          return worldRoute = new ArrayList<>(worldRoute.subList(i + 1, worldRoute.size()));
      // No route to this target last time: only try again every few ticks.
      if (worldRoute.isEmpty() && client.getTickCount() - worldRouteSearchedAt < 10)
        return worldRoute;
    }
    worldRouteTarget = to;
    worldRouteSearchedAt = client.getTickCount();
    worldRoute = WorldPath.find(from, to, w, h);
    return worldRoute;
  }

  private int worldRouteSearchedAt;

  private WorldPoint shortestPathTarget;
  private long shortestPathChecked;
  private boolean shortestPathOn;

  /** Whether the Shortest Path plugin is installed and switched on (checked every 10 seconds). */
  private boolean shortestPathEnabled() {
    long now = System.currentTimeMillis();
    if (now - shortestPathChecked > 10_000) {
      shortestPathChecked = now;
      shortestPathOn = false;
      for (Plugin p : pluginManager.getPlugins())
        if ("Shortest Path".equals(p.getName()) && pluginManager.isPluginEnabled(p))
          shortestPathOn = true;
    }
    return shortestPathOn;
  }

  /**
   * Sets (or with null, clears) Shortest Path's target through its plugin-message API, the way
   * other plugins drive it. Only sent when the target changes.
   */
  private void shortestPath(WorldPoint target) {
    if (Objects.equals(target, shortestPathTarget)) return;
    if (target == null && shortestPathTarget == null) return;
    shortestPathTarget = target;
    if (!shortestPathEnabled()) return;
    if (target == null) {
      eventBus.post(new PluginMessage("shortestpath", "clear"));
      return;
    }
    Map<String, Object> data = new HashMap<>();
    data.put("target", target);
    eventBus.post(new PluginMessage("shortestpath", "path", data));
  }

  /** On a Travel step, once teleported out: the rest is on foot towards the patch. */
  private boolean arrivedLeg(Guidance step) {
    WorldPoint at = TargetData.PATCH_POINTS.get(step.patchId);
    Player player = client.getLocalPlayer();
    return at != null && player != null && at.distanceTo(player.getWorldLocation()) < 120;
  }

  private volatile String noteAdvice;

  /** Shown when herbs should be noted now, with the leprechaun and grimy herbs highlighted. */
  String noteAdvice() {
    return noteAdvice;
  }

  /** Notes only when the inventory fills up while there are still herbs to pick. */
  private String noteAdvice(Guidance step, Player player, TileObject patch) {
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    if (inventory == null) return null;
    int used = 0, grimy = 0;
    for (Item item : inventory.getItems())
      if (item.getId() > 0) {
        used++;
        if (GRIMY.contains(item.getId())) grimy++;
      }
    int free = 28 - used;
    if (grimy == 0) return null;
    // Noting only ever happens when the inventory fills up while picking: pick until full, note
    // at that patch's leprechaun, then finish. Nothing is noted ahead of time, so the advice never
    // springs up as you're about to teleport, and you never walk past the patch to note first.
    if (!step.kind.equals("herb") || !arrived(step.patchId, player)) return null;
    boolean harvesting = step.phase.equals("Harvest");
    boolean stillToPick = false;
    if (patch != null) {
      ObjectComposition c = client.getObjectDefinition(patch.getId());
      if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
      if (c != null)
        for (String a : c.getActions())
          stillToPick |= a != null && a.toLowerCase(Locale.ROOT).startsWith("pick");
    }
    if (harvesting && stillToPick && free == 0)
      return "Inventory full: note your herbs at this leprechaun, then finish picking";
    return null;
  }

  /** The herb patch you've started picking at (set by the first herb picked there). */
  private volatile String pickingPatch;

  /**
   * The fairy ring or spirit tree to use on this Travel step, once it's in view (e.g. inside the
   * house after the house teleport). Also matches the combined spiritual fairy tree.
   */
  /** Set once this patch's trip has teleported out of the house or through the ring/tree. */
  private boolean jumped;

  private String jumpPatch;

  private Object transportObject(Guidance step, Player player) {
    String travel = step.travel.toLowerCase(Locale.ROOT);
    boolean house = client.getTopLevelWorldView().isInstance();
    // Already through the fairy ring or spirit tree: Tree Gnome Village's railing and Elkoy are
    // still to come, but nothing else is.
    // After the quetzal lands at Quetzacalli Gorge: the Mountain Guide takes you to Auburn Valley.
    // (The whistle isn't a ring or tree, so no jump is waited for: the guide is only ever there.)
    if (travel.contains("mountain guide")) {
      NPC guide = null;
      int closest = Integer.MAX_VALUE;
      for (NPC n : sceneNpcs) {
        if (n.getName() == null || !n.getName().equalsIgnoreCase("mountain guide")) continue;
        int d = n.getWorldLocation().distanceTo(player.getWorldLocation());
        if (d < closest) {
          guide = n;
          closest = d;
        }
      }
      return guide;
    }
    if (jumped && !travel.contains("elkoy")) return null;
    // Tree Gnome Village: squeeze through the loose railing, then follow Elkoy through the maze.
    if (travel.contains("elkoy") && !house) {
      NPC elkoy = null;
      for (NPC n : sceneNpcs)
        if (n.getName() != null && n.getName().equalsIgnoreCase("elkoy")) elkoy = n;
      TileObject railing = nearestObject(player, 30, "railing");
      if (railing != null && !railingDone) return railing;
      if (elkoy != null) return elkoy;
      // Already through the spirit tree: never fall back to highlighting the village's own tree.
      if (jumped) return null;
    }
    List<String> names = new ArrayList<>();
    if (travel.contains("fairy ring")) names.addAll(Arrays.asList("fairy ring", "spiritual fairy tree"));
    if (travel.contains("spirit tree")) names.addAll(Arrays.asList("spirit tree", "spiritual fairy tree"));
    // In the house: the jewellery box or the portal (nexus) the route uses.
    if (house && travel.contains("jewellery box")) names.add("jewellery box");
    // The nexus or the destination's own portal (e.g. "Catherby portal"), never the plain exit
    // "Portal" by the house entrance.
    if (house && travel.contains("portal")) {
      names.add("nexus");
      String place = FarmingInventoryOverlay.destination(step.travel);
      if (place.length() > 3) names.add(place);
    }
    if (names.isEmpty()) return null;
    return nearestObject(player, 40, names.toArray(new String[0]));
  }

  /** The nearest remembered object whose name contains one of these (lower case) words. */
  private TileObject nearestObject(Player player, int within, String... names) {
    TileObject best = null;
    int closest = within;
    for (TileObject o : sceneObjects) {
      ObjectComposition c = client.getObjectDefinition(o.getId());
      if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
      if (c == null || c.getName() == null) continue;
      String n = c.getName().toLowerCase(Locale.ROOT);
      boolean match = false;
      for (String name : names) match |= n.contains(name);
      if (!match) continue;
      int d = o.getWorldLocation().distanceTo(player.getWorldLocation());
      if (d < closest) {
        best = o;
        closest = d;
      }
    }
    return best;
  }

  /** Set once you squeeze through the Tree Gnome Village railing on this patch's trip. */
  private boolean railingDone;

  private String railingPatch;

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked e) {
    String option = Text.removeTags(e.getMenuOption()).toLowerCase(Locale.ROOT);
    String target = Text.removeTags(e.getMenuTarget()).toLowerCase(Locale.ROOT);
    // The magic cape's Spellbook option: a change of spellbook soon after counts as one of the
    // 5 a day.
    if (option.equals("spellbook") && (target.contains("magic cape") || target.contains("max cape")))
      capeSwapClickedAt = client.getTickCount();
    Guidance step = guidance;
    if (step == null) return;
    // Fairy rings, spirit trees, portals and boxes: the next big jump is the route's own.
    // (Not the house's plain exit "Portal".)
    if (target.contains("fairy") || target.contains("spirit tree")
        || target.contains("portal") && !target.equals("portal")
        || target.contains("jewellery box") || target.contains("nexus"))
      usedTransport = true;
    // A teleport on this step's item (or a teleport spell) was clicked: it's under way.
    if (step.phase.equals("Travel") && !TELEPORT_NOT.contains(option)) {
      boolean ours = option.equals("cast") && target.contains("teleport");
      for (String item : step.items)
        ours |= target.startsWith(menuName(FarmingPlanner.normalize(item)));
      if (ours) {
        teleportClickedAt = client.getTickCount();
        teleportClickedPatch = step.patchId;
      }
    }
    // Checking a tree at another route patch moves the guide there (you've skipped ahead).
    if (option.equals("check-health")) patchAction("Check health", null);
    if (target.contains("resurrect crops")) {
      resurrectCastOn = step.patchId;
      resurrectTried.add(step.patchId);
      event("cast resurrect: " + step.patchId);
    }
    if (!step.patchId.equals(railingPatch)) {
      railingPatch = step.patchId;
      railingDone = false;
    }
    if (Text.removeTags(e.getMenuOption()).equalsIgnoreCase("squeeze-through")
        && Text.removeTags(e.getMenuTarget()).toLowerCase(Locale.ROOT).contains("railing"))
      railingDone = true;
  }

  /** What to click for a substep: the patch, its gardener or the nearby tool leprechaun. */
  private Object target(
      Guidance step, String phase, NPC gardener, TileObject patch, NPC leprechaun) {
    if (phase.equals("Travel")) return null;
    if (phase.startsWith("Note")) return leprechaun;
    if (!step.kind.equals("herb")
        && (phase.equals("Protect")
            || phase.equals("Clear") && GARDENER_REMOVAL.contains(step.kind) && !deadPatch))
      return gardener;
    return patch;
  }
}
