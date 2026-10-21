package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.feature.AuctionPriceTracker;
import com.github.kargone.skyblockflips.feature.SellOfferMarks;
import com.github.kargone.skyblockflips.feature.ForgeTracker;
import com.github.kargone.skyblockflips.util.ScreenConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

import java.util.ArrayList;
import java.util.List;

public class OverlayManager {
    static {
        System.out.println("[SkyblockFlips] OverlayManager class loaded!");
    }

    private static final MarketRemindersOverlay remindersOverlay = new MarketRemindersOverlay();
    private static final CraftProductsOverlay auctionOverlay = CraftProductsOverlay.auction();
    private static final CraftProductsOverlay forgeOverlay = CraftProductsOverlay.forge();
    private static final PositionsOverlay activeAuctionsOverlay = PositionsOverlay.activeAuctions();
    private static final PositionsOverlay investmentsOverlay = PositionsOverlay.investments();
    private static final SignAmountOverlay signAmountOverlay = new SignAmountOverlay();

    /**
     * Set once the screen-specific hooks have fired. Each of them draws after that
     * screen has drawn itself, so they are preferred where they apply; until one
     * proves it applied, the generic Screen hook keeps drawing, so the overlay is
     * never lost on a client where a hook does not bind.
     */
    private static volatile boolean containerHookActive = false;
    private static volatile boolean signHookActive = false;

    public static void markContainerHookActive() {
        if (!containerHookActive) {
            containerHookActive = true;
            System.out.println("[SkyblockFlips] Container render hook active; overlays draw above menu slots.");
        }
    }

    public static void markSignHookActive() {
        if (!signHookActive) {
            signHookActive = true;
            System.out.println("[SkyblockFlips] Sign render hook active; the amount button draws above the sign.");
        }
    }

    /** Whether the generic Screen hook should still draw for this screen. */
    public static boolean screenHookOwns(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> && containerHookActive) return false;
        if (screen instanceof AbstractSignEditScreen && signHookActive) return false;
        return true;
    }

    public static void draw(GuiGraphicsExtractor graphics, Screen screen, String title, int mouseX, int mouseY) {
        // The amount sign is matched on type: its title says nothing useful, and it
        // is the one screen here that is not a Skyblock menu.
        if (screen instanceof AbstractSignEditScreen) {
            signAmountOverlay.render(graphics, screen, mouseX, mouseY);
            return;
        }

        if (title == null || title.isEmpty()) return;

        // Tells the server what is forging, since Hypixel never says so in chat.
        if (screen instanceof AbstractContainerScreen<?> container && ForgeTracker.isForgeScreen(title)) {
            ForgeTracker.update(container.getMenu());
        }

        // Keeps the lowest BIN ready for the price sign; the Auction House panel
        // below still draws on this menu as it always has.
        if (screen instanceof AbstractContainerScreen<?> container && AuctionPriceTracker.isCreateBinScreen(title)) {
            AuctionPriceTracker.update(container.getMenu());
        }

        // Use contains for more flexible matching (handles color codes §)
        boolean bazaar = title.contains(ScreenConstants.BAZAAR) || title.toLowerCase().contains("bazaar");
        boolean auction = !bazaar && ScreenConstants.isAuctionScreen(title);
        boolean forge = !bazaar && !auction && (title.contains(ScreenConstants.FORGE)
                || title.contains(ScreenConstants.FORGE_SUB_MENU) || title.toLowerCase().contains("forge"));
        if (!bazaar && !auction && !forge) return;

        // A screen can show more than one panel, so the mouse is polled once and
        // every panel's bounds and click regions are published together. Panels
        // added later are drawn over earlier ones and win clicks where they overlap.
        AuctionPanelInput.pollMouse(screen, mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        List<AuctionPanelInput.PanelBounds> panels = new ArrayList<>();
        List<AuctionPanelInput.ClickTarget> targets = new ArrayList<>();

        if (bazaar) {
            // The marks themselves are drawn per slot, behind the items.
            if (screen instanceof AbstractContainerScreen<?> container && SellOfferMarks.isOrdersScreen(title)) {
                SellOfferMarks.remember(container);
            }
            investmentsOverlay.render(graphics, screen, mouseX, mouseY, panels, targets);
            remindersOverlay.render(graphics, screen, mouseX, mouseY, panels, targets);
        } else if (auction) {
            // Drawn first, so the products panel's breakdown window stays on top.
            activeAuctionsOverlay.render(graphics, screen, mouseX, mouseY, panels, targets);
            auctionOverlay.render(graphics, screen, mouseX, mouseY, panels, targets);
        } else {
            forgeOverlay.render(graphics, screen, mouseX, mouseY, panels, targets);
        }

        AuctionPanelInput.publish(screen, List.copyOf(panels), List.copyOf(targets));
    }
}
