package com.yonwi.farming;

import static com.yonwi.farming.PanelWidgets.CARD_TEXT;
import static com.yonwi.farming.PanelWidgets.addCard;
import static com.yonwi.farming.PanelWidgets.collapsibleHeading;
import static com.yonwi.farming.PanelWidgets.column;
import static com.yonwi.farming.PanelWidgets.fixHeight;
import static com.yonwi.farming.PanelWidgets.html;
import static com.yonwi.farming.PanelWidgets.label;
import static com.yonwi.farming.PanelWidgets.muted;
import static com.yonwi.farming.PanelWidgets.onClick;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The Setup tab, in two pages: 1 · Travel (what was detected, and tickboxes for what couldn't be)
 * and 2 · Your kit (outfit, tools, Resurrect Crops and the yield estimates).
 */
final class SetupTab {
  private static final int TRAVEL = 0;
  private static final int KIT = 1;
  private static final String[] PAGE_TITLES = {"1 · Travel", "2 · Your kit"};

  /** Every travel unlock: {key, name, what it means}. */
  private static final String[][] UNLOCKS = {
    {"herbPriest", "Port Phasmatys unlocked", "Priest in Peril and farm access."},
    {"herbTroll", "Troll Stronghold patch", "My Arm’s Big Adventure completed."},
    {"herbWeiss", "Weiss patch", "Making Friends with My Arm completed."},
    {"herbHarmony", "Harmony Island patch", "Morytania elite diary completed."},
    {"herbFortis", "Civitas Fortis patch", "Varlamore farm access."},
    {"fortisGlory", "Civitas illa Fortis patch is disease-free", "16,000 Glory in the Fortis Colosseum."},
    {"attas", "Attas planted at the Farming Guild", "Detected when you're at the guild. +5% herbs while it lives."},
    {"kourendEasy", "Kourend easy diary", "Hosidius herb patch is disease-free."},
    {"kourendHard", "Kourend hard diary", "+5% herbs at Hosidius and the Farming Guild."},
    {"kandarinMedium", "Kandarin medium diary", "+5% herbs at Catherby."},
    {"kandarinHard", "Kandarin hard diary", "+10% herbs at Catherby."},
    {"kandarinElite", "Kandarin elite diary", "+15% herbs at Catherby."},
    {"herbExplorer", "Explorer’s ring farm teleport", "Requires cabbage teleport."},
    {"herbArdy", "Ardougne cloak farm teleport", "Requires farm teleport charges."},
    {"herbXeric", "Xeric’s talisman", "Xeric’s Glade teleport available."},
    {"herbEcto", "Ectophial", "Ghosts Ahoy completed."},
    {"herbBasalt", "Stony and icy basalt", "Bring consumable basalts."},
    {"herbTrollheim", "Trollheim tablet", "Requires teleport unlock."},
    {"herbHarmonyTab", "Harmony island tablet", "Bring Harmony island teleport."},
    {"herbSack", "Herb sack", "Open, it holds 30 of each herb before your inventory, so less noting."},
    {"gnome", "Spirit tree network", "Tree Gnome Village and Gnome Stronghold access."},
    {"lletya", "Lletya / eternal teleport crystal", "Lletya unlocked. Bring charged or eternal teleport crystal."},
    {"jewellery", "House jewellery box", "House entry followed by jewellery teleports."},
    {"spirit", "Spirit tree in house", "House entry followed by a spirit tree."},
    {"cape", "Construction cape", "House portals at Taverley or Brimhaven, plus house entry."},
    {"farmingCape", "Farming cape", "Farming Guild teleport. Requires level 99."},
    {
      "pouch",
      "Standard spellbook rune pouch",
      "Law, lava and mist runes. Selected spells require their Magic levels and quest unlocks."
    },
    {"wealth", "Ring of wealth", "Falador Park teleport. Requires charges."},
    {"faladorElite", "Falador elite diary", "Falador Park tree can't get diseased: no payment or compost."},
    {"slayer", "Slayer ring", "Stronghold Slayer Cave teleport."},
    {"skills", "Skills necklace", "Farming Guild teleport."},
    {"catherby", "Catherby teleport", "Bring a Catherby teleport tablet, or use your house portal."},
    {"redirect", "Redirected house tablets", "Taverley and Brimhaven house tablets."},
    {"catherbyPortal", "Catherby Teleport in portal nexus", "Your portal nexus has Catherby Teleport."},
    {"fairy", "Fairy rings", "Bring dramen staff unless your unlocks waive it."},
    {
      "houseFairy",
      "Fairy ring in house",
      "Fairy ring or spiritual fairy tree in your house: fairy trips start with a house teleport."
    },
    {"varlamore", "Auburn Valley unlocked", "Children of the Sun completed (Varlamore)."},
    {
      "kastori",
      "Kastori unlocked",
      "You can reach Kastori in southern Varlamore: its quetzal landing site built, its Pendant"
          + " of ates statue activated, or by Achilka's boat."
    },
    {"pendant", "Pendant of ates", "Nemus Retreat and Kastori teleport options."},
    {"quetzal", "Quetzal landing sites", "Required landing sites must already be built."},
    {"whistle", "Quetzal whistle", "Bring a charged whistle."},
    {"fossil", "Fossil Island unlocked", "Requires Bone Voyage."},
    {"digsite", "Digsite pendant / mushtree", "Fossil Island and Mushroom Meadow unlocked."},
    {"summer", "Summer Shore unlocked", "Troubled Tortugans, CJQ and 50 Agility."},
    {"locus", "Locus Oasis unlocked", "Ribbiting Tale completed."},
    {"anglers", "Anglers’ Retreat rowboat", "Rowboat built, 51 Sailing and 50 Construction."}
  };

  /** Short name and picture for each detected unlock: {key, name, item}. */
  private static final String[][] UNLOCK_LOOKS = {
    {"herbPriest", "Port Phasmatys", "Ectophial"},
    {"herbTroll", "Troll Stronghold", "Stony basalt"},
    {"herbWeiss", "Weiss", "Icy basalt"},
    {"herbHarmony", "Harmony Island", "Harmony island teleport"},
    {"herbFortis", "Civitas Fortis", "Civitas illa Fortis teleport"},
    {"fortisGlory", "Fortis disease-free", "Civitas illa Fortis teleport"},
    {"attas", "Attas plant", "Magic secateurs"},
    {"kourendEasy", "Kourend easy", "Xeric's talisman"},
    {"kourendHard", "Kourend hard", "Xeric's talisman"},
    {"kandarinMedium", "Kandarin medium", "Camelot teleport"},
    {"kandarinHard", "Kandarin hard", "Camelot teleport"},
    {"kandarinElite", "Kandarin elite", "Camelot teleport"},
    {"herbExplorer", "Explorer’s ring", "Explorer's ring"},
    {"herbArdy", "Ardougne cloak", "Ardougne cloak"},
    {"herbXeric", "Xeric’s talisman", "Xeric's talisman"},
    {"herbEcto", "Ectophial", "Ectophial"},
    {"herbBasalt", "Basalts", "Stony basalt"},
    {"herbTrollheim", "Trollheim tablet", "Trollheim teleport"},
    {"herbHarmonyTab", "Harmony tablet", "Harmony island teleport"},
    {"herbSack", "Herb sack", "Herb sack"},
    {"gnome", "Spirit trees", "Spirit sapling"},
    {"lletya", "Teleport crystal", "Teleport crystal"},
    {"jewellery", "Jewellery box", "Skills necklace"},
    {"spirit", "House spirit tree", "Spirit sapling"},
    {"cape", "Construction cape", "Construction cape"},
    {"farmingCape", "Farming cape", "Farming cape"},
    {"pouch", "Rune pouch", "Rune pouch"},
    {"wealth", "Ring of wealth", "Ring of wealth"},
    {"faladorElite", "Falador elite", "Falador teleport"},
    {"slayer", "Slayer ring", "Slayer ring"},
    {"skills", "Skills necklace", "Skills necklace"},
    {"catherby", "Catherby tablet", "Catherby teleport"},
    {"redirect", "House tablets", "House teleport"},
    {"catherbyPortal", "Nexus: Catherby", "House teleport"},
    {"fairy", "Fairy rings", "Dramen or lunar staff"},
    {"houseFairy", "House fairy ring", "Dramen or lunar staff"},
    {"varlamore", "Auburn Valley", "Pendant of ates"},
    {"kastori", "Kastori", "Pendant of ates"},
    {"pendant", "Pendant of ates", "Pendant of ates"},
    {"quetzal", "Quetzal sites", "Quetzal whistle"},
    {"whistle", "Quetzal whistle", "Quetzal whistle"},
    {"fossil", "Fossil Island", "Digsite pendant"},
    {"digsite", "Digsite pendant", "Digsite pendant"},
    {"summer", "Summer Shore", "Calquat sapling"},
    {"locus", "Locus Oasis", "Teak sapling"},
    {"anglers", "Anglers’ Retreat", "Mahogany sapling"}
  };

  private static final String[] HOUSES = {"brimhaven", "taverley", "rimmington", "other"};

  private final FarmingPanel panel;
  private final JPanel page = column();
  private int pageShown = TRAVEL;
  /** "Detected on your account" is shown (it starts collapsed each session). */
  private boolean detectedOpen;

  SetupTab(FarmingPanel panel) {
    this.panel = panel;
  }

  JPanel page() {
    return page;
  }

  void reset() {
    pageShown = TRAVEL;
  }

  /** The Travel page is showing, which lists what was detected on the account. */
  boolean showsTravel() {
    return pageShown == TRAVEL;
  }

  void render() {
    page.removeAll();
    JLabel title = label(PAGE_TITLES[pageShown]);
    title.setFont(Style.bold());
    page.add(title);
    page.add(Box.createVerticalStrut(6));
    if (panel.runStarted()) page.add(muted("Finish or reset your run to change setup."));
    if (pageShown == TRAVEL) renderTravel();
    else renderKit();
    renderNavigation();
  }

  /** Page 1: tickboxes for what can't be detected, then what was. */
  private void renderTravel() {
    AccountScan.Result account = panel.account();
    Map<String, Boolean> known = account == null ? Collections.emptyMap() : account.unlocks;
    if (account == null)
      page.add(muted("Log in to detect travel options from your quests, diaries, bank and gear."));
    else page.add(Style.section("Choose what can’t be detected"));
    JPanel unlockCard = Style.card();
    for (String[] unlock : UNLOCKS) if (!known.containsKey(unlock[0])) unlockCard.add(unlockBox(unlock));
    if (account == null || account.house == null) addHouseChoice(unlockCard);
    addCard(page, unlockCard);
    // What the account already has, below the choices: there's nothing to do with these.
    if (account != null) addDetected(account, known);
  }

  private JCheckBox unlockBox(String[] unlock) {
    String key = unlock[0];
    Settings settings = panel.settings();
    JCheckBox box =
        panel.settingBox(
            unlock[1],
            settings.has(key),
            on -> {
              if (on) settings.unlocks.add(key);
              else settings.unlocks.remove(key);
            });
    box.setToolTipText(unlock[2]);
    return box;
  }

  private void addHouseChoice(JPanel card) {
    Settings settings = panel.settings();
    card.add(Box.createVerticalStrut(6));
    card.add(muted("House location"));
    JComboBox<String> house = new JComboBox<>(HOUSES);
    Style.combo(house);
    house.setSelectedItem(settings.house);
    house.setEnabled(!panel.runStarted());
    house.addActionListener(e -> panel.change(() -> settings.house = (String) house.getSelectedItem()));
    card.add(house);
  }

  private void addDetected(AccountScan.Result account, Map<String, Boolean> known) {
    page.add(
        collapsibleHeading(
            Style.section("Detected on your account"),
            detectedOpen,
            "Hide",
            "Show what was detected",
            () -> {
              detectedOpen = !detectedOpen;
              render();
            }));
    if (!detectedOpen) return;
    boolean anyFound = false;
    for (String[] unlock : UNLOCKS) anyFound |= Boolean.TRUE.equals(known.get(unlock[0]));
    JPanel detected = Style.card();
    if (!anyFound && account.house == null) detected.add(muted("Nothing yet."));
    else detected.add(detectedGrid(known, account.house));
    if (!account.bankSeen) detected.add(muted("Open your bank once so items stored there can be detected."));
    addCard(page, detected);
  }

  /** Two columns of detected unlocks: the item's picture and a short name. */
  private JPanel detectedGrid(Map<String, Boolean> known, String house) {
    Map<String, String[]> looks = new HashMap<>();
    for (String[] look : UNLOCK_LOOKS) looks.put(look[0], look);
    JPanel grid = new JPanel(new GridLayout(0, 2, 4, 3));
    grid.setOpaque(false);
    for (String[] unlock : UNLOCKS) {
      if (!Boolean.TRUE.equals(known.get(unlock[0]))) continue;
      String[] look = looks.getOrDefault(unlock[0], new String[] {unlock[0], unlock[1], ""});
      grid.add(detectedCell(look[1], look[2], unlock[1] + ". " + unlock[2]));
    }
    // Your house's location sits with the rest, as one more detected thing.
    if (house != null)
      grid.add(
          detectedCell(
              Character.toUpperCase(house.charAt(0)) + house.substring(1) + " house",
              "House teleport",
              "Your house portal is in " + house));
    grid.setAlignmentX(Component.LEFT_ALIGNMENT);
    return grid;
  }

  /** One detected unlock: its item picture and a short name (the ✓ is the card's heading). */
  private JPanel detectedCell(String text, String itemName, String tip) {
    JPanel cell = new JPanel(new BorderLayout(3, 0));
    cell.setOpaque(false);
    JLabel item = new JLabel();
    item.setPreferredSize(new Dimension(18, 16));
    if (!itemName.isEmpty()) panel.pictures().small(item, itemName, 18, 16);
    JLabel name = new JLabel("<html><div style=\"width:52px\">" + html(text) + "</div></html>");
    name.setFont(Style.small());
    name.setForeground(Style.INK);
    cell.setToolTipText(tip);
    cell.add(item, BorderLayout.WEST);
    cell.add(name, BorderLayout.CENTER);
    return cell;
  }

  /** Page 2: outfit, tools and Resurrect Crops, and the yield estimates. */
  private void renderKit() {
    page.add(Style.section("Options"));
    addCard(page, optionsCard());
    page.add(Style.section("Estimates"));
    JPanel estimates = estimatesCard();
    if (estimates.getComponentCount() > 0) addCard(page, estimates);
  }

  private JPanel optionsCard() {
    Settings settings = panel.settings();
    List<FarmingPlanner.Step> route = panel.route();
    JPanel options = Style.card();
    options.add(
        iconCheck("Farmer’s outfit (otherwise Graceful)", "Farmer's jacket", settings.farmer, on -> settings.farmer = on));
    options.add(
        iconCheck(
            "Bottomless ultracompost bucket", "Bottomless compost bucket", settings.bucket, on -> settings.bucket = on));
    options.add(iconCheck("Magic secateurs", "Magic secateurs", settings.secateurs, on -> settings.secateurs = on));
    options.add(
        iconCheck(
            "Resurrect Crops on dead herbs (Arceuus, 78 Magic)",
            "Soul rune",
            settings.resurrect,
            on -> settings.resurrect = on));
    if (settings.resurrect && !settings.resurrects(route)) {
      String why = whyNoResurrect(settings, route);
      if (!why.isEmpty()) options.add(muted(why));
    }
    options.add(
        iconCheck("Stamina potion (runs of 6+ patches)", "Stamina potion(4)", settings.stamina, on -> settings.stamina = on));
    return options;
  }

  private static String whyNoResurrect(Settings settings, List<FarmingPlanner.Step> route) {
    if (settings.magic > 0 && settings.magic < 78) return "Needs 78 Magic.";
    if (Settings.usesSpells(route)) return "Off for this route: it casts standard spellbook teleports.";
    if (route.stream().noneMatch(step -> step.patch.kind.equals("herb"))) return "Only used on herb runs.";
    return settings.magic == 0 ? "Log in to check your Magic level." : "";
  }

  private JPanel estimatesCard() {
    Settings settings = panel.settings();
    List<FarmingPlanner.Step> route = panel.route();
    JPanel estimates = Style.card();
    // Herbs per patch is worked out with the OSRS wiki's herb calculator formula.
    List<FarmingPlanner.Step> herbSteps = new ArrayList<>();
    for (FarmingPlanner.Step step : route) if (step.patch.kind.equals("herb")) herbSteps.add(step);
    if (!herbSteps.isEmpty()) {
      boolean cape = FarmingPlanner.farmingCapeWorn(route, settings);
      double total = 0;
      for (FarmingPlanner.Step step : herbSteps) total += FarmingPlanner.expectedHerbs(step.patch, settings, cape);
      JLabel herbs =
          muted(String.format(Locale.ROOT, "Herbs per patch: %.1f on average", total / herbSteps.size()));
      herbs.setForeground(Style.INK);
      herbs.setToolTipText(
          "<html>OSRS wiki herb calculator: your Farming level, ultracompost,"
              + (settings.secateurs ? " magic secateurs," : "")
              + (cape ? " farming cape," : "")
              + " diary bonuses<br>(Kandarin at Catherby, Kourend at Hosidius and the guild)"
              + " and disease-free patches.<br>Dead patches are counted in.</html>");
      estimates.add(herbs);
      estimates.add(Box.createVerticalStrut(6));
    }
    if (route.stream().anyMatch(step -> step.patch.kind.equals("celastrus"))) {
      JLabel bark =
          muted(String.format(Locale.ROOT, "Celastrus bark per patch: %.1f on average", FarmingPlanner.bark(settings)));
      bark.setForeground(Style.INK);
      bark.setToolTipText("From your Farming level, as in the OSRS wiki's money making guide (8.8 at 99)");
      estimates.add(bark);
    }
    return estimates;
  }

  /** A tickbox row with the item's picture between the box and its text. */
  private JPanel iconCheck(String text, String item, boolean selected, Consumer<Boolean> apply) {
    JCheckBox box = Style.tickbox(new JCheckBox("", selected));
    box.setOpaque(false);
    box.setEnabled(!panel.runStarted());
    box.addActionListener(e -> panel.change(() -> apply.accept(box.isSelected())));
    JLabel picture = new JLabel();
    picture.setPreferredSize(new Dimension(24, 22));
    panel.pictures().small(picture, item, 24, 21);
    JLabel words = label("<html><div style=\"width:" + (CARD_TEXT - 50) + "px\">" + html(text) + "</div></html>");
    words.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    onClick(
        words,
        () -> {
          if (box.isEnabled()) box.doClick();
        });
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    left.setOpaque(false);
    left.add(box);
    left.add(picture);
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.setOpaque(false);
    row.setBorder(new EmptyBorder(2, 0, 2, 0));
    row.add(left, BorderLayout.WEST);
    row.add(words, BorderLayout.CENTER);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    fixHeight(row);
    return row;
  }

  /** Back / Next under the pages; "Done" on the last goes back to the Run tab. */
  private void renderNavigation() {
    JPanel navigation = new JPanel(new GridLayout(1, 2, 5, 5));
    navigation.setOpaque(false);
    JButton back = new JButton("Back");
    JButton next = new JButton(pageShown == KIT ? "Done" : "Next");
    back.setEnabled(pageShown > TRAVEL);
    back.addActionListener(
        e -> {
          pageShown--;
          render();
        });
    next.addActionListener(
        e -> {
          if (pageShown < KIT) {
            pageShown++;
            render();
          } else panel.setupDone();
        });
    navigation.add(back);
    navigation.add(next);
    page.add(Box.createVerticalStrut(10));
    fixHeight(navigation);
    page.add(navigation);
    page.add(Box.createVerticalGlue());
    page.revalidate();
    page.repaint();
  }
}
