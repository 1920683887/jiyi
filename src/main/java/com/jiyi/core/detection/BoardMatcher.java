package com.jiyi.core.detection;

import com.jiyi.core.model.Board;

import java.awt.Rectangle;
import java.util.*;

public class BoardMatcher {
    private static final int ROWS = 10;
    private static final int COLS = 9;

    public record MatchResult(Board board, boolean flipped) {}

    public MatchResult match(DetectionResult det) {
        if (det.boardRect() == null) return null;

        var rect = det.boardRect();
        double cellW = (double) rect.width / COLS;
        double cellH = (double) rect.height / ROWS;

        char[][] chars = new char[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            Arrays.fill(chars[r], ' ');

        for (var piece : det.pieces()) {
            double cx = piece.rect().getCenterX();
            double cy = piece.rect().getCenterY();
            int col = (int) Math.round((cx - rect.x) / cellW);
            int row = (int) Math.round((cy - rect.y) / cellH);
            if (col < 0 || col >= COLS || row < 0 || row >= ROWS) continue;

            char existing = chars[row][col];
            if (existing == ' ' || piece.confidence() > getConfidence(existing, row, col)) {
                chars[row][col] = piece.label();
            }
        }

        Board board = Board.fromChars(chars);
        boolean flipped = shouldFlip(board);
        if (flipped) board = board.mirrorVertical().mirrorHorizontal();

        return new MatchResult(board, flipped);
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
        return blackKingRow < redKingRow;
    }

    private float getConfidence(char piece, int row, int col) {
        return 0.5f;
    }
}
