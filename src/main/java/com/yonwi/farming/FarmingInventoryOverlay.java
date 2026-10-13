package com.yonwi.farming;

import java.awt.*;
import java.util.*;
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
import net.runelite.client.ui.overlay.*;
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

  @Override
  public Dimension render(Graphics2D g) {
    Guidance step = plugin.guide();
    if (client.getGameState() == GameState.LOGGED_IN && plugin.kitFilterOpen()) markBankCounts(g);
    // Spellbook switched: the magic cape goes back in the bank.
    if (config.highlight()
        && step == null
        && plugin.missingItems().contains(FarmingPanel.DEPOSIT_CAPE)) {
      Set<Integer> cape = FarmingPlugin.itemIds(Collections.singletonList("Magic cape"));
      for (Widget item : visibleChildren(InterfaceID.Bankside.ITEMS))
        if (cape.contains(item.getItemId())) mark(g, item.getBounds(), ITEM);
    }
    if (config.highlight() && step == null && client.getGameState() == GameState.LOGGED_IN) {
      Set<Integer> shortRunes = plugin.pouchShortfall();
      Widget bankWindow = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
      if (!shortRunes.isEmpty() && bankWindow != null && !bankWindow.isHidden()) {
        markPouchSetup(g, shortRunes);
        return null;
      }
    }
    if (config.highlight()
        && step == null
        && client.getGameState() == GameState.LOGGED_IN
        && plugin.switchRoute() != null) {
      markSpellbookSwitch(g);
      return null;
    }
    if (!config.highlight() || step == null || client.getGameState() != GameState.LOGGED_IN)
      return null;
    // Dead herbs and Resurrect Crops on: the spell first, then the patch (once it's selected).
    if (plugin.resurrectNow()) {
      if (client.isWidgetSelected()) return null;
      int tab = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
      if (tab != MAGIC_TAB) mark(g, stone(2));
      else mark(g, client.getWidget(InterfaceID.MagicSpellbook.RESURRECT_CROPS));
      return null;
    }
    Set<Integer> relevant = plugin.itemIds(step);

    int open = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    // Next substep's items in orange, when the inventory is already showing.
    // On the patch's last step, "next" is the next patch's teleport.
    boolean nextIsTeleport = step.nextPhase == null;
    Set<Integer> next =
        plugin.usableIds(nextIsTeleport ? step.nextTravelItems : step.nextItems);
    next.removeAll(relevant);
    // Already-worn equipment (secateurs, a staff) isn't something to click: leave it alone.
    if (!nextIsTeleport) next.removeAll(wornIds());
    // Secateurs only light up once picking is the current step (and they aren't worn).
    if (!nextIsTeleport)
      next.removeAll(FarmingPlugin.itemIds(Collections.singletonList("Magic secateurs")));
    // Noting comes first: don't show what's after it yet (e.g. the next teleport).
    // (The next patch's teleport stays orange, so it's ready once you've noted.)
    if (plugin.noteAdvice() != null && !nextIsTeleport) next.clear();
    if (open == INVENTORY_TAB && !next.isEmpty())
      markInventory(g, next, NEXT, !nextIsTeleport && step.nextPhase.startsWith("Note"));
    // The next teleport is worn (e.g. a ring or cape): show its tab in orange, or the item itself
    // when that tab is open.
    if (nextIsTeleport && !next.isEmpty() && !contains(InventoryID.INV, next) && contains(InventoryID.WORN, next)) {
      if (open == EQUIPMENT_TAB) markWorn(g, next, NEXT);
      else if (open == INVENTORY_TAB) mark(g, stone(1), NEXT);
    }
    // Noting advised: the unnoted grimy herbs are what to use on the leprechaun.
    if (plugin.noteAdvice() != null) {
      if (open != INVENTORY_TAB) mark(g, stone(0));
      else markInventory(g, FarmingPlugin.grimyHerbs(), ITEM, true);
      return null;
    }
    // Once the seed or compost is selected, only the patch is left to click.
    if (plugin.selectedItem(relevant)) return null;
    // Spirit tree, portal nexus and jewellery box menus: highlight this route's destination.
    if (step.phase.equals("Travel")) markMenuChoice(g, step.travel);
    // The fairy ring's travel log, in the house too.
    if (step.phase.equals("Travel")) markFairyLog(g, step.travel);
    // A teleport's menu or map is open: the destination in it is the one thing to click now.
    if (step.phase.equals("Travel") && destinationMenuOpen()) {
      markQuetzalDestination(g, step.travel);
      plugin.setOverlayHint("destination menu open");
      return null;
    }
    // The teleport has just been clicked: it's happening, so stop pointing at the item.
    if (step.phase.equals("Travel") && plugin.teleportClicked()) return null;
    // Inside the house (an instance) the teleport item is spent; the house portal comes next.
    if (step.phase.equals("Travel") && client.getTopLevelWorldView().isInstance()) return null;
    // A fairy ring staff comes first: equip it, then it stops being highlighted for good.
    if (step.phase.equals("Travel") && step.items.contains("Dramen/lunar staff if required")) {
      Set<Integer> staff = FarmingPlugin.itemIds(Collections.singletonList("Dramen or lunar staff"));
      relevant.removeAll(staff);
      if (!contains(InventoryID.WORN, staff) && contains(InventoryID.INV, staff)) {
        if (open != INVENTORY_TAB) mark(g, stone(0));
        else markInventory(g, staff, ITEM, false);
        return null;
      }
    }
    boolean inInventory = contains(InventoryID.INV, relevant);
    // What this overlay decided, for the debug box (e.g. to check the equipment tab hint).
    plugin.setOverlayHint(
        relevant.isEmpty()
            ? "no item for this step"
            : inInventory
                ? "item in inventory" + (open != INVENTORY_TAB ? " (tab highlighted)" : "")
                : contains(InventoryID.WORN, relevant)
                    ? "item worn" + (open != EQUIPMENT_TAB ? " (equipment tab highlighted)" : "")
                    : "item not carried");
    if (inInventory) {
      if (open != INVENTORY_TAB) mark(g, stone(0));
      else markInventory(g, relevant, ITEM, step.phase.startsWith("Note"));
    } else if (step.phase.equals("Travel") && contains(InventoryID.WORN, relevant)) {
      if (open != EQUIPMENT_TAB) mark(g, stone(1));
      else {
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        Item[] items = worn == null ? new Item[0] : worn.getItems();
        for (int i = 0; i < items.length; i++)
          if (relevant.contains(items[i].getId()) && WORN_SLOTS.containsKey(i))
            mark(g, client.getWidget(WORN_SLOTS.get(i)));
      }
    }
    if (step.phase.equals("Travel")) markQuetzalDestination(g, step.travel);
    if (step.phase.equals("Travel") && step.spell != null && !inInventory) {
      if (open != MAGIC_TAB) mark(g, stone(2));
      else mark(g, client.getWidget(SPELLS.get(step.spell)));
    }
    return null;
  }

  /**
   * Switching spellbook before a run: the magic cape (inventory or worn) and then the book in its
   * dialogue, or the Construction cape / house tablet to get home to the altar. The altar itself
   * is outlined by the highlight overlay.
   */
  private void markSpellbookSwitch(Graphics2D g) {
    String route = plugin.switchRoute();
    int open = client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL);
    String word = plugin.spellbookWord();
    // A dialogue asking which spellbook: highlight the one we want.
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (word != null && options != null && !options.isHidden()) {
      markText(g, options, word, 0);
      return;
    }
    if ("cape".equals(route)) {
      Set<Integer> cape = FarmingPlugin.itemIds(Collections.singletonList("Magic cape"));
      if (contains(InventoryID.INV, cape)) {
        if (open != INVENTORY_TAB) mark(g, stone(0));
        else markInventory(g, cape, ITEM, false);
      } else if (contains(InventoryID.WORN, cape)) {
        if (open != EQUIPMENT_TAB) mark(g, stone(1));
        else markWorn(g, cape);
      }
    } else if ("house".equals(route)) {
      Set<Integer> home =
          FarmingPlugin.itemIds(java.util.Arrays.asList("Construction cape", "House teleport"));
      if (contains(InventoryID.INV, home)) {
        if (open != INVENTORY_TAB) mark(g, stone(0));
        else markInventory(g, home, ITEM, false);
      } else if (contains(InventoryID.WORN, home)) {
        if (open != EQUIPMENT_TAB) mark(g, stone(1));
        else markWorn(g, home);
      }
    }
  }

  /**
   * The rune pouch is missing runes the run needs: highlight the pouch (its Configure is the
   * left-click), then, in the pouch window, a saved loadout holding all of them, whatever order
   * they're in.
   */
  private void markPouchSetup(Graphics2D g, Set<Integer> shortRunes) {
    // The pouch can only be configured at the bank.
    Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (bank == null || bank.isHidden()) return;
    Set<Integer> needed = plugin.runesNeeded();
    Widget window = client.getWidget(InterfaceID.RunePouch.UNIVERSE);
    if (window != null && !window.isHidden()) {
      // A saved loadout holding at least 3 of the run's runes (or all of them, if fewer).
      java.util.List<Widget> buttons = new java.util.ArrayList<>();
      loadButtons(window, buttons, 0);
      Widget best = null;
      int bestOverlap = 0;
      for (Widget button : buttons) {
        Set<Integer> runes = runesIn(button.getParent(), 0);
        if (runes.isEmpty() && button.getParent() != null)
          runes = runesIn(button.getParent().getParent(), 0);
        runes.retainAll(needed);
        if (runes.size() > bestOverlap) {
          best = button;
          bestOverlap = runes.size();
        }
      }
      if (best != null && bestOverlap >= Math.min(3, needed.size())) {
        mark(g, visibleBounds(best), ITEM);
        plugin.setPouchHint(null);
      } else {
        // No saved loadout fits: point at the runes to put in by hand.
        markBankItems(g, shortRunes);
        plugin.setPouchHint("No saved loadout fits: put the highlighted runes in your pouch");
      }
      return;
    }
    // The pouch already holds three kinds and none of them is short: what's missing goes in the
    // inventory, so it's the runes in the bank to click, not the pouch again.
    Set<Integer> inPouch = new java.util.HashSet<>();
    for (java.util.Map.Entry<Integer, Integer> rune : plugin.pouchRunes().entrySet())
      if (rune.getValue() > 0) inPouch.add(rune.getKey());
    if (inPouch.size() >= 3 && Collections.disjoint(inPouch, shortRunes)) {
      markBankItems(g, shortRunes);
      plugin.setPouchHint("Your pouch is full: withdraw the highlighted runes");
      return;
    }
    // In the bank: the pouch in the bank-side inventory, with Configure as its left-click.
    plugin.setPouchHint("Configure your rune pouch for this run");
    Set<Integer> pouch = FarmingPlugin.itemIds(Collections.singletonList("Rune pouch"));
    for (Widget item : visibleChildren(InterfaceID.Bankside.ITEMS))
      if (pouch.contains(item.getItemId())) mark(g, item.getBounds(), ITEM);
  }

  /** Highlights bank items with these IDs. */
  private void markBankItems(Graphics2D g, Set<Integer> ids) {
    for (Widget item : visibleChildren(InterfaceID.Bankmain.ITEMS))
      if (ids.contains(item.getItemId())) mark(g, item.getBounds(), ITEM);
  }

  /** The visible children of a container widget, or none. */
  private java.util.List<Widget> visibleChildren(int containerId) {
    java.util.List<Widget> out = new java.util.ArrayList<>();
    Widget container = client.getWidget(containerId);
    Widget[] kids = container == null || container.isHidden() ? null : container.getDynamicChildren();
    if (kids != null) for (Widget kid : kids) if (kid != null && !kid.isHidden() && kid.getItemId() > 0) out.add(kid);
    return out;
  }

  /**
   * Every "Load" button in the pouch window. The loadout layout isn't documented, so a loadout's
   * runes are taken to be the rune pictures beside its button (or one level up).
   */
  private static void loadButtons(Widget widget, java.util.List<Widget> found, int depth) {
    if (widget == null || widget.isHidden() || depth > 8) return;
    String[] actions = widget.getActions();
    if (actions != null)
      for (String action : actions)
        if (action != null && Text.removeTags(action).toLowerCase(Locale.ROOT).startsWith("load")) {
          found.add(widget);
          break;
        }
    for (Widget[] kids :
        new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
      if (kids != null) for (Widget kid : kids) loadButtons(kid, found, depth + 1);
  }

  /** Item IDs pictured anywhere inside a widget. */
  private static Set<Integer> runesIn(Widget widget, int depth) {
    Set<Integer> ids = new java.util.HashSet<>();
    if (widget == null || depth > 3) return ids;
    if (widget.getItemId() > 0) ids.add(widget.getItemId());
    for (Widget[] kids : new Widget[][] {widget.getStaticChildren(), widget.getDynamicChildren()})
      if (kids != null) for (Widget kid : kids) ids.addAll(runesIn(kid, depth + 1));
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
      Rectangle visible = visibleBounds(entry);
      if (!visible.isEmpty()) mark(g, visible, ITEM);
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
      Widget w = client.getWidget(id);
      if (w != null && !w.isHidden()) return true;
    }
    return false;
  }

  /** IDs of everything worn. */
  private Set<Integer> wornIds() {
    Set<Integer> ids = new java.util.HashSet<>();
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
  static void mark(Graphics2D g, Widget w, Color color) {
    if (w == null || w.isHidden()) return;
    mark(g, w.getBounds(), color);
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
        mark(g, item.getBounds(), color);
  }

  /**
   * In the kit bank filter, writes how many of each item the run needs (e.g. "×7" on saplings)
   * in the top-right corner, so you know what to type for Withdraw-X.
   */
  private void markBankCounts(Graphics2D g) {
    Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
    Widget[] children = items == null || items.isHidden() ? null : items.getDynamicChildren();
    if (children == null) return;
    g.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
    for (Widget item : children) {
      if (item == null || item.isHidden() || item.getItemId() <= 0) continue;
      // What's still to withdraw (inventory, worn and pouch already counted), or a green tick
      // once you have enough.
      int left = plugin.kitRemaining(item.getItemId());
      if (left < 0) continue;
      Rectangle b = item.getBounds();
      if (left == 0) {
        Icons.get("check").paintIcon(null, g, b.x + b.width - 14, b.y + b.height - 15);
        continue;
      }
      if (plugin.kitCount(item.getItemId()) <= 1) continue; // a single item needs no number
      // Coins get no number: the sidebar says when you're short.
      if (item.getItemId() == ItemID.COINS) continue;
      String text = "×" + left;
      int x = b.x + b.width - g.getFontMetrics().stringWidth(text);
      // Bottom right, clear of the bank's own yellow stack size in the top left.
      int y = b.y + b.height - 1;
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
        .replace("portal", "")
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  private void markMenuChoice(Graphics2D g, String travel) {
    String place = destination(travel);
    if (place.length() < 4) return;
    for (int id : CHOICE_MENUS) {
      Widget root = client.getWidget(id);
      if (root != null && !root.isHidden()) markText(g, root, place, 0);
    }
    // A teleport's chat options (e.g. a cape's destination list) name just the place.
    String word = place.split(" ")[0];
    Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
    if (word.length() >= 4 && options != null && !options.isHidden()) markText(g, options, word, 0);
  }

  /** Marks the row whose text names the destination, searching nested widgets. */
  private void markText(Graphics2D g, Widget w, String place, int depth) {
    if (w == null || w.isHidden() || depth > 6) return;
    String text = w.getText() == null ? "" : Text.removeTags(w.getText()).toLowerCase(Locale.ROOT);
    if (!text.isEmpty() && text.contains(place)) {
      Rectangle visible = visibleBounds(w);
      if (!visible.isEmpty()) mark(g, visible, ITEM);
      return;
    }
    for (Widget[] kids :
        new Widget[][] {w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
      if (kids != null) for (Widget k : kids) markText(g, k, place, depth + 1);
  }

  /**
   * A widget's bounds cut to what its parents show. Menu text rows (e.g. the portal nexus list)
   * are wider than their scroll box, and rows scrolled out of view lie outside it.
   */
  private static Rectangle visibleBounds(Widget w) {
    Rectangle bounds = w.getBounds();
    for (Widget parent = w.getParent(); parent != null; parent = parent.getParent())
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
    for (String s : QUETZAL_SITES) if (text.contains(s)) site = s;
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
          for (String a : icon.getActions()) if (a != null) words.append(' ').append(a);
        if (words.toString().toLowerCase(Locale.ROOT).contains(site))
          mark(g, icon.getBounds(), ITEM);
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
      Widget w = client.getWidget(id);
      if (w != null && !w.isHidden()) return w;
    }
    return null;
  }

  /** Bright outline with a 20% fill. */
  static void mark(Graphics2D g, Widget w) {
    if (w == null || w.isHidden()) return;
    mark(g, w.getBounds(), ITEM);
  }

  static void mark(Graphics2D g, Shape shape, Color color) {
    if (shape == null) return;
    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 51));
    g.fill(shape);
    g.setColor(color);
    g.setStroke(new BasicStroke(2));
    g.draw(shape);
  }
}
