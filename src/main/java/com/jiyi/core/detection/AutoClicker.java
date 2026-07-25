package com.jiyi.core.detection;

import com.jiyi.core.model.Move;
import com.jiyi.infra.platform.Platform;

import java.awt.Point;
import java.awt.Rectangle;

public class AutoClicker {
    private final Platform platform;
    private Rectangle lastBoardRect;

    public AutoClicker(Platform platform) {
        this.platform = platform;
    }

    public void setBoardRect(Rectangle rect) {
        this.lastBoardRect = rect;
    }

    public void click(Move move, boolean flipped) {
        if (lastBoardRect == null) return;

        var from = boardToScreen(move.fromRow(), move.fromCol(), flipped);
        var to = boardToScreen(move.toRow(), move.toCol(), flipped);

        platform.mouseClick(from, to, Platform.ClickMode.FRONT);
    }

    public void clickCell(int row, int col, boolean flipped) {
        var pt = boardToScreen(row, col, flipped);
        platform.mouseClick(null, pt, Platform.ClickMode.FRONT);
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
