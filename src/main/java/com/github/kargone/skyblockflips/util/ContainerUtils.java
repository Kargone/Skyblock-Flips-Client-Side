package com.github.kargone.skyblockflips.util;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContainerUtils {

    /** Width of the vanilla chest background, which every Hypixel menu uses. */
    public static final int CHEST_WIDTH = 176;

    /**
     * Where the chest background sits on screen.
     *
     * @param left   x of the container's top-left corner
     * @param top    y of the container's top-left corner
     * @param width  background width in gui pixels
     * @param height background height in gui pixels
     */
    public record ChestLayout(int left, int top, int width, int height) {
    }

    /** True for the slots of the opened menu itself, false for the player's own inventory. */
    public static boolean isContainerSlot(Slot slot) {
        return !(slot.container instanceof Inventory);
    }

    /**
     * Recreates the layout {@code AbstractContainerScreen} computes in {@code init()}.
     * The fields themselves are protected, and reading them through an accessor mixin
     * is exactly the kind of thing that breaks on a remapped client, so the row count
     * is derived from the menu instead.
     */
    public static ChestLayout layoutOf(AbstractContainerScreen<?> screen) {
        int containerSlots = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (isContainerSlot(slot)) containerSlots++;
        }

        int rows = Math.max(1, containerSlots / 9);
        int height = 114 + rows * 18;
        return new ChestLayout(
                (screen.width - CHEST_WIDTH) / 2,
                (screen.height - height) / 2,
                CHEST_WIDTH,
                height
        );
    }

    public static void logContainerItems(Screen screen) {}
}
