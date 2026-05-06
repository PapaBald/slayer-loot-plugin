package com.papabald.slayerloot;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.game.ItemStack;

@PluginDescriptor(
    name = "Slayer Loot",
    description = "Track loot, kills, and actual profit per Slayer task",
    tags = {"slayer", "loot", "profit", "tracker"}
)
public class SlayerLootPlugin extends Plugin
{
    private static final String DEFAULT_TASK = "Unknown Task";
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("(?i)you are assigned to kill (.+?)(?:\\.|;).*");
    private static final Pattern COMPLETE_PATTERN = Pattern.compile("(?i)(?:you've completed|you have completed).+task");

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    @Inject
    private SlayerLootConfig config;

    private final Gson gson = new Gson();
    private final Map<String, TaskLootRecord> taskRecords = new LinkedHashMap<>();
    private String currentTaskName = DEFAULT_TASK;
    private String currentTaskKey;

    private SlayerLootPanel panel;
    private NavigationButton navButton;

    @Provides
    SlayerLootConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SlayerLootConfig.class);
    }

    @Override
    protected void startUp()
    {
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
        if (icon == null)
        {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
        panel = new SlayerLootPanel(this);
        navButton = NavigationButton.builder()
            .tooltip("Slayer Loot")
            .icon(icon)
            .priority(6)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);

        loadPersistedState();
        refreshCurrentTask();
        panel.rebuild();
    }

    @Override
    protected void shutDown()
    {
        savePersistedState();
        clientToolbar.removeNavigation(navButton);
        taskRecords.clear();
        currentTaskName = DEFAULT_TASK;
        currentTaskKey = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        refreshCurrentTask();
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        // Slayer plugins commonly update task state when stats/vars move.
        // We keep this cheap and simply refresh our current task pointer.
        refreshCurrentTask();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        ChatMessageType type = event.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
        {
            return;
        }

        String message = Text.removeTags(event.getMessage()).trim();
        Matcher assignmentMatcher = ASSIGNMENT_PATTERN.matcher(message);
        if (assignmentMatcher.matches())
        {
            String taskName = assignmentMatcher.group(1).trim();
            if (!taskName.isEmpty())
            {
                switchTask(taskName);
            }
            return;
        }

        if (COMPLETE_PATTERN.matcher(message).find())
        {
            currentTaskName = DEFAULT_TASK;
            currentTaskKey = null;
            savePersistedState();
            panel.rebuild();
        }
    }

    @Subscribe
    public void onServerNpcLoot(ServerNpcLoot event)
    {
        clientThread.invokeLater(() ->
        {
            TaskLootRecord record = getOrCreateCurrentRecord();
            record.incrementKills();
            for (ItemStack stack : event.getItems())
            {
                int itemId = stack.getId();
                int qty = stack.getQuantity();
                ItemComposition itemComposition = itemManager.getItemComposition(itemId);
                String itemName = Text.removeTags(itemComposition.getName());
                long itemPrice = Math.max(itemManager.getItemPrice(itemId), itemComposition.getHaPrice());
                long geValue = itemPrice * (long) qty;

                record.addLoot(itemId, itemName, qty, geValue);
            }
            trimTaskHistory();
            savePersistedState();
            panel.rebuild();
        });
    }

    Collection<TaskLootRecord> getTaskRecords()
    {
        return taskRecords.values();
    }

    void setItemIncluded(String taskKey, int itemId, boolean included)
    {
        TaskLootRecord record = taskRecords.get(taskKey);
        if (record == null)
        {
            return;
        }

        record.setItemExcluded(itemId, !included);
        savePersistedState();
        panel.rebuild();
    }

    void setTaskHidden(String taskKey, boolean hidden)
    {
        TaskLootRecord record = taskRecords.get(taskKey);
        if (record == null)
        {
            return;
        }

        record.setHidden(hidden);
        savePersistedState();
        panel.rebuild();
    }

    void resetCurrentTask()
    {
        if (currentTaskKey == null)
        {
            return;
        }

        taskRecords.remove(currentTaskKey);
        currentTaskKey = null;
        currentTaskName = DEFAULT_TASK;
        savePersistedState();
        panel.rebuild();
    }

    void resetAllTasks()
    {
        taskRecords.clear();
        currentTaskKey = null;
        currentTaskName = DEFAULT_TASK;
        savePersistedState();
        panel.rebuild();
    }

    String getCurrentTaskKey()
    {
        return currentTaskKey;
    }

    private TaskLootRecord getOrCreateCurrentRecord()
    {
        refreshCurrentTask();
        if (currentTaskKey == null)
        {
            currentTaskName = DEFAULT_TASK;
            currentTaskKey = currentTaskName + "-" + Instant.now();
        }

        final String recordTaskName = currentTaskName;
        return taskRecords.computeIfAbsent(currentTaskKey, ignored -> new TaskLootRecord(currentTaskKey, recordTaskName));
    }

    private void refreshCurrentTask()
    {
        String widgetTask = readTaskNameFromWidget();
        if (!widgetTask.isEmpty())
        {
            if (!widgetTask.equals(currentTaskName) || currentTaskKey == null)
            {
                switchTask(widgetTask);
            }
            return;
        }

        // Keep current task when widget is unavailable (login screens, tab closed, etc.).
    }

    private String readTaskNameFromWidget()
    {
        Widget widget = client.getWidget(WidgetInfo.SLAYER_TASK);
        if (widget == null)
        {
            return "";
        }

        String text = Text.removeTags(widget.getText()).trim();
        if (text.isEmpty())
        {
            return "";
        }

        return text.replace("Task: ", "").trim();
    }

    private void trimTaskHistory()
    {
        int maxTasks = Math.max(1, config.maxStoredTasks());
        while (taskRecords.size() > maxTasks)
        {
            String firstKey = taskRecords.keySet().iterator().next();
            taskRecords.remove(firstKey);
        }
    }

    private void switchTask(String taskName)
    {
        currentTaskName = taskName;
        currentTaskKey = currentTaskName + "-" + Instant.now();
        savePersistedState();
    }

    private void savePersistedState()
    {
        if (!config.persistAcrossSessions())
        {
            config.setPersistedData("");
            return;
        }

        SlayerLootPersistence state = SlayerLootPersistence.fromRecords(taskRecords.values(), currentTaskName, currentTaskKey);
        config.setPersistedData(gson.toJson(state));
    }

    private void loadPersistedState()
    {
        if (!config.persistAcrossSessions())
        {
            return;
        }

        String raw = config.persistedData();
        if (raw == null || raw.isBlank())
        {
            return;
        }

        try
        {
            SlayerLootPersistence state = gson.fromJson(raw, SlayerLootPersistence.class);
            if (state == null)
            {
                return;
            }

            taskRecords.clear();
            for (TaskLootRecord record : state.toRecords())
            {
                taskRecords.put(record.getTaskKey(), record);
            }
            currentTaskName = state.currentTaskName == null || state.currentTaskName.isBlank() ? DEFAULT_TASK : state.currentTaskName;
            currentTaskKey = state.currentTaskKey;
            trimTaskHistory();
        }
        catch (JsonSyntaxException ignored)
        {
            config.setPersistedData("");
        }
    }
}
