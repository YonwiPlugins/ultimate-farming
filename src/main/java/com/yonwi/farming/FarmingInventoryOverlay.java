package com.yonwi.farming;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Leads the player through the interface: the side tab to open, then the item, worn item or spell
 * to use for the guided action.
 */
final class FarmingInventoryOverlay extends Overlay {
  /** Current substep: item in blue, its patch or NPC a lighter blue until the item is selected. */
  static final Color ITEM = new Color(0, 140, 255), TARGET = new Color(110, 200, 255);
  /** The substep after the current one, so the next click is always ready. */
  static final Color NEXT = new Color(255, 150, 0);

  private static final int INVENTORY_TAB = 3, EQUIPMENT_TAB = 4, MAGIC_TAB = 6;
  private static final int[][] STONES = {
    {
      InterfaceID.Toplevel.STONE3,
      InterfaceID.ToplevelOsrsStretch.STONE3,
      InterfaceID.ToplevelPreEoc.STONE3
    },
    {
      InterfaceID.Toplevel.STONE4,
      InterfaceID.ToplevelOsrsStretch.STONE4,
      InterfaceID.ToplevelPreEoc.STONE4
    },
    {
      InterfaceID.Toplevel.STONE6,
      InterfaceID.ToplevelOsrsStretch.STONE6,
      InterfaceID.ToplevelPreEoc.STONE6
    }
  };
  /** Equipment slot index to its widget in the worn equipment tab. */
  private static final Map<Integer, Integer> WORN_SLOTS = new HashMap<>();

  static {
    int[] slots = {0, 1, 2, 3, 4, 5, 7, 9, 10, 12};
    int[] widgets = {
      InterfaceID.Wornitems.SLOT0,
      InterfaceID.Wornitems.SLOT1,
      InterfaceID.Wornitems.SLOT2,
      InterfaceID.Wornitems.SLOT3,
      InterfaceID.Wornitems.SLOT4,
      InterfaceID.Wornitems.SLOT5,
      InterfaceID.Wornitems.SLOT7,
      InterfaceID.Wornitems.SLOT9,
      InterfaceID.Wornitems.SLOT10,
      InterfaceID.Wornitems.SLOT12
    };
    for (int i = 0; i < slots.length; i++) WORN_SLOTS.put(slots[i], widgets[i]);
  }

  private static final Map<String, Integer> SPELLS = new HashMap<>();

  static {
    SPELLS.put("Falador", InterfaceID.MagicSpellbook.FALADOR_TELEPORT);
    SPELLS.put("Lumbridge", InterfaceID.MagicSpellbook.LUMBRIDGE_TELEPORT);
    SPELLS.put("Varrock", InterfaceID.MagicSpellbook.VARROCK_TELEPORT);
    SPELLS.put("Camelot", InterfaceID.MagicSpellbook.CAMELOT_TELEPORT);
    SPELLS.put("Ardougne", InterfaceID.MagicSpellbook.ARDOUGNE_TELEPORT);
    SPELLS.put("House", InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
  }

  private final FarmingPlugin plugin;
  private final FarmingConfig config;
  private final Client client;

  @Inject
  FarmingInventoryOverlay(FarmingPlugin plugin, FarmingConfig config, Client client) {
    this.plugin = plugin;
    this.config = config;
    this.client = client;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.ABOVE_WIDGETS);
  }

  /** What this frame highlighted, as {colour, what}, while the debug setting is on; else null. */
  private static List<String[]> recording;

  @Override
  public Dimension render(Graphics2D g) {
    recording = config.debug() ? new ArrayList<>() : null;
    try {
      return renderMarks(g);
    } finally {
      if (recording != null) {
        // Grouped by colour, item IDs named: "blue [Rune pouch, Load] · orange [Harmony …]".
        Map<String, Set<String>> byColour = new TreeMap<>();
        for (String[] entry : recording) {
          String what = entry[1];
          if (what.startsWith("item ")) {
            int id = Integer.parseInt(what.substring(5));
            what = client.getItemDefinition(id).getName() + " (" + id + ")";
          }
          byColour.computeIfAbsent(entry[0], k -> new TreeSet<>()).add(what);
        }
        StringBuilder line = new StringBuilder();
        for (Map.Entry<String, Set<String>> group : byColour.entrySet())
          line.append(line.length() > 0 ? " · " : "").append(group.getKey()).append(' ').append(group.getValue());
        plugin.debug().noteHighlights("interface", line.toString());
        recording = null;
      }
    }
  }

  /** Debug: remembers one highlight of this frame. */
  static void note(String what, Color color) {
    if (recording != null) recording.add(new String[] {colourName(color), what});
  }

  static String colourName(Color color) {
    if (color.equals(ITEM)) return "blue";
    if (color.equals(TARGET)) return "light blue";
    if (color.equals(NEXT)) return "orange";
    return String.format("#%06x", color.getRGB() & 0xFFFFFF);
  }

  /** A widget in words: its item, text, option or ID. */
  static String describe(Widget widget) {
    if (widget.getItemId() > 0) return "item " + widget.getItemId();
    String text = widget.getText() == null ? "" : Text.removeTags(widget.getText()).trim();
    if (!text.isEmpty()) return "'" + text + "'";
    if (widget.getActions() != null)
      for (String action : widget.getActions()) if (action != null && !action.isEmpty()) return "'" + Text.removeTags(action) + "'";
    String name = widget.getName() == null ? "" : Text.removeTags(widget.getName()).trim();
    if (!name.isEmpty()) return "'" + name + "'";
    return "widget " + (widget.getId() >>> 16) + "/" + (widget.getId() & 0xFFFF);
  }

  /** Marks a widget, cut to what its parents show. */
  private static void markVisible(Graphics2D g, Widget widget, Color color) {
    Rectangle visible = visibleBounds(widget);
    if (visible.isEmpty()) return;
    note(describe(widget), color);
    draw(g, visible, color);
  }

  private Dimension renderMarks(Graphics2D g) {
    Guidance step = plugin.guide();
    boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
    if (loggedIn && plugin.bankButton().filterOpen()) markBankCounts(g);
    if (!config.highlight()) return null;
    if (step == null) markBeforeRun(g, loggedIn);
    else if (loggedIn) markStep(g, step);
    return null;
  }

  /** At the bank before a run: the cape to put back, the rune pouch to set up, or the spellbook switch. */
  private void markBeforeRun(Graphics2D g, boolean loggedIn) {
    // Spellbook switched: the magic cape goes back in the bank.
    if (plugin.kit().missing().contains(FarmingPanel.DEPOSIT_CAPE)) {
      Set<Integer> cape = FarmingData.itemIds("Magic cape");
      for (Widget item : visibleChildren(InterfaceID.Bankside.ITEMS))
        if (cape.contains(item.getItemId())) mark(g, item, ITEM);
    }
    if (!loggedIn) return;
    Set<Integer> shortRunes = plugin.kit().pouchShortfall();
    Widget bankWindow = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (!shortRunes.isEmpty() && bankWindow != null && !bankWindow.isHidden()) markPouchSetup(g, shortRunes);
    else if (plugin.spellbook().route() != null) markSpellbookSwitch(g);
  }

  /** During a run: the side tab, item, worn item or spell for the guided step, and what's next. */
  private void markStep(Graphics2D g, Guidance step) {
    // Dead herbs and Resurrect Crops on: the spell first, then the patch (once it's selected).
    if (plugin.progress().resurrectNow()) {
      markResurrect(g);
      return;
    }
    Set<Integer> relevant = plugin.targets().itemIds(step);
    int open = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    markNextItems(g, step, relevant, open);
    // Noting advised: the unnoted grimy herbs are what to use on the leprechaun.
    if (plugin.noting().advice() != null) {
      pointToInventory(g, open, plugin.noting().produce(), true);
      return;
    }
    // Herb sack carried shut: open it before picking, so the herbs go in it.
    if (plugin.harvest().openSackNow()) {
      pointToInventory(g, open, HarvestTracker.shutHerbSacks(), true);
      return;
    }
    // Paying the gardener to remove the tree: "Yes." in his "...chopped down?" choice.
    if (step.phase.equals("Clear")) markRemovalYes(g);
    // Once the seed or compost is selected, only the patch is left to click.
    if (plugin.targets().selectedItem(relevant)) return;
    boolean travel = step.phase.equals("Travel");
    if (markTravelMenus(g, step, travel)) return;
    // The teleport has just been clicked: it's happening, so stop pointing at the item.
    if (travel && plugin.progress().travelItemDone()) return;
    // Inside the house (an instance) the teleport item is spent; the house portal comes next.
    if (travel && client.getTopLevelWorldView().isInstance()) return;
    // A fairy ring staff comes first: equip it, then it stops being highlighted for good.
    if (travel && step.items.contains("Dramen/lunar staff if required")) {
      Set<Integer> staff = FarmingData.itemIds("Dramen or lunar staff");
      relevant.removeAll(staff);
      if (!contains(InventoryID.WORN, staff) && contains(InventoryID.INV, staff)) {
        pointToInventory(g, open, staff, false);
        return;
      }
    }
    boolean inInventory = markStepItem(g, step, relevant, open);
    if (travel) markQuetzalDestination(g, step.travel);
    if (travel && step.spell != null && !inInventory) {
      if (open != MAGIC_TAB) mark(g, stone(2));
      else mark(g, client.getWidget(SPELLS.get(step.spell)));
    }
  }

  private void markResurrect(Graphics2D g) {
    if (client.isWidgetSelected()) return;
    int tab = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    if (tab != MAGIC_TAB) mark(g, stone(2));
    else mark(g, client.getWidget(InterfaceID.MagicSpellbook.RESURRECT_CROPS));
  }

  /**
   * The next substep's items in orange, when the inventory is already showing. On the patch's
   * last step, "next" is the next patch's teleport.
   */
  private void markNextItems(Graphics2D g, Guidance step, Set<Integer> relevant, int open) {
    boolean nextIsTeleport = step.nextPhase == null;
    Set<Integer> next = plugin.targets().usableIds(nextIsTeleport ? step.nextTravelItems : step.nextItems);
    next.removeAll(relevant);
    if (!nextIsTeleport) {
      // Already-worn equipment (secateurs, a staff) isn't something to click: leave it alone.
      next.removeAll(wornIds());
      // Secateurs only light up once picking is the current step (and they aren't worn).
      next.removeAll(FarmingData.itemIds("Magic secateurs"));
      // Noting comes first: don't show what's after it yet (e.g. the next teleport).
      // (The next patch's teleport stays orange, so it's ready once you've noted.)
      if (plugin.noting().advice() != null) next.clear();
    }
    // Picking herbs that will likely fill the inventory: the unnoted herbs are next, not the seed.
    boolean noteSoon = plugin.noting().soon();
    if (noteSoon) next = new HashSet<>(plugin.noting().produce());
    if (open == INVENTORY_TAB && !next.isEmpty())
      markInventory(g, next, NEXT, noteSoon || !nextIsTeleport && step.nextPhase.startsWith("Note"));
    // The next teleport is worn (e.g. a ring or cape): show its tab in orange, or the item itself
    // when that tab is open.
    if (nextIsTeleport && !next.isEmpty() && !contains(InventoryID.INV, next) && contains(InventoryID.WORN, next)) {
      if (open == EQUIPMENT_TAB) markWorn(g, next, NEXT);
      else if (open == INVENTORY_TAB) mark(g, stone(1), NEXT);
    }
  }

  /**
   * Destination menus and maps while travelling (or back in the house partway through a stage that
   * came through it). True when nothing else is to be marked.
   */
  private boolean markTravelMenus(Graphics2D g, Guidance step, boolean travel) {
    boolean houseReturn = plugin.progress().houseReturn();
    // Spirit tree, portal nexus and jewellery box menus: highlight this route's destination.
    if (travel || houseReturn) markMenuChoice(g, step.travel);
    // A charter ship's map: the X for this route's port (its tooltip names it, e.g. Corsair Cove).
    if (travel && !plugin.progress().chartered()) markCharterPort(g, step.travel);
    // The fairy ring's travel log, in the house too.
    if (travel || houseReturn) markFairyLog(g, step.travel);
    // Nothing in the inventory is for the house: the way back out is all that's lit up.
    if (houseReturn) return true;
    // A teleport's menu or map is open: the destination in it is the one thing to click now.
    if (travel && destinationMenuOpen()) {
      markQuetzalDestination(g, step.travel);
      plugin.debug().setOverlayHint("destination menu open");
      return true;
    }
    return false;
  }

  /** The step's item in the inventory, or (travelling) worn. Returns whether it's in the inventory. */
  private boolean markStepItem(Graphics2D g, Guidance step, Set<Integer> relevant, int open) {
    boolean inInventory = contains(InventoryID.INV, relevant);
    boolean worn = contains(InventoryID.WORN, relevant);
    // What this overlay decided, for the debug box (e.g. to check the equipment tab hint).
    plugin.debug().setOverlayHint(
        relevant.isEmpty()
            ? "no item for this step"
            : inInventory
                ? "item in inventory" + (open != INVENTORY_TAB ? " (tab highlighted)" : "")
                : worn
                    ? "item worn" + (open != EQUIPMENT_TAB ? " (equipment tab highlighted)" : "")
                    : "item not carried");
    if (inInventory) pointToInventory(g, open, relevant, step.phase.startsWith("Note"));
    else if (step.phase.equals("Travel") && worn) pointToWorn(g, open, relevant);
    return inInventory;
  }

  /** The inventory tab, or once it's open, these items in it. */
  private void pointToInventory(Graphics2D g, int open, Set<Integer> ids, boolean unnotedOnly) {
    if (open != INVENTORY_TAB) mark(g, stone(0));
    else markInventory(g, ids, ITEM, unnotedOnly);
  }

  /** The equipment tab, or once it's open, these worn items. */
  private void pointToWorn(Graphics2D g, int open, Set<Integer> ids) {
    if (open != EQUIPMENT_TAB) mark(g, stone(1));
    else markWorn(g, ids);
  }

  /**
   * Switching spellbook before a run: the magic cape (inventory or worn) and then the book in its
   * dialogue, or the Construction cape / house tablet to get home to the altar. The altar itself
   * is outlined by the highlight overlay.
   */
  private void markSpellbookSwitch(Graphics2D g) {
    String route = plugin.spellbook().route();
    int open = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    String word = plugin.spellbook().word();
    // A dialogue asking which spellbook: highlight the one we want.
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (word != null && options != null && !options.isHidden()) {
      markText(g, options, word, 0);
      return;
    }
    if ("cape".equals(route)) pointToCarried(g, open, FarmingData.itemIds("Magic cape"));
    else if ("house".equals(route))
      pointToCarried(g, open, FarmingData.itemIds(Arrays.asList("Construction cape", "House teleport")));
  }

  /** These items wherever they're carried: in the inventory, else worn. */
  private void pointToCarried(Graphics2D g, int open, Set<Integer> ids) {
    if (contains(InventoryID.INV, ids)) pointToInventory(g, open, ids, false);
    else if (contains(InventoryID.WORN, ids)) pointToWorn(g, open, ids);
  }

  /** The bank's rune pouch panel: each saved loadout's runes and its Load button, A to J. */
  private static final int[][] LOADOUTS = {
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_A, InterfaceID.Bankside.RUNEPOUCH_LOAD_A},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_B, InterfaceID.Bankside.RUNEPOUCH_LOAD_B},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_C, InterfaceID.Bankside.RUNEPOUCH_LOAD_C},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_D, InterfaceID.Bankside.RUNEPOUCH_LOAD_D},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_E, InterfaceID.Bankside.RUNEPOUCH_LOAD_E},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_F, InterfaceID.Bankside.RUNEPOUCH_LOAD_F},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_G, InterfaceID.Bankside.RUNEPOUCH_LOAD_G},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_H, InterfaceID.Bankside.RUNEPOUCH_LOAD_H},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_I, InterfaceID.Bankside.RUNEPOUCH_LOAD_I},
    {InterfaceID.Bankside.RUNEPOUCH_LOADOUT_J, InterfaceID.Bankside.RUNEPOUCH_LOAD_J},
  };

  /**
   * The rune pouch is missing runes the run needs: highlight the pouch (its Configure is the
   * left-click), then, in the pouch panel, the Load button of a saved loadout holding the run's
   * runes, whatever order they're in. Never the runes in the bank: the pouch is what to set up.
   */
  private void markPouchSetup(Graphics2D g, Set<Integer> shortRunes) {
    // The pouch can only be configured at the bank.
    Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (bank == null || bank.isHidden()) return;
    Set<Integer> needed = plugin.kit().runesNeeded();
    Widget panel = client.getWidget(InterfaceID.Bankside.RUNEPOUCH_CONTAINER);
    Widget window = client.getWidget(InterfaceID.RunePouch.UNIVERSE);
    if (panel != null && !panel.isHidden()) markBankLoadout(g, needed);
    else if (window != null && !window.isHidden()) markPouchLoadout(g, window, needed, shortRunes);
    else {
      // In the bank: the pouch in the bank-side inventory, with Configure as its left-click (even
      // when it's full: a loadout swaps the run's runes in).
      plugin.kit().setPouchHint("Configure your rune pouch for this run");
      markBanksidePouch(g);
    }
  }

  /**
   * The bank's pouch panel: the loadout with the most of the run's runes (extras don't matter), as
   * long as it has all of them, or as many as the pouch holds (3, or 4 in a divine pouch).
   */
  private void markBankLoadout(Graphics2D g, Set<Integer> needed) {
    int enough = Math.min(plugin.kit().pouchKinds(), needed.size());
    Widget best = null;
    int bestOverlap = 0;
    for (int[] loadout : LOADOUTS) {
      Widget box = client.getWidget(loadout[0]);
      Widget load = client.getWidget(loadout[1]);
      if (box == null || load == null || box.isHidden() || load.isHidden()) continue;
      Set<Integer> runes = runesIn(box, 0);
      runes.retainAll(needed);
      if (runes.size() > bestOverlap) {
        best = load;
        bestOverlap = runes.size();
      }
    }
    if (best != null && bestOverlap >= enough) {
      markVisible(g, best, ITEM);
      plugin.kit().setPouchHint(null);
    } else {
      markBanksidePouch(g);
      plugin.kit().setPouchHint("No saved loadout has this run's runes: put them in your pouch");
    }
  }

  /** The pouch's own window: a saved loadout holding at least 3 of the run's runes (or all, if fewer). */
  private void markPouchLoadout(Graphics2D g, Widget window, Set<Integer> needed, Set<Integer> shortRunes) {
    List<Widget> buttons = new ArrayList<>();
    loadButtons(window, buttons, 0);
    Widget best = null;
    int bestOverlap = 0;
    for (Widget button : buttons) {
      Set<Integer> runes = runesIn(button.getParent(), 0);
      if (runes.isEmpty() && button.getParent() != null) runes = runesIn(button.getParent().getParent(), 0);
      runes.retainAll(needed);
      if (runes.size() > bestOverlap) {
        best = button;
        bestOverlap = runes.size();
      }
    }
    if (best != null && bestOverlap >= Math.min(3, needed.size())) {
      markVisible(g, best, ITEM);
      plugin.kit().setPouchHint(null);
    } else {
      // No saved loadout fits: point at the runes to put in by hand.
      markBankItems(g, shortRunes);
      plugin.kit().setPouchHint("No saved loadout fits: put the highlighted runes in your pouch");
    }
  }

  /** The rune pouch in the bank-side inventory. */
  private void markBanksidePouch(Graphics2D g) {
    Set<Integer> pouch = FarmingData.itemIds("Rune pouch");
    for (Widget item : visibleChildren(InterfaceID.Bankside.ITEMS))
      if (pouch.contains(item.getItemId())) mark(g, item, ITEM);
  }

  /** Highlights bank items with these IDs. */
  private void markBankItems(Graphics2D g, Set<Integer> ids) {
    for (Widget item : visibleChildren(InterfaceID.Bankmain.ITEMS))
      if (ids.contains(item.getItemId())) mark(g, item, ITEM);
  }

  /** The visible children of a container widget, or none. */
  private List<Widget> visibleChildren(int containerId) {
    List<Widget> out = new ArrayList<>();
    Widget container = client.getWidget(containerId);
    Widget[] children = container == null || container.isHidden() ? null : container.getDynamicChildren();
    if (children != null) for (Widget child : children) if (child != null && !child.isHidden() && child.getItemId() > 0) out.add(child);
    return out;
  }

  /**
   * Every "Load" button in the pouch window. The loadout layout isn't documented, so a loadout's
   * runes are taken to be the rune pictures beside its button (or one level up).
   */
  private static void loadButtons(Widget widget, List<Widget> found, int depth) {
    if (widget == null || widget.isHidden() || depth > 8) return;
    String[] actions = widget.getActions();
    if (actions != null)
      for (String action : actions)
        if (action != null && Text.removeTags(action).toLowerCase(Locale.ROOT).startsWith("load")) {
          found.add(widget);
          break;
        }
    for (Widget[] children :
        new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
      if (children != null) for (Widget child : children) loadButtons(child, found, depth + 1);
  }

  /** Item IDs pictured anywhere inside a widget. */
  private static Set<Integer> runesIn(Widget widget, int depth) {
    Set<Integer> ids = new HashSet<>();
    if (widget == null || depth > 3) return ids;
    if (widget.getItemId() > 0) ids.add(widget.getItemId());
    for (Widget[] children : new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren()})
      if (children != null) for (Widget child : children) ids.addAll(runesIn(child, depth + 1));
    return ids;
  }

  /**
   * In the fairy ring's travel log (the list beside the dials), highlights this route's code.
   * Each code has its own widget, named after it (e.g. FairyringsLog.ALQ).
   */
  private void markFairyLog(Graphics2D g, String travel) {
    String code = FarmingPlanner.fairyCode(travel);
    if (code == null) return;
    Widget log = client.getWidget(InterfaceID.FairyringsLog.UNIVERSE);
    if (log == null || log.isHidden()) return;
    try {
      int id = InterfaceID.FairyringsLog.class.getField(code).getInt(null);
      Widget entry = client.getWidget(id);
      if (entry == null || entry.isHidden()) return;
      markVisible(g, entry, ITEM);
    } catch (ReflectiveOperationException ignored) {
      // No log entry for this code.
    }
  }

  /** A spirit tree, nexus or jewellery box list, quetzal map or fairy ring dial is showing. */
  private boolean destinationMenuOpen() {
    int[] menus = {
      InterfaceID.Menu.LJ_LAYER1,
      InterfaceID.Menu.LJ_LAYER2,
      InterfaceID.TelenexusTeleport.UNIVERSE,
      InterfaceID.PohJewelleryBox.UNIVERSE,
      InterfaceID.QuetzalwhistleMenu.ICONS,
      InterfaceID.QuetzalMenu.ICONS,
      InterfaceID.FairyringsLog.UNIVERSE
    };
    for (int id : menus) {
      Widget menu = client.getWidget(id);
      if (menu != null && !menu.isHidden()) return true;
    }
    return false;
  }

  /** IDs of everything worn. */
  private Set<Integer> wornIds() {
    Set<Integer> ids = new HashSet<>();
    ItemContainer worn = client.getItemContainer(InventoryID.WORN);
    if (worn != null) for (Item item : worn.getItems()) if (item.getId() > 0) ids.add(item.getId());
    return ids;
  }

  /** Highlights worn items matching these IDs in the equipment tab. */
  private void markWorn(Graphics2D g, Set<Integer> ids) {
    markWorn(g, ids, ITEM);
  }

  private void markWorn(Graphics2D g, Set<Integer> ids, Color color) {
    ItemContainer worn = client.getItemContainer(InventoryID.WORN);
    Item[] items = worn == null ? new Item[0] : worn.getItems();
    for (int i = 0; i < items.length; i++)
      if (ids.contains(items[i].getId()) && WORN_SLOTS.containsKey(i))
        mark(g, client.getWidget(WORN_SLOTS.get(i)), color);
  }

  /** Highlights a widget in the given colour. */
  static void mark(Graphics2D g, Widget widget, Color color) {
    if (widget == null || widget.isHidden()) return;
    note(describe(widget), color);
    draw(g, widget.getBounds(), color);
  }

  /** With {@code unnotedOnly}, noted copies aren't marked: for noting, they're already done. */
  private void markInventory(Graphics2D g, Set<Integer> ids, Color color, boolean unnotedOnly) {
    Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
    Widget[] children =
        inventory == null || inventory.isHidden() ? null : inventory.getDynamicChildren();
    if (children == null) return;
    for (Widget item : children)
      if (item != null
          && !item.isHidden()
          && item.getItemId() > 0
          && ids.contains(unnotedOnly ? item.getItemId() : unnoted(item.getItemId())))
        mark(g, item, color);
  }

  /**
   * In the kit bank filter, writes how many of each item the run needs (e.g. "×7" on saplings)
   * in the top-right corner, so you know what to type for Withdraw-X.
   */
  private void markBankCounts(Graphics2D g) {
    Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
    Widget[] children = items == null || items.isHidden() ? null : items.getDynamicChildren();
    if (children == null) return;
    g.setFont(FontManager.getRunescapeSmallFont());
    for (Widget item : children) {
      if (item == null || item.isHidden() || item.getItemId() <= 0) continue;
      // What's still to withdraw (inventory, worn and pouch already counted), or a green tick
      // once you have enough.
      int left = plugin.kit().remaining(item.getItemId());
      if (left < 0) continue;
      Rectangle bounds = item.getBounds();
      if (left == 0) {
        Icons.get("check").paintIcon(null, g, bounds.x + bounds.width - 14, bounds.y + bounds.height - 15);
        continue;
      }
      if (plugin.kit().count(item.getItemId()) <= 1) continue; // a single item needs no number
      // Coins get no number: the sidebar says when you're short.
      if (item.getItemId() == ItemID.COINS) continue;
      String text = "×" + left;
      int x = bounds.x + bounds.width - g.getFontMetrics().stringWidth(text);
      // Bottom right, clear of the bank's own yellow stack size in the top left.
      int y = bounds.y + bounds.height - 1;
      g.setColor(Color.BLACK);
      g.drawString(text, x + 1, y + 1);
      g.setColor(ITEM);
      g.drawString(text, x, y);
    }
  }

  /** Teleport choice menus: spirit tree list, portal nexus and house jewellery box. */
  private static final int[] CHOICE_MENUS = {
    InterfaceID.Menu.LJ_LAYER1,
    InterfaceID.Menu.LJ_LAYER2,
    InterfaceID.TelenexusTeleport.UNIVERSE,
    InterfaceID.PohJewelleryBox.UNIVERSE
  };

  /** The destination word(s) of a travel line, e.g. "gnome stronghold" or "catherby". */
  static String destination(String travel) {
    int arrow = travel.indexOf('→');
    if (arrow < 0) return "";
    return travel
        .substring(arrow + 1)
        .split("[,;]| then | → ")[0]
        .replaceFirst("(?i)^\\s*portal nexus:\\s*", "")
        // "Taverley house portal": the Construction cape's list just says "Taverley".
        .replace("house portal", "")
        .replace("portal", "")
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  /** "Yes." in the gardener's "Pay 200 Coins to have your tree chopped down?" options only. */
  private void markRemovalYes(Graphics2D g) {
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (options == null || options.isHidden() || options.getDynamicChildren() == null) return;
    boolean removal = false;
    for (Widget row : options.getDynamicChildren())
      removal |= row != null && row.getText() != null &&row.getText().toLowerCase(Locale.ROOT).contains("chopped down?");
    if (removal) markText(g, options, "yes.", 0);
  }

  private static final Pattern MUSHTREE_TO = Pattern.compile("mushtree to ([a-z' ]+?)(;|,| then|$)");

  /** In a destination menu (spirit tree, nexus, jewellery box, mushtree map), this route's place. */
  private void markMenuChoice(Graphics2D g, String travel) {
    String place = destination(travel);
    if (place.length() < 4) return;
    for (int id : CHOICE_MENUS) {
      Widget root = client.getWidget(id);
      if (root != null && !root.isHidden()) markText(g, root, place, 0);
    }
    // The Magic Mushtree's map: "mushtree to Mushroom Meadow".
    Matcher mush = MUSHTREE_TO.matcher(travel.toLowerCase(Locale.ROOT));
    Widget mushtrees = client.getWidget(InterfaceID.FossilMushtrees.UNIVERSE);
    if (mush.find() && mushtrees != null && !mushtrees.isHidden()) {
      String meadow = mush.group(1).trim();
      markText(g, mushtrees, meadow, 0);
      markNamed(g, mushtrees, meadow, 0);
    }
    // A teleport's chat options (e.g. a cape's destination list) name just the place.
    String word = place.split(" ")[0];
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (word.length() >= 4 && options != null && !options.isHidden()) markText(g, options, word, 0);
  }

  /** Marks the row whose text names the destination, searching nested widgets. */
  private void markCharterPort(Graphics2D g, String travel) {
    String port = FarmingPlanner.charterPort(travel);
    if (port == null) return;
    Widget[] roots = client.getWidgetRoots();
    if (roots != null) for (Widget root : roots) markNamed(g, root, port, 0);
  }

  /**
   * Marks the widget whose name or click option names this place, searching nested widgets: the
   * charter map's X and the Destination list's row both have the port as their option.
   */
  private void markNamed(Graphics2D g, Widget widget, String place, int depth) {
    if (widget == null || widget.isHidden() || depth > 10) return;
    StringBuilder all = new StringBuilder(widget.getName() == null ? "" : widget.getName());
    if (widget.getActions() != null) for (String action : widget.getActions()) if (action != null) all.append(' ').append(action);
    String name = Text.removeTags(all.toString()).toLowerCase(Locale.ROOT);
    if (name.contains(place)) {
      markVisible(g, widget, ITEM);
      return;
    }
    for (Widget[] children :
        new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
      if (children != null) for (Widget child : children) markNamed(g, child, place, depth + 1);
  }

  private void markText(Graphics2D g, Widget widget, String place, int depth) {
    if (widget == null || widget.isHidden() || depth > 6) return;
    String text = widget.getText() == null ? "" : Text.removeTags(widget.getText()).toLowerCase(Locale.ROOT);
    if (!text.isEmpty() && text.contains(place)) {
      markVisible(g, widget, ITEM);
      return;
    }
    for (Widget[] children :
        new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
      if (children != null) for (Widget child : children) markText(g, child, place, depth + 1);
  }

  /**
   * A widget's bounds cut to what its parents show. Menu text rows (e.g. the portal nexus list)
   * are wider than their scroll box, and rows scrolled out of view lie outside it.
   */
  private static Rectangle visibleBounds(Widget widget) {
    Rectangle bounds = widget.getBounds();
    for (Widget parent = widget.getParent(); parent != null; parent = parent.getParent())
      bounds = bounds.intersection(parent.getBounds());
    return bounds;
  }

  /** Landing sites named in travel text, matched against the quetzal map's destination icons. */
  private static final String[] QUETZAL_SITES = {"hunter guild", "quetzacalli", "kastori", "wyrm"};

  /** On the quetzal map (whistle or the Civitas quetzal), highlights where this route goes. */
  private void markQuetzalDestination(Graphics2D g, String travel) {
    String text = travel.toLowerCase(Locale.ROOT);
    if (!text.contains("quetzal")) return;
    String site = null;
    for (String known : QUETZAL_SITES) if (text.contains(known)) site = known;
    if (site == null) return;
    for (int id : new int[] {InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.QuetzalMenu.ICONS}) {
      Widget icons = client.getWidget(id);
      if (icons == null || icons.isHidden()) continue;
      Widget[] children = icons.getDynamicChildren();
      if (children == null) continue;
      for (Widget icon : children) {
        if (icon == null || icon.isHidden()) continue;
        StringBuilder words = new StringBuilder(Text.removeTags(icon.getName()));
        if (icon.getActions() != null)
          for (String action : icon.getActions()) if (action != null) words.append(' ').append(action);
        if (words.toString().toLowerCase(Locale.ROOT).contains(site))
          mark(g, icon, ITEM);
      }
    }
  }

  private boolean contains(int container, Set<Integer> ids) {
    ItemContainer items = client.getItemContainer(container);
    if (items == null) return false;
    for (Item item : items.getItems()) if (ids.contains(unnoted(item.getId()))) return true;
    return false;
  }

  private int unnoted(int id) {
    if (id <= 0) return id;
    return client.getItemDefinition(id).getNote() != -1
        ? client.getItemDefinition(id).getLinkedNoteId()
        : id;
  }

  private Widget stone(int tab) {
    for (int id : STONES[tab]) {
      Widget widget = client.getWidget(id);
      if (widget != null && !widget.isHidden()) return widget;
    }
    return null;
  }

  /** Bright outline with a 20% fill. */
  static void mark(Graphics2D g, Widget widget) {
    mark(g, widget, ITEM);
  }

  static void mark(Graphics2D g, Shape shape, Color color) {
    if (shape == null) return;
    note("shape", color);
    draw(g, shape, color);
  }

  private static void draw(Graphics2D g, Shape shape, Color color) {
    if (shape == null) return;
    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 51));
    g.fill(shape);
    g.setColor(color);
    g.setStroke(new BasicStroke(2));
    g.draw(shape);
  }
}
