# Slayer Loot

A RuneLite plugin that tracks loot, kills, and profit on a per-Slayer-task basis.

While the standard Loot Tracker shows totals by NPC, Slayer Loot rolls every drop from a single task into one card so you can see exactly what each task earned you — useful for picking the most profitable masters and tasks.

## Features

- **Per-task records.** Each Slayer assignment gets its own card with kill count, gross GE value, and the drops grid.
- **Active-task gating.** Only kills that count toward the current task are credited (uses the same NPC alias map as the official Slayer plugin), so killing cows or chickens between tasks does not pollute your dog/rat/whatever totals.
- **Live progress readout.** "Current task" panel mirrors the in-game Slayer counter (`X/Y left`) and updates the moment a kill ticks the counter — even on kills that drop nothing.
- **Click-to-exclude.** Left-click any drop to remove it from the **Actual** total; click again to include it. The icon dims and shows a red X while excluded.
- **Hide / delete tasks.** Compact icon controls per task: collapse, hide from the main list, or delete the record entirely. A master toggle collapses or expands every card at once.
- **Persistence.** Task history is saved to your RuneLite config (toggle in plugin settings) and restored on startup.

## Configuration

| Setting | Default | What it does |
| --- | --- | --- |
| `Persist across sessions` | `true` | Save tracked task history to your RuneLite config so it survives restarts. |
| `Max stored tasks` | `100` | Cap on how many completed task records are retained. |

## How tasks are matched

When a `ServerNpcLoot` event fires, the plugin compares the killed NPC's name against the active task using the same logic as the official Slayer plugin (whole-word, case-insensitive, with a hand-curated alias table — e.g. a `Dogs` task accepts `Jackal` and `Temple Guardian`, a `Birds` task accepts `Chicken`, `Duck`, `Vulture`, etc.). If you find a task / NPC pair that should match but doesn't, please open an issue.

## Compatibility

- Works alongside the official Loot Tracker plugin.
- Reads only public `VarPlayer` / `Varbit` values that the in-game Slayer interface already exposes.

## Issues / feedback

[Open an issue on GitHub.](https://github.com/PapaBald/slayer-loot-plugin/issues)

## Building from source

This is a standard Plugin Hub project.

```bash
./gradlew run
```

starts the development RuneLite client with the plugin sideloaded. Use the IntelliJ IDEA Java 11 SDK if you want to import the project. The Gradle task `installGitHooksPath` configures `core.hooksPath` so trailers are stripped from any commits made through tooling that auto-injects them.

## License

[BSD 2-Clause](LICENSE)
