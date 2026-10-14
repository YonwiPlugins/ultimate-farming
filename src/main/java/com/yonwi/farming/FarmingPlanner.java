package com.yonwi.farming;

import java.util.*;
import java.util.function.ToLongFunction;

final class FarmingPlanner {
  static final class Choice {
    final String text;
    final double cost;
    final List<String> items, gear;

    Choice(String text, double cost, List<String> items, List<String> gear) {
      this.text = text;
      this.cost = cost;
      this.items = items;
      this.gear = gear;
    }
  }

  static final class Step {
    final Patch patch;
    final Choice travel;
    boolean noteBefore, noteAfter = true;

    Step(Patch p, Choice t) {
      patch = p;
      travel = t;
    }
  }

  static final class Kit {
    final List<String> wear = new ArrayList<>();
    final Map<String, Integer> bring = new LinkedHashMap<>();

    void add(String n, int q) {
      bring.merge(n, q, Integer::sum);
    }
  }

  static void add(List<Choice> o, Settings s, String text, double cost) {
    add(o, s, text, cost, new String[0], new String[0]);
  }

  static void add(List<Choice> o, Settings s, String text, double cost, String[] items) {
    add(o, s, text, cost, items, new String[0]);
  }

  static void add(
      List<Choice> o, Settings s, String text, double cost, String[] items, String[] gear) {
    List<String> supply = new ArrayList<>(Arrays.asList(items));
    if (s.has("pouch"))
      for (String spell : Arrays.asList("Falador", "Lumbridge", "Varrock", "Camelot", "Ardougne"))
        if (supply.remove(spell + " teleport tablets")) {
          supply.add("Rune pouch");
          text = text.replace(spell + " teleport", "Cast " + spell + " Teleport");
          cost = Math.max(.3, cost - .12);
        }
    List<String> worn = new ArrayList<>(Arrays.asList(gear));
    if (s.has("staffless")) worn.remove("Dramen/lunar staff if required");
    // With a fairy ring in the house, every fairy ring trip starts with a house teleport.
    if (s.has("houseFairy") && text.startsWith("Fairy ring ")) {
      text = "House teleport → " + text;
      if (s.has("cape")) worn.add("Construction cape");
      else supply.add(s.has("pouch") ? "Rune pouch" : "House teleport tablets");
    }
    o.add(new Choice(text, cost, supply, worn));
  }

  /** A spirit tree in the house: every spirit tree trip starts there. */
  static boolean houseTree(Settings s) {
    return s.poh() && s.has("spirit");
  }

  /** The three-letter fairy ring code in a travel line, or null. */
  static String fairyCode(String travel) {
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("Fairy ring ([A-Z]{3})").matcher(travel);
    return m.find() ? m.group(1) : null;
  }

  static void house(List<Choice> o, Settings s, String text, double cost) {
    poh(o, s, text, cost);
  }

  static void poh(List<Choice> o, Settings s, String text, double cost) {
    add(
        o,
        s,
        text,
        s.has("cape") ? Math.max(.3, cost - .16) : cost,
        s.has("cape")
            ? new String[0]
            : new String[] {s.has("pouch") ? "Rune pouch" : "House teleport tablets"},
        s.has("cape") ? new String[] {"Construction cape"} : new String[0]);
  }

  static List<Choice> choices(Patch p, Settings s) {
    List<Choice> o = new ArrayList<>();
    if (p.kind.equals("herb")) {
      switch (p.id) {
        case "herbfalador":
          if (s.has("herbExplorer")) add(o,s,"Explorer's ring → cabbage patch",.5,new String[]{},new String[]{"Explorer's ring"});
          add(o,s,"Falador teleport → south to farm",2,new String[]{"Falador teleport tablets"}); break;
        case "herbardougne":
          if(s.has("herbArdy")) add(o,s,"Ardougne cloak → farm",.5,new String[]{"Ardougne cloak"});
          add(o,s,"Ardougne teleport → north to farm",2,new String[]{"Ardougne teleport tablets"}); break;
        case "herbcatherby":
          if(s.has("catherbyPortal")) poh(o,s,"House teleport → Portal nexus: Catherby, then north-west to herb patch",.6);
          if(s.has("catherby")) add(o,s,"Catherby teleport → north-west to herb patch",.5,new String[]{"Catherby teleport tablets"});
          add(o,s,"Camelot teleport → south-east to Catherby farm",1.5,new String[]{"Camelot teleport tablets"}); break;
        case "herbhosidius":
          if(s.has("herbXeric")) add(o,s,"Xeric's talisman → Xeric's Glade, then south-west",.7,new String[]{},new String[]{"Xeric's talisman"});
          add(o,s,"Walk from Hosidius bank to herb patch",3); break;
        case "herbphasmatys":
          if(s.has("herbEcto")) add(o,s,"Ectophial → west to farm",1,new String[]{"Ectophial"});
          // With a fairy ring in the house, house → ALQ → 42 steps beats the Ectophial's walk
          // (Shortest Path agrees).
          if(s.has("fairy")) add(o,s,"Fairy ring ALQ → north to farm",s.has("houseFairy") && s.poh() ? .9 : 2,new String[]{},new String[]{"Dramen/lunar staff if required"});
          break;
        case "herbtroll":
          if(s.has("herbBasalt")) add(o,s,"Stony basalt → Troll Stronghold patch",.5,new String[]{"Stony basalt"});
          if(s.has("herbTrollheim")) add(o,s,"Trollheim teleport → Stronghold rooftop",2,new String[]{"Trollheim teleport"}); break;
        case "herbweiss":
          if(s.has("herbBasalt")) add(o,s,"Icy basalt → Weiss patch",.4,new String[]{"Icy basalt"}); break;
        case "herbharmony":
          if(s.has("herbHarmonyTab")) add(o,s,"Harmony island teleport → herb patch",.5,new String[]{"Harmony island teleport"}); break;
        case "herbfortis":
          if(s.has("whistle")) add(o,s,"Quetzal whistle → Hunter Guild, then run north",1,new String[]{"Quetzal whistle"});
          if(s.has("fairy")) add(o,s,"Fairy ring AJP → north-west to farm",1.8,new String[]{},new String[]{"Dramen/lunar staff if required"});
          if(s.has("quetzal")) add(o,s,"Civitas illa Fortis teleport → quetzal to Hunter Guild, then north",1.6,new String[]{"Civitas illa Fortis teleport tablet"}); break;
        case "herbguild":
          if(s.level==99 && s.has("farmingCape")) add(o,s,"Farming cape → Farming Guild",.5,new String[]{"Farming cape"});
          if(s.has("skills")) add(o,s,"Skills necklace → Farming Guild",.7,new String[]{},new String[]{"Skills necklace"});
          if(s.poh() && s.has("jewellery")) poh(o,s,"POH jewellery box → Farming Guild",1.2); break;
      }
      o.sort(Comparator.comparingDouble(c -> c.cost)); return o;
    }
    switch (p.id) {
      case "falador":
        if (s.has("wealth"))
          add(
              o,
              s,
              "Ring of wealth → Falador Park",
              .45,
              new String[] {},
              new String[] {"Ring of wealth"});
        if (s.poh() && s.has("jewellery")) poh(o, s, "POH jewellery box → Falador Park", 1.05);
        add(
            o,
            s,
            "Falador teleport → east to the park",
            1.8,
            new String[] {"Falador teleport tablets"});
        break;
      case "taverley":
        if (s.poh() && s.house.equals("taverley"))
          house(o, s, "Teleport outside your Taverley house → south-east", .65);
        if (s.has("redirect"))
          add(
              o,
              s,
              "Redirected house tab → Taverley",
              .8,
              new String[] {"Taverley teleport tablets"});
        if (s.has("cape"))
          add(
              o,
              s,
              "Construction cape → Taverley house portal",
              .65,
              new String[] {},
              new String[] {"Construction cape"});
        add(
            o,
            s,
            "Falador teleport → through the Taverley gate",
            2.65,
            new String[] {"Falador teleport tablets"});
        break;
      case "lumbridge":
        add(
            o,
            s,
            "Lumbridge teleport → west through the castle",
            1.45,
            new String[] {"Lumbridge teleport tablets"});
        break;
      case "varrock":
        add(
            o,
            s,
            "Varrock teleport → palace courtyard",
            1.15,
            new String[] {"Varrock teleport tablets"});
        if (s.has("gnome") && !houseTree(s)) add(o, s, "Spirit tree → Grand Exchange, then south-east", 2.1);
        break;
      case "strongtree":
        if (s.poh() && s.has("spirit"))
          poh(o, s, "House spirit tree → Gnome Stronghold, then south-west", 1.4);
        if (s.has("slayer"))
          add(
              o,
              s,
              "Slayer ring → Stronghold Slayer Cave",
              .45,
              new String[] {},
              new String[] {"Slayer ring"});
        if (s.has("gnome") && !houseTree(s)) add(o, s, "Spirit tree → Stronghold, then south-west", 1.35);
        add(
            o,
            s,
            "Royal seed pod → Stronghold, then south-west",
            1.6,
            new String[] {"Royal seed pod"});
        break;
      case "strongfruit":
        if (s.poh() && s.has("spirit"))
          poh(o, s, "House spirit tree → Gnome Stronghold, then east", 1.1);
        if (s.has("gnome") && !houseTree(s)) add(o, s, "Spirit tree → Stronghold, then east", .8);
        add(o, s, "Royal seed pod → Stronghold, then east", 1.25, new String[] {"Royal seed pod"});
        break;
      case "village":
        if (s.has("gnome") && !houseTree(s)) add(o, s, "Spirit tree → Tree Gnome Village; follow Elkoy", 1.8);
        if (s.poh() && s.has("spirit"))
          poh(o, s, "House spirit tree → Tree Gnome Village; follow Elkoy", 2.1);
        break;
      case "guildredwood":
      case "guildcelastrus":
      case "guildtree":
      case "guildfruit":
        if (s.level >= 99 && s.has("farmingCape"))
          add(
              o,
              s,
              "Farming cape → Farming Guild",
              p.kind.equals("tree") ? .48 : .62,
              new String[] {"Farming cape"});
        if (s.has("skills"))
          add(
              o,
              s,
              "Skills necklace → Farming Guild",
              p.kind.equals("tree") ? .75 : 1.05,
              new String[] {},
              new String[] {"Skills necklace"});
        if (s.poh() && s.has("jewellery"))
          poh(o, s, "POH jewellery box → Farming Guild", p.kind.equals("tree") ? 1.25 : 1.55);
        break;
      case "catherby":
        if (s.has("catherbyPortal"))
          poh(o, s, "House teleport → Portal nexus: Catherby, then east to the beach", .85);
        if (s.has("catherby"))
          add(
              o,
              s,
              "Catherby teleport → east to the beach",
              .75,
              new String[] {"Catherby teleport tablets"});
        add(
            o,
            s,
            "Camelot teleport → south-east to Catherby",
            2.25,
            new String[] {"Camelot teleport tablets"});
        break;
      case "brimhaven":
        if (s.poh() && s.house.equals("brimhaven"))
          house(o, s, "Teleport outside your Brimhaven house → north", .7);
        if (s.has("redirect"))
          add(
              o,
              s,
              "Redirected house tab → Brimhaven",
              .85,
              new String[] {"Brimhaven teleport tablets"});
        if (s.has("cape"))
          add(
              o,
              s,
              "Construction cape → Brimhaven house portal, then north",
              .6,
              new String[] {},
              new String[] {"Construction cape"});
        add(
            o,
            s,
            "Ardougne teleport → boat to Brimhaven",
            3.3,
            new String[] {"Ardougne teleport tablets", "Boat fare"});
        break;
      case "lletya":
        add(
            o,
            s,
            "Eternal or charged teleport crystal → Lletya; run south-east",
            .8,
            new String[] {"Teleport crystal"});
        break;
      case "auburn":
        if (s.has("pendant"))
          add(
              o,
              s,
              "Pendant of ates → Nemus Retreat; run north",
              1.2,
              new String[] {},
              new String[] {"Pendant of ates"});
        if (s.has("quetzal") || s.has("whistle")) {
          if (s.has("whistle"))
            add(
                o,
                s,
                "Quetzal whistle → Quetzacalli Gorge; follow Mountain Guide",
                2.4,
                new String[] {"Quetzal whistle"});
          else
            add(
                o,
                s,
                "Civitas illa Fortis teleport → Quetzal to Quetzacalli Gorge; follow Mountain"
                    + " Guide",
                3.25,
                new String[] {"Civitas illa Fortis teleport tablet"});
        }
        if (s.has("fairy"))
          add(
              o,
              s,
              "Fairy ring AIS → south-west, cross river, then north-west to tree patch",
              2.8,
              new String[] {},
              new String[] {"Dramen/lunar staff if required"});
        break;
      case "kastoricalquat":
      case "kastori":
        if (s.has("pendant"))
          add(
              o,
              s,
              "Pendant of ates → Kastori; run south-west",
              1.15,
              new String[] {},
              new String[] {"Pendant of ates"});
        if (s.has("quetzal") || s.has("whistle")) {
          if (s.has("whistle"))
            add(
                o,
                s,
                "Quetzal whistle → Kastori landing site; run north to fruit tree",
                1.55,
                new String[] {"Quetzal whistle"});
          else
            add(
                o,
                s,
                "Civitas illa Fortis teleport → Quetzal to Kastori; run north",
                2.35,
                new String[] {"Civitas illa Fortis teleport tablet"});
        }
        if (s.has("fairy"))
          add(
              o,
              s,
              "Fairy ring AIS → south-west to Achilka; boat to Kastori, then north",
              4.1,
              new String[] {"Boat fare"},
              new String[] {"Dramen/lunar staff if required"});
        break;
      case "calquat":
        add(
            o,
            s,
            "Tai bwo wannai teleport → north to the Calquat patch",
            .8,
            new String[] {"Tai bwo wannai teleport"});
        if (s.has("fairy"))
          add(
              o,
              s,
              "Fairy ring CKR → north to Tai Bwo Wannai",
              1.8,
              new String[] {},
              new String[] {"Dramen/lunar staff if required"});
        if (s.has("cape"))
          add(
              o,
              s,
              "Construction cape → Brimhaven, then run south",
              2.2,
              new String[] {},
              new String[] {"Construction cape"});
        break;
      case "summercalquat":
        add(
            o,
            s,
            "Fairy ring CJQ → south, then west via the 50 Agility shortcut",
            1.5,
            new String[] {},
            new String[] {"Dramen/lunar staff if required"});
        break;
      case "fossil1":
      case "fossil2":
      case "fossil3":
        if (s.has("digsite"))
          add(
              o,
              s,
              "Digsite pendant → Fossil Island; mushtree to Mushroom Meadow, then south-east",
              1.3,
              new String[] {"Digsite pendant"});
        add(
            o,
            s,
            "Digsite teleport → barge to Fossil Island; follow forest path to hardwood patches",
            4.2,
            new String[] {"Digsite teleport"});
        break;
      case "locus":
        if (s.has("fairy"))
          add(
              o,
              s,
              "Fairy ring AJP → south-east to Locus Oasis",
              1.7,
              new String[] {},
              new String[] {"Dramen/lunar staff if required"});
        if (s.has("quetzal") || s.has("whistle"))
          add(
              o,
              s,
              (s.has("whistle") ? "Quetzal whistle" : "Civitas illa Fortis teleport")
                  + " → Colossal Wyrm Remains landing site, then north",
              2.7,
              new String[] {
                s.has("whistle") ? "Quetzal whistle" : "Civitas illa Fortis teleport tablet"
              });
        break;
      case "anglers":
        // Catherby Teleport in your portal nexus: lands by the charter ship, much nearer than Camelot.
        if (s.has("catherbyPortal"))
          add(
              o,
              s,
              "House teleport → Portal nexus: Catherby; charter to Corsair Cove, rowboat to Anglers’"
                  + " Retreat, then south",
              s.has("cape") ? 4.2 : 4.4,
              s.has("cape")
                  ? new String[] {"Boat fare"}
                  : new String[] {s.has("pouch") ? "Rune pouch" : "House teleport tablets", "Boat fare"},
              s.has("cape") ? new String[] {"Construction cape"} : new String[0]);
        add(
            o,
            s,
            "Camelot teleport → Catherby charter to Corsair Cove; rowboat to Anglers’ Retreat, then"
                + " south",
            4.6,
            new String[] {"Camelot teleport tablets", "Boat fare"});
        break;
    }
    if (p.id.equals("kastoricalquat")) {
      List<Choice> revised = new ArrayList<>();
      for (Choice c : o)
        revised.add(
            new Choice(
                c.text
                    .replace("north to fruit tree", "north-east to Calquat patch")
                    .replace("run south-west", "run south to Calquat patch"),
                c.cost,
                c.items,
                c.gear));
      o = revised;
    }
    o.sort(Comparator.comparingDouble(c -> c.cost));
    return o;
  }

  static boolean accessible(Patch p, Settings s) {
    if (!s.kinds.contains(p.kind) || s.crop(p.kind) == null || s.excluded.contains(p.id))
      return false;
    switch (p.id) {
      case "herbtroll": return s.has("herbTroll");
      case "herbweiss": return s.has("herbWeiss");
      case "herbharmony": return s.has("herbHarmony");
      case "herbfortis": return s.has("herbFortis");
      case "herbphasmatys": return s.has("herbPriest");
      case "herbguild": return s.level >= 65;
      case "village":
        return s.has("gnome") || (s.poh() && s.has("spirit"));
      case "guildtree":
        return s.level >= 65;
      case "guildfruit":
        return s.level >= 85;
      case "lletya":
        return s.has("lletya");
      case "auburn":
        return s.has("varlamore");
      case "kastori":
      case "kastoricalquat":
        return s.has("kastori");
      case "summercalquat":
        return s.has("summer") && s.has("fairy");
      case "fossil1":
      case "fossil2":
      case "fossil3":
        return s.has("fossil");
      case "locus":
        return s.has("locus");
      case "anglers":
        return s.has("anglers");
      default:
        return true;
    }
  }

  static Choice walk(String text, double cost) {
    return new Choice(text, cost, Collections.emptyList(), Collections.emptyList());
  }

  static Choice transfer(Patch a, Patch b, Settings s) {
    if ((a.id.startsWith("guild") || a.id.equals("herbguild")) && (b.id.startsWith("guild") || b.id.equals("herbguild")))
      return walk("Walk within Farming Guild to " + b.kind + " patch", .4);
    if (a.id.startsWith("fossil") && b.id.startsWith("fossil"))
      return walk("Walk to Fossil Island hardwood patch " + b.id.substring(6), .12);
    if (new HashSet<>(Arrays.asList(a.id,b.id)).containsAll(Arrays.asList("catherby","herbcatherby"))) return walk("Walk to Catherby " + b.kind + " patch", .5);
    Set<String> pair = new HashSet<>(Arrays.asList(a.id, b.id));
    if (pair.containsAll(Arrays.asList("strongtree", "strongfruit")))
      return walk(
          "Walk to the Stronghold " + b.kind + " patch", a.id.equals("strongtree") ? .38 : .43);
    if (pair.containsAll(Arrays.asList("falador", "taverley")))
      return walk("Run between Falador Park and Taverley", 1.85);
    if (pair.containsAll(Arrays.asList("kastori", "kastoricalquat")))
      return walk("Walk to the Kastori " + b.kind + " patch", .5);
    if (pair.containsAll(Arrays.asList("brimhaven", "calquat")))
      return walk(
          b.id.equals("calquat")
              ? "Run south to Tai Bwo Wannai Calquat patch"
              : "Run north to Brimhaven fruit patch",
          2);
    // With a spirit tree in the house, every spirit tree trip goes from there (cape → house tree).
    if (s.has("gnome") && !houseTree(s) && a.id.startsWith("strong") && b.id.equals("village"))
      return walk("Spirit tree to Tree Gnome Village, then follow Elkoy", 1.65);
    if (s.has("gnome") && !houseTree(s) && a.id.equals("village") && b.id.startsWith("strong"))
      return walk("Spirit tree to Gnome Stronghold", 1.7);
    if (a.id.equals("catherby") && b.id.equals("brimhaven"))
      return new Choice(
          "Charter ship to Brimhaven", 2.3, Arrays.asList("Boat fare"), Collections.emptyList());
    if (a.id.equals("brimhaven") && b.id.equals("catherby"))
      return new Choice(
          "Charter ship to Catherby", 2.5, Arrays.asList("Boat fare"), Collections.emptyList());
    if (Arrays.asList("catherby", "brimhaven").contains(a.id) && b.id.equals("anglers"))
      return new Choice(
          "Charter to Corsair Cove, rowboat to Anglers’ Retreat",
          2.8,
          Arrays.asList("Boat fare"),
          Collections.emptyList());
    return null;
  }

  static Choice edge(List<Patch> p, List<Choice> direct, int a, int b, Settings s) {
    Choice t = transfer(p.get(a), p.get(b), s);
    return t != null && t.cost < direct.get(b).cost ? t : direct.get(b);
  }

  static List<Step> plan(Settings s) {
    return reorder(suggested(s), s);
  }

  /** Applies the player's chosen order, recalculating travel between neighbouring patches. */
  static List<Step> reorder(List<Step> route, Settings s) {
    if (s.order == null || s.order.isEmpty()) return route;
    List<Step> sorted = new ArrayList<>(route);
    sorted.sort(
        Comparator.comparingInt(
            x -> s.order.contains(x.patch.id) ? s.order.indexOf(x.patch.id) : Integer.MAX_VALUE));
    List<Step> result = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++) {
      Patch p = sorted.get(i).patch;
      Choice direct = choices(p, s).get(0);
      Choice t = i == 0 ? null : transfer(sorted.get(i - 1).patch, p, s);
      result.add(new Step(p, t != null && t.cost < direct.cost ? t : direct));
    }
    setNoting(result);
    return result;
  }

  static List<Step> suggested(Settings s) {
    List<Patch> patches = new ArrayList<>();
    List<Choice> direct = new ArrayList<>();
    for (Patch p : FarmingData.PATCHES)
      if (accessible(p, s)) {
        List<Choice> cs = choices(p, s);
        if (!cs.isEmpty()) {
          patches.add(p);
          direct.add(cs.get(0));
        }
      }
    List<Step> result = new ArrayList<>();
    if (s.kinds.size()==1 && s.kinds.contains("herb")) {
      for (int i=0;i<patches.size();i++) result.add(new Step(patches.get(i), direct.get(i)));
      setNoting(result); return result;
    }
    Set<Integer> unseen = new LinkedHashSet<>();
    for (int i = 0; i < patches.size(); i++) unseen.add(i);
    while (!unseen.isEmpty()) {
      List<Integer> g = new ArrayList<>();
      int first = unseen.iterator().next();
      g.add(first);
      unseen.remove(first);
      for (int i = 0; i < g.size(); i++)
        for (int j : new ArrayList<>(unseen))
          if (edge(patches, direct, g.get(i), j, s) != direct.get(j)
              || edge(patches, direct, j, g.get(i), s) != direct.get(g.get(i))) {
            g.add(j);
            unseen.remove(j);
          }
      int n = g.size(), full = (1 << n) - 1;
      double[][] dp = new double[full + 1][n];
      int[][] parent = new int[full + 1][n];
      for (int m = 0; m <= full; m++) {
        Arrays.fill(dp[m], Double.POSITIVE_INFINITY);
        Arrays.fill(parent[m], -1);
      }
      for (int j = 0; j < n; j++) dp[1 << j][j] = direct.get(g.get(j)).cost;
      for (int mask = 1; mask <= full; mask++)
        for (int i = 0; i < n; i++)
          if ((mask & (1 << i)) != 0)
            for (int j = 0; j < n; j++)
              if ((mask & (1 << j)) == 0) {
                int next = mask | (1 << j);
                double c = dp[mask][i] + edge(patches, direct, g.get(i), g.get(j), s).cost;
                if (c < dp[next][j]) {
                  dp[next][j] = c;
                  parent[next][j] = i;
                }
              }
      int end = 0;
      for (int i = 1; i < n; i++) if (dp[full][i] < dp[full][end]) end = i;
      int mask = full;
      List<Integer> order = new ArrayList<>();
      while (end >= 0) {
        order.add(g.get(end));
        int prev = parent[mask][end];
        mask ^= 1 << end;
        end = prev;
      }
      Collections.reverse(order);
      for (int i = 0; i < order.size(); i++) {
        int at = order.get(i);
        result.add(
            new Step(
                patches.get(at),
                i == 0 ? direct.get(at) : edge(patches, direct, order.get(i - 1), at, s)));
      }
    }
    setNoting(result);
    return result;
  }

  /** Herb runs have no noting steps: the player notes herbs whenever suits them. */
  static void setNoting(List<Step> route) {
    for (Step step : route) {
      step.noteBefore = false;
      step.noteAfter = false;
    }
  }

  static String normalize(String n) {
    if (n.equals("Boat fare") || n.equals("Coins for tree removal")) return "Coins";
    if (n.endsWith(" tablets")) return n.substring(0, n.length() - 8);
    if (n.equals("Civitas illa Fortis teleport tablet")) return "Civitas illa Fortis teleport";
    if (n.equals("Dramen/lunar staff if required")) return "Dramen or lunar staff";
    return n;
  }

  static String slot(String n) {
    String l = n.toLowerCase(Locale.ROOT);
    if (l.contains("hat") || l.contains("hood")) return "head";
    if (l.contains("cape")) return "cape";
    if (l.contains("necklace") || l.contains("pendant") || l.contains("talisman")) return "neck";
    if (l.contains("staff") || l.contains("secateurs")) return "weapon";
    if (l.contains("jacket") || l.contains(" top")) return "body";
    if (l.contains("trousers") || l.contains(" legs")) return "legs";
    if (l.contains("gloves")) return "hands";
    if (l.contains("boots")) return "feet";
    if (l.contains("ring")) return "ring";
    return "";
  }

  static Kit kit(List<Step> route, Settings s) {
    Kit k = new Kit();
    if (route.isEmpty()) return k;
    Set<String> occupied = new HashSet<>();
    Set<String> gear = new LinkedHashSet<>();
    // The farming cape is worn first (+5% herbs, and it still teleports); a Construction cape
    // then rides in the inventory, where its Tele to POH still works.
    boolean farmingCape = s.level >= 99 && s.has("farmingCape");
    if (farmingCape) gear.add("Farming cape");
    for (Step step : route) gear.addAll(step.travel.gear);
    if(s.secateurs && route.stream().anyMatch(x -> x.patch.kind.equals("herb"))) gear.add("Magic secateurs");
    gear.addAll(
        s.farmer
            ? Arrays.asList(
                "Farmer's strawhat", "Farmer's jacket", "Farmer's boro trousers", "Farmer's boots")
            : Arrays.asList(
                "Graceful hood",
                "Graceful cape",
                "Graceful top",
                "Graceful legs",
                "Graceful gloves",
                "Graceful boots"));
    for (String raw : gear) {
      String name = normalize(raw), slot = slot(name);
      if (farmingCape && name.equals("Graceful cape")) continue; // the cape slot is taken
      if (!slot.isEmpty() && occupied.add(slot)) k.wear.add(name);
      else k.add(name, 1);
    }
    k.add("Spade", 1);
    k.add("Rake", 1);
    int compost = 0, removal = 0;
    for (Step step : route) {
      Crop c = s.crop(step.patch.kind);
      k.add(c.sapling, 1);
      if (s.pays(step.patch)) k.add(c.payment, c.paymentQty);
      if (s.composts(step.patch)) compost++;
      removal += removalCost(step.patch);
      for (String raw : step.travel.items) {
        String name = normalize(raw);
        if (k.wear.contains(name)) continue; // already worn, e.g. the farming cape
        if (name.equals("Coins")) k.bring.put(name, Math.max(10000, k.bring.getOrDefault(name, 0)));
        else if (name.endsWith("teleport")) k.add(name, 1);
        else k.bring.put(name, 1);
      }
    }
    if(route.stream().anyMatch(x -> x.patch.kind.equals("herb"))) k.add("Seed dibber",1);
    if (removal > 0) k.add("Coins", removal);
    if (compost > 0)
      k.add(s.bucket ? "Bottomless compost bucket" : "Ultracompost", s.bucket ? 1 : compost);
    if (route.stream().anyMatch(x -> x.patch.kind.equals("celastrus"))) k.add("Axe", 1);
    // The pouch carries the standard teleport runes when the route casts them, otherwise the
    // Resurrect Crops runes (earth runes ride loose: the pouch holds 3 types).
    if (s.has("pouch") && Settings.usesSpells(route)) {
      k.bring.put("Rune pouch", 1);
      k.bring.put("Law rune", 1);
      k.bring.put("Lava rune", 1);
      k.bring.put("Mist rune", 1);
    } else if (s.resurrects(route)) {
      if (s.has("pouch")) k.bring.put("Rune pouch", 1);
      // Enough for 2 casts: covers 2 dead patches (about 1 run in 19 has 2; 3+ is 1 in 180).
      for (String[] rune : RESURRECT_RUNES) k.bring.put(rune[0], 2 * Integer.parseInt(rune[1]));
    }
    if (s.stamina && route.size() > 5) k.add("Stamina potion(4)", 1);
    return k;
  }

  static int removalCost(Patch p) {
    return p.kind.equals("redwood")
        ? 2000
        : Arrays.asList("calquat", "celastrus", "herb").contains(p.kind) ? 0 : 200;
  }

  // ---- Herb yield, from the OSRS wiki's herb calculator (Module:Herb_Farming_calculator) ----

  /** Each herb's chance to save a harvest life at level 1, out of 256; it's 80 at level 99. */
  private static final Map<String, Integer> HERB_LOW_CTS = new HashMap<>();

  static {
    String[] names = {
      "Guam", "Marrentill", "Tarromin", "Harralander", "Ranarr", "Toadflax", "Irit", "Avantoe",
      "Kwuarm", "Snapdragon", "Cadantine", "Lantadyme", "Dwarf weed", "Torstol"
    };
    int[] low = {25, 28, 31, 36, 39, 43, 46, 50, 54, 57, 60, 64, 67, 71};
    for (int i = 0; i < names.length; i++) HERB_LOW_CTS.put(names[i], low[i]);
  }

  private static final int HERB_HIGH_CTS = 80;

  /** Harvest lives with ultracompost: 3 base + 3 from the compost. */
  private static final int ULTRACOMPOST_LIVES = 6;

  /** Chance to die per growth stage with ultracompost: (floor(27 / 10) + 1) / 128. */
  private static final double ULTRACOMPOST_DEATH_PER_STAGE = (27 / 10 + 1) / 128.0;

  /** Herb patches that can't get diseased with the given unlock (null = always, once reachable). */
  private static boolean diseaseFree(Patch p, Settings s) {
    switch (p.id) {
      case "herbtroll": // My Arm's Big Adventure
      case "herbweiss": // Fire of Nourishment
      case "herbharmony": // Morytania elite diary
        return true;
      case "herbhosidius":
        return s.has("kourendEasy");
      case "herbfortis":
        return s.has("fortisGlory");
      default:
        return false;
    }
  }

  /** Chance the herbs survive to harvest: (1 - P)^3, or 1 on a disease-free patch. */
  static double herbSurvival(Patch p, Settings s) {
    return herbSurvival(p, s, false);
  }

  /**
   * With Resurrect Crops, the wiki's death chance is
   * (1-(1-P)^4)(1-R) + 6P²(1-P)² + 4P³(1-P) + P⁴, R being the spell's success chance.
   */
  static double herbSurvival(Patch p, Settings s, boolean resurrect) {
    if (diseaseFree(p, s)) return 1;
    double P = ULTRACOMPOST_DEATH_PER_STAGE;
    if (!resurrect) return Math.pow(1 - P, 3);
    double R = resurrectChance(s.magic);
    double death =
        (1 - Math.pow(1 - P, 4)) * (1 - R)
            + 6 * P * P * Math.pow(1 - P, 2)
            + 4 * Math.pow(P, 3) * (1 - P)
            + Math.pow(P, 4);
    return 1 - death;
  }

  /** Resurrect Crops success: 50% at 78 Magic to 75% at 99 (wiki: interpolate -111 to 191). */
  static double resurrectChance(int magic) {
    int level = Math.max(1, Math.min(99, magic));
    double value = (int) (-111 * (99 - level) / 98.0) + (int) (191 * (level - 1) / 98.0) + 1;
    return Math.min(Math.max(value / 256, 0), 1);
  }

  /** Runes for one Resurrect Crops cast. */
  static final String[][] RESURRECT_RUNES = {
    {"Soul rune", "8"}, {"Nature rune", "12"}, {"Blood rune", "8"}, {"Earth rune", "25"}
  };

  /** Diary yield bonus as a flat addition to the chance to save (5% = 10, 10% = 17, 15% = 25). */
  private static int diaryBonus(Patch p, Settings s) {
    if ((p.id.equals("herbhosidius") || p.id.equals("herbguild")) && s.has("kourendHard")) return 10;
    if (p.id.equals("herbcatherby")) {
      if (s.has("kandarinElite")) return 25;
      if (s.has("kandarinHard")) return 17;
      if (s.has("kandarinMedium")) return 10;
    }
    return 0;
  }

  /** Whether the farming cape (+5% yield) is worn: 99 Farming, owned, and the cape slot is free. */
  static boolean farmingCapeWorn(List<Step> route, Settings s) {
    if (s.level < 99 || !s.has("farmingCape")) return false;
    for (String worn : kit(route, s).wear)
      if (slot(worn).equals("cape") && !worn.equals("Farming cape")) return false;
    return true;
  }

  /**
   * Expected herbs from one patch, dead patches included, exactly as the wiki calculator works it
   * out: chance to save interpolated between level 1 and 99 values (after item and diary bonuses),
   * harvest lives / (1 - chance to save), times the chance the patch survives.
   */
  static double expectedHerbs(Patch p, Settings s, boolean capeWorn) {
    return expectedHerbs(p, s, capeWorn, false);
  }

  static double expectedHerbs(Patch p, Settings s, boolean capeWorn, boolean resurrect) {
    Crop crop = s.crop(p.kind);
    Integer low = crop == null ? null : HERB_LOW_CTS.get(crop.name);
    if (low == null) return 0;
    double itemBonus = (s.secateurs ? .1 : 0) + (capeWorn ? .05 : 0);
    int diary = diaryBonus(p, s);
    // An Attas plant at the Farming Guild multiplies the chance to save by 1.05.
    double attas = s.has("attas") ? .05 : 0;
    int lowFinal = (int) Math.floor(((int) Math.floor(low * (1 + itemBonus)) + diary) * (1 + attas));
    int highFinal =
        (int) Math.floor(((int) Math.floor(HERB_HIGH_CTS * (1 + itemBonus)) + diary) * (1 + attas));
    int level = Math.max(1, Math.min(99, s.level));
    // Module:Skilling success chart: truncated interpolation, + 1, out of 256.
    double value = (int) (lowFinal * (99 - level) / 98.0) + (int) (highFinal * (level - 1) / 98.0) + 1;
    double chanceToSave = Math.min(Math.max(value / 256, 0), 1);
    double yield = chanceToSave >= 1 ? 0 : ULTRACOMPOST_LIVES / (1 - chanceToSave);
    return yield * herbSurvival(p, s, resurrect);
  }

  /** Expected herbs from one patch that survives to harvest. */
  static double expectedHerbsIfAlive(Patch p, Settings s, boolean capeWorn) {
    double survival = herbSurvival(p, s, false);
    return survival <= 0 ? 0 : expectedHerbs(p, s, capeWorn, false) / survival;
  }

  static double xp(List<Step> route, Settings s) {
    double total = 0;
    boolean cape = farmingCapeWorn(route, s);
    boolean resurrect = s.resurrects(route);
    for (Step step : route) {
      Crop c = s.crop(step.patch.kind);
      if (step.patch.kind.equals("herb")) {
        // As the wiki: herbs picked plus planting when the patch survives; no compost XP.
        total +=
            expectedHerbs(step.patch, s, cape, resurrect) * c.harvest
                + c.plant * herbSurvival(step.patch, s, resurrect);
        continue;
      }
      total +=
          c.plant
              + c.check
              + (!s.harvests(step.patch.kind) ? 0 : step.patch.kind.equals("celastrus") ? bark(s) * 23.5 : c.harvest);
    }
    return total * (s.farmer ? 1.025 : 1);
  }

  static Long net(List<Step> route, Settings s, ToLongFunction<String> price) {
    long net = 0;
    boolean cape = farmingCapeWorn(route, s);
    boolean resurrect = s.resurrects(route);
    long castCost = 0;
    if (resurrect) {
      for (String[] rune : RESURRECT_RUNES) {
        long each = price.applyAsLong(rune[0]);
        if (each <= 0) return null;
        castCost += each * Integer.parseInt(rune[1]);
      }
    }
    for (Step step : route) {
      // Rune cost × the patch's death chance with resurrect, as the wiki calculator charges it.
      if (resurrect && step.patch.kind.equals("herb"))
        net -= Math.round(castCost * (1 - herbSurvival(step.patch, s, true)));
      Crop c = s.crop(step.patch.kind);
      long sap = price.applyAsLong(c.sapling);
      if (sap <= 0) return null;
      net -= sap;
      net -= removalCost(step.patch);
      if (s.pays(step.patch)) {
        long pay = price.applyAsLong(c.payment);
        if (pay <= 0) return null;
        net -= (long) pay * c.paymentQty;
      }
      if (s.composts(step.patch)) {
        long compost = price.applyAsLong("Ultracompost");
        if (compost <= 0) return null;
        net -= s.bucket ? compost / 2 : compost;
      }
      String harvest = step.patch.kind.equals("celastrus") ? "Celastrus bark" : c.fruit;
      if (!harvest.isEmpty() && s.harvests(step.patch.kind)) {
        long val = price.applyAsLong(harvest);
        if (val <= 0 && !harvest.equals("Calquat fruit")) return null;
        double herbs =
            s.herbActual != null
                ? Math.max(0, s.herbActual) // a finished run's real average
                : expectedHerbs(step.patch, s, cape, resurrect);
        net +=
            Math.round(
                val
                    * (step.patch.kind.equals("herb")
                        ? herbs
                        : step.patch.kind.equals("celastrus") ? bark(s) : 6));
      }
    }
    return net;
  }

  /** Every item ID for the kit, including charged variants, for bank tags and the bank view. */
  static Set<Integer> itemIds(Kit kit, Settings s) {
    Set<Integer> ids = new LinkedHashSet<>();
    List<String> items = new ArrayList<>(kit.wear);
    items.addAll(kit.bring.keySet());
    if (s.level == 99) items.add("Farming cape");
    for (String n : items) for (int id : FarmingData.IDS.getOrDefault(n, new int[0])) ids.add(id);
    return ids;
  }

  static String tags(Kit kit, Settings s) {
    StringBuilder b = new StringBuilder("Ultimate Farming");
    for (int id : itemIds(kit, s)) b.append(',').append(id);
    return b.toString();
  }

  static String travelItem(Choice t, Settings s) {
    String text = t.text.toLowerCase(Locale.ROOT);
    if (text.startsWith("walk") || text.startsWith("run ")) return "Graceful boots";
    if (text.contains("spirit tree")) return "Spirit sapling";
    if (text.contains("farming cape")) return "Farming cape";
    if (text.contains("construction cape")) return "Construction cape";
    if (!t.items.isEmpty()) return normalize(t.items.get(0));
    if (!t.gear.isEmpty()) return normalize(t.gear.get(0));
    return "Spade";
  }

  static String signature(List<Step> route, Kit kit, Settings s) {
    List<String> parts = new ArrayList<>();
    for (Step step : route) parts.add(step.patch.id + ":" + step.travel.text);
    Collections.sort(parts);
    List<String> gear = new ArrayList<>(kit.wear);
    Collections.sort(gear);
    return String.join("|", parts)
        + "/"
        + gear
        + "/"
        + s.farmer
        + "/"
        + s.unprotected
        + "/"
        + s.unharvested
        + "/"
        + s.bucket
        + "/"
        + (route.stream().anyMatch(x -> x.patch.kind.equals("celastrus")) ? s.level : 0);
  }

  /**
   * Celastrus bark per patch, worked out rather than asked for: the wiki's money making guide
   * expects 8.8 at 99 Farming (ultracompost, magic secateurs), and about 6 at 85. In between it scales with level; Attas adds 5%.
   */
  static double bark(Settings s) {
    double level = Math.max(85, Math.min(99, s.level));
    double bark = 6 + 2.8 * (level - 85) / 14;
    if (!s.secateurs) bark /= 1.1;
    if (s.has("attas")) bark *= 1.05;
    return bark;
  }
}