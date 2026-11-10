package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.MarketData;
import com.github.kargone.skyblockflips.flip.ProductEntry;
import com.github.kargone.skyblockflips.flip.Recommendation;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * The Bazaar's optimizer window: the buy orders the server's optimizer ranks best,
 * from {@code /api/recommendation}. The orders come grouped by order size bucket;
 * this shows the top few of each (see {@link #BUCKETS}), smallest first, with a line
 * between buckets. The ranking is the optimizer's; this only lays it out.
 *
 * Sits under the Current Investments panel by default. Clicking an item opens it in
 * the Bazaar, ready to fill in a buy order's amount on the sign, and the window can
 * be dragged anywhere and remembers where it was put.
 */
public class OptimizerOverlay {

    private static final int PADDING = 5;
    private static final int MIN_WIDTH = 160;

    /** The buckets in the order they are shown, by the optimizer's {@code display_bucket} names. */
    private static final List<Bucket> BUCKETS = List.of(
            new Bucket("1k+500", 5),
            new Bucket("2k", 5),
            new Bucket("4k", 7),
            new Bucket("7k", 7));

    /** One bucket and how many of its top orders are shown. */
    private record Bucket(String name, int shown) {
    }

    /** The optimizer writes every few seconds, so this much older means it has stopped. */
    private static final long STALE_MS = 3 * 60 * 1000L;

    private static final int PANEL_BACKGROUND = 0xE0120D1C;
    private static final int PANEL_BORDER = 0xFF6B4CA8;

    public void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY,
                       List<AuctionPanelInput.PanelBounds> panels, List<AuctionPanelInput.ClickTarget> targets) {
        ServerData.poll();

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int maxContentWidth = guiWidth - 8 - PADDING * 2;

        Recommendation recommendation = ServerData.recommendation();
        Table table = orderTable(recommendation.orders());

        String title = "§6§lBAZAAR OPTIMIZER";
        String age = recommendation.available() ? ageText(recommendation.generatedAt()) : "";
        String summary = recommendation.available() ? summary(recommendation) : null;
        String status = statusLine(recommendation, table);

        int contentWidth = Math.max(MIN_WIDTH, font.width(title) + 8 + font.width(age));
        if (summary != null) contentWidth = Math.max(contentWidth, font.width(summary));
        if (status == null) contentWidth = Math.max(contentWidth, table.width(font, maxContentWidth));
        contentWidth = Math.min(contentWidth, maxContentWidth);
        int width = contentWidth + PADDING * 2;

        int lines = 1 + (summary == null ? 0 : 1);
        int height = PADDING * 2 + lines * lineHeight + (status == null ? table.height(lineHeight) : lineHeight);

        int panelX;
        int panelY;
        if (PanelPosition.isSet(PanelPosition.Panel.OPTIMIZER)) {
            panelX = PanelPosition.x(PanelPosition.Panel.OPTIMIZER);
            panelY = PanelPosition.y(PanelPosition.Panel.OPTIMIZER);
        } else {
            int[] placement = CraftProductsOverlay.placeBeside(graphics, screen, width, false);
            panelX = placement[0];
            panelY = belowInvestments(panels, placement[1]);
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, guiWidth - width));
        panelY = Math.clamp(panelY, 0, Math.max(0, guiHeight - height));

        graphics.fill(panelX, panelY, panelX + width, panelY + height, PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, width, height, PANEL_BORDER);

        int textX = panelX + PADDING;
        int rowY = panelY + PADDING;
        graphics.text(font, title, textX, rowY, 0xFFFFFFFF, true);
        graphics.text(font, age, textX + contentWidth - font.width(age), rowY, 0xFFFFFFFF, true);
        rowY += lineHeight;

        if (summary != null) {
            graphics.text(font, SkyblockText.fit(summary, contentWidth), textX, rowY, 0xFFFFFFFF, true);
            rowY += lineHeight;
        }

        if (status != null) {
            graphics.text(font, SkyblockText.fit(status, contentWidth), textX, rowY, 0xFFFFFFFF, true);
        } else {
            table.draw(graphics, font, textX, rowY, contentWidth, lineHeight, mouseX, mouseY, targets);
        }

        panels.add(new AuctionPanelInput.PanelBounds(PanelPosition.Panel.OPTIMIZER, panelX, panelY, width, height));
    }

    /** Just under the Current Investments panel, wherever that is, or the fallback without one. */
    private static int belowInvestments(List<AuctionPanelInput.PanelBounds> panels, int fallback) {
        for (AuctionPanelInput.PanelBounds bounds : panels) {
            if (bounds.panel() == PanelPosition.Panel.INVESTMENTS) return bounds.y() + bounds.height() + 4;
        }
        return fallback;
    }

    /** The line shown in place of the table, or null when there is a table to show. */
    private static String statusLine(Recommendation recommendation, Table table) {
        if (!recommendation.loaded()) {
            return ServerData.lastError().isEmpty() ? "§8waiting for the optimizer..." : "§cserver offline";
        }
        if (!recommendation.available()) return "§8The optimizer hasn't run yet";
        return table.isEmpty() ? "§8No orders to place" : null;
    }

    /** How much of the daily cap is left, and how many orders pass the optimizer's checks. */
    private static String summary(Recommendation recommendation) {
        return "§7Cap left §f" + SkyblockText.coins(recommendation.dailyCapRemaining())
                + " §8| §7Passing §f" + recommendation.qualified();
    }

    /** How old the list is; red once the optimizer looks to have stopped, since its orders are out of date. */
    private static String ageText(long generatedAt) {
        long age = Math.max(0L, System.currentTimeMillis() - generatedAt);
        String text = CraftProductsOverlay.countdown(age) + " ago";
        return (age > STALE_MS ? "§c" : "§8") + text;
    }

    /**
     * The top orders of each bucket, in the optimizer's order: the ones that pass
     * its checks first, best margin first. When a bucket has fewer than
     * its share that pass, the failing ones fill in, greyed out.
     */
    private static Table orderTable(List<Recommendation.Order> orders) {
        Table table = new Table("Item", "Order", "Amount", "Buy", "Sell", "Profit/unit", "Vol 7d buy / sell");
        for (Bucket bucket : BUCKETS) {
            List<Recommendation.Order> top = orders.stream()
                    .filter(order -> bucket.name().equals(order.bucket()))
                    .limit(bucket.shown())
                    .toList();
            if (top.isEmpty()) continue;

            table.addBreak();
            for (Recommendation.Order order : top) table.add(row(order));
        }
        return table;
    }

    private static Table.Row row(Recommendation.Order order) {
        boolean sell = "sell".equals(order.action());
        boolean passed = order.qualified();
        return new Table.Row(List.of(
                order.productName(),
                passed ? (sell ? "§csell" : "§abuy") : "§8" + order.action(),
                (passed ? "§f" : "§8") + SkyblockText.coins(order.quantity()),
                (passed ? "§f" : "§8") + SkyblockText.coins(order.orderPrice()),
                order.sellPrice() > 0 ? (passed ? "§f" : "§8") + SkyblockText.coins(order.sellPrice()) : "§8-",
                passed ? signed(order.unitProfit()) : "§8" + SkyblockText.signedCoins(order.unitProfit()),
                volume(order.productName(), passed)),
                // A sell offer lists the whole holding and its only sign is the
                // price, so only a buy order has an amount to fill in.
                AuctionPanelInput.Action.BAZAAR, order.productName(), sell ? 0 : (int) order.quantity());
    }

    /**
     * The dashboard's Volume column: units bought / sold on the Bazaar over the last
     * 7 days, from Hypixel's quick status. A dash until the market data arrives.
     */
    private static String volume(String productName, boolean passed) {
        ProductEntry entry = ServerData.products().get(productName);
        MarketData.BazaarQuote quote = entry == null || !entry.hasApiName()
                ? null : ServerData.market().bazaarQuote(entry.apiName());
        if (quote == null) return "§8-";
        String color = passed ? "§7" : "§8";
        return color + SkyblockText.coins(quote.buyMovingWeek()) + " §8/ " + color + SkyblockText.coins(quote.sellMovingWeek());
    }

    private static String signed(double amount) {
        return (amount >= 0 ? "§a" : "§c") + SkyblockText.signedCoins(amount);
    }
}
