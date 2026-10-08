package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What is in the forge and what it has made, from the server's trader data.
 *
 * The server fills {@code forge} from the snapshots {@code ForgeTracker} sends:
 * one entry per running process, with what its materials cost and when it ends.
 * A process is ready once its end time has passed; nothing has to watch for that.
 *
 * @param processes      running processes, by slot
 * @param profitToday    today's forge profit, read the way the dashboard reads it
 * @param claimedAllTime how many processes have been claimed
 * @param loaded         false until trader data has arrived at least once
 */
public record ForgeStatus(List<Process> processes, double profitToday, int claimedAllTime, boolean loaded) {

    public static final ForgeStatus EMPTY = new ForgeStatus(List.of(), 0D, 0, false);

    /**
     * One running forge process.
     *
     * @param name         the product list key, e.g. "Drill Motor (forge)"
     * @param materialCost what the materials cost, for every item it makes together
     * @param timeEnds     epoch millis when it finishes
     */
    public record Process(int slot, String name, int amount, double materialCost, long timeEnds) {

        public boolean isReady(long now) {
            return now >= timeEnds;
        }

        public String displayName() {
            return CraftFlipRanking.displayNameOf(name);
        }
    }

    public static ForgeStatus parse(JsonObject traderData) {
        List<Process> processes = new ArrayList<>();
        JsonElement forge = traderData.get("forge");
        if (forge != null && forge.isJsonArray()) {
            for (JsonElement element : forge.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject process = element.getAsJsonObject();

                String name = asString(process.get("item-name"));
                if (name.isEmpty()) continue;

                processes.add(new Process(
                        (int) asDouble(process.get("slot"), 0D),
                        name,
                        Math.max(1, (int) asDouble(process.get("amount"), 1D)),
                        asDouble(process.get("material-cost"), 0D),
                        (long) asDouble(process.get("time-ends"), 0D)));
            }
        }
        processes.sort(Comparator.comparingInt(Process::slot));

        JsonElement history = traderData.get("forge-history");
        int claimed = history != null && history.isJsonArray() ? history.getAsJsonArray().size() : 0;

        return new ForgeStatus(List.copyOf(processes), profitToday(traderData), claimed, true);
    }

    /**
     * The forge profit of the last day in {@code daily-profits}, which is how the
     * dashboard picks "today". The server writes those keys in date order.
     */
    private static double profitToday(JsonObject traderData) {
        JsonElement dailyProfits = traderData.get("daily-profits");
        if (dailyProfits == null || !dailyProfits.isJsonObject()) return 0D;

        JsonElement lastDay = null;
        for (Map.Entry<String, JsonElement> day : dailyProfits.getAsJsonObject().entrySet()) {
            lastDay = day.getValue();
        }
        if (lastDay == null || !lastDay.isJsonObject()) return 0D;
        return asDouble(lastDay.getAsJsonObject().get("forge-profit"), 0D);
    }

    private static String asString(JsonElement element) {
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static double asDouble(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
