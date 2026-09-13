package com.papabald.slayerloot;

import java.util.Collections;
import java.util.List;

/** Immutable snapshot handed from the client thread to the EDT. */
class PanelState
{
    static final PanelState EMPTY = new PanelState(Collections.emptyList(), "Unknown Task", 0, 0, false);

    final List<TaskLootRecord> records;
    final String currentTaskName;
    final int remaining;
    final int original;
    final boolean hasActiveTask;

    PanelState(List<TaskLootRecord> records, String currentTaskName, int remaining, int original, boolean hasActiveTask)
    {
        this.records = Collections.unmodifiableList(records);
        this.currentTaskName = currentTaskName;
        this.remaining = remaining;
        this.original = original;
        this.hasActiveTask = hasActiveTask;
    }

    int completed()
    {
        if (original <= 0 || remaining < 0)
        {
            return 0;
        }
        return Math.max(0, original - remaining);
    }
}