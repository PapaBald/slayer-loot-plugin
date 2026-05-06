package com.papabald.slayerloot;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SlayerLootPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(SlayerLootPlugin.class);
        RuneLite.main(args);
    }
}
