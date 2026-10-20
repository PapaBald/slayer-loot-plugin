# Slayer Loot

A RuneLite plugin that tracks loot, kills, supplies and profit for each Slayer task.

The standard Loot Tracker shows totals by NPC. Slayer Loot puts every drop from a single task on one card, along with the supplies you used, so you can see what each task actually earned you. Handy for figuring out which masters and tasks are worth your time.

## Features

- **Per-task records.** Each Slayer assignment gets its own card with kill count, GE value, drops and supplies used.
- **Active-task gating.** Only kills that count toward your current task are credited. It uses the same NPC alias list as the official Slayer plugin, so killing cows or chickens between tasks won't mess up your totals.
- **Live progress.** The "Current task" box mirrors the in-game Slayer counter (`X/Y left`) and updates on every kill, even kills that drop nothing.
- **Profit after supplies.** Food, potions, runes, ammo, teleports and cannonballs used on task are subtracted from your loot to show real profit.
- **Click to exclude.** Click any drop or supply to leave it out of the totals. Click again to put it back. Excluded items are dimmed with a red X.
- **Hide / delete tasks.** Each card can be collapsed, hidden from the list or deleted. One button collapses or expands every card at once.
- **Persistence.** Task history is saved to your RuneLite config and restored on startup (can be turned off in settings).

## Configuration

| Setting | Default | What it does |
| --- | --- | --- |
| `Persist across sessions` | `true` | Save task history to your RuneLite config so it survives restarts. |
| `Max stored tasks` | `100` | How many task records to keep. |
| `Track supplies` | `true` | Count supplies used on task and show profit after supplies. Turn off for a loot-only tracker. |
| `Stop counting after` | `5 min` | Supplies stop counting toward the task after this long without a task kill. |
| `Count before first kill` | `30 s` | Supplies used this long before the first kill of a trip still count, e.g. boosting or teleporting to the task. |

## Supplies

### What's tracked

- Food
- Potions, counted per dose. One sip from a 4-dose costs a quarter of a 4-dose.
- Runes, from your inventory and rune pouch
- Arrows, bolts, darts and other ammo in your ammo slot or inventory
- Teleport tabs and other one-use teleports
- Charged jewellery like the slayer ring, ring of dueling and amulet of glory, counted per charge
- Cannonballs, counted per shot while loaded in your cannon
- Bones you bury and anything else you use up from your inventory

Each task card shows a **Supplies** total and a **Profit** line (Actual loot minus supplies), plus a grid of everything used. Potions show as the 1-dose icon with the number of doses used.

### How it works

Every game tick the plugin compares what you're carrying with the tick before. That covers your inventory, worn equipment, rune pouch and the cannonballs loaded in your cannon. If something went down, it counts as used and is priced at its current GE value. Moving items between those places (equipping gear, filling the rune pouch, loading the cannon) cancels out and costs nothing.

Supplies only count while you're on task. Counting starts at your first task kill and stops once you've gone **Stop counting after** minutes without one, so a bank trip or doing something else between kills doesn't get billed to the task. Anything used in the **Count before first kill** window right before that first kill is added too.

These are never counted as supplies:

- Banking, deposit boxes, shops, trades and the Grand Exchange
- Dropping, destroying or emptying an item
- Dying
- Setting up or picking up your cannon
- Most items that change form without being used up, like Barrows gear degrading

### Limitations

- **Charged weapons and staves aren't tracked.** This includes the toxic blowpipe, trident of the seas/swamp, sanguinesti staff, Tumeken's shadow, scythe of vitur, warped sceptre, venator bow, toxic staff of the dead, serpentine helm, crystal gear, tome pages and similar. Their charges aren't items you carry, so they can't be measured the same way. Runes or scales you spend recharging them mid-task will show up though.
- **Untradeable items are worth 0 gp** and are left out of the supplies list.
- **Granite cannonballs** are counted when you load them instead of per shot.
- **Looting bag, herb sack and similar storage.** Items you put in through the "Fill" option are ignored, but items added another way may be counted as used.
- **High alchemy** counts the alched item as used and doesn't add the coins you got back.
- **Pies, pizzas and cakes** eaten in bites may come out slightly high, since the first bite counts the whole item.
- **Picking up an item and using the same item on the same tick** can cause that one use to be missed.

If something gets counted that shouldn't be, click it in the Supplies grid to exclude it.

## How tasks are matched

When a kill's loot arrives, the plugin compares the NPC's name against your active task using the same logic as the official Slayer plugin: whole-word, case-insensitive, with an alias list. For example a `Dogs` task accepts `Jackal` and `Temple Guardian`, and a `Birds` task accepts `Chicken`, `Duck`, `Vulture` and so on. If you find a task and NPC that should match but don't, please open an issue.

## Compatibility

- Works alongside the official Loot Tracker plugin.
- Only reads values the game client already exposes (your Slayer task counters, inventory and equipment). Nothing is sent anywhere.

## Issues / feedback

[Open an issue on GitHub.](https://github.com/PapaBald/slayer-loot-plugin/issues)

## Icons

- **`icon.png`** (repo root, about 48x72): Plugin Hub listing image, not loaded from the JAR.
- **`src/main/resources/com/papabald/slayerloot/panel_icon.png`** (about 24x24): side panel tab icon bundled with the plugin. `SlayerLootPlugin` loads it with `ImageUtil.loadImageResource(SlayerLootPlugin.class, "panel_icon.png")`. A copy of `panel_icon.png` at the repo root is optional for editing and ignored by Git (`.gitignore`).

## Building from source

This is a standard Plugin Hub project.

```bash
./gradlew run
```

starts the development RuneLite client with the plugin loaded. Use a Java 11 SDK if you import the project into IntelliJ IDEA. The Gradle task `installGitHooksPath` sets `core.hooksPath` so trailers are stripped from commits made through tooling that adds them automatically.

## License

[BSD 2-Clause](LICENSE)
