package com.papabald.slayerloot;

class TaskLootItem
{
    private final int itemId;
    private final String itemName;
    private int quantity;
    private long totalGeValue;
    private boolean excluded;

    TaskLootItem(int itemId, String itemName)
    {
        this.itemId = itemId;
        this.itemName = itemName;
    }

    int getItemId()
    {
        return itemId;
    }

    String getItemName()
    {
        return itemName;
    }

    int getQuantity()
    {
        return quantity;
    }

    long getTotalGeValue()
    {
        return totalGeValue;
    }

    boolean isExcluded()
    {
        return excluded;
    }

    void add(int qty, long geValue)
    {
        quantity += qty;
        totalGeValue += geValue;
    }

    void setExcluded(boolean excluded)
    {
        this.excluded = excluded;
    }

    void restore(int quantity, long totalGeValue, boolean excluded)
    {
        this.quantity = quantity;
        this.totalGeValue = totalGeValue;
        this.excluded = excluded;
    }
}
