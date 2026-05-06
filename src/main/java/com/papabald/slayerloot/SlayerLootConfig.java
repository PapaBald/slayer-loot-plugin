package com.papabald.slayerloot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
