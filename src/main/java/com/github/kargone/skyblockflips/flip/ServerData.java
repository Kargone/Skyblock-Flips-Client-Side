package com.github.kargone.skyblockflips.flip;

import com.github.kargone.skyblockflips.SkyblockHttpClient;
import com.github.kargone.skyblockflips.util.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulls the flip data out of the local Skyblock Flips server, on the same
 * endpoints the web dashboard uses.
 *
 * Refreshing is lazy and only happens while something is actually asking to draw
 * the panel: {@link #poll()} is called from the render path, notices which of the
 * three feeds has gone stale, and hands the work to one background thread. The
 * render path itself never touches the network and never parses JSON.
 */
public final class ServerData {

    /** Where the server is, from {@code serverUrl} in the mod's config file. */
    public static String baseUrl() {
        return ModConfig.serverUrl();
    }

    /** The product list is a static file on the server; it barely ever changes. */
    private static final long PRODUCT_LIST_TTL_MS = 10 * 60 * 1000L;
    /** Matches the dashboard's own bazaar refresh interval. */
    private static final long MARKET_TTL_MS = 60 * 1000L;
    /** Only changes when a trade happens, so this can be slower than the dashboard's 1s poll. */
    private static final long TRADER_TTL_MS = 15 * 1000L;
    /** Mayor reminders: prices move slowly and the timers count down locally. */
    private static final long MAYOR_TTL_MS = 60 * 1000L;
    /** How long to stay quiet after the server turns out not to be running. */
    private static final long BACKOFF_MS = 30 * 1000L;

    private static final SkyblockHttpClient HTTP = new SkyblockHttpClient();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkyblockFlips-ServerData");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private static volatile ProductList productList = ProductList.EMPTY;
    private static volatile MarketData marketData = MarketData.EMPTY;
    private static volatile InventorySnapshot investments = InventorySnapshot.EMPTY;
    private static volatile ForgeStatus forgeStatus = ForgeStatus.EMPTY;
    private static volatile MayorData mayorData = MayorData.EMPTY;

    private static volatile long productListFetchedAt = 0L;
    private static volatile long marketFetchedAt = 0L;
    private static volatile long traderFetchedAt = 0L;
    private static volatile long mayorFetchedAt = 0L;
    private static volatile String lastMayorError = "";
    private static volatile long lastSuccessAt = 0L;
    private static volatile long quietUntil = 0L;
    private static volatile String lastError = "";

    private ServerData() {
    }

    /** True once at least the product list and market data have arrived. */
    public static boolean isReady() {
        return !productList.isEmpty() && !marketData.isEmpty();
    }

    /** Milliseconds since the last successful refresh, or -1 when nothing has landed yet. */
    public static long ageOfData() {
        return lastSuccessAt == 0L ? -1L : System.currentTimeMillis() - lastSuccessAt;
    }

    /** Last failure message, for the "server offline" line in the panel. */
    public static String lastError() {
        return lastError;
    }

    /** Latest market data, {@link MarketData#EMPTY} until the first refresh lands. */
    public static MarketData market() {
        return marketData;
    }

    /** Mayor reminders and timers; {@link MayorData#EMPTY} until they arrive. */
    public static MayorData mayor() {
        return mayorData;
    }

    /** What is forging, from the trader data; {@link ForgeStatus#EMPTY} until it arrives. */
    public static ForgeStatus forge() {
        return forgeStatus;
    }

    /**
     * Fetches the trader data on the next poll instead of waiting out its TTL, so
     * a forge process shows up as soon as the server has recorded it.
     */
    public static void refreshTraderDataSoon() {
        traderFetchedAt = 0L;
        quietUntil = 0L;
    }

    public static boolean hasInvestments() {
        return !investments.isEmpty();
    }

    /**
     * Refreshes anything stale, in the background. Safe and cheap to call every
     * frame: it returns immediately unless a feed is actually due.
     */
    public static void poll() {
        long now = System.currentTimeMillis();
        if (now < quietUntil) return;

        boolean productListDue = now - productListFetchedAt > PRODUCT_LIST_TTL_MS;
        boolean marketDue = now - marketFetchedAt > MARKET_TTL_MS;
        boolean traderDue = now - traderFetchedAt > TRADER_TTL_MS;
        boolean mayorDue = now - mayorFetchedAt > MAYOR_TTL_MS;
        if (!productListDue && !marketDue && !traderDue && !mayorDue) return;

        if (!REFRESHING.compareAndSet(false, true)) return;

        WORKER.execute(() -> {
            try {
                refresh(productListDue, marketDue, traderDue, mayorDue);
            } finally {
                REFRESHING.set(false);
            }
        });
    }

    /** Drops everything, e.g. on disconnect, so stale prices are never shown as current. */
    public static void clear() {
        productList = ProductList.EMPTY;
        marketData = MarketData.EMPTY;
        investments = InventorySnapshot.EMPTY;
        forgeStatus = ForgeStatus.EMPTY;
        mayorData = MayorData.EMPTY;
        mayorFetchedAt = 0L;
        productListFetchedAt = 0L;
        marketFetchedAt = 0L;
        traderFetchedAt = 0L;
        lastSuccessAt = 0L;
        quietUntil = 0L;
        lastError = "";
        CraftFlipRanking.clear();
    }

    private static void refresh(boolean productListDue, boolean marketDue, boolean traderDue, boolean mayorDue) {
        long now = System.currentTimeMillis();
        boolean changed = mayorDue && refreshMayor(now);

        try {
            if (productListDue) {
                productList = ProductList.parse(getJson("/api/product-list"));
                productListFetchedAt = now;
                changed = true;
            }
            if (marketDue) {
                marketData = MarketData.parse(getJson("/api/market-data"));
                marketFetchedAt = now;
                changed = true;
            }
            if (traderDue) {
                JsonObject traderData = getJson("/api/trader-data");
                investments = InventorySnapshot.parse(traderData);
                forgeStatus = ForgeStatus.parse(traderData);
                traderFetchedAt = now;
                changed = true;
            }

            lastError = "";
            lastSuccessAt = now;

            if (changed) {
                CraftFlipRanking.recompute(productList, marketData, investments);
            }
        } catch (Exception e) {
            // The server not running is the normal case when the mod is used alone,
            // so this backs off quietly instead of retrying every frame.
            quietUntil = now + BACKOFF_MS;
            String message = e.getMessage();
            String describe = message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
            if (!describe.equals(lastError)) {
                lastError = describe;
                System.out.println("[SkyblockFlips] Flip data unavailable: " + describe);
            }
        }
    }

    /**
     * Fetches the mayor reminders. Kept apart from the other feeds, so a server too
     * old to have the endpoint costs only this panel, not the flip data.
     *
     * @return true when Derpy's tax multiplier changed, which changes every margin
     */
    private static boolean refreshMayor(long now) {
        mayorFetchedAt = now;
        try {
            MayorData fetched = MayorData.parse(getJson("/api/mayor-data"));
            mayorData = fetched;

            if (SkyblockFees.derpyTaxMultiplier != fetched.taxMultiplier()) {
                SkyblockFees.derpyTaxMultiplier = fetched.taxMultiplier();
                return true;
            }
            lastMayorError = "";
        } catch (Exception e) {
            String message = String.valueOf(e.getMessage());
            if (!message.equals(lastMayorError)) {
                lastMayorError = message;
                System.out.println("[SkyblockFlips] Mayor data unavailable: " + message);
            }
        }
        return false;
    }

    private static JsonObject getJson(String path) throws Exception {
        String body = HTTP.sendGET(baseUrl() + path);
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("empty response from " + path);
        }

        var parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("unexpected response from " + path);
        }
        return parsed.getAsJsonObject();
    }
}
