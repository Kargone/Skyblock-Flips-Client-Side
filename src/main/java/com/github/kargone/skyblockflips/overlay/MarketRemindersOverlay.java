package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.flip.MayorData;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Bazaar's reminder window: the mayor and the election, what your notes in
 * mayor-info.json say to buy, sell or watch because of them, the next Spooky
 * Festival with Ectoplasm's price, and the forge.
 *
 * Everything in it comes from the server's {@code /api/mayor-data} and trader data:
 * which reminders apply, their prices and their trend are worked out there, so this
 * only lays them out. Clicking a product opens it in the Bazaar. The window can be
 * dragged anywhere and remembers where it was put.
 */
public class MarketRemindersOverlay {

    private static final int WIDTH = 300;
    private static final int PADDING = 5;

    /** Space between a row's columns. */
    private static final int COLUMN_GAP = 6;

    private static final long SB_YEAR_MS = 124 * 3600 * 1000L;

    private static final int PANEL_BACKGROUND = 0xE0120D1C;
    private static final int PANEL_BORDER = 0xFF6B4CA8;
    private static final int HOVER_BACKGROUND = 0x40FFFFFF;

    /**
     * One line of the window: either plain text, or a product row with a verb, a
     * clickable name, its price and its trend.
     */
    private record Line(String text, String verb, String product, String price, String trend) {

        static Line text(String text) {
            return new Line(text, null, null, null, null);
        }

        boolean isRow() {
            return product != null;
        }
    }

    public void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        ServerData.poll();

        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 1;
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int width = Math.min(WIDTH, guiWidth - 8);

        List<Line> lines = buildLines(ServerData.mayor(), System.currentTimeMillis());
        int panelHeight = PADDING * 2 + lines.size() * lineHeight;

        AuctionPanelInput.pollMouse(screen, mouseX, mouseY, guiWidth, guiHeight);

        int panelX;
        int panelY;
        if (PanelPosition.isSet(PanelPosition.Panel.REMINDERS)) {
            panelX = PanelPosition.x(PanelPosition.Panel.REMINDERS);
            panelY = PanelPosition.y(PanelPosition.Panel.REMINDERS);
        } else {
            int[] placement = CraftProductsOverlay.autoPlace(graphics, screen, width);
            panelX = placement[0];
            panelY = placement[1];
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, guiWidth - width));
        panelY = Math.clamp(panelY, 0, Math.max(0, guiHeight - panelHeight));

        graphics.fill(panelX, panelY, panelX + width, panelY + panelHeight, PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, width, panelHeight, PANEL_BORDER);

        int textX = panelX + PADDING;
        int contentWidth = width - PADDING * 2;
        List<AuctionPanelInput.ClickTarget> targets = new ArrayList<>();

        // Columns sized to what they hold this frame: the verb on the left, the trend
        // against the right edge with the price just before it, and the name gets
        // whatever is left, so no column can run into the next.
        int verbWidth = 0;
        int priceWidth = 0;
        int trendWidth = 0;
        for (Line line : lines) {
            if (!line.isRow()) continue;
            if (line.verb() != null) verbWidth = Math.max(verbWidth, textWidth(font, line.verb()));
            priceWidth = Math.max(priceWidth, textWidth(font, line.price()));
            trendWidth = Math.max(trendWidth, textWidth(font, line.trend()));
        }
        Columns columns = new Columns(
                textX,
                textX + (verbWidth == 0 ? 0 : verbWidth + COLUMN_GAP),
                textX + contentWidth - trendWidth - COLUMN_GAP - priceWidth,
                textX + contentWidth - trendWidth);

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int rowY = panelY + PADDING + i * lineHeight;

            if (!line.isRow()) {
                graphics.text(font, SkyblockText.fit(line.text(), contentWidth), textX, rowY, 0xFFFFFFFF, true);
                continue;
            }
            drawRow(graphics, font, line, columns, rowY, lineHeight, mouseX, mouseY, targets);
        }

        // The year sits at the right end of the title line.
        MayorData mayor = ServerData.mayor();
        if (mayor.loaded() && mayor.skyblockYear() > 0) {
            String year = "§8Year " + mayor.skyblockYear();
            graphics.text(font, year, textX + contentWidth - font.width(SkyblockText.strip(year)),
                    panelY + PADDING, 0xFFFFFFFF, true);
        }

        AuctionPanelInput.publish(screen,
                List.of(new AuctionPanelInput.PanelBounds(PanelPosition.Panel.REMINDERS, panelX, panelY, width, panelHeight)),
                List.copyOf(targets));
    }

    /** Where each column of a row starts, in gui coordinates. */
    private record Columns(int verbX, int nameX, int priceX, int trendX) {
    }

    private static int textWidth(Font font, String text) {
        return text == null ? 0 : font.width(SkyblockText.strip(text));
    }

    private void drawRow(GuiGraphicsExtractor graphics, Font font, Line line, Columns columns, int rowY, int lineHeight,
                         int mouseX, int mouseY, List<AuctionPanelInput.ClickTarget> targets) {
        if (line.verb() != null) graphics.text(font, line.verb(), columns.verbX(), rowY, 0xFFFFFFFF, true);

        int nameX = columns.nameX();
        String name = SkyblockText.fit(line.product(), columns.priceX() - COLUMN_GAP - nameX);
        int nameWidth = font.width(name);
        boolean hovered = mouseX >= nameX && mouseX < nameX + nameWidth && mouseY >= rowY && mouseY < rowY + lineHeight;
        if (hovered) {
            graphics.fill(nameX - 1, rowY - 1, nameX + nameWidth + 1, rowY + lineHeight - 1, HOVER_BACKGROUND);
        }
        graphics.text(font, (hovered ? "§e" : "§f") + name, nameX, rowY, 0xFFFFFFFF, true);

        // Right aligned, so prices and trends line up on their last digit.
        int priceRight = columns.trendX() - COLUMN_GAP;
        graphics.text(font, line.price(), priceRight - textWidth(font, line.price()), rowY, 0xFFFFFFFF, true);
        graphics.text(font, line.trend(), columns.trendX(), rowY, 0xFFFFFFFF, true);

        targets.add(new AuctionPanelInput.ClickTarget(nameX, rowY, nameWidth, lineHeight,
                AuctionPanelInput.Action.BAZAAR, line.product(), 0));
    }

    private static List<Line> buildLines(MayorData data, long now) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.text("§6§lMARKET REMINDERS"));

        if (!data.loaded()) {
            lines.add(Line.text(ServerData.lastError().isEmpty()
                    ? "§8waiting for mayor data..."
                    : "§cserver offline"));
            addForge(lines);
            return lines;
        }

        String election = data.electionEndsAt() > now
                ? "   §7Election §f" + CraftProductsOverlay.countdown(data.electionEndsAt() - now)
                        + (data.electionLeader().isEmpty() ? "" : " §8- §f" + data.electionLeader() + " §7leading")
                : "";
        lines.add(Line.text("§7Mayor §f" + orUnknown(data.mayor()) + election));

        if (data.taxMultiplier() > 1D) {
            lines.add(Line.text("§c§lBazaar tax x" + trim(data.taxMultiplier()) + " §c(Derpy's QUAD TAXES)"));
        }

        // Now: the mayor in office.
        List<MayorData.Reminder> during = byTiming(data, "during");
        List<MayorData.Note> notes = data.notes().stream().filter(note -> "during".equals(note.timing())).toList();
        if (!during.isEmpty() || !notes.isEmpty()) {
            lines.add(Line.text("§8-- now: §f" + data.mayor() + " §8--"));
            for (MayorData.Reminder reminder : during) lines.add(row(reminder));
            for (MayorData.Note note : notes) lines.add(Line.text("§8 • §7" + note.text()));
        }

        // Coming: what to buy or sell before the election leader takes office.
        List<MayorData.Reminder> before = byTiming(data, "before");
        List<MayorData.Note> beforeNotes = data.notes().stream().filter(note -> "before".equals(note.timing())).toList();
        if (!before.isEmpty() || !beforeNotes.isEmpty()) {
            lines.add(Line.text("§8-- coming: §f" + data.electionLeader() + " §8(leading) --"));
            for (MayorData.Reminder reminder : before) lines.add(row(reminder));
            for (MayorData.Note note : beforeNotes) lines.add(Line.text("§8 • §7" + note.text()));
        }

        // After: one section per mayor whose aftermath is being watched.
        Map<String, List<MayorData.Reminder>> after = new LinkedHashMap<>();
        for (MayorData.Reminder reminder : byTiming(data, "after")) {
            after.computeIfAbsent(reminder.mayor(), key -> new ArrayList<>()).add(reminder);
        }
        for (Map.Entry<String, List<MayorData.Reminder>> section : after.entrySet()) {
            MayorData.Reminder first = section.getValue().getFirst();
            lines.add(Line.text("§8-- after: §f" + section.getKey() + " §8(" + afterStatus(first, now) + ") --"));
            for (MayorData.Reminder reminder : section.getValue()) lines.add(row(reminder));
        }

        // The Spooky Festival, and Ectoplasm, which it is the time to think about.
        String festival = data.spookyStartsAt() <= now && now < data.spookyEndsAt()
                ? "§aon now"
                : data.spookyStartsAt() > now ? "in §f" + CraftProductsOverlay.countdown(data.spookyStartsAt() - now) : "§8-";
        lines.add(Line.text("§8-- Spooky Festival " + festival + " §8--"));
        lines.add(new Line(null, null, "Ectoplasm", price(data.ectoplasmPrice()), trend(data.ectoplasmTrend())));

        addForge(lines);
        return lines;
    }

    private static void addForge(List<Line> lines) {
        for (String forgeLine : CraftProductsOverlay.forgeLines()) lines.add(Line.text(forgeLine));
    }

    private static List<MayorData.Reminder> byTiming(MayorData data, String timing) {
        return data.reminders().stream().filter(reminder -> timing.equals(reminder.timing())).toList();
    }

    private static Line row(MayorData.Reminder reminder) {
        String verb = switch (reminder.action()) {
            case "buy" -> "§abuy";
            case "sell" -> "§csell";
            default -> "§ewatch";
        };
        return new Line(null, verb, reminder.product(), price(reminder.price()), trend(reminder.trend()));
    }

    /** "ends in 3d 2h, watch 2y" before the watch starts, "6d 4h left" once it has. */
    private static String afterStatus(MayorData.Reminder reminder, long now) {
        if (now < reminder.windowStarts()) {
            long years = Math.max(1L, Math.round((reminder.windowEnds() - reminder.windowStarts()) / (double) SB_YEAR_MS));
            return "ends in " + CraftProductsOverlay.countdown(reminder.windowStarts() - now) + ", watch " + years + "y";
        }
        return CraftProductsOverlay.countdown(reminder.windowEnds() - now) + " left";
    }

    /** "buy order / insta buy" off the Bazaar. */
    private static String price(MayorData.Price price) {
        if (price == null) return "§8no price";
        return "§f" + SkyblockText.coins(price.buyOrder()) + " §8/ §f" + SkyblockText.coins(price.instaBuy());
    }

    private static String trend(MayorData.Trend trend) {
        String percent = Math.round(Math.abs(trend.change()) * 100) + "%";
        return switch (trend.state()) {
            case "falling" -> "§c▼ " + percent;
            case "rising" -> "§a▲ " + percent;
            case "bottoming" -> "§a● bottom";
            case "flat" -> "§7– flat";
            default -> "§8collecting";
        };
    }

    private static String orUnknown(String value) {
        return value == null || value.isEmpty() ? "?" : value;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
