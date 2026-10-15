package com.yonwi.farming;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Infers travel unlocks from quests, diaries and items the logged-in account owns. */
final class AccountScan {
  /** Unlocks that cannot be read from the client and always stay manual. */
  static final Set<String> MANUAL =
      new HashSet<>(
          Arrays.asList(
              "jewellery", "spirit", "kastori", "quetzal", "anglers", "catherbyPortal", "houseFairy",
              "fortisGlory"));

  static final Map<String, int[]> ITEMS = new LinkedHashMap<>();

  /** The Farming Guild's map regions, from RuneLite's Time Tracking FarmingWorld. */
  private static final Set<Integer> GUILD_REGIONS =
      new HashSet<>(Arrays.asList(4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667));

  static {
    ITEMS.put(
        "herbExplorer",
        new int[] {
          ItemID.LUMBRIDGE_RING_EASY,
          ItemID.LUMBRIDGE_RING_MEDIUM,
          ItemID.LUMBRIDGE_RING_HARD,
          ItemID.LUMBRIDGE_RING_ELITE
        });
    ITEMS.put(
        "herbArdy",
        new int[] {
          ItemID.ARDY_CAPE_EASY,
          ItemID.ARDY_CAPE_MEDIUM,
          ItemID.ARDY_CAPE_HARD,
          ItemID.ARDY_CAPE_ELITE,
          ItemID.SKILLCAPE_MAX_ARDY
        });
    ITEMS.put("herbXeric", new int[] {ItemID.XERIC_TALISMAN});
    ITEMS.put("herbEcto", new int[] {ItemID.ECTOPHIAL, ItemID.ECTOPHIAL_EMPTY});
    ITEMS.put(
        "herbBasalt",
        new int[] {ItemID.STRONGHOLD_TELEPORT_BASALT, ItemID.WEISS_TELEPORT_BASALT});
    ITEMS.put("herbTrollheim", new int[] {ItemID.NZONE_TELETAB_TROLLHEIM});
    ITEMS.put("herbHarmonyTab", new int[] {ItemID.TELETAB_HARMONY});
    ITEMS.put("herbSack", FarmingData.IDS.get("Herb sack"));
    ITEMS.put("cape", FarmingData.IDS.get("Construction cape"));
    ITEMS.put("farmingCape", FarmingData.IDS.get("Farming cape"));
    ITEMS.put(
        "pouch",
        new int[] {
          ItemID.BH_RUNE_POUCH,
          ItemID.BH_RUNE_POUCH_TROUVER,
          ItemID.DIVINE_RUNE_POUCH,
          ItemID.DIVINE_RUNE_POUCH_TROUVER
        });
    ITEMS.put(
        "wealth",
        new int[] {
          ItemID.RING_OF_WEALTH_1,
          ItemID.RING_OF_WEALTH_2,
          ItemID.RING_OF_WEALTH_3,
          ItemID.RING_OF_WEALTH_4,
          ItemID.RING_OF_WEALTH_5,
          ItemID.RING_OF_WEALTH_I1,
          ItemID.RING_OF_WEALTH_I2,
          ItemID.RING_OF_WEALTH_I3,
          ItemID.RING_OF_WEALTH_I4,
          ItemID.RING_OF_WEALTH_I5
        });
    ITEMS.put(
        "slayer",
        new int[] {
          ItemID.SLAYER_RING_1,
          ItemID.SLAYER_RING_2,
          ItemID.SLAYER_RING_3,
          ItemID.SLAYER_RING_4,
          ItemID.SLAYER_RING_5,
          ItemID.SLAYER_RING_6,
          ItemID.SLAYER_RING_7,
          ItemID.SLAYER_RING_8,
          ItemID.SLAYER_RING_ETERNAL
        });
    ITEMS.put(
        "skills",
        new int[] {
          ItemID.JEWL_NECKLACE_OF_SKILLS_1,
          ItemID.JEWL_NECKLACE_OF_SKILLS_2,
          ItemID.JEWL_NECKLACE_OF_SKILLS_3,
          ItemID.JEWL_NECKLACE_OF_SKILLS_4,
          ItemID.JEWL_NECKLACE_OF_SKILLS_5,
          ItemID.JEWL_NECKLACE_OF_SKILLS_6
        });
    ITEMS.put("catherby", new int[] {ItemID.LUNAR_TABLET_CATHERBY_TELEPORT});
    ITEMS.put(
        "redirect", new int[] {ItemID.NZONE_TELETAB_TAVERLEY, ItemID.NZONE_TELETAB_BRIMHAVEN});
    ITEMS.put("pendant", new int[] {ItemID.PENDANT_OF_ATES});
    ITEMS.put("whistle", FarmingData.IDS.get("Quetzal whistle"));
    ITEMS.put("digsite", FarmingData.IDS.get("Digsite pendant"));
    ITEMS.put("lletya", FarmingData.IDS.get("Teleport crystal"));
  }

  static final class Result {
    /** True or false when known; keys missing from the map need manual selection. */
    final Map<String, Boolean> unlocks = new LinkedHashMap<>();

    String house;
    boolean bankSeen;
    /** Canonical IDs of everything carried, worn or banked. */
    Set<Integer> owned = new HashSet<>();
    /** Canonical ID to quantity for everything in the inventory and worn, notes unnoted. */
    Map<Integer, Integer> carried = new HashMap<>();
    /** Rune item ID to quantity inside the rune pouch. */
    Map<Integer, Integer> pouch = new LinkedHashMap<>();
    /** 0 standard, 1 ancient, 2 lunar, 3 arceuus. */
    int spellbook;
    /** Real Magic level. */
    int magic;
  }

  /**
   * Must run on the client thread while logged in. {@code owned} holds every item ID seen in the
   * inventory, equipment and (when {@code bankSeen}) the bank.
   */
  static Result scan(Client client, Set<Integer> owned, boolean bankSeen) {
    Result r = new Result();
    r.bankSeen = bankSeen;
    r.owned = owned;
    for (Map.Entry<String, int[]> e : ITEMS.entrySet()) {
      boolean has = false;
      for (int id : e.getValue()) has |= owned.contains(id);
      if (has) r.unlocks.put(e.getKey(), true);
      else if (bankSeen) r.unlocks.put(e.getKey(), false);
    }
    quest(r, "herbPriest", client, Quest.PRIEST_IN_PERIL);
    quest(r, "herbTroll", client, Quest.MY_ARMS_BIG_ADVENTURE);
    quest(r, "herbWeiss", client, Quest.MAKING_FRIENDS_WITH_MY_ARM);
    quest(r, "herbFortis", client, Quest.CHILDREN_OF_THE_SUN);
    quest(r, "varlamore", client, Quest.CHILDREN_OF_THE_SUN);
    quest(r, "gnome", client, Quest.TREE_GNOME_VILLAGE);
    quest(r, "fossil", client, Quest.BONE_VOYAGE);
    quest(r, "locus", client, Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE);
    r.unlocks.put(
        "summer",
        finished(client, Quest.TROUBLED_TORTUGANS) && client.getRealSkillLevel(Skill.AGILITY) >= 50);
    r.unlocks.put("herbHarmony", client.getVarbitValue(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE) == 1);
    r.unlocks.put("faladorElite", client.getVarbitValue(VarbitID.FALADOR_DIARY_ELITE_COMPLETE) == 1);
    r.magic = client.getRealSkillLevel(Skill.MAGIC);
    // The Farming Guild's anima patch (varbit read only while in the guild, as RuneLite's Time
    // Tracking does): 8–15 is a living Attas plant, which boosts herb yield until it dies.
    net.runelite.api.Player player = client.getLocalPlayer();
    if (player != null && GUILD_REGIONS.contains(player.getWorldLocation().getRegionID())) {
      int anima = client.getVarbitValue(VarbitID.FARMING_TRANSMIT_M);
      r.unlocks.put("attas", anima >= 8 && anima <= 15);
    }
    // Herb yield and disease (OSRS wiki herb calculator): Kourend easy protects Hosidius, Kourend
    // hard adds 5% at Hosidius and the Farming Guild, Kandarin adds 5/10/15% at Catherby.
    r.unlocks.put("kourendEasy", client.getVarbitValue(VarbitID.KOUREND_DIARY_EASY_COMPLETE) == 1);
    r.unlocks.put("kourendHard", client.getVarbitValue(VarbitID.KOUREND_DIARY_HARD_COMPLETE) == 1);
    r.unlocks.put("kandarinMedium", client.getVarbitValue(VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE) == 1);
    r.unlocks.put("kandarinHard", client.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1);
    r.unlocks.put("kandarinElite", client.getVarbitValue(VarbitID.KANDARIN_DIARY_ELITE_COMPLETE) == 1);
    // The Lumbridge & Draynor elite diary lets fairy rings work without a staff.
    r.unlocks.put(
        "staffless", client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1);
    QuestState fairy = Quest.FAIRYTALE_II__CURE_A_QUEEN.getState(client);
    if (fairy == QuestState.FINISHED) r.unlocks.put("fairy", true);
    else if (fairy == QuestState.NOT_STARTED) r.unlocks.put("fairy", false);
    // Owning the item is not enough when the destination itself is locked.
    if (!finished(client, Quest.BONE_VOYAGE)) r.unlocks.put("digsite", false);
    if (!finished(client, Quest.MOURNINGS_END_PART_I)) r.unlocks.put("lletya", false);
    if (!finished(client, Quest.GHOSTS_AHOY)) r.unlocks.put("herbEcto", false);
    switch (client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION)) {
      case 0:
        break;
      case 1:
        r.house = "rimmington";
        break;
      case 2:
        r.house = "taverley";
        break;
      case 5:
        r.house = "brimhaven";
        break;
      default:
        r.house = "other";
    }
    return r;
  }

  private static boolean finished(Client client, Quest quest) {
    return quest.getState(client) == QuestState.FINISHED;
  }

  private static void quest(Result r, String key, Client client, Quest quest) {
    r.unlocks.put(key, finished(client, quest));
  }
}
