package com.yonwi.farming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * With the fairy ring's dials open, scrolls the travel log so this route's code is at the top and
 * colours it green, the way the Shortest Path plugin does (ShortestPathPlugin
 * .scrollFairyRingPanel, BSD-2-Clause).
 */
final class FairyRingLog {
  private static final String LOG_TAG = "(Ultimate Farming) ";
  private static final int GREEN = 0x00FF00;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  void highlight(Guidance step) {
    String code = FarmingPlanner.fairyCode(step.travel);
    Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
    if (code == null || contents == null || contents.isHidden()) return;
    List<String> seen = new ArrayList<>();
    Widget entry = entryFor(client, code, seen);
    plugin.debug().logOnce("fairy log: " + code + (entry == null ? " not found in " : " found in ") + seen);
    if (entry == null) return;
    entry.setTextColor(GREEN);
    String text = String.valueOf(entry.getText());
    if (!text.contains(LOG_TAG)) entry.setText(LOG_TAG + text);
    scrollTo(contents, entry);
  }

  /**
   * This code's entry in the open travel log (favourites first), or null. Notes the text of every
   * entry looked at in seen.
   */
  static Widget entryFor(Client client, String code, List<String> seen) {
    Widget entry = null;
    Widget faves = client.getWidget(InterfaceID.FairyringsLog.FAVES);
    if (faves != null && faves.getStaticChildren() != null)
      entry = find(faves.getStaticChildren(), code, seen);
    Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
    if (entry == null && contents != null && contents.getDynamicChildren() != null)
      entry = find(contents.getDynamicChildren(), code, seen);
    return entry;
  }

  /** The first entry for this code, noting the text of every entry looked at. */
  private static Widget find(Widget[] entries, String code, List<String> seen) {
    Widget found = null;
    for (Widget entry : entries)
      if (entry != null && entry.getText() != null && !entry.getText().isEmpty()) {
        seen.add(entry.getText());
        if (found == null && isEntryFor(entry, code)) found = entry;
      }
    return found;
  }

  private void scrollTo(Widget contents, Widget entry) {
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

  /** A travel log entry for this code: it starts with the code, however it's spaced or tagged. */
  private static boolean isEntryFor(Widget entry, String code) {
    String flat =
        Text.removeTags(String.valueOf(entry.getText()))
            .replace(LOG_TAG.trim(), "")
            .replaceAll("\\s", "")
            .toUpperCase(Locale.ROOT);
    return flat.startsWith(code);
  }
}
