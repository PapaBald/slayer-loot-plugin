package com.papabald.slayerloot;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.game.ItemStack;

@Slf4j
@PluginDescriptor(
    name = "Slayer Loot",
    description = "Track loot, kills, and actual profit per Slayer task",
    tags = {"slayer", "loot", "profit", "tracker"}
)
public class SlayerLootPlugin extends Plugin
{
    private static final String DEFAULT_TASK = "Unknown Task";
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("(?i)(?:you(?:'re| are) assigned to kill|your new task is to kill) (.+?)(?:\\.|;|,).*");
    private static final Pattern CURRENT_ASSIGNMENT_PATTERN = Pattern.compile("(?i)you(?:'re| are) (?:still|currently) (?:assigned to kill|hunting) (.+?)(?:\\.|;|,).*");
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

    @Inject
    private Gson gson;
    private final Map<String, TaskLootRecord> taskRecords = new LinkedHashMap<>();
    private String currentTaskName = DEFAULT_TASK;
    private String currentTaskKey;
    private int currentTaskRemaining;
    private int currentTaskOriginal;

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
        // Package-relative path -> resolves to
        // src/main/resources/com/papabald/slayerloot/panel_icon.png on disk.
        // Matches the convention used by every official RuneLite plugin.
        BufferedImage icon;
        try
        {
            icon = ImageUtil.loadImageResource(SlayerLootPlugin.class, "panel_icon.png");
        }
        catch (IllegalArgumentException ex)
        {
            log.warn("Failed to load Slayer Loot panel icon, falling back to a transparent stub", ex);
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
        panel.rebuild();
        clientThread.invoke(() ->
        {
            refreshCurrentTask();
            panel.rebuild();
        });
    }

    @Override
    protected void shutDown()
    {
        savePersistedState();
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel = null;
        taskRecords.clear();
        currentTaskName = DEFAULT_TASK;
        currentTaskKey = null;
        currentTaskRemaining = 0;
        currentTaskOriginal = 0;
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
    public void onVarbitChanged(VarbitChanged event)
    {
        // The Slayer task counters are stored in player varps, so we only
        // refresh when one of those specifically changes. This is what makes
        // the panel update on kills that produce no loot/XP events (e.g. a
        // plain Rat dropping nothing visible) — the server still ticks
        // SLAYER_COUNT, and we listen for that here.
        int varp = event.getVarpId();
        if (varp == VarPlayerID.SLAYER_COUNT
            || varp == VarPlayerID.SLAYER_COUNT_ORIGINAL
            || varp == VarPlayerID.SLAYER_TARGET)
        {
            refreshCurrentTask();
            if (panel != null)
            {
                panel.rebuild();
            }
        }
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

        Matcher currentAssignmentMatcher = CURRENT_ASSIGNMENT_PATTERN.matcher(message);
        if (currentAssignmentMatcher.matches())
        {
            String taskName = currentAssignmentMatcher.group(1).trim();
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
        final List<ItemStack> drops = new ArrayList<>(event.getItems());
        final NPCComposition composition = event.getComposition();
        // Some NPC names contain colour tags; strip them so the matcher sees plain text.
        final String npcName = composition == null ? null : Text.removeTags(composition.getName());

        clientThread.invokeLater(() ->
        {
            // Refresh the cached Slayer task state BEFORE gating. The
            // SLAYER_COUNT varbit can update either side of this loot event
            // (e.g. on the final kill of a task it ticks 1 -> 0), and a
            // stale cache caused legitimate task kills to be dropped on the
            // floor.
            refreshCurrentTask();

            // Match purely on task name. SlayerTaskTargets.matches() already
            // returns false for the DEFAULT_TASK placeholder, so this also
            // covers the "no active task" case without us depending on the
            // remaining-count varbit being fresh at the moment of the kill.
            boolean matched = SlayerTaskTargets.matches(currentTaskName, npcName);
            if (log.isDebugEnabled())
            {
                log.debug("ServerNpcLoot npc='{}' task='{}' matched={} drops={}", npcName, currentTaskName, matched, drops.size());
            }
            if (!matched)
            {
                return;
            }

            TaskLootRecord record = getOrCreateCurrentRecord();
            record.incrementKills();
            for (ItemStack stack : drops)
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

    void deleteTask(String taskKey)
    {
        if (taskKey == null || !taskRecords.containsKey(taskKey))
        {
            return;
        }

        taskRecords.remove(taskKey);
        if (taskKey.equals(currentTaskKey))
        {
            currentTaskKey = null;
            currentTaskName = DEFAULT_TASK;
        }
        savePersistedState();
        panel.rebuild();
    }

    String getCurrentTaskKey()
    {
        return currentTaskKey;
    }

    String getCurrentTaskName()
    {
        return currentTaskName;
    }

    int getCurrentTaskRemaining()
    {
        return currentTaskRemaining;
    }

    AsyncBufferedImage getItemIcon(int itemId, int quantity)
    {
        // stackable=true forces the quantity overlay (top-left) to render even when the base item is non-stackable.
        return itemManager.getImage(itemId, quantity, true);
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
        int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        currentTaskRemaining = amount;
        currentTaskOriginal = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
        if (amount <= 0)
        {
            return "";
        }

        int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
        if (taskId <= 0)
        {
            return "";
        }

        int taskDbRow;
        if (taskId == 98)
        {
            var bossRows = client.getDBRowsByValue(
                DBTableID.SlayerTaskSublist.ID,
                DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
                0,
                client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)
            );
            if (bossRows.isEmpty())
            {
                return "";
            }

            Object[] taskField = client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
            if (taskField == null || taskField.length == 0 || !(taskField[0] instanceof Integer))
            {
                return "";
            }
            taskDbRow = (Integer) taskField[0];
        }
        else
        {
            var taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
            if (taskRows.isEmpty())
            {
                return "";
            }
            taskDbRow = taskRows.get(0);
        }

        Object[] nameField = client.getDBTableField(taskDbRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
        if (nameField == null || nameField.length == 0 || !(nameField[0] instanceof String))
        {
            return "";
        }

        String taskName = ((String) nameField[0]).trim();
        if (taskName.isEmpty())
        {
            return "";
        }

        // DB value is uppercase; normalize for panel display.
        taskName = taskName.toLowerCase();
        if (taskName.length() == 1)
        {
            return taskName.toUpperCase();
        }
        return Character.toUpperCase(taskName.charAt(0)) + taskName.substring(1);
    }

    int getCurrentTaskOriginalAmount()
    {
        return currentTaskOriginal;
    }

    int getCurrentTaskCompletedCount()
    {
        int remain = currentTaskRemaining;
        int original = currentTaskOriginal;
        if (original <= 0 || remain < 0)
        {
            return 0;
        }
        return Math.max(0, original - remain);
    }

    boolean hasActiveSlayerTask()
    {
        return currentTaskRemaining > 0;
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
