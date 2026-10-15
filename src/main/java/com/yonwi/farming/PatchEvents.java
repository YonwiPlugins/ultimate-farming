package com.yonwi.farming;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Planting, composting and payments seen in game message events and gardener dialogue, reported
 * to the sidebar with the patches you're beside.
 */
final class PatchEvents {
  private static final String[] KINDS = {"herb", "calquat", "celastrus", "redwood", "hardwood"};
  /** How near a gardener has to be for his patch to count as one you're beside. */
  private static final int GARDENER_TILES = 20;

  @Inject private Client client;
  @Inject private FarmingPlugin plugin;

  /** The gardener's last dialogue line, to read each once. */
  private String lastDialog;

  void gameMessage(ChatMessage e) {
    // Debug: the game's own messages and dialogue boxes (never players' chat).
    if (e.getType() == ChatMessageType.GAMEMESSAGE
        || e.getType() == ChatMessageType.SPAM
        || e.getType() == ChatMessageType.MESBOX
        || e.getType() == ChatMessageType.DIALOG)
      plugin.debug().log("chat [" + e.getType() + "] " + Text.removeTags(e.getMessage()));
    if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM) return;
    String message = Text.removeTags(e.getMessage()).toLowerCase(Locale.ROOT);
    if (message.contains("spellbook")) plugin.spellbook().gameMessage(message);
    if (message.startsWith("you pay ") && message.contains(" and sail to ")) plugin.progress().charterSailed();
    if (message.contains("already resurrected")) plugin.progress().alreadyResurrected();
    if (!message.contains("patch")) return;
    plugin.debug().event("msg: " + message);
    if (message.startsWith("you plant ")) patchAction("Plant", patchKind(message));
    // Only "You treat the … patch": inspecting says "has been treated", which isn't you composting.
    else if (message.startsWith("you treat") && message.contains("compost")) patchAction("Compost", patchKind(message));
    // Missed it (e.g. lag)? Inspecting the patch says "has been treated with … compost": while
    // composting is the step, that counts too.
    else if (message.contains("treated with") && message.contains("compost") && composting())
      patchAction("Compost", patchKind(message));
  }

  private boolean composting() {
    Guidance step = plugin.guide();
    return step != null && step.phase.equals("Compost");
  }

  /** Gardeners confirm protection in dialogue rather than in game messages. */
  void watchGardenerDialog() {
    if (plugin.config().debug()) plugin.debugTools().logDialogue();
    Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
    String value = text == null || text.isHidden() ? null : text.getText();
    if (Objects.equals(value, lastDialog)) return;
    lastDialog = value;
    if (value == null) return;
    String line = Text.removeTags(value).toLowerCase(Locale.ROOT);
    if (line.contains("leave it with me") || line.contains("already looking after")) {
      plugin.debug().event("gardener: " + line);
      patchAction("Protect", null);
    }
  }

  private static String patchKind(String message) {
    for (String kind : KINDS) if (message.contains(kind)) return kind;
    if (message.contains("fruit tree")) return "fruit";
    if (message.contains("tree patch")) return "tree";
    return null;
  }

  /** Reports an observed planting, compost or payment with the patches the player is beside. */
  void patchAction(String action, String kind) {
    Player player = client.getLocalPlayer();
    if (player == null) return;
    Set<String> nearby = new HashSet<>();
    int region = player.getWorldLocation().getRegionID();
    for (Map.Entry<String, Set<Integer>> entry : TargetData.HERB_REGIONS.entrySet())
      if (entry.getValue().contains(region)) nearby.add(entry.getKey());
    for (Map.Entry<String, Integer> entry : TargetData.GARDENERS.entrySet())
      for (NPC npc : plugin.scene().npcs())
        if (npc.getId() == entry.getValue()
            && npc.getWorldLocation().distanceTo(player.getWorldLocation()) <= GARDENER_TILES)
          nearby.add(entry.getKey());
    plugin.toPanel(panel -> panel.patchAction(action, kind, nearby));
  }
}
