package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.CostBreakdown;
import com.github.kargone.skyblockflips.flip.CraftFlipRanking;
import com.github.kargone.skyblockflips.flip.MaterialCost;
import com.github.kargone.skyblockflips.flip.RecipeTally;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The material breakdown for one product, with its recipe tree flattened.
 *
 * Under the title sit the craftable steps of the recipe - every material with a
 * recipe of its own, found recursively - and clicking one runs {@code /viewrecipe}
 * for it. Below those is the tally of what actually has to be bought, summed
 * across the whole tree, and a total line.
 *
 * The {@code x1} button next to the title scales every quantity and cost for that
 * many crafts: right click raises it, left click lowers it, and it is back to 1
 * whenever a different product is opened. Clicking a material to buy it carries
 * the scaled quantity through to the amount sign.
 */
public final class BreakdownWindow {

    private static final int WIDTH = 286;
    private static final int PADDING = 5;

    /** Column offsets from the panel's content edge. */
    private static final int QTY_COLUMN = 112;
    private static final int COST_COLUMN = 148;
    private static final int HAVE_COLUMN = 238;

    /** How far each level of the recipe tree is indented. */
    private static final int DEPTH_INDENT = 6;

    private static final int MAX_MULTIPLIER = 9999;

    private static final int PANEL_BACKGROUND = 0xF0140F20;
    private static final int PANEL_BORDER = 0xFF8A6BC8;
    private static final int HOVER_BACKGROUND = 0x40FFFFFF;
    private static final int BUTTON_BACKGROUND = 0x30FFFFFF;

    /** Product list key of the product on show, or null when the window is closed. */
    private static volatile String openProduct = null;

    /** How many crafts the quantities and costs are scaled for. */
    private static volatile int multiplier = 1;

    public static boolean isOpen() {
        return openProduct != null;
    }

    /** Opens this product, or closes the window when it is already the one on show. */
    public static void toggle(String productName) {
        if (productName.equals(openProduct)) {
            openProduct = null;
        } else {
            openProduct = productName;
            multiplier = 1;
        }
    }

    public static void close() {
        openProduct = null;
    }

    public static void raiseMultiplier() {
        if (multiplier < MAX_MULTIPLIER) multiplier++;
    }

    public static void lowerMultiplier() {
        if (multiplier > 1) multiplier--;
    }

    /**
     * Draws the window, if one is open, and collects what can be clicked in it.
     *
     * @param mainPanel where the main panel is, used to place this one under it
     */
    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                       AuctionPanelInput.PanelBounds mainPanel,
                       List<AuctionPanelInput.PanelBounds> panels,
                       List<AuctionPanelInput.ClickTarget> targets) {
        String productName = openProduct;
        if (productName == null) return;

        CraftFlipRanking.RankedProduct product = CraftFlipRanking.find(productName);
        if (product == null) {
            // The product left the ranking, e.g. the data was cleared underneath us.
            close();
            return;
        }

        int crafts = multiplier;
        int lineHeight = font.lineHeight + 1;
        RecipeTally tally = product.tally();
        List<RecipeTally.SubRecipe> subRecipes = tally.subRecipes();
        List<MaterialCost> materials = tally.materials();

        // Title and summary; the recipe header and its rows when there are any; the
        // material header, one line per material, and the total.
        int lines = 2
                + (subRecipes.isEmpty() ? 0 : 1 + subRecipes.size())
                + 1 + Math.max(1, materials.size())
                + (materials.isEmpty() ? 0 : 1);
        int panelHeight = PADDING * 2 + lines * lineHeight;

        int width = Math.min(WIDTH, graphics.guiWidth() - 8);
        int panelX;
        int panelY;
        if (PanelPosition.isSet(PanelPosition.Panel.BREAKDOWN)) {
            panelX = PanelPosition.x(PanelPosition.Panel.BREAKDOWN);
            panelY = PanelPosition.y(PanelPosition.Panel.BREAKDOWN);
        } else {
            panelX = mainPanel.x();
            panelY = autoPlaceY(graphics, mainPanel, panelHeight);
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, graphics.guiWidth() - width));
        panelY = Math.clamp(panelY, 0, Math.max(0, graphics.guiHeight() - panelHeight));

        graphics.fill(panelX, panelY, panelX + width, panelY + panelHeight, PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, width, panelHeight, PANEL_BORDER);

        int textX = panelX + PADDING;
        int rowY = panelY + PADDING;

        drawTitle(graphics, font, product, crafts, textX, rowY, width, lineHeight, mouseX, mouseY, targets);
        rowY += lineHeight;

        // Insta buy is what starting the craft right now costs, so that margin is the one shown.
        CostBreakdown breakdown = product.breakdown();
        double instaMargin = breakdown.instaBuyMargin() * crafts;
        String marginColor = instaMargin >= 0 ? "§a" : "§c";
        graphics.text(font, "§7" + SkyblockText.coins(breakdown.instaBuy() * crafts)
                        + " §8> §f" + SkyblockText.coins(breakdown.marketValue() * crafts)
                        + " " + marginColor + SkyblockText.signedCoins(instaMargin) + " §8(insta)",
                textX, rowY, 0xFFFFFFFF, true);
        rowY += lineHeight;

        if (!subRecipes.isEmpty()) {
            graphics.text(font, "§8sub recipes", textX, rowY, 0xFFFFFFFF, true);
            graphics.text(font, "§8qty", textX + QTY_COLUMN, rowY, 0xFFFFFFFF, true);
            rowY += lineHeight;
            for (RecipeTally.SubRecipe subRecipe : subRecipes) {
                drawSubRecipe(graphics, font, subRecipe, crafts, textX, rowY, lineHeight, mouseX, mouseY, targets);
                rowY += lineHeight;
            }
        }

        graphics.text(font, "§8material", textX, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8qty", textX + QTY_COLUMN, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8cost (insta)", textX + COST_COLUMN, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§8have", textX + HAVE_COLUMN, rowY, 0xFFFFFFFF, true);
        rowY += lineHeight;

        if (materials.isEmpty()) {
            graphics.text(font, "§8No materials listed", textX, rowY, 0xFFFFFFFF, true);
        } else {
            for (MaterialCost material : materials) {
                drawMaterial(graphics, font, material, crafts, textX, rowY, lineHeight, mouseX, mouseY, targets);
                rowY += lineHeight;
            }

            double totalPremium = tally.totalInstaBuy() - tally.totalBuyOrder();
            graphics.text(font, "§fTotal", textX, rowY, 0xFFFFFFFF, true);
            graphics.text(font, "§fx" + tally.totalItems() * crafts, textX + QTY_COLUMN, rowY, 0xFFFFFFFF, true);
            graphics.text(font, "§f" + SkyblockText.coins(tally.totalBuyOrder() * crafts)
                            + " §8(+" + SkyblockText.coins(totalPremium * crafts) + ")",
                    textX + COST_COLUMN, rowY, 0xFFFFFFFF, true);
        }

        panels.add(new AuctionPanelInput.PanelBounds(
                PanelPosition.Panel.BREAKDOWN, panelX, panelY, width, panelHeight));
    }

    private void drawTitle(GuiGraphicsExtractor graphics, Font font, CraftFlipRanking.RankedProduct product,
                           int crafts, int textX, int textY, int width, int lineHeight,
                           int mouseX, int mouseY, List<AuctionPanelInput.ClickTarget> targets) {
        String close = "§c[x]";
        int closeWidth = font.width(SkyblockText.strip(close));
        int closeX = textX + width - PADDING * 2 - closeWidth;

        // Left click lowers, right click raises; AuctionPanelInput routes the right click.
        String multiplierLabel = "x" + crafts;
        int multiplierWidth = font.width(multiplierLabel) + 4;
        int multiplierX = closeX - 6 - multiplierWidth;
        boolean multiplierHovered = hovering(mouseX, mouseY, multiplierX, textY - 1, multiplierWidth, lineHeight);
        graphics.fill(multiplierX, textY - 1, multiplierX + multiplierWidth, textY + lineHeight - 1,
                multiplierHovered ? HOVER_BACKGROUND : BUTTON_BACKGROUND);

        String name = SkyblockText.fit(product.displayName(), multiplierX - 8 - textX);
        int nameWidth = font.width(name);
        boolean hovered = hovering(mouseX, mouseY, textX, textY, nameWidth, lineHeight);
        if (hovered) {
            graphics.fill(textX - 1, textY - 1, textX + nameWidth + 1, textY + lineHeight - 1, HOVER_BACKGROUND);
        }

        graphics.text(font, (hovered ? "§e§l" : "§6§l") + name, textX, textY, 0xFFFFFFFF, true);
        graphics.text(font, (multiplierHovered ? "§e" : "§b") + multiplierLabel,
                multiplierX + 2, textY, 0xFFFFFFFF, true);
        graphics.text(font, close, closeX, textY, 0xFFFFFFFF, true);

        targets.add(new AuctionPanelInput.ClickTarget(textX, textY, nameWidth, lineHeight,
                AuctionPanelInput.Action.VIEW_RECIPE, product.itemId(), 0));
        targets.add(new AuctionPanelInput.ClickTarget(multiplierX, textY - 1, multiplierWidth, lineHeight,
                AuctionPanelInput.Action.MULTIPLIER, "", 0));
        targets.add(new AuctionPanelInput.ClickTarget(closeX, textY, closeWidth, lineHeight,
                AuctionPanelInput.Action.CLOSE_BREAKDOWN, "", 0));
    }

    /** One craftable step of the recipe, indented by how deep in the tree it sits. */
    private void drawSubRecipe(GuiGraphicsExtractor graphics, Font font, RecipeTally.SubRecipe subRecipe,
                               int crafts, int textX, int rowY, int lineHeight, int mouseX, int mouseY,
                               List<AuctionPanelInput.ClickTarget> targets) {
        int nameX = textX + (subRecipe.depth() - 1) * DEPTH_INDENT;
        String name = SkyblockText.fit(subRecipe.name(), textX + QTY_COLUMN - 6 - nameX);
        int nameWidth = font.width(name);
        boolean hovered = hovering(mouseX, mouseY, nameX, rowY, nameWidth, lineHeight);
        if (hovered) {
            graphics.fill(nameX - 1, rowY - 1, nameX + nameWidth + 1, rowY + lineHeight - 1, HOVER_BACKGROUND);
        }

        graphics.text(font, (hovered ? "§e" : "§d") + name, nameX, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§7x" + (long) subRecipe.quantity() * crafts,
                textX + QTY_COLUMN, rowY, 0xFFFFFFFF, true);

        targets.add(new AuctionPanelInput.ClickTarget(nameX, rowY, nameWidth, lineHeight,
                AuctionPanelInput.Action.VIEW_RECIPE, subRecipe.itemId(), 0));
    }

    private void drawMaterial(GuiGraphicsExtractor graphics, Font font, MaterialCost material, int crafts,
                              int textX, int rowY, int lineHeight, int mouseX, int mouseY,
                              List<AuctionPanelInput.ClickTarget> targets) {
        int quantity = (int) Math.min(Integer.MAX_VALUE, (long) material.quantity() * crafts);

        String name = SkyblockText.fit(material.name(), QTY_COLUMN - 6);
        int nameWidth = font.width(name);
        boolean hovered = hovering(mouseX, mouseY, textX, rowY, nameWidth, lineHeight);
        if (hovered) {
            graphics.fill(textX - 1, rowY - 1, textX + nameWidth + 1, rowY + lineHeight - 1, HOVER_BACKGROUND);
        }

        // White opens the Bazaar, gold searches the Auction House for anything the
        // Bazaar does not carry.
        String nameColor = hovered ? "§e" : material.onBazaar() ? "§f" : "§6";
        graphics.text(font, nameColor + name, textX, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§7x" + quantity, textX + QTY_COLUMN, rowY, 0xFFFFFFFF, true);
        graphics.text(font, "§7" + SkyblockText.coins(material.buyOrder() * crafts)
                        + " §8(+" + SkyblockText.coins(material.instaBuyPremium() * crafts) + ")",
                textX + COST_COLUMN, rowY, 0xFFFFFFFF, true);
        if (material.owned() != 0) {
            graphics.text(font, "§b" + SkyblockText.coins(material.owned()),
                    textX + HAVE_COLUMN, rowY, 0xFFFFFFFF, true);
        }

        // The quantity rides along with the click so the amount sign can be filled
        // with it, already scaled by the multiplier.
        if (material.onBazaar()) {
            // The api name decides whether this is a Bazaar item at all, but the
            // command itself wants the display name.
            targets.add(new AuctionPanelInput.ClickTarget(textX, rowY, nameWidth, lineHeight,
                    AuctionPanelInput.Action.BAZAAR, material.name(), quantity));
        } else {
            // Not on the Bazaar, so it is bought on the Auction House. Searching
            // wants the display name, not an api name.
            targets.add(new AuctionPanelInput.ClickTarget(textX, rowY, nameWidth, lineHeight,
                    AuctionPanelInput.Action.AH_SEARCH, material.name(), quantity));
        }
    }

    /** Sits under the main panel, or above it when there is no room below. */
    private int autoPlaceY(GuiGraphicsExtractor graphics, AuctionPanelInput.PanelBounds mainPanel, int height) {
        int below = mainPanel.y() + mainPanel.height() + 4;
        if (below + height <= graphics.guiHeight() - 4) return below;

        int above = mainPanel.y() - height - 4;
        if (above >= 4) return above;

        return Math.max(4, graphics.guiHeight() - height - 4);
    }

    private boolean hovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
