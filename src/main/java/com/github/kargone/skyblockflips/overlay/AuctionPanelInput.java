package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.feature.PendingAmount;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Dragging and clicking for the overlay panels.
 *
 * Each panel publishes its bounds and its clickable regions every frame. A press
 * inside a panel starts a drag of that panel; a press released without moving
 * runs whatever the region under it says to run.
 *
 * <p>All of that is driven from {@link #pollMouse}, which the render pass calls
 * every frame and which reads the button straight off the mouse handler. Nothing
 * here needs a mixin to work: {@code ContainerScreenMixin} only forwards presses
 * so the click can be swallowed before the menu sees it, and if that injection
 * ever fails to bind the panels still drag and still respond.
 */
public final class AuctionPanelInput {

    /** What clicking a region does. */
    public enum Action {
        /** Open the material breakdown for the product named in the payload. */
        OPEN_BREAKDOWN,
        /** Close the breakdown window. */
        CLOSE_BREAKDOWN,
        /** Ask the server to show a recipe; the payload is a Skyblock item id. */
        VIEW_RECIPE,
        /** Open the Bazaar for an item; the payload is its display name. */
        BAZAAR,
        /** Search the Auction House for an item; the payload is its display name. */
        AH_SEARCH,
        /** Type an amount onto the open sign; the payload is the number. */
        FILL_SIGN,
        /** The breakdown's craft multiplier: left click lowers it, right click raises it. */
        MULTIPLIER,
        /** Switch the forge panel between Auction House and Bazaar recipes. */
        FORGE_MARKET
    }

    /** Longest a sign line can be, and so how many deletes clear one. */
    private static final int SIGN_LINE_LIMIT = 24;

    /**
     * A clickable region of a panel, in gui coordinates.
     *
     * @param amount quantity this region refers to, 0 when it refers to none
     */
    public record ClickTarget(int x, int y, int width, int height, Action action, String payload, int amount) {

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    /** Where a panel was drawn this frame, so a press can find and move it. */
    public record PanelBounds(PanelPosition.Panel panel, int x, int y, int width, int height) {

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    /** How far the cursor may travel before a press stops counting as a click. */
    private static final double CLICK_SLOP = 3D;

    private static WeakReference<Screen> panelScreen = new WeakReference<>(null);
    private static List<PanelBounds> panels = List.of();
    private static List<ClickTarget> targets = List.of();

    private static boolean leftWasDown = false;
    private static boolean rightWasDown = false;
    /** Set when the click hook already acted on the current right press. */
    private static boolean rightPressHandled = false;
    private static PanelPosition.Panel draggedPanel = null;
    private static int dragWidth;
    private static int dragHeight;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static double pressX;
    private static double pressY;
    private static ClickTarget pressedTarget;

    private AuctionPanelInput() {
    }

    /** Called by the overlay every frame with where it just drew everything. */
    public static void publish(Screen screen, List<PanelBounds> panelBounds, List<ClickTarget> clickTargets) {
        panelScreen = new WeakReference<>(screen);
        panels = panelBounds;
        targets = clickTargets;
    }

    public static boolean isDragging() {
        return draggedPanel != null;
    }

    /** Drives press, drag and release from the render pass. */
    public static void pollMouse(Screen screen, int mouseX, int mouseY, int guiWidth, int guiHeight) {
        boolean leftDown = buttonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean ourScreen = panelScreen.get() == screen;

        pollRightButton(ourScreen, mouseX, mouseY);

        // The menu changed mid-drag. Drop the drag rather than letting a panel snap
        // to the cursor on a screen it was never grabbed on.
        if (draggedPanel != null && !ourScreen) {
            finish(false);
            leftWasDown = leftDown;
            return;
        }

        if (leftDown && !leftWasDown && ourScreen) {
            beginPress(mouseX, mouseY);
        } else if (draggedPanel != null && leftDown) {
            PanelPosition.set(draggedPanel,
                    Math.clamp(mouseX - dragOffsetX, 0, Math.max(0, guiWidth - dragWidth)),
                    Math.clamp(mouseY - dragOffsetY, 0, Math.max(0, guiHeight - dragHeight)));
        } else if (draggedPanel != null) {
            boolean moved = Math.abs(mouseX - pressX) > CLICK_SLOP || Math.abs(mouseY - pressY) > CLICK_SLOP;
            finish(!moved);
        }

        leftWasDown = leftDown;
    }

    /**
     * Forwarded from the menu's own click handler so a press on a panel can be
     * swallowed. The press itself is started here too, so a click is not lost in
     * the gap before the next frame.
     *
     * @return true when a panel took the click, which stops it reaching the menu
     */
    public static boolean mousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (panelScreen.get() != screen) return false;
        if (panelAt(mouseX, mouseY) == null) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightClick(mouseX, mouseY);
            rightPressHandled = true;
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        beginPress(mouseX, mouseY);
        return true;
    }

    /**
     * Right clicks only do something on the multiplier, and have no drag, so they
     * act on the press. The frame poll is the fallback for when the click hook is
     * not bound; a press the hook already acted on is skipped here.
     */
    private static void pollRightButton(boolean ourScreen, int mouseX, int mouseY) {
        boolean rightDown = buttonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        if (rightDown && !rightWasDown) {
            if (!rightPressHandled && ourScreen && draggedPanel == null) rightClick(mouseX, mouseY);
            rightPressHandled = false;
        } else if (!rightDown) {
            rightPressHandled = false;
        }

        rightWasDown = rightDown;
    }

    private static void rightClick(double mouseX, double mouseY) {
        ClickTarget target = targetAt(mouseX, mouseY);
        if (target != null && target.action() == Action.MULTIPLIER) {
            BreakdownWindow.raiseMultiplier();
        }
    }

    private static void beginPress(double mouseX, double mouseY) {
        // Both the click hook and the frame poll can see the same press; whichever
        // arrives first wins and the other is a no-op.
        if (draggedPanel != null) return;

        PanelBounds panel = panelAt(mouseX, mouseY);
        if (panel == null) return;

        draggedPanel = panel.panel();
        dragWidth = panel.width();
        dragHeight = panel.height();
        dragOffsetX = (int) Math.round(mouseX) - panel.x();
        dragOffsetY = (int) Math.round(mouseY) - panel.y();
        pressX = mouseX;
        pressY = mouseY;
        pressedTarget = targetAt(mouseX, mouseY);
    }

    private static void finish(boolean wasClick) {
        if (wasClick && pressedTarget != null) {
            perform(pressedTarget);
        } else if (!wasClick) {
            PanelPosition.save();
        }

        draggedPanel = null;
        pressedTarget = null;
    }

    private static void perform(ClickTarget target) {
        switch (target.action()) {
            case OPEN_BREAKDOWN -> {
                // Picking a different item drops whatever the last shopping click
                // was going to fill in, so a stale number cannot resurface.
                PendingAmount.clear();
                BreakdownWindow.toggle(target.payload());
            }
            case CLOSE_BREAKDOWN -> BreakdownWindow.close();
            case VIEW_RECIPE -> sendCommand("/viewrecipe " + target.payload());
            case BAZAAR -> {
                PendingAmount.remember(target.payload(), target.amount());
                sendCommand("/bazaar " + target.payload());
            }
            case AH_SEARCH -> {
                PendingAmount.remember(target.payload(), target.amount());
                sendCommand("/ahsearch " + target.payload());
            }
            case FILL_SIGN -> {
                fillSign(target.payload());
                PendingAmount.clear();
            }
            case MULTIPLIER -> BreakdownWindow.lowerMultiplier();
            case FORGE_MARKET -> CraftProductsOverlay.toggleForgeMarket();
        }
    }

    /** The topmost panel under the cursor; later panels are drawn over earlier ones. */
    private static PanelBounds panelAt(double mouseX, double mouseY) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).contains(mouseX, mouseY)) return panels.get(i);
        }
        return null;
    }

    private static ClickTarget targetAt(double mouseX, double mouseY) {
        for (int i = targets.size() - 1; i >= 0; i--) {
            if (targets.get(i).contains(mouseX, mouseY)) return targets.get(i);
        }
        return null;
    }

    /**
     * Types a number onto the sign Hypixel is asking for an amount on.
     *
     * The screen's own {@code keyPressed} and {@code charTyped} are public, so the
     * digits go in the way the keyboard would put them there - no reaching into the
     * sign's private text state, and the screen updates itself exactly as it would
     * for a real keystroke. Anything already on the line is deleted first, so a
     * half typed number is replaced rather than appended to.
     */
    private static void fillSign(String amount) {
        if (!(Minecraft.getInstance().screen instanceof AbstractSignEditScreen sign)) return;

        System.out.println("[SkyblockFlips] Filling sign with " + amount);

        for (int i = 0; i < SIGN_LINE_LIMIT; i++) {
            sign.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        }
        for (int i = 0; i < amount.length(); i++) {
            sign.charTyped(new CharacterEvent(amount.charAt(i)));
        }
    }

    /**
     * Whether a mouse button is physically down.
     *
     * {@code MouseHandler.isLeftPressed()} cannot answer this: the field behind it
     * is only written when no screen is open, because vanilla uses it for clicking
     * in the world. With a menu open it is stuck at false, so the button is read
     * from the window instead.
     */
    private static boolean buttonDown(int button) {
        Window window = Minecraft.getInstance().getWindow();
        if (window == null) return false;

        try {
            return GLFW.glfwGetMouseButton(window.handle(), button) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Runs a command exactly the way typing it would.
     *
     * {@code ChatScreen.handleChatInput} adds the line to the recent chat buffer and
     * then sends it as a command packet; doing both here means the command shows up
     * in the chat history you scroll back through with the up arrow.
     */
    private static void sendCommand(String typed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) return;

        System.out.println("[SkyblockFlips] Sending " + typed);

        if (minecraft.gui != null) {
            minecraft.gui.getChat().addRecentChat(typed);
        }
        minecraft.player.connection.sendCommand(typed.substring(1));
    }
}
