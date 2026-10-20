package com.papabald.slayerloot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SupplySessionTest
{
    private static final int TIMEOUT = 500;
    private static final int GRACE = 50;

    private static List<SupplyUsageTracker.Usage> shark()
    {
        return Collections.singletonList(new SupplyUsageTracker.Usage(1, "Shark", 1, 1000));
    }

    @Test
    public void closedUntilFirstKill()
    {
        SupplySession session = new SupplySession();
        assertFalse(session.isOpen(0, TIMEOUT));

        session.onTaskKill(10, TIMEOUT, GRACE);
        assertTrue(session.isOpen(10, TIMEOUT));
    }

    @Test
    public void closesAfterIdleTimeout()
    {
        SupplySession session = new SupplySession();
        session.onTaskKill(10, TIMEOUT, GRACE);

        assertTrue(session.isOpen(10 + TIMEOUT, TIMEOUT));
        assertFalse(session.isOpen(10 + TIMEOUT + 1, TIMEOUT));
    }

    @Test
    public void killExtendsSession()
    {
        SupplySession session = new SupplySession();
        session.onTaskKill(10, TIMEOUT, GRACE);
        session.onTaskKill(400, TIMEOUT, GRACE);

        assertTrue(session.isOpen(800, TIMEOUT));
    }

    @Test
    public void suppliesInGraceWindowAreClaimedByFirstKill()
    {
        SupplySession session = new SupplySession();
        session.hold(100, GRACE, shark());

        List<SupplyUsageTracker.Usage> claimed = session.onTaskKill(100 + GRACE, TIMEOUT, GRACE);
        assertEquals(1, claimed.size());
    }

    @Test
    public void suppliesBeforeGraceWindowAreDropped()
    {
        SupplySession session = new SupplySession();
        session.hold(100, GRACE, shark());

        assertTrue(session.onTaskKill(100 + GRACE + 1, TIMEOUT, GRACE).isEmpty());
    }

    @Test
    public void killInOpenSessionClaimsNothing()
    {
        SupplySession session = new SupplySession();
        session.onTaskKill(10, TIMEOUT, GRACE);
        session.hold(20, GRACE, shark());

        assertTrue(session.onTaskKill(30, TIMEOUT, GRACE).isEmpty());
    }

    @Test
    public void zeroGraceHoldsNothing()
    {
        SupplySession session = new SupplySession();
        session.hold(100, 0, shark());

        assertTrue(session.onTaskKill(100, TIMEOUT, 0).isEmpty());
    }

    @Test
    public void resetClosesSession()
    {
        SupplySession session = new SupplySession();
        session.onTaskKill(10, TIMEOUT, GRACE);
        session.reset();

        assertFalse(session.isOpen(11, TIMEOUT));
    }
}
