package com.jiyi.core.detection;

import com.jiyi.core.model.Move;
import com.jiyi.infra.platform.Platform;

import java.awt.Point;
import java.awt.Rectangle;

public class AutoClicker {
    private static final int DEFAULT_DELAY_MS = 50;

    private final Platform platform;
    private Rectangle lastBoardRect;
    private int clickDelayMs = DEFAULT_DELAY_MS;

    public AutoClicker(Platform platform) {
        this.platform = platform;
    }

    public void setBoardRect(Rectangle rect) {
        this.lastBoardRect = rect;
    }

    public void setClickDelay(int ms) { this.clickDelayMs = ms; }

    public void click(Move move, boolean flipped) {
        if (lastBoardRect == null) return;

        // First click on the source (select piece)
        var from = boardToScreen(move.fromRow(), move.fromCol(), flipped);
        platform.mouseClick(null, from, Platform.ClickMode.FRONT);
        sleep(clickDelayMs);

        // Then click on the destination (drop piece)
        var to = boardToScreen(move.toRow(), move.toCol(), flipped);
        platform.mouseClick(null, to, Platform.ClickMode.FRONT);
    }

    public void clickCell(int row, int col, boolean flipped) {
        var pt = boardToScreen(row, col, flipped);
        platform.mouseClick(null, pt, Platform.ClickMode.FRONT);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Point boardToScreen(int row, int col, boolean flipped) {
        var r = lastBoardRect;
        int fr = flipped ? 9 - row : row;
        int fc = flipped ? 8 - col : col;
        double cellW = r.width / 9.0;
        double cellH = r.height / 10.0;
        return new Point(
            r.x + (int) (fc * cellW + cellW / 2),
            r.y + (int) (fr * cellH + cellH / 2)
        );
    }
}
