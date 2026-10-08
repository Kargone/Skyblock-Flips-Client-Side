package com.github.kargone.skyblockflips.feature;

import com.github.kargone.skyblockflips.SkyblockHttpClient;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.ContainerUtils;
import com.github.kargone.skyblockflips.util.SkyblockText;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports what is in the forge to the server.
 *
 * Hypixel says nothing in chat when a forge process starts or is claimed, so this
 * reads the seven slots of "The Forge" menu instead and posts a snapshot of them
 * whenever one changes. The server compares each snapshot with the last one to
 * work out what started and what was claimed; this class only reads the menu.
 *
 * <p>What the slots look like, from the forge container dump:
 * <ul>
 *   <li>empty: named "Slot #1", lore ends "Click to select a process!"</li>
 *   <li>forging: named after the product, lore "Time Remaining: 1d 6h"</li>
 *   <li>ready: lore "Time Remaining: Completed!" then "Click to claim!"</li>
 * </ul>
 * Anything else - a locked slot, or one still loading - is sent as "unknown" and
 * the server leaves it alone.
 */
public final class ForgeTracker {

    public static final String FORGE_TITLE = "The Forge";

    /** Container slots of forge slots #1 to #7, the middle of the second row. */
    private static final int FIRST_SLOT = 10;
    private static final int SLOT_COUNT = 7;

    private static final String TIME_REMAINING = "Time Remaining:";
    private static final String COMPLETED = "Completed!";
    private static final String SELECT_PROCESS = "Click to select a process!";
    private static final String CURRENTLY_MAKING = "Currently making:";

    /** "1d 6h 3m 20s", any subset, in any spacing. */
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])");

    /** The slots change at most a few times a second; no need to read them every frame. */
    private static final long READ_INTERVAL_MS = 250L;
    /** How long to wait before resending a snapshot the server did not take. */
    private static final long RETRY_MS = 10_000L;

    private static final SkyblockHttpClient HTTP = new SkyblockHttpClient();
    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkyblockFlips-ForgeTracker");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean SENDING = new AtomicBoolean(false);

    private static long lastReadAt = 0L;
    /** Fingerprint of the last snapshot the server accepted; empty until one has been sent. */
    private static volatile String lastSent = "";
    private static volatile long lastFailureAt = 0L;

    private enum State { EMPTY, FORGING, READY, UNKNOWN }

    private record ForgeSlot(int number, State state, String itemName, String itemId, int amount, long remainingSeconds) {

        /** What has to change for the server to care; the countdown does not count. */
        String fingerprint() {
            return number + ":" + state + ":" + itemId + ":" + itemName + ":" + amount;
        }
    }

    private ForgeTracker() {
    }

    public static boolean isForgeScreen(String title) {
        return title != null && SkyblockText.strip(title).equals(FORGE_TITLE);
    }

    /** Called every frame "The Forge" is drawn. */
    public static void update(AbstractContainerMenu menu) {
        long now = System.currentTimeMillis();
        if (now - lastReadAt < READ_INTERVAL_MS) return;
        lastReadAt = now;

        List<ForgeSlot> slots = readSlots(menu);
        if (slots == null) return;

        StringBuilder fingerprint = new StringBuilder();
        for (ForgeSlot slot : slots) fingerprint.append(slot.fingerprint()).append('|');
        String key = fingerprint.toString();

        if (key.equals(lastSent)) return;
        if (now - lastFailureAt < RETRY_MS) return;
        if (!SENDING.compareAndSet(false, true)) return;

        String payload = toJson(slots, now).toString();
        SENDER.execute(() -> {
            try {
                HTTP.sendPOST(ServerData.baseUrl(), payload);
                lastSent = key;
                System.out.println("[SkyblockFlips] Sent forge snapshot " + key);
                // Show the start or claim in the forge panel without waiting for the next refresh.
                ServerData.refreshTraderDataSoon();
            } catch (Exception e) {
                lastFailureAt = System.currentTimeMillis();
                System.err.println("[SkyblockFlips] Could not send forge snapshot: " + e.getMessage());
            } finally {
                SENDING.set(false);
            }
        });
    }

    /** The seven forge slots, or null while the menu has not finished loading. */
    private static List<ForgeSlot> readSlots(AbstractContainerMenu menu) {
        if (menu.slots.size() < FIRST_SLOT + SLOT_COUNT) return null;

        List<ForgeSlot> slots = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            Slot slot = menu.slots.get(FIRST_SLOT + i);
            if (!ContainerUtils.isContainerSlot(slot)) return null;

            // Every forge slot holds something, even an empty one, so a blank stack
            // means the contents have not arrived yet.
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return null;

            slots.add(readSlot(i + 1, stack));
        }
        return slots;
    }

    private static ForgeSlot readSlot(int number, ItemStack stack) {
        String name = SkyblockText.strip(stack.getHoverName().getString());
        List<String> lore = loreOf(stack);

        if (lore.contains(SELECT_PROCESS)) {
            return new ForgeSlot(number, State.EMPTY, "", "", 0, 0L);
        }

        for (String line : lore) {
            if (!line.startsWith(TIME_REMAINING)) continue;

            String remaining = line.substring(TIME_REMAINING.length()).trim();
            String itemName = currentlyMaking(lore, name);
            String itemId = itemIdOf(stack);
            int amount = stack.getCount();

            if (remaining.startsWith(COMPLETED)) {
                return new ForgeSlot(number, State.READY, itemName, itemId, amount, 0L);
            }
            long seconds = parseDuration(remaining);
            if (seconds < 0) break;
            return new ForgeSlot(number, State.FORGING, itemName, itemId, amount, seconds);
        }

        return new ForgeSlot(number, State.UNKNOWN, name, "", 0, 0L);
    }

    /** The product named on the "Currently making:" line, else the slot's own name. */
    private static String currentlyMaking(List<String> lore, String fallback) {
        for (String line : lore) {
            if (line.startsWith(CURRENTLY_MAKING)) {
                String product = line.substring(CURRENTLY_MAKING.length()).trim();
                if (!product.isEmpty()) return product;
            }
        }
        return fallback;
    }

    private static List<String> loreOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();

        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) lines.add(SkyblockText.strip(line.getString()));
        return lines;
    }

    /** Hypixel's item id, e.g. {@code DRILL_ENGINE}, kept in the item's custom data. */
    private static String itemIdOf(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? "" : customData.copyTag().getStringOr("id", "");
    }

    /** "1d 6h" to 108000 seconds; -1 when the text holds no duration at all. */
    static long parseDuration(String text) {
        Matcher matcher = DURATION_PART.matcher(text);
        long seconds = 0L;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            long value = Long.parseLong(matcher.group(1));
            seconds += switch (matcher.group(2)) {
                case "d" -> value * 86_400L;
                case "h" -> value * 3_600L;
                case "m" -> value * 60L;
                default -> value;
            };
        }
        return found ? seconds : -1L;
    }

    private static JsonObject toJson(List<ForgeSlot> slots, long now) {
        JsonArray array = new JsonArray();
        for (ForgeSlot slot : slots) {
            JsonObject json = new JsonObject();
            json.addProperty("slot", slot.number());
            json.addProperty("state", slot.state().name().toLowerCase());
            json.addProperty("item-name", slot.itemName());
            json.addProperty("item-id", slot.itemId());
            json.addProperty("amount", slot.amount());
            if (slot.state() == State.FORGING) json.addProperty("remaining-seconds", slot.remainingSeconds());
            array.add(json);
        }

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("type", "forge-snapshot");
        snapshot.addProperty("time", now);
        snapshot.add("slots", array);
        return snapshot;
    }
}
