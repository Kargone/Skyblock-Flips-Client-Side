package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.feature.PendingAmount;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * A one-click fill for the amount sign.
 *
 * Buying a stack of materials on the Bazaar ends with Hypixel opening a sign and
 * asking for a number. Having just clicked that material in the breakdown, the
 * mod knows which number, so it offers a button rather than making it be typed.
 */
public class SignAmountOverlay {

    private static final int PADDING = 4;
    private static final int BUTTON_BACKGROUND = 0xF0143018;
    private static final int BUTTON_BORDER = 0xFF5BD97B;
    private static final int HOVER_BACKGROUND = 0x4DFFFFFF;

    public void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        if (!PendingAmount.isFresh()) return;

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();

        String amount = String.valueOf(PendingAmount.amount());
        String label = "§a§l" + amount;
        String subtitle = "§7" + PendingAmount.itemName();

        int width = Math.max(font.width(SkyblockText.strip(label)), font.width(SkyblockText.strip(subtitle)))
                + PADDING * 2;
        int height = PADDING * 2 + lineHeight * 2;

        AuctionPanelInput.pollMouse(screen, mouseX, mouseY, guiWidth, guiHeight);

        int x;
        int y;
        if (PanelPosition.isSet(PanelPosition.Panel.SIGN)) {
            x = PanelPosition.x(PanelPosition.Panel.SIGN);
            y = PanelPosition.y(PanelPosition.Panel.SIGN);
        } else {
            // Beside the sign, level with the Done button vanilla puts at height/4 + 120.
            x = guiWidth / 2 + 105;
            y = guiHeight / 4 + 120;
        }
        x = Math.clamp(x, 0, Math.max(0, guiWidth - width));
        y = Math.clamp(y, 0, Math.max(0, guiHeight - height));

        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        graphics.fill(x, y, x + width, y + height, hovered ? HOVER_BACKGROUND : BUTTON_BACKGROUND);
        graphics.outline(x, y, width, height, BUTTON_BORDER);

        graphics.text(font, label, x + PADDING, y + PADDING, 0xFFFFFFFF, true);
        graphics.text(font, SkyblockText.fit(subtitle, width - PADDING * 2),
                x + PADDING, y + PADDING + lineHeight, 0xFFFFFFFF, true);

        List<AuctionPanelInput.PanelBounds> panels = new ArrayList<>(1);
        panels.add(new AuctionPanelInput.PanelBounds(PanelPosition.Panel.SIGN, x, y, width, height));

        List<AuctionPanelInput.ClickTarget> targets = new ArrayList<>(1);
        targets.add(new AuctionPanelInput.ClickTarget(x, y, width, height,
                AuctionPanelInput.Action.FILL_SIGN, amount, 0));

        AuctionPanelInput.publish(screen, List.copyOf(panels), List.copyOf(targets));
    }
}
