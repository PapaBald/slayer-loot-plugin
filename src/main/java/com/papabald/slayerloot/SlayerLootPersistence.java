package com.papabald.slayerloot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class SlayerLootPersistence
{
    String currentTaskName;
    String currentTaskKey;
    List<PersistedTask> tasks = new ArrayList<>();

    static SlayerLootPersistence fromRecords(Collection<TaskLootRecord> records, String currentTaskName, String currentTaskKey)
    {
        SlayerLootPersistence state = new SlayerLootPersistence();
        state.currentTaskName = currentTaskName;
        state.currentTaskKey = currentTaskKey;
        for (TaskLootRecord record : records)
        {
            PersistedTask task = new PersistedTask();
            task.taskKey = record.getTaskKey();
            task.taskName = record.getTaskName();
            task.kills = record.getKills();
            task.startedAt = record.getStartedAt().toString();
            task.hidden = record.isHidden();
            record.getItems().forEach(item ->
            {
                PersistedItem persistedItem = new PersistedItem();
                persistedItem.itemId = item.getItemId();
                persistedItem.itemName = item.getItemName();
                persistedItem.quantity = item.getQuantity();
                persistedItem.totalGeValue = item.getTotalGeValue();
                persistedItem.excluded = item.isExcluded();
                task.items.add(persistedItem);
            });
            state.tasks.add(task);
        }
        return state;
    }

    List<TaskLootRecord> toRecords()
    {
        List<TaskLootRecord> records = new ArrayList<>();
        for (PersistedTask persistedTask : tasks)
        {
            Instant startedAt = parseInstant(persistedTask.startedAt);
            TaskLootRecord record = new TaskLootRecord(
                persistedTask.taskKey,
                persistedTask.taskName,
                startedAt,
                persistedTask.kills
            );
            record.setHidden(persistedTask.hidden);
            for (PersistedItem persistedItem : persistedTask.items)
            {
                record.putRestoredItem(
                    persistedItem.itemId,
                    persistedItem.itemName,
                    persistedItem.quantity,
                    persistedItem.totalGeValue,
                    persistedItem.excluded
                );
            }
            records.add(record);
        }
        return records;
    }

    private static Instant parseInstant(String value)
    {
        try
        {
            return Instant.parse(value);
        }
        catch (Exception ignored)
        {
            return Instant.now();
        }
    }

    static class PersistedTask
    {
        String taskKey;
        String taskName;
        String startedAt;
        int kills;
        boolean hidden;
        List<PersistedItem> items = new ArrayList<>();
    }

    static class PersistedItem
    {
        int itemId;
        String itemName;
        int quantity;
        long totalGeValue;
        boolean excluded;
    }
}
