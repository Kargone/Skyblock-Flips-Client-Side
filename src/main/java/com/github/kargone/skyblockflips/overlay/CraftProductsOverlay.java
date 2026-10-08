package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.CostBreakdown;
import com.github.kargone.skyblockflips.flip.CraftFlipRanking;
import com.github.kargone.skyblockflips.flip.ForgeStatus;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.ContainerUtils;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The dashboard's "Auction Products" and "Forge Products" tables, in game: one
 * class, built once for each by {@link #auction()} and {@link #forge()}.
 *
 * Each entry is one craftable product - what its materials cost with buy
 * orders, what the crafted item sells for, and the margin - ranked so the best
 * flip is first, laid out down the left column then down the right. The forge
 * table also shows each product's forge time and whether it sells on the Bazaar
 * or the Auction House, and a button beside the title switches it between the
 * top recipes that sell on the Auction House and those that sell on the Bazaar.
 * Above the recipes, the forge panel lists what is forging right now, from the
 * processes the server recorded off the forge menu.
 *
 * The panel can be dragged anywhere and remembers where it was put, and clicking
 * a product name asks the server for its recipe. Both live in
 * {@link AuctionPanelInput}; this class only draws and reports where it drew.
 */
public class CraftProductsOverlay {

    private static final int COLUMN_WIDTH = 185;
    private static final int COLUMN_GAP = 8;
    private static final int PADDING = 5;
    /** Narrower than this and the insta-buy margin is dropped to keep a row on one line. */
    private static final int INSTA_MARGIN_WIDTH = 170;
    /** Header, status and legend lines, which span the whole panel. */
    private static final int SPANNING_LINES = 3;
    /** Gap between a product name and its "have mats" note. */
    private static final int HAVE_GAP = 6;

    private static final int PANEL_BACKGROUND = 0xE0120D1C;
    private static final int PANEL_BORDER = 0xFF6B4CA8;
    private static final int HOVER_BACKGROUND = 0x40FFFFFF;
    private static final int BUTTON_BACKGROUND = 0x30FFFFFF;

    /** Whether the forge panel lists Bazaar-sold recipes rather than Auction House ones. */
    private static volatile boolean forgeShowsBazaar = false;

    private final String title;
    private final String emptyText;
    private final Supplier<List<CraftFlipRanking.RankedProduct>> ranking;
    private final int maxProducts;
    private final PanelPosition.Panel panel;
    private final boolean forgeColumns;
    private final BreakdownWindow breakdownWindow = new BreakdownWindow();

    private CraftProductsOverlay(String title, String emptyText,
                                 Supplier<List<CraftFlipRanking.RankedProduct>> ranking,
                                 int maxProducts, PanelPosition.Panel panel, boolean forgeColumns) {
        this.title = title;
        this.emptyText = emptyText;
        this.ranking = ranking;
        this.maxProducts = maxProducts;
        this.panel = panel;
        this.forgeColumns = forgeColumns;
    }

    public static CraftProductsOverlay auction() {
        return new CraftProductsOverlay("§6§lAUCTION PRODUCTS", "§8No auction products",
                CraftFlipRanking::auctionProducts, 40, PanelPosition.Panel.MAIN, false);
    }

    public static CraftProductsOverlay forge() {
        return new CraftProductsOverlay("§5§lFORGE PRODUCTS", "§8No forge products",
                () -> CraftFlipRanking.forgeProducts(forgeShowsBazaar), 20, PanelPosition.Panel.FORGE, true);
    }

    public static void toggleForgeMarket() {
        forgeShowsBazaar = !forgeShowsBazaar;
    }

    public void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        ServerData.poll();

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();

        List<CraftFlipRanking.RankedProduct> products = ranking.get();
        List<String> forgeLines = forgeColumns ? forgeLines() : List.of();

        int twoColumnWidth = PADDING * 2 + COLUMN_WIDTH * 2 + COLUMN_GAP;
        int columns = guiWidth >= twoColumnWidth + 8 && products.size() > 1 ? 2 : 1;
        int columnWidth = columns == 2
                ? COLUMN_WIDTH
                : Math.min(COLUMN_WIDTH, guiWidth - 8 - PADDING * 2);
        int panelWidth = PADDING * 2 + columnWidth * columns + (columns - 1) * COLUMN_GAP;

        // Two lines per product, and whatever is left after the spanning lines.
        int linesThatFit = (guiHeight - 8 - PADDING * 2) / lineHeight - SPANNING_LINES - forgeLines.size();
        int maxRows = Math.clamp(linesThatFit / 2, 1, ceilDiv(maxProducts, columns));
        int shown = Math.min(products.size(), maxRows * columns);
        int rows = ceilDiv(shown, columns);

        int panelHeight = PADDING * 2 + (SPANNING_LINES + forgeLines.size() + rows * 2) * lineHeight;

        AuctionPanelInput.pollMouse(screen, mouseX, mouseY, guiWidth, guiHeight);

        int panelX;
        int panelY;
        if (PanelPosition.isSet(panel)) {
            panelX = PanelPosition.x(panel);
            panelY = PanelPosition.y(panel);
        } else {
            int[] placement = autoPlace(graphics, screen, panelWidth);
            panelX = placement[0];
            panelY = placement[1];
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, guiWidth - panelWidth));
        panelY = Math.clamp(panelY, 0, Math.max(0, guiHeight - panelHeight));

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);

        int textX = panelX + PADDING;
        int textY = panelY + PADDING;
        graphics.text(font, title, textX, textY, 0xFFFFFFFF, true);
        graphics.text(font, statusLine(), textX, textY + lineHeight, 0xFFFFFFFF, true);

        for (int i = 0; i < forgeLines.size(); i++) {
            graphics.text(font, SkyblockText.fit(forgeLines.get(i), panelWidth - PADDING * 2),
                    textX, textY + lineHeight * (2 + i), 0xFFFFFFFF, true);
        }

        int rowsTop = textY + lineHeight * (2 + forgeLines.size());
        List<AuctionPanelInput.ClickTarget> targets = new ArrayList<>();

        if (forgeColumns) {
            drawMarketToggle(graphics, font, textX + panelWidth - PADDING * 2, textY, lineHeight,
                    mouseX, mouseY, targets);
        }

        for (int i = 0; i < shown; i++) {
            int column = i / rows;
            int row = i % rows;
            int cellX = textX + column * (columnWidth + COLUMN_GAP);
            int cellY = rowsTop + row * lineHeight * 2;

            drawProduct(graphics, font, products.get(i), i + 1, cellX, cellY,
                    columnWidth, lineHeight, mouseX, mouseY, targets);
        }

        if (shown == 0) {
            graphics.text(font, emptyLine(), textX, rowsTop, 0xFFFFFFFF, true);
        }

        String legend = columnWidth >= INSTA_MARGIN_WIDTH
                ? "§8cost > value  buy order/insta §7- click a name for its materials"
                : "§8cost > value  buy order";
        if (forgeColumns) {
            legend = "§6BZ§8/§dAH §8button switches market  " + legend;
        }
        graphics.text(font, SkyblockText.fit(legend, panelWidth - PADDING * 2),
                textX, rowsTop + rows * lineHeight * 2, 0xFFFFFFFF, true);

        List<AuctionPanelInput.PanelBounds> panels = new ArrayList<>(2);
        panels.add(new AuctionPanelInput.PanelBounds(
                panel, panelX, panelY, panelWidth, panelHeight));

        // Drawn after the main panel, so it sits on top and wins a press that lands
        // where the two overlap.
        breakdownWindow.render(graphics, font, mouseX, mouseY, panels.getFirst(), panels, targets);

        AuctionPanelInput.publish(screen, List.copyOf(panels), List.copyOf(targets));
    }

    private void drawProduct(GuiGraphicsExtractor graphics, Font font, CraftFlipRanking.RankedProduct product,
                             int rank, int cellX, int cellY, int columnWidth, int lineHeight,
                             int mouseX, int mouseY, List<AuctionPanelInput.ClickTarget> targets) {
        CostBreakdown breakdown = product.breakdown();

        String haveMats = breakdown.owned() != 0 ? "§8have §b" + SkyblockText.coins(breakdown.owned()) : "";
        if (forgeColumns) {
            // Forge time and where it sells sit after the name, ahead of any "have".
            String forgeNote = "§7" + duration(product.entry().secondsToCraft())
                    + (product.onBazaar() ? " §6BZ" : " §dAH");
            haveMats = haveMats.isEmpty() ? forgeNote : forgeNote + " " + haveMats;
        }
        int haveWidth = haveMats.isEmpty() ? 0 : font.width(SkyblockText.strip(haveMats));

        String rankPrefix = "§8" + rank + ". ";
        int rankWidth = font.width(SkyblockText.strip(rankPrefix));
        int nameBudget = columnWidth - rankWidth - (haveWidth == 0 ? 0 : haveWidth + HAVE_GAP);
        String name = SkyblockText.fit(product.displayName(), nameBudget);

        int titleWidth = rankWidth + font.width(name);
        boolean hovered = mouseX >= cellX && mouseX < cellX + titleWidth
                && mouseY >= cellY && mouseY < cellY + lineHeight;
        if (hovered) {
            graphics.fill(cellX - 1, cellY - 1, cellX + titleWidth + 1, cellY + lineHeight - 1, HOVER_BACKGROUND);
        }

        graphics.text(font, rankPrefix + (hovered ? "§e" : "§f") + name, cellX, cellY, 0xFFFFFFFF, true);
        if (!haveMats.isEmpty()) {
            graphics.text(font, haveMats, cellX + titleWidth + HAVE_GAP, cellY, 0xFFFFFFFF, true);
        }
        targets.add(new AuctionPanelInput.ClickTarget(cellX, cellY, titleWidth, lineHeight,
                AuctionPanelInput.Action.OPEN_BREAKDOWN, product.name(), 0));

        String marginColor = breakdown.margin() >= 0 ? "§a" : "§c";
        String instaColor = breakdown.instaBuyMargin() >= 0 ? "§a" : "§c";
        String instaMargin = columnWidth >= INSTA_MARGIN_WIDTH
                ? "§8/" + instaColor + SkyblockText.signedCoins(breakdown.instaBuyMargin())
                : "";

        graphics.text(font, "§7" + SkyblockText.coins(breakdown.buyOrder())
                        + " §8> §f" + SkyblockText.coins(breakdown.marketValue())
                        + " " + marginColor + SkyblockText.signedCoins(breakdown.margin())
                        + instaMargin,
                cellX, cellY + lineHeight, 0xFFFFFFFF, true);
    }

    /**
     * The "your forge" block: a summary line, then one line per running process
     * with its time left and what it should make once sold. Empty until the trader
     * data has arrived, so a server that is not running costs no space.
     */
    static List<String> forgeLines() {
        ForgeStatus status = ServerData.forge();
        if (!status.loaded()) return List.of();

        long now = System.currentTimeMillis();
        int ready = 0;
        double value = 0D;
        List<String> processLines = new ArrayList<>(status.processes().size());

        for (ForgeStatus.Process process : status.processes()) {
            boolean isReady = process.isReady(now);
            if (isReady) ready++;

            StringBuilder line = new StringBuilder("§8#").append(process.slot())
                    .append(" §f").append(process.displayName());
            if (process.amount() > 1) line.append(" §7x").append(process.amount());
            line.append(isReady ? " §aREADY" : " §e" + countdown(process.timeEnds() - now));

            // Valued the way its ranking row is: market value less the sale fee,
            // which is the margin plus the buy order cost it was taken from.
            CraftFlipRanking.RankedProduct ranked = CraftFlipRanking.find(process.name());
            if (ranked != null) {
                CostBreakdown breakdown = ranked.breakdown();
                double netSale = (breakdown.margin() + breakdown.buyOrder()) * process.amount();
                double profit = netSale - process.materialCost();
                value += breakdown.marketValue() * process.amount();
                line.append(profit >= 0 ? " §a" : " §c").append(SkyblockText.signedCoins(profit));
            }
            processLines.add(line.toString());
        }

        String summary = status.processes().isEmpty()
                ? "§8nothing forging"
                : "§7" + status.processes().size() + " forging"
                        + (ready > 0 ? " §8- §a" + ready + " ready" : "")
                        + " §8- §f" + SkyblockText.coins(value) + " §8value";
        String today = (status.profitToday() >= 0 ? "§a" : "§c") + SkyblockText.signedCoins(status.profitToday());

        List<String> lines = new ArrayList<>(processLines.size() + 1);
        lines.add("§6Your forge §8| " + summary + "  §8today " + today);
        lines.addAll(processLines);
        return lines;
    }

    /** The two largest units of a span of time: "1d 6h", "5h 3m", "2m 10s", "45s". */
    static String countdown(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;

        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds % 60L + "s";
        return seconds + "s";
    }

    /**
     * The AH/BZ switch, right aligned on the title line and styled like the
     * breakdown's multiplier: a shaded chip that lights up under the cursor.
     */
    private void drawMarketToggle(GuiGraphicsExtractor graphics, Font font, int rightEdge, int textY,
                                  int lineHeight, int mouseX, int mouseY,
                                  List<AuctionPanelInput.ClickTarget> targets) {
        boolean bazaar = forgeShowsBazaar;
        String label = bazaar ? "BZ" : "AH";
        int buttonWidth = font.width(label) + 4;
        int buttonX = rightEdge - buttonWidth;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth
                && mouseY >= textY - 1 && mouseY < textY + lineHeight - 1;

        graphics.fill(buttonX, textY - 1, buttonX + buttonWidth, textY + lineHeight - 1,
                hovered ? HOVER_BACKGROUND : BUTTON_BACKGROUND);
        graphics.text(font, (hovered ? "§e" : bazaar ? "§6" : "§d") + label,
                buttonX + 2, textY, 0xFFFFFFFF, true);

        targets.add(new AuctionPanelInput.ClickTarget(buttonX, textY - 1, buttonWidth, lineHeight,
                AuctionPanelInput.Action.FORGE_MARKET, "", 0));
    }

    /**
     * Where the panel sits before it has ever been dragged: in whichever margin
     * beside the menu is roomier, or the top left corner when neither fits.
     */
    static int[] autoPlace(GuiGraphicsExtractor graphics, Screen screen, int panelWidth) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return new int[]{4, 4};
        }

        ContainerUtils.ChestLayout layout = ContainerUtils.layoutOf(container);
        int leftSpace = layout.left() - 10;
        int rightSpace = graphics.guiWidth() - (layout.left() + layout.width()) - 10;

        if (Math.max(leftSpace, rightSpace) < panelWidth) {
            return new int[]{4, 4};
        }
        return new int[]{
                leftSpace >= rightSpace ? layout.left() - panelWidth - 6 : layout.left() + layout.width() + 6,
                Math.max(4, layout.top())
        };
    }

    private String emptyLine() {
        if (!ServerData.isReady()) return "§8Waiting for flip data...";
        if (forgeColumns) return forgeShowsBazaar ? "§8No Bazaar forge products" : "§8No Auction House forge products";
        return emptyText;
    }

    private String statusLine() {
        if (!ServerData.isReady()) {
            return ServerData.lastError().isEmpty()
                    ? "§8connecting to server..."
                    : "§cserver offline §8(" + SkyblockText.fit(ServerData.lastError(), 90) + ")";
        }

        long age = ServerData.ageOfData();
        String freshness = age < 0 ? "§8no data" : "§8updated " + (age / 1000L) + "s ago";
        return ServerData.hasInvestments() ? freshness : freshness + " §8- no mats";
    }

    /** Port of the dashboard's {@code formatDuration}: "8h 0m", "45m", or "-" when unset. */
    private static String duration(int seconds) {
        if (seconds <= 0) return "-";
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }
}
