package com.jiyi.core.detection;

import com.jiyi.core.model.Board;

import java.awt.Rectangle;
import java.util.*;

public class BoardMatcher {
    private static final int ROWS = 10;
    private static final int COLS = 9;

    public record MatchResult(Board board, boolean flipped) {}

    /** 诊断结果：匹配失败时 error 非空并给出原因，board 为翻转前的原始匹配局面 */
    public record MatchDiagnose(Board board, boolean flipped, String error) {}

    public MatchResult match(DetectionResult det) {
        var diag = diagnose(det);
        return diag.error() != null ? null : new MatchResult(diag.board(), diag.flipped());
    }

    /**
     * 诊断版匹配：生产逻辑与诊断共用同一实现，
     * 失败时返回具体原因（将数量、子力超限等），供离线管线调试定位。
     */
    public MatchDiagnose diagnose(DetectionResult det) {
        if (det.boardRect() == null) return new MatchDiagnose(null, false, "no board rect");

        var rect = det.boardRect();

        char[][] chars = new char[ROWS][COLS];
        float[][] conf = new float[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            Arrays.fill(chars[r], ' ');

        double cellW = (double) rect.width / 8;
        double cellH = (double) rect.height / 9;

        for (var piece : det.pieces()) {
            double cx = piece.rect().getCenterX();
            double cy = piece.rect().getCenterY();
            // 只映射中心点位于棋盘候选框内的棋子：过滤窗口其他区域（顶部小棋盘图等）的误检棋子，
            // 避免误检棋子覆盖真实棋子导致合法性校验失败
            if (cx < rect.x || cx > rect.x + rect.width || cy < rect.y || cy > rect.y + rect.height) continue;
            // YOLO 框直接作为格点区（对齐 C++ boardRegion /8 映射，不做外扩）
            int col = (int) Math.round((cx - rect.x) / cellW);
            int row = (int) Math.round((cy - rect.y) / cellH);
            if (col < 0 || col >= COLS || row < 0 || row >= ROWS) continue;

            // 置信度覆盖保护：只有更高置信度的检测才覆盖该格（低置信度误检不污染真实棋子）
            if (chars[row][col] == ' ' || piece.confidence() > conf[row][col]) {
                chars[row][col] = piece.label();
                conf[row][col] = piece.confidence();
            }
        }

        Board raw = Board.fromChars(chars);
        String error = checkValidity(raw);
        if (error != null) return new MatchDiagnose(raw, false, error);

        boolean flipped = shouldFlip(raw);
        // ★ 对齐 C++ flipBoard：180° 旋转 (r,c)→(9-r,8-c)（Board.mirrorVertical 即 180° 旋转）。
        //   原 mirrorVertical().mirrorHorizontal() 合成后是"纯行翻转 (r,c)→(9-r,c)"，
        //   与 C++/点击坐标（180° 旋转）不一致——开局回文局面下等价、对局中列会错位！
        Board board = flipped ? raw.mirrorVertical() : raw;
        return new MatchDiagnose(board, flipped, null);
    }

    /** 局面合法性校验：返回错误原因，合法返回 null */
    private String checkValidity(Board board) {
        int redKing = 0, blackKing = 0;
        int[] counts = new int[128];
        int totalPieces = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                char p = board.pieceAt(r, c);
                if (p == ' ') continue;
                counts[p]++;
                totalPieces++;
                if (p == 'K') redKing++;
                if (p == 'k') blackKing++;
            }
        }
        // 空棋盘拒绝：截图内容错位/窗口被遮挡时 YOLO 检测不到任何棋子
        // （对齐 C++ pieces.empty 处理：不把空局面当有效棋盘）
        if (totalPieces == 0) return "empty board";
        // 将帅数量：>1 必定错误；允许暂时为 0（动画期间/识别不稳定，由稳定帧等待过滤中间态）
        // （对齐 C++ sanitizeAndValidate：只拒绝多将，不拒绝缺将）
        if (redKing > 1) return "red king count=" + redKing;
        if (blackKing > 1) return "black king count=" + blackKing;
        // ★ 将帅九宫约束（方向无关）：任一将/帅出现即必须在某个九宫内（cols 3-5 × rows 0-2/7-9），
        //   且红黑两帅不能在同一侧。运行时截图错位（DPI 坐标错位等）产生的垃圾局面将帅必然出宫，
        //   据此在派发给引擎前拦截（2026-09-13 2017 连线事故：垃圾局面稳定输出 5 分钟）。
        boolean redTop = false, blackTop = false;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                char p = board.pieceAt(r, c);
                if (p == 'K' || p == 'k') {
                    if (c < 3 || c > 5 || (r > 2 && r < 7)) {
                        return "king out of palace (" + r + "," + c + ")";
                    }
                    if (p == 'K') redTop = r <= 2;
                    else blackTop = r <= 2;
                }
            }
        }
        if (redKing > 0 && blackKing > 0 && redTop == blackTop) return "kings on same side";
        if (counts['R'] > 2) return "red rook count=" + counts['R'];
        if (counts['r'] > 2) return "black rook count=" + counts['r'];
        if (counts['N'] > 2) return "red knight count=" + counts['N'];
        if (counts['n'] > 2) return "black knight count=" + counts['n'];
        if (counts['C'] > 2) return "red cannon count=" + counts['C'];
        if (counts['c'] > 2) return "black cannon count=" + counts['c'];
        if (counts['B'] > 2) return "red bishop count=" + counts['B'];
        if (counts['b'] > 2) return "black bishop count=" + counts['b'];
        if (counts['A'] > 2) return "red advisor count=" + counts['A'];
        if (counts['a'] > 2) return "black advisor count=" + counts['a'];
        if (counts['P'] > 5) return "red pawn count=" + counts['P'];
        if (counts['p'] > 5) return "black pawn count=" + counts['p'];
        return null;
    }

    private boolean shouldFlip(Board board) {
        int redKingRow = -1, blackKingRow = -1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                char p = board.pieceAt(r, c);
                if (p == 'K') redKingRow = r;
                if (p == 'k') blackKingRow = r;
            }
        }
        if (redKingRow < 0 || blackKingRow < 0) return false;
        // Black king should be at top (row 0-4), red king at bottom (row 5-9)
        // If black is below red, board needs flipping
        return blackKingRow > redKingRow;
    }
}
