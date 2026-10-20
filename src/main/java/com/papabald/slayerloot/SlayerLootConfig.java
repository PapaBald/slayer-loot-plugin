package com.papabald.slayerloot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("slayerloot")
public interface SlayerLootConfig extends Config
{
    @ConfigItem(
        keyName = "persistAcrossSessions",
        name = "Persist across sessions",
        description = "Keep tracked task data after restarting RuneLite"
    )
    default boolean persistAcrossSessions()
    {
        return true;
    }

    @ConfigItem(
        keyName = "maxStoredTasks",
        name = "Max stored tasks",
        description = "Maximum number of completed tasks retained in memory"
    )
    default int maxStoredTasks()
    {
        return 100;
    }

    @ConfigSection(
        name = "Supplies",
        description = "Count supplies used on task and show profit after supplies",
        position = 10
    )
    String suppliesSection = "supplies";

    @ConfigItem(
        keyName = "trackSupplies",
        name = "Track supplies",
        description = "Count food, potions, runes, ammo and other supplies used on task, and show profit after supplies",
        section = suppliesSection,
        position = 0
    )
    default boolean trackSupplies()
    {
        return true;
    }

    @Range(min = 1, max = 60)
    @Units(Units.MINUTES)
    @ConfigItem(
        keyName = "supplyIdleTimeout",
        name = "Stop counting after",
        description = "Supplies stop counting toward the task after this long without a task kill, "
            + "so a bank trip or other activity isn't billed to the task",
        section = suppliesSection,
        position = 1
    )
    default int supplyIdleTimeout()
    {
        return 5;
    }

    @Range(min = 0, max = 300)
    @Units(Units.SECONDS)
    @ConfigItem(
        keyName = "supplyGraceWindow",
        name = "Count before first kill",
        description = "Supplies used this long before the first kill of a trip still count, "
            + "e.g. boosting or teleporting to the task",
        section = suppliesSection,
        position = 2
    )
    default int supplyGraceWindow()
    {
        return 30;
    }

    @ConfigItem(
        keyName = "persistedData",
        name = "",
        description = "",
        hidden = true
    )
    default String persistedData()
    {
        return "";
    }

    @ConfigItem(
        keyName = "persistedData",
        name = "",
        description = ""
    )
    void setPersistedData(String data);
}
