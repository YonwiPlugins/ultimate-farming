package com.yonwi.farming;

import java.util.*;
import net.runelite.api.gameval.ItemID;

final class FarmingData {
  static final Map<String, List<Crop>> CROPS = new LinkedHashMap<>();
  static final Map<String, int[]> IDS = new LinkedHashMap<>();
  static final List<Patch> PATCHES = new ArrayList<>();

  static {
    CROPS.put(
        "tree",
        Arrays.asList(
            new Crop("Oak", 15, "Oak sapling", 14, 467.3, 0, "", "Tomatoes(5)", 1),
            new Crop("Willow", 30, "Willow sapling", 25, 1456.5, 0, "", "Apples(5)", 1),
            new Crop("Maple", 45, "Maple sapling", 45, 3403.4, 0, "", "Oranges(5)", 1),
            new Crop("Yew", 60, "Yew sapling", 81, 7069.9, 0, "", "Cactus spine", 10),
            new Crop("Magic", 75, "Magic sapling", 145.5, 13768.3, 0, "", "Coconut", 25)));
    CROPS.put(
        "fruit",
        Arrays.asList(
            new Crop("Apple", 27, "Apple sapling", 22, 1199.5, 51, "Cooking apple", "Sweetcorn", 9),
            new Crop("Banana", 33, "Banana sapling", 28, 1750.5, 63, "Banana", "Apples(5)", 4),
            new Crop(
                "Orange", 39, "Orange sapling", 35.5, 2470.2, 81, "Orange", "Strawberries(5)", 3),
            new Crop("Curry", 42, "Curry sapling", 40, 2906.9, 90, "Curry leaf", "Bananas(5)", 5),
            new Crop(
                "Pineapple",
                51,
                "Pineapple sapling",
                57,
                4605.7,
                129,
                "Pineapple",
                "Watermelon",
                10),
            new Crop(
                "Papaya",
                57,
                "Papaya sapling",
                145.5,
                6146.4,
                162,
                "Papaya fruit",
                "Pineapple",
                10),
            new Crop(
                "Palm", 68, "Palm sapling", 110.5, 10150.1, 249, "Coconut", "Papaya fruit", 15),
            new Crop(
                "Dragonfruit",
                81,
                "Dragonfruit sapling",
                140,
                17335,
                350,
                "Dragonfruit",
                "Coconut",
                15)));
    CROPS.put(
        "redwood",
        Arrays.asList(
            new Crop("Redwood", 90, "Redwood sapling", 230, 22450, 0, "", "Dragonfruit", 6)));
    CROPS.put(
        "celastrus",
        Arrays.asList(
            new Crop("Celastrus", 85, "Celastrus sapling", 200, 14130, 0, "", "Potato cactus", 8)));
    CROPS.put(
        "calquat",
        Arrays.asList(
            new Crop(
                "Calquat",
                72,
                "Calquat sapling",
                129.5,
                12096,
                291,
                "Calquat fruit",
                "Poison ivy berries",
                8)));
    CROPS.put(
        "hardwood",
        Arrays.asList(
            new Crop("Teak", 35, "Teak sapling", 35, 7290, 0, "", "Limpwurt root", 15),
            new Crop("Mahogany", 55, "Mahogany sapling", 63, 15720, 0, "", "Yanillian hops", 25),
            new Crop("Camphor", 66, "Camphor sapling", 88, 17840, 0, "", "White berries", 10),
            new Crop("Ironwood", 80, "Ironwood sapling", 145, 20380, 0, "", "Curry leaf", 10),
            new Crop("Rosewood", 92, "Rosewood sapling", 252, 23100, 0, "", "Dragonfruit", 8)));
    IDS.put("Spade", new int[] {ItemID.SPADE});
    IDS.put("Seed dibber", new int[] {ItemID.DIBBER});
    // Any usable axe counts, from bronze to infernal (charged or not) and felling axes.
    IDS.put(
        "Axe",
        // Best first: the bank filter shows only the best axe you own.
        new int[] {
          ItemID.CRYSTAL_AXE, ItemID.CRYSTAL_AXE_2H, ItemID.TRAILBLAZER_RELOADED_AXE,
          ItemID.TRAILBLAZER_AXE, ItemID.INFERNAL_AXE, ItemID._3A_AXE, ItemID._3A_AXE_2H,
          ItemID.DRAGON_AXE, ItemID.DRAGON_AXE_2H, ItemID.TRAIL_GILDED_AXE, ItemID.RUNE_AXE,
          ItemID.RUNE_AXE_2H, ItemID.ADAMANT_AXE, ItemID.ADAMANT_AXE_2H, ItemID.MITHRIL_AXE,
          ItemID.MITHRIL_AXE_2H, ItemID.BLACK_AXE, ItemID.BLACK_AXE_2H, ItemID.STEEL_AXE,
          ItemID.STEEL_AXE_2H, ItemID.IRON_AXE, ItemID.IRON_AXE_2H, ItemID.BRONZE_AXE,
          ItemID.BRONZE_AXE_2H, ItemID.CRYSTAL_AXE_INACTIVE, ItemID.CRYSTAL_AXE_2H_INACTIVE,
          ItemID.TRAILBLAZER_RELOADED_AXE_EMPTY, ItemID.TRAILBLAZER_RELOADED_AXE_NO_INFERNAL,
          ItemID.TRAILBLAZER_AXE_EMPTY, ItemID.TRAILBLAZER_AXE_NO_INFERNAL, ItemID.INFERNAL_AXE_EMPTY
        });
    IDS.put(
        "Digsite pendant",
        new int[] {
          ItemID.NECKLACE_OF_DIGSITE_1,
          ItemID.NECKLACE_OF_DIGSITE_2,
          ItemID.NECKLACE_OF_DIGSITE_3,
          ItemID.NECKLACE_OF_DIGSITE_4,
          ItemID.NECKLACE_OF_DIGSITE_5
        });
    IDS.put("Digsite teleport", new int[] {ItemID.TELEPORTSCROLL_DIGSITE});
    IDS.put("Coins", new int[] {ItemID.COINS, ItemID.FAKE_COINS});
    IDS.put("Rake", new int[] {ItemID.RAKE});
    IDS.put("House teleport", new int[] {ItemID.POH_TABLET_TELEPORTTOHOUSE});
    IDS.put("Civitas illa Fortis teleport", new int[] {ItemID.POH_TABLET_FORTISTELEPORT});
    IDS.put(
        "Farmer's strawhat",
        new int[] {
          ItemID.TITHE_REWARD_HAT_MALE,
          ItemID.TITHE_REWARD_HAT_FEMALE,
          ItemID.TITHE_REWARD_HAT_MALE_DUMMY,
          ItemID.TITHE_REWARD_HAT_FEMALE_DUMMY
        });
    IDS.put(
        "Farmer's jacket",
        new int[] {ItemID.TITHE_REWARD_TORSO_MALE, ItemID.TITHE_REWARD_TORSO_FEMALE});
    IDS.put(
        "Farmer's boro trousers",
        new int[] {ItemID.TITHE_REWARD_LEGS_MALE, ItemID.TITHE_REWARD_LEGS_FEMALE});
    IDS.put(
        "Farmer's boots",
        new int[] {ItemID.TITHE_REWARD_FEET_MALE, ItemID.TITHE_REWARD_FEET_FEMALE});
    IDS.put(
        "Construction cape",
        new int[] {ItemID.SKILLCAPE_CONSTRUCTION, ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED});
    IDS.put("Farming cape", new int[] {ItemID.SKILLCAPE_FARMING, ItemID.SKILLCAPE_FARMING_TRIMMED});
    IDS.put(
        "Ring of wealth",
        // Charged only: an uncharged ring ("Ring of wealth", no number) can't teleport.
        new int[] {
          ItemID.RING_OF_WEALTH_I5,
          ItemID.RING_OF_WEALTH_I4,
          ItemID.RING_OF_WEALTH_I3,
          ItemID.RING_OF_WEALTH_I2,
          ItemID.RING_OF_WEALTH_I1,
          ItemID.RING_OF_WEALTH_5,
          ItemID.RING_OF_WEALTH_4,
          ItemID.RING_OF_WEALTH_3,
          ItemID.RING_OF_WEALTH_2,
          ItemID.RING_OF_WEALTH_1
        });
    IDS.put(
        "Slayer ring",
        new int[] {
          ItemID.SLAYER_RING_8,
          ItemID.SLAYER_RING_7,
          ItemID.SLAYER_RING_6,
          ItemID.SLAYER_RING_5,
          ItemID.SLAYER_RING_4,
          ItemID.SLAYER_RING_3,
          ItemID.SLAYER_RING_2,
          ItemID.SLAYER_RING_1
        });
    IDS.put(
        "Skills necklace",
        new int[] {
          ItemID.JEWL_NECKLACE_OF_SKILLS_6,
          ItemID.JEWL_NECKLACE_OF_SKILLS_5,
          ItemID.JEWL_NECKLACE_OF_SKILLS_4,
          ItemID.JEWL_NECKLACE_OF_SKILLS_3,
          ItemID.JEWL_NECKLACE_OF_SKILLS_2,
          ItemID.JEWL_NECKLACE_OF_SKILLS_1
          // Not JEWL_NECKLACE_OF_SKILLS: that's the uncharged necklace.
        });
    IDS.put(
        "Bottomless compost bucket",
        new int[] {ItemID.BOTTOMLESS_COMPOST_BUCKET, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED});
    IDS.put(
        "Rune pouch",
        new int[] {
          ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER, ItemID.BH_RUNE_POUCH,
          ItemID.BH_RUNE_POUCH_TROUVER
        });
    IDS.put("Law rune", new int[] {ItemID.LAWRUNE});
    IDS.put("Lava rune", new int[] {ItemID.LAVARUNE});
    IDS.put("Mist rune", new int[] {ItemID.MISTRUNE});
    // Resurrect Crops: 8 soul, 12 nature, 8 blood and 25 earth runes per cast.
    IDS.put("Soul rune", new int[] {ItemID.SOULRUNE});
    IDS.put("Nature rune", new int[] {ItemID.NATURERUNE});
    IDS.put("Blood rune", new int[] {ItemID.BLOODRUNE});
    IDS.put("Earth rune", new int[] {ItemID.EARTHRUNE});
    IDS.put(
        "Magic cape",
        new int[] {ItemID.SKILLCAPE_MAGIC, ItemID.SKILLCAPE_MAGIC_TRIMMED, ItemID.SKILLCAPE_MAX});
    IDS.put(
        "Teleport crystal",
        new int[] {
          ItemID.MOURNING_TELEPORT_CRYSTAL_1,
          ItemID.MOURNING_TELEPORT_CRYSTAL_2,
          ItemID.MOURNING_TELEPORT_CRYSTAL_3,
          ItemID.MOURNING_TELEPORT_CRYSTAL_4,
          ItemID.MOURNING_TELEPORT_CRYSTAL_5,
          ItemID.PRIF_TELEPORT_CRYSTAL
        });
    IDS.put("Royal seed pod", new int[] {ItemID.MM2_ROYAL_SEED_POD});
    IDS.put(
        "Dramen or lunar staff",
        new int[] {ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF});
    IDS.put("Lunar staff", new int[] {ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF});
    IDS.put("Pendant of ates", new int[] {ItemID.PENDANT_OF_ATES});
    IDS.put(
        "Quetzal whistle",
        new int[] {
          // Best first: the bank filter shows the best one you own.
          ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE,
          ItemID.HG_QUETZALWHISTLE_PERFECTED,
          ItemID.HG_QUETZALWHISTLE_ENHANCED,
          ItemID.HG_QUETZALWHISTLE_BASIC
        });
    IDS.put("Graceful hood", new int[] {ItemID.GRACEFUL_HOOD});
    IDS.put("Graceful cape", new int[] {ItemID.GRACEFUL_CAPE});
    IDS.put("Graceful top", new int[] {ItemID.GRACEFUL_TOP});
    IDS.put("Graceful legs", new int[] {ItemID.GRACEFUL_LEGS});
    IDS.put("Graceful gloves", new int[] {ItemID.GRACEFUL_GLOVES});
    IDS.put("Graceful boots", new int[] {ItemID.GRACEFUL_BOOTS});
    IDS.put("Oak sapling", new int[] {ItemID.PLANTPOT_OAK_SAPLING});
    IDS.put("Tomatoes(5)", new int[] {ItemID.BASKET_TOMATO_5});
    IDS.put("Willow sapling", new int[] {ItemID.PLANTPOT_WILLOW_SAPLING});
    IDS.put("Apples(5)", new int[] {ItemID.BASKET_APPLE_5});
    IDS.put("Maple sapling", new int[] {ItemID.PLANTPOT_MAPLE_SAPLING});
    IDS.put("Oranges(5)", new int[] {ItemID.BASKET_ORANGE_5});
    IDS.put("Yew sapling", new int[] {ItemID.PLANTPOT_YEW_SAPLING});
    IDS.put("Cactus spine", new int[] {ItemID.CACTUS_SPINE});
    IDS.put("Magic sapling", new int[] {ItemID.PLANTPOT_MAGIC_TREE_SAPLING});
    IDS.put("Coconut", new int[] {ItemID.COCONUT});
    IDS.put("Apple sapling", new int[] {ItemID.PLANTPOT_APPLE_SAPLING});
    IDS.put("Sweetcorn", new int[] {ItemID.SWEETCORN});
    IDS.put("Cooking apple", new int[] {ItemID.COOKING_APPLE});
    IDS.put("Banana sapling", new int[] {ItemID.PLANTPOT_BANANA_SAPLING});
    IDS.put("Banana", new int[] {ItemID.BANANA});
    IDS.put("Orange sapling", new int[] {ItemID.PLANTPOT_ORANGE_SAPLING});
    IDS.put("Strawberries(5)", new int[] {ItemID.BASKET_STRAWBERRY_5});
    IDS.put("Orange", new int[] {ItemID.ORANGE});
    IDS.put("Curry sapling", new int[] {ItemID.PLANTPOT_CURRY_SAPLING});
    IDS.put("Bananas(5)", new int[] {ItemID.BASKET_BANANA_5});
    IDS.put("Curry leaf", new int[] {ItemID.CURRY_LEAF});
    IDS.put("Pineapple sapling", new int[] {ItemID.PLANTPOT_PINEAPPLE_SAPLING});
    IDS.put("Watermelon", new int[] {ItemID.WATERMELON});
    IDS.put("Pineapple", new int[] {ItemID.PINEAPPLE});
    IDS.put("Papaya sapling", new int[] {ItemID.PLANTPOT_PAPAYA_SAPLING});
    IDS.put("Papaya fruit", new int[] {ItemID.PAPAYA});
    IDS.put("Palm sapling", new int[] {ItemID.PLANTPOT_PALM_SAPLING});
    IDS.put("Dragonfruit sapling", new int[] {ItemID.PLANTPOT_DRAGONFRUIT_SAPLING});
    IDS.put("Dragonfruit", new int[] {ItemID.DRAGONFRUIT});
    IDS.put("Redwood sapling", new int[] {ItemID.PLANTPOT_REDWOOD_TREE_SAPLING});
    IDS.put("Celastrus sapling", new int[] {ItemID.PLANTPOT_CELASTRUS_TREE_SAPLING});
    IDS.put("Potato cactus", new int[] {ItemID.CACTUS_POTATO});
    IDS.put("Calquat sapling", new int[] {ItemID.PLANTPOT_CALQUAT_SAPLING});
    IDS.put("Poison ivy berries", new int[] {ItemID.POISONIVY_BERRIES});
    IDS.put("Calquat fruit", new int[] {ItemID.CALQUAT_FRUIT});
    IDS.put("Teak sapling", new int[] {ItemID.PLANTPOT_TEAK_SAPLING});
    IDS.put("Limpwurt root", new int[] {ItemID.LIMPWURT_ROOT});
    IDS.put("Mahogany sapling", new int[] {ItemID.PLANTPOT_MAHOGANY_SAPLING});
    IDS.put("Yanillian hops", new int[] {ItemID.YANILLIAN_HOPS});
    IDS.put("Camphor sapling", new int[] {ItemID.PLANTPOT_CAMPHOR_SAPLING});
    IDS.put("White berries", new int[] {ItemID.WHITE_BERRIES});
    IDS.put("Ironwood sapling", new int[] {ItemID.PLANTPOT_IRONWOOD_SAPLING});
    IDS.put("Rosewood sapling", new int[] {ItemID.PLANTPOT_ROSEWOOD_SAPLING});
    IDS.put("Celastrus bark", new int[] {ItemID.CELASTRUS_WOOD});
    IDS.put("Ultracompost", new int[] {ItemID.BUCKET_ULTRACOMPOST});
    IDS.put("Tai bwo wannai teleport", new int[] {ItemID.TELEPORTSCROLL_TAIBWO});
    IDS.put("Stamina potion(4)", new int[] {ItemID._4DOSESTAMINA});
    IDS.put("Taverley teleport", new int[] {ItemID.NZONE_TELETAB_TAVERLEY});
    IDS.put("Brimhaven teleport", new int[] {ItemID.NZONE_TELETAB_BRIMHAVEN});
    IDS.put("Falador teleport", new int[] {ItemID.POH_TABLET_FALADORTELEPORT});
    IDS.put("Lumbridge teleport", new int[] {ItemID.POH_TABLET_LUMBRIDGETELEPORT});
    IDS.put("Varrock teleport", new int[] {ItemID.POH_TABLET_VARROCKTELEPORT});
    IDS.put("Catherby teleport", new int[] {ItemID.LUNAR_TABLET_CATHERBY_TELEPORT});
    IDS.put("Camelot teleport", new int[] {ItemID.POH_TABLET_CAMELOTTELEPORT});
    IDS.put("Ardougne teleport", new int[] {ItemID.POH_TABLET_ARDOUGNETELEPORT});
    IDS.put("Farmer's shirt", new int[] {ItemID.TITHE_REWARD_TORSO_FEMALE});
    IDS.put("Eternal teleport crystal", new int[] {ItemID.PRIF_TELEPORT_CRYSTAL});
    PATCHES.add(new Patch("falador", "Falador Park", "tree"));
    PATCHES.add(new Patch("taverley", "Taverley", "tree"));
    PATCHES.add(new Patch("lumbridge", "Lumbridge", "tree"));
    PATCHES.add(new Patch("varrock", "Varrock Palace", "tree"));
    PATCHES.add(new Patch("strongtree", "Gnome Stronghold", "tree"));
    PATCHES.add(new Patch("strongfruit", "Gnome Stronghold", "fruit"));
    PATCHES.add(new Patch("village", "Tree Gnome Village", "fruit"));
    PATCHES.add(new Patch("guildtree", "Farming Guild", "tree"));
    PATCHES.add(new Patch("guildfruit", "Farming Guild", "fruit"));
    PATCHES.add(new Patch("catherby", "Catherby", "fruit"));
    PATCHES.add(new Patch("brimhaven", "Brimhaven", "fruit"));
    PATCHES.add(new Patch("lletya", "Lletya", "fruit"));
    PATCHES.add(new Patch("auburn", "Auburn Valley", "tree"));
    PATCHES.add(new Patch("kastori", "Kastori", "fruit"));
    PATCHES.add(new Patch("guildredwood", "Farming Guild", "redwood"));
    PATCHES.add(new Patch("guildcelastrus", "Farming Guild", "celastrus"));
    PATCHES.add(new Patch("calquat", "Tai Bwo Wannai", "calquat"));
    PATCHES.add(new Patch("kastoricalquat", "Kastori", "calquat"));
    PATCHES.add(new Patch("summercalquat", "Summer Shore", "calquat"));
    PATCHES.add(new Patch("fossil1", "Fossil Island · patch 1", "hardwood"));
    PATCHES.add(new Patch("fossil2", "Fossil Island · patch 2", "hardwood"));
    PATCHES.add(new Patch("fossil3", "Fossil Island · patch 3", "hardwood"));
    PATCHES.add(new Patch("locus", "Locus Oasis", "hardwood"));
    PATCHES.add(new Patch("anglers", "Anglers’ Retreat", "hardwood"));
  }

  static {
    CROPS.put("herb", Arrays.asList(new Crop("Guam", 9, "Guam seed", 11, 0, 12.5, "Grimy guam leaf", "", 0),
new Crop("Marrentill", 14, "Marrentill seed", 13.5, 0, 15, "Grimy marrentill", "", 0),
new Crop("Tarromin", 19, "Tarromin seed", 16, 0, 18, "Grimy tarromin", "", 0),
new Crop("Harralander", 26, "Harralander seed", 21.5, 0, 24, "Grimy harralander", "", 0),
new Crop("Ranarr", 32, "Ranarr seed", 27, 0, 30.5, "Grimy ranarr weed", "", 0),
new Crop("Toadflax", 38, "Toadflax seed", 34, 0, 38.5, "Grimy toadflax", "", 0),
new Crop("Irit", 44, "Irit seed", 43, 0, 48.5, "Grimy irit leaf", "", 0),
new Crop("Avantoe", 50, "Avantoe seed", 54.5, 0, 61.5, "Grimy avantoe", "", 0),
new Crop("Kwuarm", 56, "Kwuarm seed", 69, 0, 78, "Grimy kwuarm", "", 0),
new Crop("Snapdragon", 62, "Snapdragon seed", 87.5, 0, 98.5, "Grimy snapdragon", "", 0),
new Crop("Cadantine", 67, "Cadantine seed", 106.5, 0, 120, "Grimy cadantine", "", 0),
new Crop("Lantadyme", 73, "Lantadyme seed", 134.5, 0, 151.5, "Grimy lantadyme", "", 0),
new Crop("Dwarf weed", 79, "Dwarf weed seed", 170.5, 0, 192, "Grimy dwarf weed", "", 0),
new Crop("Torstol", 85, "Torstol seed", 199.5, 0, 224.5, "Grimy torstol", "", 0)));
IDS.put("Guam seed", new int[] {ItemID.GUAM_SEED});
IDS.put("Grimy guam leaf", new int[] {ItemID.UNIDENTIFIED_GUAM});
IDS.put("Marrentill seed", new int[] {ItemID.MARRENTILL_SEED});
IDS.put("Grimy marrentill", new int[] {ItemID.UNIDENTIFIED_MARENTILL});
IDS.put("Tarromin seed", new int[] {ItemID.TARROMIN_SEED});
IDS.put("Grimy tarromin", new int[] {ItemID.UNIDENTIFIED_TARROMIN});
IDS.put("Harralander seed", new int[] {ItemID.HARRALANDER_SEED});
IDS.put("Grimy harralander", new int[] {ItemID.UNIDENTIFIED_HARRALANDER});
IDS.put("Ranarr seed", new int[] {ItemID.RANARR_SEED});
IDS.put("Grimy ranarr weed", new int[] {ItemID.UNIDENTIFIED_RANARR});
IDS.put("Toadflax seed", new int[] {ItemID.TOADFLAX_SEED});
IDS.put("Grimy toadflax", new int[] {ItemID.UNIDENTIFIED_TOADFLAX});
IDS.put("Irit seed", new int[] {ItemID.IRIT_SEED});
IDS.put("Grimy irit leaf", new int[] {ItemID.UNIDENTIFIED_IRIT});
IDS.put("Avantoe seed", new int[] {ItemID.AVANTOE_SEED});
IDS.put("Grimy avantoe", new int[] {ItemID.UNIDENTIFIED_AVANTOE});
IDS.put("Kwuarm seed", new int[] {ItemID.KWUARM_SEED});
IDS.put("Grimy kwuarm", new int[] {ItemID.UNIDENTIFIED_KWUARM});
IDS.put("Snapdragon seed", new int[] {ItemID.SNAPDRAGON_SEED});
IDS.put("Grimy snapdragon", new int[] {ItemID.UNIDENTIFIED_SNAPDRAGON});
IDS.put("Cadantine seed", new int[] {ItemID.CADANTINE_SEED});
IDS.put("Grimy cadantine", new int[] {ItemID.UNIDENTIFIED_CADANTINE});
IDS.put("Lantadyme seed", new int[] {ItemID.LANTADYME_SEED});
IDS.put("Grimy lantadyme", new int[] {ItemID.UNIDENTIFIED_LANTADYME});
IDS.put("Dwarf weed seed", new int[] {ItemID.DWARF_WEED_SEED});
IDS.put("Grimy dwarf weed", new int[] {ItemID.UNIDENTIFIED_DWARF_WEED});
IDS.put("Torstol seed", new int[] {ItemID.TORSTOL_SEED});
IDS.put("Grimy torstol", new int[] {ItemID.UNIDENTIFIED_TORSTOL});
IDS.put("Magic secateurs", new int[] {ItemID.FAIRY_ENCHANTED_SECATEURS});
IDS.put("Seed dibber", new int[] {ItemID.DIBBER});
// Empty for a few seconds after teleporting, while it refills at the Ectofuntus.
IDS.put("Ectophial", new int[] {ItemID.ECTOPHIAL, ItemID.ECTOPHIAL_EMPTY});
IDS.put("Stony basalt", new int[] {ItemID.STRONGHOLD_TELEPORT_BASALT});
IDS.put("Icy basalt", new int[] {ItemID.WEISS_TELEPORT_BASALT});
IDS.put("Harmony island teleport", new int[] {ItemID.TELETAB_HARMONY});
IDS.put("Trollheim teleport", new int[] {ItemID.NZONE_TELETAB_TROLLHEIM});
IDS.put("Xeric's talisman", new int[] {ItemID.XERIC_TALISMAN});
IDS.put("Explorer's ring", new int[] {ItemID.LUMBRIDGE_RING_EASY, ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.LUMBRIDGE_RING_HARD, ItemID.LUMBRIDGE_RING_ELITE});
IDS.put("Ardougne cloak", new int[] {ItemID.ARDY_CAPE_EASY, ItemID.ARDY_CAPE_MEDIUM, ItemID.ARDY_CAPE_HARD, ItemID.ARDY_CAPE_ELITE});
PATCHES.add(new Patch("herbtroll", "Troll Stronghold", "herb"));
PATCHES.add(new Patch("herbweiss", "Weiss", "herb"));
PATCHES.add(new Patch("herbharmony", "Harmony Island", "herb"));
PATCHES.add(new Patch("herbardougne", "Ardougne", "herb"));
PATCHES.add(new Patch("herbhosidius", "Hosidius", "herb"));
PATCHES.add(new Patch("herbphasmatys", "Port Phasmatys", "herb"));
PATCHES.add(new Patch("herbfalador", "Falador", "herb"));
PATCHES.add(new Patch("herbfortis", "Civitas Fortis", "herb"));
PATCHES.add(new Patch("herbcatherby", "Catherby", "herb"));
PATCHES.add(new Patch("herbguild", "Farming Guild", "herb"));
 IDS.put("Spirit sapling", new int[] {ItemID.PLANTPOT_SPIRIT_TREE_SAPLING});
  }

  /**
   * Growth per crop: {minutes per growth tick, number of stages}, from RuneLite's Time Tracking
   * plugin (timetracking/farming/Produce.java). A crop needs (stages - 1) ticks to grow.
   */
  static final Map<String, int[]> GROWTH = new HashMap<>();

  static {
    for (Crop c : CROPS.get("herb")) GROWTH.put(c.name, new int[] {20, 5});
    int[][] trees = {{40, 5}, {40, 7}, {40, 9}, {40, 11}, {40, 13}};
    String[] treeNames = {"Oak", "Willow", "Maple", "Yew", "Magic"};
    for (int i = 0; i < trees.length; i++) GROWTH.put(treeNames[i], trees[i]);
    for (Crop c : CROPS.get("fruit")) GROWTH.put(c.name, new int[] {160, 7});
    GROWTH.put("Calquat", new int[] {160, 9});
    GROWTH.put("Celastrus", new int[] {160, 6});
    GROWTH.put("Redwood", new int[] {640, 11});
    GROWTH.put("Teak", new int[] {640, 8});
    GROWTH.put("Mahogany", new int[] {640, 9});
    GROWTH.put("Camphor", new int[] {640, 9});
    GROWTH.put("Ironwood", new int[] {640, 9});
    GROWTH.put("Rosewood", new int[] {640, 10});
  }

  /**
   * When a crop planted at {@code planted} should be fully grown. Growth ticks fall on fixed
   * clock boundaries, so the first one comes at the next boundary. Approximate: the game adds a
   * per-account offset.
   */
  static long readyAt(String crop, long planted) {
    int[] g = GROWTH.get(crop);
    if (g == null) return planted;
    long tick = g[0] * 60_000L;
    long firstTick = (planted / tick + 1) * tick;
    return firstTick + (g[1] - 2) * tick;
  }

  /**
   * The variant of this item to show: the first in its best-first list that you own (bank,
   * inventory or worn), e.g. your infernal axe. If you own none: a bronze axe for "Axe" (the one to
   * get), otherwise the usual picture.
   */
  static int bestOwned(String name, Set<Integer> owned) {
    int[] ids = IDS.get(name);
    if (ids != null && owned != null) for (int id : ids) if (owned.contains(id)) return id;
    if (name.equals("Axe")) return ItemID.BRONZE_AXE;
    return id(name);
  }

  static int id(String name) {
    if (name.equals("Farming cape")) return ItemID.SKILLCAPE_FARMING_TRIMMED;
    if (name.equals("Construction cape")) return ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED;
    if (name.equals("Teleport crystal")) return ItemID.PRIF_TELEPORT_CRYSTAL;
    if (name.equals("Bottomless compost bucket")) return ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
    // The divine pouch is listed first for loading, but the plain pouch is the usual picture.
    if (name.equals("Rune pouch")) return ItemID.BH_RUNE_POUCH;
    int[] values = IDS.get(name);
    return values == null || values.length == 0 ? 0 : values[0];
  }
}
