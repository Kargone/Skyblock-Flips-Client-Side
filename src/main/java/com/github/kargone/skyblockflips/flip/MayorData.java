package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The payload of {@code GET /api/mayor-data}: the mayor, the election, the next
 * Spooky Festival, and what the notes in the server's mayor-info.json say to buy,
 * sell or watch right now, each with its price and where that price is heading.
 *
 * The server works all of that out ({@code mayor.mjs}); this only reads it, so the
 * Bazaar window and the dashboard always agree. Times are epoch millis, 0 when
 * unknown.
 */
public record MayorData(
        int skyblockYear,
        String mayor,
        long termEnd,
        String electionLeader,
        long electionEndsAt,
        long spookyStartsAt,
        long spookyEndsAt,
        Price ectoplasmPrice,
        Trend ectoplasmTrend,
        double taxMultiplier,
        List<Reminder> reminders,
        List<Note> notes,
        boolean loaded
) {

    public static final MayorData EMPTY = new MayorData(0, "", 0L, "", 0L, 0L, 0L, null,
            Trend.COLLECTING, 1D, List.of(), List.of(), false);

    /** Top of the Bazaar's book: the buy order price and the insta-buy price. */
    public record Price(double buyOrder, double instaBuy) {
    }

    /**
     * Where a price is heading, for the "bottoming out" hint.
     *
     * @param state  falling, bottoming, rising, flat, or collecting (under a day of data)
     * @param change change against the same time yesterday, 0.05 for +5%
     * @param low    lowest price over the window the server looked at
     */
    public record Trend(String state, double change, double low) {

        public static final Trend COLLECTING = new Trend("collecting", 0D, 0D);
    }

    /**
     * Something the notes say to do.
     *
     * @param timing       during (the mayor in office), before (the election leader),
     *                     or after (the years following a term)
     * @param action       buy, sell or watch
     * @param product      product list name
     * @param price        null when the server has no price for it
     * @param windowStarts for "after": when the watch starts, which is the term's end
     * @param windowEnds   for "after": when the watch ends
     */
    public record Reminder(String timing, String action, String mayor, String product,
                           Price price, Trend trend, long windowStarts, long windowEnds) {
    }

    public record Note(String timing, String mayor, String text) {
    }

    public static MayorData parse(JsonObject root) {
        JsonObject mayor = object(root, "mayor");
        JsonObject election = object(root, "election");
        JsonObject spooky = object(root, "spooky-festival");
        JsonObject ectoplasm = object(root, "ectoplasm");

        List<Reminder> reminders = new ArrayList<>();
        JsonElement reminderArray = root.get("reminders");
        if (reminderArray != null && reminderArray.isJsonArray()) {
            for (JsonElement element : reminderArray.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject reminder = element.getAsJsonObject();
                String product = string(reminder, "product");
                if (product.isEmpty()) continue;

                reminders.add(new Reminder(
                        string(reminder, "timing"),
                        string(reminder, "action"),
                        string(reminder, "mayor"),
                        product,
                        price(object(reminder, "price")),
                        trend(object(reminder, "trend")),
                        (long) number(reminder, "window-starts"),
                        (long) number(reminder, "window-ends")));
            }
        }

        List<Note> notes = new ArrayList<>();
        JsonElement noteArray = root.get("notes");
        if (noteArray != null && noteArray.isJsonArray()) {
            for (JsonElement element : noteArray.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject note = element.getAsJsonObject();
                notes.add(new Note(string(note, "timing"), string(note, "mayor"), string(note, "text")));
            }
        }

        double taxMultiplier = number(root, "tax-multiplier");

        return new MayorData(
                (int) number(root, "sb-year"),
                mayor == null ? "" : string(mayor, "name"),
                mayor == null ? 0L : (long) number(mayor, "term-end"),
                election == null ? "" : string(election, "leader"),
                election == null ? 0L : (long) number(election, "ends-at"),
                spooky == null ? 0L : (long) number(spooky, "starts-at"),
                spooky == null ? 0L : (long) number(spooky, "ends-at"),
                ectoplasm == null ? null : price(object(ectoplasm, "price")),
                ectoplasm == null ? Trend.COLLECTING : trend(object(ectoplasm, "trend")),
                taxMultiplier > 0 ? taxMultiplier : 1D,
                List.copyOf(reminders),
                List.copyOf(notes),
                true);
    }

    private static Price price(JsonObject price) {
        if (price == null) return null;
        return new Price(number(price, "buy-order"), number(price, "insta-buy"));
    }

    private static Trend trend(JsonObject trend) {
        if (trend == null) return Trend.COLLECTING;
        String state = string(trend, "state");
        return new Trend(state.isEmpty() ? "collecting" : state, number(trend, "change"), number(trend, "low"));
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static double number(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return 0D;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return 0D;
        }
    }
}
