package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.CraftFlipRanking;
import com.github.kargone.skyblockflips.flip.PositionValues;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Every lot of one investment, the dashboard's sub-orders table for it: how much
 * was bought at each price, most expensive first, with its margin and profit at
 * the current sell price after tax.
 *
 * Opened by clicking an investment in the Current Investments panel or in the big
 * investments of the Market Reminders window, and placed under whichever of the
 * two it came from. Clicking the name in its title opens the item in the Bazaar.
 */
public final class InvestmentBreakdownWindow {

    private static final int WIDTH = 286;
    private static final int PADDING = 5;

    /** Column offsets from the panel's content edge. */
    private static final int PRICE_COLUMN = 48;
    private static final int MARGIN_COLUMN = 102;
    private static final int PROFIT_COLUMN = 158;
    private static final int BOUGHT_COLUMN = 216;

    private static final int PANEL_BACKGROUND = 0xF0140F20;
    private static final int PANEL_BORDER = 0xFF8A6BC8;
    private static final int HOVER_BACKGROUND = 0x40FFFFFF;

    private static final DateTimeFormatter BOUGHT_FORMAT =
            DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault());

    /** {@link PositionValues.InvestmentRow#key()} of the investment on show, or null when closed. */
    private static volatile String openKey = null;

    /** Opens this investment, or closes the window when it is already the one on show. */
    public static void toggle(String key) {
        openKey = key.equals(openKey) ? null : key;
    }

    public static void close() {
        openKey = null;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                       List<AuctionPanelInput.PanelBounds> panels,
                       List<AuctionPanelInput.ClickTarget> targets) {
        String key = openKey;
        if (key == null) return;

        PositionValues.InvestmentRow investment = PositionValues.findInvestment(key);
        if (investment == null) {
            // It dropped out of both lists, e.g. it was sold or fell under the minimum profit.
            close();
            return;
        }

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        List<PositionValues.LotRow> lots = investment.lots();

        // Title, summary and header, then as many lots as fit on screen.
        int fixedLines = 3;
        int maxLots = Math.max(1, (graphics.guiHeight() - 8 - PADDING * 2) / lineHeight - fixedLines - 1);
        int shownLots = Math.min(lots.size(), maxLots);
        boolean truncated = shownLots < lots.size();
        int lines = fixedLines + Math.max(1, shownLots) + (truncated ? 1 : 0);
        int panelHeight = PADDING * 2 + lines * lineHeight;

        int width = Math.min(WIDTH, graphics.guiWidth() - 8);
        int panelX;
        int panelY;
        if (PanelPosition.isSet(PanelPosition.Panel.INVESTMENT_BREAKDOWN)) {
            panelX = PanelPosition.x(PanelPosition.Panel.INVESTMENT_BREAKDOWN);
            panelY = PanelPosition.y(PanelPosition.Panel.INVESTMENT_BREAKDOWN);
        } else {
            PanelPosition.Panel source = PositionValues.isBig(key)
                    ? PanelPosition.Panel.REMINDERS : PanelPosition.Panel.INVESTMENTS;
            AuctionPanelInput.PanelBounds anchor = find(panels, source);
            panelX = anchor == null ? 4 : anchor.x();
            panelY = anchor == null ? 4 : autoPlaceY(graphics, anchor, panelHeight);
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, graphics.guiWidth() - width));
        panelY = Math.clamp(panelY, 0, Math.max(0, graphics.guiHeight() - panelHeight));

        graphics.fill(panelX, panelY, panelX + width, panelY + panelHeight, PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, width, panelHeight, PANEL_BORDER);

        int textX = panelX + PADDING;
        int rowY = panelY + PADDING;

        drawTitle(graphics, font, investment, textX, rowY, width, lineHeight, mouseX, mouseY, targets);
        rowY += lineHeight;

        boolean priced = investment.sellPrice() != 0D;
        double totalCost = 0D;
        for (PositionValues.LotRow lot : lots) totalCost += lot.amount() * lot.price();
        double averageCost = investment.amount() == 0D ? 0D : totalCost / investment.amount();
        String summary = "§7" + amount(investment.amount()) + " held §8| §7avg §f" + SkyblockText.coins(averageCost)
                + " §8> §f" + (priced ? SkyblockText.coins(investment.sellPrice()) : "-")
                + " §8| " + (priced ? signed(investment.profit()) : "§8N/A") + " §8(all lots)";
        graphics.text(font, SkyblockText.fit(summary, width - PADDING * 2), textX, rowY, 0xFFFFFFFF, true);
        rowY += lineHeight;

        graphics.text(font, "§8amount", textX, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8price paid", textX + PRICE_COLUMN, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8margin", textX + MARGIN_COLUMN, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8profit", textX + PROFIT_COLUMN, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8bought", textX + BOUGHT_COLUMN, rowY, 0xFFFFFFFF, true);
        rowY += lineHeight;

        if (lots.isEmpty()) {
            graphics.text(font, "§8No sub-orders", textX, rowY, 0xFFFFFFFF, true);
        }
        for (int i = 0; i < shownLots; i++) {
            PositionValues.LotRow lot = lots.get(i);
            graphics.text(font, "§f" + amount(lot.amount()), textX, rowY, 0xFFFFFFFF, true);
            graphics.text(font, "§7" + SkyblockText.coins(lot.price()), textX + PRICE_COLUMN, rowY, 0xFFFFFFFF, true);
            graphics.text(font, priced ? signed(lot.margin()) : "§8N/A", textX + MARGIN_COLUMN, rowY, 0xFFFFFFFF, true);
            graphics.text(font, priced ? signed(lot.profit()) : "§8N/A", textX + PROFIT_COLUMN, rowY, 0xFFFFFFFF, true);
            graphics.text(font, lot.timeBought() > 0
                            ? "§7" + BOUGHT_FORMAT.format(Instant.ofEpochMilli(lot.timeBought())) : "§8-",
                    textX + BOUGHT_COLUMN, rowY, 0xFFFFFFFF, true);
            rowY += lineHeight;
        }
        if (truncated) {
            graphics.text(font, "§8+" + (lots.size() - shownLots) + " more lots", textX, rowY, 0xFFFFFFFF, true);
        }

        panels.add(new AuctionPanelInput.PanelBounds(
                PanelPosition.Panel.INVESTMENT_BREAKDOWN, panelX, panelY, width, panelHeight));
    }

    private void drawTitle(GuiGraphicsExtractor graphics, Font font, PositionValues.InvestmentRow investment,
                           int textX, int textY, int width, int lineHeight,
                           int mouseX, int mouseY, List<AuctionPanelInput.ClickTarget> targets) {
        String close = "§c[x]";
        int closeWidth = font.width(SkyblockText.strip(close));
        int closeX = textX + width - PADDING * 2 - closeWidth;

        String type = investment.longTerm() ? "§dLong" : "§7Short";
        int typeWidth = font.width(SkyblockText.strip(type));
        int typeX = closeX - 6 - typeWidth;

        String name = SkyblockText.fit(investment.name(), typeX - 8 - textX);
        int nameWidth = font.width(name);
        boolean hovered = hovering(mouseX, mouseY, textX, textY, nameWidth, lineHeight);
        if (hovered) {
            graphics.fill(textX - 1, textY - 1, textX + nameWidth + 1, textY + lineHeight - 1, HOVER_BACKGROUND);
        }

        graphics.text(font, (hovered ? "§e§l" : "§6§l") + name, textX, textY, 0xFFFFFFFF, true);
        graphics.text(font, type, typeX, textY, 0xFFFFFFFF, true);
        graphics.text(font, close, closeX, textY, 0xFFFFFFFF, true);

        targets.add(new AuctionPanelInput.ClickTarget(textX, textY, nameWidth, lineHeight,
                AuctionPanelInput.Action.BAZAAR, CraftFlipRanking.displayNameOf(investment.name()), 0));
        targets.add(new AuctionPanelInput.ClickTarget(closeX, textY, closeWidth, lineHeight,
                AuctionPanelInput.Action.CLOSE_INVESTMENT, "", 0));
    }

    private static AuctionPanelInput.PanelBounds find(List<AuctionPanelInput.PanelBounds> panels,
                                                      PanelPosition.Panel panel) {
        for (AuctionPanelInput.PanelBounds bounds : panels) {
            if (bounds.panel() == panel) return bounds;
        }
        return null;
    }

    /** Sits under the panel it was opened from, or above it when there is no room below. */
    private int autoPlaceY(GuiGraphicsExtractor graphics, AuctionPanelInput.PanelBounds anchor, int height) {
        int below = anchor.y() + anchor.height() + 4;
        if (below + height <= graphics.guiHeight() - 4) return below;

        int above = anchor.y() - height - 4;
        if (above >= 4) return above;

        return Math.max(4, graphics.guiHeight() - height - 4);
    }

    private static String signed(double coins) {
        return (coins >= 0 ? "§a" : "§c") + SkyblockText.signedCoins(coins);
    }

    private static String amount(double amount) {
        return String.format(Locale.ROOT, "%,d", Math.round(amount));
    }

    private boolean hovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
