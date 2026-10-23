package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.PositionValues;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.ModConfig;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * The dashboard's "Active Auctions" and "Current Investments" tabs, in game: one
 * class, built once for each by {@link #activeAuctions()} and {@link #investments()}.
 *
 * Active auctions sit to the right of the Auction House menu; short term
 * investments making at least the configured minimum profit sit to the left of
 * the Bazaar. Long term investments are left out of that panel; big ones are
 * listed in the Market Reminders window instead. Both panels can be dragged anywhere and remember where they were put.
 */
public class PositionsOverlay {

    private static final int PADDING = 5;
    private static final int MIN_WIDTH = 160;

    private static final int PANEL_BACKGROUND = 0xE0120D1C;
    private static final int PANEL_BORDER = 0xFF6B4CA8;

    private static final DateTimeFormatter BOUGHT_FORMAT = DateTimeFormatter.ofPattern("M/d").withZone(ZoneId.systemDefault());

    private final String title;
    private final PanelPosition.Panel panel;
    private final boolean rightSide;
    private final Supplier<Table> table;
    private final Supplier<String> summary;
    private final String emptyText;

    private PositionsOverlay(String title, PanelPosition.Panel panel, boolean rightSide,
                             Supplier<Table> table, Supplier<String> summary, String emptyText) {
        this.title = title;
        this.panel = panel;
        this.rightSide = rightSide;
        this.table = table;
        this.summary = summary;
        this.emptyText = emptyText;
    }

    public static PositionsOverlay activeAuctions() {
        return new PositionsOverlay("§6§lACTIVE AUCTIONS", PanelPosition.Panel.ACTIVE_AUCTIONS, true,
                () -> auctionTable(PositionValues.auctions()),
                PositionsOverlay::auctionSummary,
                "§8No active auctions");
    }

    public static PositionsOverlay investments() {
        return new PositionsOverlay("§a§lCURRENT INVESTMENTS", PanelPosition.Panel.INVESTMENTS, false,
                () -> investmentTable(PositionValues.investments()),
                () -> "§8profit over " + SkyblockText.coins(ModConfig.minInvestmentProfit()),
                "§8No investments over the minimum profit");
    }

    public void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY,
                       List<AuctionPanelInput.PanelBounds> panels, List<AuctionPanelInput.ClickTarget> targets) {
        ServerData.poll();

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int maxContentWidth = guiWidth - 8 - PADDING * 2;

        Table rows = table.get();
        String status = statusLine(rows);
        String titleRight = PositionValues.loaded() ? summary.get() : "";

        int titleWidth = font.width(SkyblockText.strip(title)) + 8 + font.width(SkyblockText.strip(titleRight));
        int contentWidth = Math.max(Math.max(MIN_WIDTH, titleWidth), status == null ? rows.width(font, maxContentWidth) : 0);
        contentWidth = Math.min(contentWidth, maxContentWidth);
        int width = contentWidth + PADDING * 2;
        int height = PADDING * 2 + lineHeight + (status == null ? rows.height(lineHeight) : lineHeight);

        int panelX;
        int panelY;
        if (PanelPosition.isSet(panel)) {
            panelX = PanelPosition.x(panel);
            panelY = PanelPosition.y(panel);
        } else {
            int[] placement = CraftProductsOverlay.placeBeside(graphics, screen, width, rightSide);
            panelX = placement[0];
            panelY = placement[1];
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, guiWidth - width));
        panelY = Math.clamp(panelY, 0, Math.max(0, guiHeight - height));

        graphics.fill(panelX, panelY, panelX + width, panelY + height, PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, width, height, PANEL_BORDER);

        int textX = panelX + PADDING;
        int textY = panelY + PADDING;
        graphics.text(font, title, textX, textY, 0xFFFFFFFF, true);
        graphics.text(font, titleRight, textX + contentWidth - font.width(SkyblockText.strip(titleRight)),
                textY, 0xFFFFFFFF, true);

        if (status != null) {
            graphics.text(font, SkyblockText.fit(status, contentWidth), textX, textY + lineHeight, 0xFFFFFFFF, true);
        } else {
            rows.draw(graphics, font, textX, textY + lineHeight, contentWidth, lineHeight, mouseX, mouseY, targets);
        }

        panels.add(new AuctionPanelInput.PanelBounds(panel, panelX, panelY, width, height));
    }

    /** The line shown in place of the table, or null when there is a table to show. */
    private String statusLine(Table rows) {
        if (!PositionValues.loaded()) {
            return ServerData.lastError().isEmpty() ? "§8waiting for trader data..." : "§cserver offline";
        }
        return rows.isEmpty() ? emptyText : null;
    }

    private static String auctionSummary() {
        long sold = PositionValues.auctions().stream().filter(PositionValues.AuctionRow::sold).count();
        long active = PositionValues.auctions().size() - sold;
        return "§7" + active + " active" + (sold > 0 ? " §8- §a" + sold + " sold" : "");
    }

    /**
     * The Active Auctions tab: what each listing sells for against what it cost,
     * with what it would make relisted at the lowest BIN in brackets. Clicking a
     * name searches the Auction House for it.
     */
    static Table auctionTable(List<PositionValues.AuctionRow> auctions) {
        Table table = new Table("Item", "lBIN", "Selling", "Cost", "Profit (relisted)", "Status");
        for (PositionValues.AuctionRow auction : auctions) {
            table.add(new Table.Row(List.of(
                    auction.itemName(),
                    "§f" + SkyblockText.coins(auction.lowestBin()),
                    "§f" + SkyblockText.coins(auction.priceSelling()),
                    "§7" + SkyblockText.coins(auction.materialCost()),
                    signed(auction.profit()) + " §8(" + signed(auction.relistProfit()) + "§8)",
                    auction.sold() ? "§aSold" : "§eActive"),
                    AuctionPanelInput.Action.AH_SEARCH, searchName(auction.itemName())));
        }
        return table;
    }

    /**
     * The Current Investments tab: profit as if every lot cost the highest price,
     * with the profit over the lots at what they really cost in brackets. Clicking
     * a name opens its lots in {@link InvestmentBreakdownWindow}.
     */
    static Table investmentTable(List<PositionValues.InvestmentRow> investments) {
        Table table = new Table("Item", "Type", "Amount", "Highest", "Sell", "Margin", "Profit (all lots)", "Bought");
        for (PositionValues.InvestmentRow investment : investments) {
            boolean priced = investment.sellPrice() != 0D;
            table.add(new Table.Row(List.of(
                    investment.name(),
                    investment.longTerm() ? "§dLong" : "§7Short",
                    "§f" + SkyblockText.coins(investment.amount()),
                    "§7" + SkyblockText.coins(investment.highestPrice()),
                    priced ? "§f" + SkyblockText.coins(investment.sellPrice()) : "§8-",
                    priced ? signed(investment.margin()) : "§8N/A",
                    priced ? signed(investment.profitAtHighest()) + " §8(" + signed(investment.profit()) + "§8)" : "§8N/A",
                    investment.boughtAt() > 0 ? "§7" + BOUGHT_FORMAT.format(Instant.ofEpochMilli(investment.boughtAt())) : "§8-"),
                    AuctionPanelInput.Action.OPEN_INVESTMENT, investment.key()));
        }
        return table;
    }

    private static String signed(double amount) {
        return (amount >= 0 ? "§a" : "§c") + SkyblockText.signedCoins(amount);
    }

    /** "Beastmaster Crest - EPIC" searches for "Beastmaster Crest"; the tier is not in the item's name. */
    private static String searchName(String itemName) {
        int tier = itemName.lastIndexOf(" - ");
        return tier > 0 ? itemName.substring(0, tier) : itemName;
    }
}
