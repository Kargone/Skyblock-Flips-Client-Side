package com.github.kargone.skyblockflips.mixin;

import com.github.kargone.skyblockflips.overlay.AuctionPanelInput;
import com.github.kargone.skyblockflips.overlay.OverlayManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws container overlays after the menu has extracted its own contents.
 *
 * {@code AbstractContainerScreen.extractContents} calls the Screen version of
 * extractRenderState near the top, so anything submitted from {@link ScreenMixin}
 * ends up underneath the slots. Injecting at the tail here puts the Auction House
 * panel on top instead.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen", remap = false)
public class ContainerScreenMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"),
            require = 0
    )
    private void onContainerRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayManager.markContainerHookActive();

        Screen screen = (Screen) (Object) this;
        if (screen.getTitle() != null) {
            OverlayManager.draw(graphics, screen, screen.getTitle().getString(), mouseX, mouseY);
        }
    }

    /**
     * Lets the Auction House panel take a click before the menu sees it, so
     * dragging the panel across the slots cannot pick up or drop an item.
     */
    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (AuctionPanelInput.mousePressed(screen, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}
