package com.papabald.slayerloot;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

class TaskLootRecord
{
    private final String taskKey;
    private final String taskName;
    private final Instant startedAt;
    private int kills;
    private boolean hidden;
    private final Map<Integer, TaskLootItem> items = new LinkedHashMap<>();

    TaskLootRecord(String taskKey, String taskName)
    {
        this(taskKey, taskName, Instant.now(), 0);
    }

    TaskLootRecord(String taskKey, String taskName, Instant startedAt, int kills)
    {
        this.taskKey = taskKey;
        this.taskName = taskName;
        this.startedAt = startedAt;
        this.kills = kills;
    }

    String getTaskKey()
    {
        return taskKey;
    }

    String getTaskName()
    {
        return taskName;
    }

    int getKills()
    {
        return kills;
    }

    Collection<TaskLootItem> getItems()
    {
        return items.values();
    }

    void incrementKills()
    {
        kills++;
    }

    void addLoot(int itemId, String itemName, int qty, long geValue)
    {
        TaskLootItem item = items.computeIfAbsent(itemId, ignored -> new TaskLootItem(itemId, itemName));
        item.add(qty, geValue);
    }

    void setItemExcluded(int itemId, boolean excluded)
    {
        TaskLootItem item = items.get(itemId);
        if (item != null)
        {
            item.setExcluded(excluded);
        }
    }

    long getGrossProfit()
    {
        return items.values().stream().mapToLong(TaskLootItem::getTotalGeValue).sum();
    }

    long getActualProfit()
    {
        return items.values().stream()
            .filter(item -> !item.isExcluded())
            .mapToLong(TaskLootItem::getTotalGeValue)
            .sum();
    }

    Instant getStartedAt()
    {
        return startedAt;
    }

    boolean isHidden()
    {
        return hidden;
    }

    void setHidden(boolean hidden)
    {
        this.hidden = hidden;
    }

    void putRestoredItem(int itemId, String itemName, int quantity, long totalGeValue, boolean excluded)
    {
        TaskLootItem item = new TaskLootItem(itemId, itemName);
        item.restore(quantity, totalGeValue, excluded);
        items.put(itemId, item);
    }
}
