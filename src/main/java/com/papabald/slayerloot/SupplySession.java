package com.papabald.slayerloot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Decides whether supplies used right now belong to the Slayer task.
 *
 * <p>A session opens on a task kill and stays open until no task kill has
 * happened for the idle timeout, so a bank trip or an unrelated activity
 * between kills isn't billed to the task. Supplies used while no session is
 * open are held for a short grace window, so a boost or teleport just before
 * the first kill of a trip still counts once that kill happens.
 *
 * <p>Ticks are counted by the caller. Not thread safe; use from the client thread.
 */
class SupplySession
{
    private static final int NO_KILL = Integer.MIN_VALUE;

    private int lastKillTick = NO_KILL;
    private final Deque<Pending> pending = new ArrayDeque<>();

    void reset()
    {
        lastKillTick = NO_KILL;
        pending.clear();
    }

    boolean isOpen(int tick, int timeoutTicks)
    {
        return lastKillTick != NO_KILL && tick - lastKillTick <= timeoutTicks;
    }

    /**
     * Record a task kill.
     *
     * @return supplies held in the grace window that now belong to the task;
     * empty when the session was already open
     */
    List<SupplyUsageTracker.Usage> onTaskKill(int tick, int timeoutTicks, int graceTicks)
    {
        boolean wasOpen = isOpen(tick, timeoutTicks);
        lastKillTick = tick;

        List<SupplyUsageTracker.Usage> claimed = new ArrayList<>();
        if (!wasOpen)
        {
            prune(tick, graceTicks);
            for (Pending p : pending)
            {
                claimed.addAll(p.used);
            }
        }
        pending.clear();
        return claimed;
    }

    /** Hold supplies used outside a session in case a task kill follows shortly. */
    void hold(int tick, int graceTicks, List<SupplyUsageTracker.Usage> used)
    {
        if (graceTicks <= 0 || used.isEmpty())
        {
            return;
        }
        pending.addLast(new Pending(tick, used));
        prune(tick, graceTicks);
    }

    private void prune(int tick, int graceTicks)
    {
        while (!pending.isEmpty() && tick - pending.peekFirst().tick > graceTicks)
        {
            pending.removeFirst();
        }
    }

    private static final class Pending
    {
        final int tick;
        final List<SupplyUsageTracker.Usage> used;

        Pending(int tick, List<SupplyUsageTracker.Usage> used)
        {
            this.tick = tick;
            this.used = used;
        }
    }
}
