# Slayer Loot (RuneLite Plugin Hub Starter)

`Slayer Loot` tracks loot, kill count, and profit by Slayer task.

## Author

Papa Bald

## Core features in this starter

- Tracks `ServerNpcLoot` drops into the current task bucket.
- Stores kill count and loot totals per task.
- Shows both:
  - Gross profit (all drops)
  - Actual profit (excluding unchecked drops)
- Lets you uncheck items in each task card to remove them from actual profit.

## Important notes before Plugin Hub submission

- This starter is intentionally simple and needs final polish/testing.
- Keep all behavior informational only (no automation).
- Ensure the final plugin respects:
  - Jagex third-party rules
  - RuneLite Plugin Hub review standards
  - Rejected/Rolled-back feature policies

## Recommended next implementation steps

1. Improve task detection:
   - Parse reliable Slayer task name/amount from chat and/or varbits.
   - Start a new task record only when assignment changes.
2. Persist data:
   - Save task history and excluded items to config JSON.
   - Add reset/export controls.
3. Improve accuracy:
   - Optionally correlate drops with `ItemSpawned`/pickup behavior for "picked up only" mode.
4. Polish UI:
   - Add per-task sorting/filtering and collapsible sections.
5. Add tests:
   - Unit-test record math and item inclusion/exclusion behavior.

## Project files

- `src/main/java/com/papabald/slayerloot/SlayerLootPlugin.java`
- `src/main/java/com/papabald/slayerloot/SlayerLootPanel.java`
- `src/main/java/com/papabald/slayerloot/TaskLootRecord.java`
- `src/main/java/com/papabald/slayerloot/TaskLootItem.java`
- `src/main/java/com/papabald/slayerloot/SlayerLootConfig.java`
- `runelite-plugin.properties`

## Setup

1. Copy these files into a repository generated from:
   - <https://github.com/runelite/example-plugin/generate>
2. Open in IntelliJ (Java 11).
3. Run the `run` Gradle task.
