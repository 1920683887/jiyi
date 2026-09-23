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
    /** 两次点击之间的移动延迟（对齐 TCHESS mouseMoveDelay） */
    private int moveDelayMs = 0;
    /** 后台点击模式（PostMessage），由 config.link().backMode 驱动 */
    private boolean backMode = false;

    public AutoClicker(Platform platform) {
        this.platform = platform;
    }

    public void setBackMode(boolean backMode) { this.backMode = backMode; }

    public void setMoveDelay(int ms) { this.moveDelayMs = Math.max(0, ms); }

    public void setBoardRect(Rectangle rect) {
        this.lastBoardRect = rect;
    }

    public Rectangle getBoardRect() { return lastBoardRect; }

    public void setClickDelay(int ms) { this.clickDelayMs = ms; }

    /** @return false=任一次点击未发出（窗口失效/注入失败），上层不应推进局面 */
    public boolean click(Move move, boolean flipped) {
        if (lastBoardRect == null) return false;

        var from = boardToScreen(move.fromRow(), move.fromCol(), flipped);
        var to = boardToScreen(move.toRow(), move.toCol(), flipped);

        Platform.ClickMode mode = backMode ? Platform.ClickMode.BACK : Platform.ClickMode.FRONT;

        // First click to select the piece at 'from'（两次独立点击模拟选中+落子）
        boolean ok = platform.mouseClick(null, from, mode);
        sleep(clickDelayMs);
        if (moveDelayMs > 0) sleep(moveDelayMs);

        // Second click to move the piece to 'to'
        ok &= platform.mouseClick(null, to, mode);
        return ok;
    }

    public void clickCell(int row, int col, boolean flipped) {
        var pt = boardToScreen(row, col, flipped);
        Platform.ClickMode mode = backMode ? Platform.ClickMode.BACK : Platform.ClickMode.FRONT;
        platform.mouseClick(null, pt, mode);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private Point boardToScreen(int row, int col, boolean flipped) {
        var r = lastBoardRect;
        int fr = flipped ? 9 - row : row;
        int fc = flipped ? 8 - col : col;
        // 对齐 C++ clickMap（LinkCore.cpp:416-417）：格点 = boardRegion.x + col*cellW，
        // cellW = boardRegion.w/8。直接点交叉点，无边缘偏移。
        double cellW = r.width / 8.0;
        double cellH = r.height / 9.0;
        double px = r.x + fc * cellW;
        double py = r.y + fr * cellH;
        return new Point((int) px, (int) py);
    }
}
