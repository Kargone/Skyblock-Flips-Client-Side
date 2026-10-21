package com.github.kargone.skyblockflips.overlay;

import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * A dashboard style table for the overlay panels: a header row, then one line per
 * row. Every column is as wide as its widest cell. The first column holds a name,
 * is left aligned and is the one that gives way when the table is too wide; the
 * rest hold numbers and are right aligned, so they line up on their last digit.
 */
final class Table {

    private static final int COLUMN_GAP = 6;
    /** A name column is never wider than this, so one long name cannot stretch the panel. */
    private static final int MAX_NAME_WIDTH = 120;
    private static final int MIN_NAME_WIDTH = 40;
    private static final int HOVER_BACKGROUND = 0x40FFFFFF;

    /**
     * One row. The first cell is a plain name; the table colours it, and when
     * {@code action} is set, clicking it does that with {@code payload}.
     */
    record Row(List<String> cells, AuctionPanelInput.Action action, String payload) {
    }

    private final List<String> headers;
    private final List<Row> rows = new ArrayList<>();

    Table(String... headers) {
        this.headers = List.of(headers);
    }

    void add(Row row) {
        rows.add(row);
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    /** The header line plus one line per row. */
    int height(int lineHeight) {
        return (rows.size() + 1) * lineHeight;
    }

    int width(Font font, int maxWidth) {
        return total(columnWidths(font, maxWidth));
    }

    void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int maxWidth, int lineHeight,
              int mouseX, int mouseY, List<AuctionPanelInput.ClickTarget> targets) {
        int[] widths = columnWidths(font, maxWidth);

        drawCells(graphics, font, headers, "§7", x, y, widths);

        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int rowY = y + (r + 1) * lineHeight;

            String name = SkyblockText.fit(row.cells().getFirst(), widths[0]);
            int nameWidth = font.width(name);
            boolean hovered = row.action() != null
                    && mouseX >= x && mouseX < x + nameWidth && mouseY >= rowY && mouseY < rowY + lineHeight;
            if (hovered) {
                graphics.fill(x - 1, rowY - 1, x + nameWidth + 1, rowY + lineHeight - 1, HOVER_BACKGROUND);
            }

            List<String> cells = new ArrayList<>(row.cells());
            cells.set(0, (hovered ? "§e" : "§f") + name);
            drawCells(graphics, font, cells, "", x, rowY, widths);

            if (row.action() != null) {
                targets.add(new AuctionPanelInput.ClickTarget(x, rowY, nameWidth, lineHeight,
                        row.action(), row.payload(), 0));
            }
        }
    }

    private static void drawCells(GuiGraphicsExtractor graphics, Font font, List<String> cells, String prefix,
                                  int x, int y, int[] widths) {
        int columnX = x;
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() ? prefix + cells.get(i) : "";
            if (i == 0) {
                graphics.text(font, cell, columnX, y, 0xFFFFFFFF, true);
            } else {
                int right = columnX + widths[i];
                graphics.text(font, cell, right - textWidth(font, cell), y, 0xFFFFFFFF, true);
            }
            columnX += widths[i] + COLUMN_GAP;
        }
    }

    private int[] columnWidths(Font font, int maxWidth) {
        int[] widths = new int[headers.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = textWidth(font, headers.get(i));
        for (Row row : rows) {
            for (int i = 0; i < widths.length && i < row.cells().size(); i++) {
                widths[i] = Math.max(widths[i], textWidth(font, row.cells().get(i)));
            }
        }

        widths[0] = Math.min(widths[0], MAX_NAME_WIDTH);
        int overflow = total(widths) - maxWidth;
        if (overflow > 0) widths[0] = Math.max(MIN_NAME_WIDTH, widths[0] - overflow);
        return widths;
    }

    private static int total(int[] widths) {
        int sum = (widths.length - 1) * COLUMN_GAP;
        for (int width : widths) sum += width;
        return sum;
    }

    private static int textWidth(Font font, String text) {
        return text == null ? 0 : font.width(SkyblockText.strip(text));
    }
}
