# Ultimate Farming (beta)

A RuneLite plugin that guides your farm runs click by click: herbs, trees, fruit trees, Calquat and Celastrus.

**This is a beta.** Please report anything odd: a wrong highlight, a route you'd never take, a teleport it doesn't know you have. Open an [issue](../../issues) with a screenshot and what you expected.

## What it does

- **Plans the route** from your unlocks. Quests, diaries, teleports in your bank and the gear you wear are detected; the few things that can't be read (your house's spirit tree, jewellery box and so on) are ticked in Setup.
- **Gets your kit ready.** One button in the bank shows exactly what the run needs, with how many are still to withdraw on each item. It also sets up your rune pouch and spellbook (magic cape or house altar).
- **Shows what to click next.** The teleport, patch, leprechaun or gardener in blue, the thing after it in orange so you're ready, and a walking path when the patch isn't where you land. Teleport menus (spirit trees, the portal nexus, fairy rings, the quetzal map, cape teleport lists) have the destination marked.
- **Handles the fiddly bits:** Resurrect Crops on dead herbs, noting when your inventory fills up, wielding your magic secateurs, paying gardeners or composting.
- **Times your runs** and keeps records per run type: herbs picked, time, profit at guide prices and GP per hour.

## Using it

1. Open the leaf button in the sidebar. In **Setup**, choose your travel options and kit (most are detected).
2. On the **Run** tab, pick what you're running and press **Prepare run**. Withdraw what it lists; the bank button shows only those items.
3. Follow the on-screen guide. Each patch finishes itself when the plugin sees the last step (planting, compost or the gardener's payment), and the timer starts with the first patch.
4. Your times and profit are on the **Records** tab.

The plugin never clicks for you, and it sends nothing over the internet.

## Settings

- On-screen guide and highlights can each be turned off.
- Records rows shown before "Show more".
- Net XP and GP in the banner (off by default).
- Debug box (off by default): shows what the plugin thinks is happening. Turn it on and include it when reporting a bug.

## Building

```
./gradlew build
```

Built against the latest RuneLite release with Java 11 bytecode. `./gradlew run` opens a development client with the plugin loaded.

## Credits

Walking paths use the collision map and transport data from the [Shortest Path](https://github.com/Skretzo/shortest-path) plugin (BSD 2-Clause); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Herb yields follow the Old School RuneScape Wiki's herb calculator.

Unofficial; not endorsed by Jagex or RuneLite.
