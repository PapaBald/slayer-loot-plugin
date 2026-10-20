package com.papabald.slayerloot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;

/**
 * Works out which supplies were used by comparing everything the player is
 * carrying from one tick to the next. Anything that went down counts as used.
 *
 * <p>Inventory, worn equipment, the rune pouch and the cannon's loaded
 * cannonballs are counted together, so moving an item between them (wielding
 * bolts, filling the pouch, loading the cannon) cancels out.
 *
 * <p>Items with a trailing "(n)" in their name (potion doses, jewellery
 * charges) are counted in those units rather than as whole items, so sipping a
 * 4-dose into a 3-dose is one dose used, priced as a quarter of the 4-dose.
 * Other items are grouped with their variants so a degrading or
 * recharging item swapping forms isn't read as the whole item being used.
 *
 * <p>Must only be used from the client thread.
 */
@Slf4j
class SupplyUsageTracker
{
    /** One supply that was used up: an item, how many, and their combined value. */
    static final class Usage
    {
        final int itemId;
        final String name;
        final int quantity;
        final long value;

        Usage(int itemId, String name, int quantity, long value)
        {
            this.itemId = itemId;
            this.name = name;
            this.quantity = quantity;
            this.value = value;
        }
    }

    private static final Pattern CHARGE_SUFFIX = Pattern.compile("^(.+?)\\s*\\((\\d+)\\)$");

    /** Cannon parts are set up and picked back up, not used up. */
    private static final Set<Integer> IGNORED_ITEMS = new HashSet<>(Arrays.asList(
        ItemID.TWPART1, ItemID.TWPART2, ItemID.TWPART3, ItemID.TWPART4
    ));

    private static final int[] RUNE_POUCH_TYPES = {
        VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
        VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
    };

    private static final int[] RUNE_POUCH_QUANTITIES = {
        VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
        VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
    };

    private final Client client;
    private final ItemManager itemManager;

    /** itemId -> quantity carried at the last measurement. Null until a baseline is taken. */
    private Map<Integer, Integer> baseline;

    /** itemId -> ticks left during which losses of that item are not counted (e.g. dropped). */
    private final Map<Integer, Integer> ignoredLosses = new HashMap<>();

    /** Lowercased charge-family name -> its fullest and single-charge variants (null if none found). */
    private final Map<String, ChargeVariants> chargeVariants = new HashMap<>();

    @Inject
    SupplyUsageTracker(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    /** Forget the baseline. The next {@link #measure()} takes a fresh one and reports nothing. */
    void reset()
    {
        baseline = null;
        ignoredLosses.clear();
    }

    /**
     * Take a fresh baseline without counting anything. Used while items are
     * moving for reasons other than being used: banking, shops, trades, dying.
     */
    void resync()
    {
        baseline = snapshot();
        ignoredLosses.clear();
    }

    /** Don't count the next few ticks of losses of this item, e.g. because the player dropped it. */
    void ignoreLossesOf(int itemId, int ticks)
    {
        if (itemId > 0)
        {
            ignoredLosses.put(itemManager.canonicalize(itemId), ticks);
        }
    }

    /**
     * Compare what is carried now against the last baseline and return what was
     * used up in between. The current state becomes the new baseline.
     */
    List<Usage> measure()
    {
        Map<Integer, Integer> current = snapshot();
        Map<Integer, Integer> previous = baseline;
        baseline = current;
        if (previous == null)
        {
            ignoredLosses.clear();
            return Collections.emptyList();
        }

        Set<Integer> ids = new HashSet<>(previous.keySet());
        ids.addAll(current.keySet());

        Map<String, Group> groups = new LinkedHashMap<>();
        for (int id : ids)
        {
            int delta = current.getOrDefault(id, 0) - previous.getOrDefault(id, 0);
            if (delta == 0 || (delta < 0 && ignoredLosses.containsKey(id)))
            {
                continue;
            }

            String name = itemName(id);
            Matcher charges = CHARGE_SUFFIX.matcher(name);
            Group group;
            int units;
            if (charges.matches())
            {
                String base = charges.group(1).trim();
                group = groups.computeIfAbsent("c:" + base.toLowerCase(Locale.ROOT), k -> new Group(base, true));
                units = delta * Integer.parseInt(charges.group(2));
            }
            else
            {
                group = groups.computeIfAbsent("v:" + ItemVariationMapping.map(id), k -> new Group(name, false));
                units = delta;
            }

            group.units += units;
            if (delta < 0 && -delta >= group.mostLost)
            {
                group.mostLost = -delta;
                group.lostItemId = id;
                group.lostItemName = name;
            }
        }

        ignoredLosses.replaceAll((id, ticks) -> ticks - 1);
        ignoredLosses.values().removeIf(ticks -> ticks <= 0);

        List<Usage> used = new ArrayList<>();
        for (Group group : groups.values())
        {
            // Only a net loss is a use. A net gain is loot or a restock, and a net
            // zero is an item changing form (degrading, recharging, swapping gear).
            if (group.units >= 0 || group.lostItemId <= 0)
            {
                continue;
            }

            Usage usage = group.byCharge ? chargeUsage(group) : itemUsage(group);
            if (usage != null && usage.value > 0)
            {
                used.add(usage);
            }
        }

        if (log.isDebugEnabled())
        {
            for (Usage u : used)
            {
                log.debug("supply used: {} x{} = {} gp", u.name, u.quantity, u.value);
            }
        }
        return used;
    }

    private Usage itemUsage(Group group)
    {
        int quantity = (int) -group.units;
        long value = (long) itemManager.getItemPrice(group.lostItemId) * quantity;
        return new Usage(group.lostItemId, group.lostItemName, quantity, value);
    }

    /** A dose or charge item: price per unit comes from its fullest tradeable variant. */
    private Usage chargeUsage(Group group)
    {
        int unitsUsed = (int) -group.units;
        String key = group.name.toLowerCase(Locale.ROOT);
        if (!chargeVariants.containsKey(key))
        {
            // Misses are cached too, so an unpriced family isn't searched again every tick.
            chargeVariants.put(key, findChargeVariants(group.name));
        }
        ChargeVariants variants = chargeVariants.get(key);

        if (variants == null)
        {
            // No tradeable variant to price against; fall back to the item that was used.
            Matcher m = CHARGE_SUFFIX.matcher(group.lostItemName);
            int perItem = m.matches() ? Math.max(1, Integer.parseInt(m.group(2))) : 1;
            long value = Math.round((double) itemManager.getItemPrice(group.lostItemId) * unitsUsed / perItem);
            return new Usage(group.lostItemId, group.lostItemName, unitsUsed, value);
        }

        long fullPrice = itemManager.getItemPrice(variants.fullestId);
        long value = Math.round((double) fullPrice * unitsUsed / variants.fullestCharges);
        // Shown as the 1-dose/1-charge item with a quantity of doses/charges used.
        int displayId = variants.singleId > 0 ? variants.singleId : group.lostItemId;
        return new Usage(displayId, group.name, unitsUsed, value);
    }

    private ChargeVariants findChargeVariants(String baseName)
    {
        int fullestId = -1;
        int fullestCharges = 0;
        int singleId = -1;
        for (ItemPrice candidate : itemManager.search(baseName))
        {
            Matcher m = CHARGE_SUFFIX.matcher(candidate.getName());
            if (!m.matches() || !m.group(1).trim().equalsIgnoreCase(baseName))
            {
                continue;
            }

            int charges = Integer.parseInt(m.group(2));
            if (charges == 1)
            {
                singleId = candidate.getId();
            }
            if (charges > fullestCharges && itemManager.getItemPrice(candidate.getId()) > 0)
            {
                fullestCharges = charges;
                fullestId = candidate.getId();
            }
        }
        return fullestId > 0 ? new ChargeVariants(fullestId, fullestCharges, singleId) : null;
    }

    private String itemName(int itemId)
    {
        ItemComposition composition = itemManager.getItemComposition(itemId);
        String name = composition == null ? null : composition.getName();
        return name == null ? "Item " + itemId : Text.removeTags(name);
    }

    private Map<Integer, Integer> snapshot()
    {
        Map<Integer, Integer> items = new HashMap<>();
        addContainer(items, InventoryID.INV);
        addContainer(items, InventoryID.WORN);
        addRunePouch(items);

        int loadedCannonballs = client.getVarpValue(VarPlayerID.ROCKTHROWER);
        if (loadedCannonballs > 0)
        {
            items.merge(ItemID.MCANNONBALL, loadedCannonballs, Integer::sum);
        }
        return items;
    }

    private void addContainer(Map<Integer, Integer> items, int containerId)
    {
        ItemContainer container = client.getItemContainer(containerId);
        if (container == null)
        {
            return;
        }

        for (Item item : container.getItems())
        {
            int id = item.getId();
            if (id <= 0 || item.getQuantity() <= 0 || IGNORED_ITEMS.contains(id))
            {
                continue;
            }
            // Noted and unnoted stacks are the same supply.
            items.merge(itemManager.canonicalize(id), item.getQuantity(), Integer::sum);
        }
    }

    private void addRunePouch(Map<Integer, Integer> items)
    {
        EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        if (runeEnum == null)
        {
            return;
        }

        for (int slot = 0; slot < RUNE_POUCH_TYPES.length; slot++)
        {
            int type = client.getVarbitValue(RUNE_POUCH_TYPES[slot]);
            int quantity = client.getVarbitValue(RUNE_POUCH_QUANTITIES[slot]);
            if (type <= 0 || quantity <= 0)
            {
                continue;
            }

            int runeId = runeEnum.getIntValue(type);
            if (runeId > 0)
            {
                items.merge(runeId, quantity, Integer::sum);
            }
        }
    }

    /** Net movement of one item (or dose/charge family) within a single measurement. */
    private static final class Group
    {
        final String name;
        final boolean byCharge;
        long units;
        int mostLost;
        int lostItemId = -1;
        String lostItemName;

        Group(String name, boolean byCharge)
        {
            this.name = name;
            this.byCharge = byCharge;
        }
    }

    private static final class ChargeVariants
    {
        final int fullestId;
        final int fullestCharges;
        final int singleId;

        ChargeVariants(int fullestId, int fullestCharges, int singleId)
        {
            this.fullestId = fullestId;
            this.fullestCharges = fullestCharges;
            this.singleId = singleId;
        }
    }
}
