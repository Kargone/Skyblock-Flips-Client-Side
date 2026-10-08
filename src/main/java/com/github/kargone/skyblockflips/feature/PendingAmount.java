package com.github.kargone.skyblockflips.feature;

/**
 * The quantity the player last went shopping for.
 *
 * Clicking a material in the breakdown window records how many the recipe needs,
 * so that when Hypixel asks for an amount on a sign the overlay can offer to fill
 * that number in rather than making it be typed out. The Create BIN Auction menu
 * uses the same slot for the item's lowest BIN, so the price sign can be filled
 * the same way.
 *
 * It is used once and then forgotten: filling a sign clears it, picking another
 * item in the panel clears it, and it goes stale on its own after a few minutes,
 * because a sign opened long after the click is almost certainly about something
 * else.
 */
public final class PendingAmount {

    private static final long TTL_MS = 3 * 60 * 1000L;

    private static volatile String itemName = "";
    private static volatile long amount = 0L;
    private static volatile long rememberedAt = 0L;

    private PendingAmount() {
    }

    public static void remember(String name, long quantity) {
        if (quantity <= 0) return;

        itemName = name;
        amount = quantity;
        rememberedAt = System.currentTimeMillis();
    }

    /** True when there is an amount worth offering. */
    public static boolean isFresh() {
        return amount > 0 && System.currentTimeMillis() - rememberedAt <= TTL_MS;
    }

    public static String itemName() {
        return itemName;
    }

    public static long amount() {
        return amount;
    }

    public static void clear() {
        itemName = "";
        amount = 0;
        rememberedAt = 0L;
    }
}
