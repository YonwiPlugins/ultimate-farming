package com.yonwi.farming;

import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;

/**
 * The button in the bank's top bar (where Quest Helper puts its own) that filters the bank down to
 * the run's kit. Only there while preparing a run.
 */
final class BankButton {
  private static final String BANK_TAG = "ultimate farming";
  // Quest Helper's exact button: 25px square at (408, 5) with a 19px icon inset by 3.
  private static final int X = 408;
  private static final int Y = 5;
  private static final int SIZE = 25;
  private static final int ICON_INSET = 3;

  @Inject private Client client;
  @Inject private ClientThread clientThread;
  @Inject private TagManager tagManager;
  @Inject private BankTagsService bankTags;
  @Inject private LayoutManager layoutManager;
  @Inject private FarmingPlugin plugin;

  private Widget button;
  private Widget icon;
  /** "Prepare run" pressed and the run not begun yet (no step done): the bank button's time. */
  private volatile boolean preparing;

  void registerFilter() {
    tagManager.registerTag(BANK_TAG, id -> plugin.kit().inBankFilter(id));
  }

  void unregisterFilter() {
    tagManager.unregisterTag(BANK_TAG);
  }

  /** On shutdown: closes the filter and hides the button. Client thread. */
  void remove() {
    if (filterOpen()) bankTags.closeBankTag();
    Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (button != null && universe != null) {
      button.setHidden(true);
      if (icon != null) icon.setHidden(true);
      universe.revalidate();
    }
    button = null;
  }

  /** True while the bank shows this plugin's kit filter. */
  boolean filterOpen() {
    return BANK_TAG.equals(bankTags.getActiveTag());
  }

  boolean bankOpen() {
    return client.getWidget(InterfaceID.Bankmain.UNIVERSE) != null;
  }

  /** Re-applies the kit filter so the bank shows exactly what's needed now. */
  void refreshFilter() {
    layoutManager.saveLayout(new Layout(BANK_TAG, plugin.kit().bankLayout()));
    bankTags.closeBankTag();
    bankTags.openBankTag(BANK_TAG, 0);
    style();
  }

  /** The bank button only shows while preparing: added or removed as that changes. */
  void setPreparing(boolean now) {
    boolean was = preparing;
    preparing = now;
    if (was != now) clientThread.invokeLater(this::update);
  }

  /** The bank opened. */
  void bankLoaded() {
    // Work out which spare variants to hide (e.g. a second quetzal whistle) before the bank shows
    // the filter, including when it was left open from last time.
    plugin.kit().bankLayout();
    clientThread.invokeLater(
        () -> {
          update();
          // Left open from last time: rebuild it from the current kit, as the run may have changed.
          if (preparing && filterOpen()) refreshFilter();
        });
  }

  void bankClosed() {
    button = null;
    icon = null;
  }

  /**
   * The bank button is only there while preparing a run; otherwise it's removed, along with the
   * kit filter if it was left showing, so the bank is untouched while you do other things.
   */
  void update() {
    Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (universe == null) return;
    if (preparing) {
      add();
      if (button != null) button.setHidden(false);
      if (icon != null) icon.setHidden(false);
      style();
      return;
    }
    if (filterOpen()) bankTags.closeBankTag();
    if (button != null) button.setHidden(true);
    if (icon != null) icon.setHidden(true);
    universe.revalidate();
  }

  /** A farming button in the bank's top bar that shows every item the run needs. */
  private void add() {
    Widget universe = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
    if (universe == null) return;
    Widget[] children = universe.getDynamicChildren();
    if (button != null && children != null && Arrays.asList(children).contains(button)) return;
    button = universe.createChild(-1, WidgetType.GRAPHIC);
    icon = universe.createChild(-1, WidgetType.GRAPHIC);
    for (Widget widget : new Widget[] {button, icon}) {
      int inset = widget == icon ? ICON_INSET : 0;
      widget.setOriginalX(X + inset);
      widget.setOriginalY(Y + inset);
      widget.setOriginalWidth(SIZE - 2 * inset);
      widget.setOriginalHeight(SIZE - 2 * inset);
    }
    icon.setSpriteId(SpriteID.SKILL_FARMING);
    button.setAction(0, "View Ultimate Farming items");
    button.setName("<col=ff9040>Ultimate Farming</col>");
    button.setHasListener(true);
    button.setNoClickThrough(true);
    button.setOnMouseOverListener(
        (JavaScriptCallback) ev -> button.setSpriteId(SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED));
    button.setOnMouseLeaveListener((JavaScriptCallback) ev -> style());
    button.setOnOpListener((JavaScriptCallback) ev -> toggleFilter());
    style();
    button.revalidate();
    icon.revalidate();
  }

  private void toggleFilter() {
    if (filterOpen()) bankTags.closeBankTag();
    else {
      // A fixed layout keeps every slot: a withdrawn item leaves a placeholder instead of the
      // rest shifting left.
      layoutManager.saveLayout(new Layout(BANK_TAG, plugin.kit().bankLayout()));
      bankTags.openBankTag(BANK_TAG, 0);
    }
    style();
  }

  /** The button stays pressed while the farming items are shown. */
  private void style() {
    if (button == null) return;
    button.setSpriteId(
        filterOpen()
            ? SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
            : SpriteID.UNKNOWN_BUTTON_SQUARE_SMALL);
  }
}
