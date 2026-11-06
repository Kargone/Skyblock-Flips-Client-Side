package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.Recommendation;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * The Bazaar's optimizer window: the orders the server's optimizer says to place
 * next, from {@code /api/recommendation}, most expected profit first. Everything in
 * it is worked out by the optimizer; this only lays it out.
 *
 * Sits under the Current Investments panel by default. Clicking an item opens it in
 * the Bazaar, ready to fill in a buy order's amount on the sign, and the window can
 * be dragged anywhere and remembers where it was put.
 */
public class OptimizerOverlay {

    private static final int PADDING = 5;
    private static final int MIN_WIDTH = 160;

    /** The title, summary, table header and "more orders" lines, which are always kept on screen. */
    private static final int FIXED_LINES = 4;

    /** The optimizer writes every 30 seconds, so this much older means it has stopped. */
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
        List<Recommendation.Order> orders = recommendation.orders();
        int maxOrders = Math.max(1, (guiHeight - 8 - PADDING * 2) / lineHeight - FIXED_LINES);
        int shownOrders = Math.min(orders.size(), maxOrders);
        Table table = orderTable(orders.subList(0, shownOrders));

        String title = "§6§lBAZAAR OPTIMIZER";
        String age = recommendation.available() ? ageText(recommendation.generatedAt()) : "";
        String summary = recommendation.available() ? summary(recommendation) : null;
        String status = statusLine(recommendation);
        String more = shownOrders < orders.size() ? "§8+" + (orders.size() - shownOrders) + " more orders" : null;

        int contentWidth = Math.max(MIN_WIDTH, font.width(title) + 8 + font.width(age));
        if (summary != null) contentWidth = Math.max(contentWidth, font.width(summary));
        if (status == null) contentWidth = Math.max(contentWidth, table.width(font, maxContentWidth));
        contentWidth = Math.min(contentWidth, maxContentWidth);
        int width = contentWidth + PADDING * 2;

        int lines = 1 + (summary == null ? 0 : 1) + (more == null ? 0 : 1);
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
            rowY += table.height(lineHeight);
        }
        if (more != null) {
            graphics.text(font, more, textX, rowY, 0xFFFFFFFF, true);
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
    private static String statusLine(Recommendation recommendation) {
        if (!recommendation.loaded()) {
            return ServerData.lastError().isEmpty() ? "§8waiting for the optimizer..." : "§cserver offline";
        }
        if (!recommendation.available()) return "§8The optimizer hasn't run yet";
        return recommendation.orders().isEmpty() ? "§8No orders to place" : null;
    }

    /** What the optimizer had to work with, and what it expects from the whole list. */
    private static String summary(Recommendation recommendation) {
        return "§7Purse §f" + SkyblockText.coins(recommendation.purse())
                + " §8| §7Slots §f" + recommendation.availableSlots()
                + " §8| §7Cap left §f" + SkyblockText.coins(recommendation.dailyCapRemaining())
                + " §8| §7Profit " + signed(recommendation.totalExpectedProfit());
    }

    /** How old the list is; red once the optimizer looks to have stopped, since its orders are out of date. */
    private static String ageText(long generatedAt) {
        long age = Math.max(0L, System.currentTimeMillis() - generatedAt);
        String text = CraftProductsOverlay.countdown(age) + " ago";
        return (age > STALE_MS ? "§c" : "§8") + text;
    }

    private static Table orderTable(List<Recommendation.Order> orders) {
        Table table = new Table("Item", "Order", "Amount", "Price", "Exp. fill", "Exp. profit");
        for (Recommendation.Order order : orders) {
            boolean sell = "sell".equals(order.action());
            table.add(new Table.Row(List.of(
                    order.productName(),
                    sell ? "§csell" : "§abuy",
                    "§f" + SkyblockText.coins(order.quantity()),
                    "§f" + SkyblockText.coins(order.orderPrice()),
                    "§7" + SkyblockText.coins(order.expectedFill()),
                    signed(order.expectedProfit())),
                    // A sell offer lists the whole holding and its only sign is the
                    // price, so only a buy order has an amount to fill in.
                    AuctionPanelInput.Action.BAZAAR, order.productName(), sell ? 0 : (int) order.quantity()));
        }
        return table;
    }

    private static String signed(double amount) {
        return (amount >= 0 ? "§a" : "§c") + SkyblockText.signedCoins(amount);
    }
}
