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
            if (existing == ' ' || piece.confidence() > 0.5f) {
                chars[row][col] = piece.label();
            }
        }

        Board board = Board.fromChars(chars);
        if (!isValid(board)) return null;

        boolean flipped = shouldFlip(board);
        if (flipped) board = board.mirrorVertical().mirrorHorizontal();

        return new MatchResult(board, flipped);
    }

    private boolean isValid(Board board) {
        int redKing = 0, blackKing = 0;
        int[] counts = new int[128];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                char p = board.pieceAt(r, c);
                if (p == ' ') continue;
                counts[p]++;
                if (p == 'K') redKing++;
                if (p == 'k') blackKing++;
            }
        }
        if (redKing != 1 || blackKing != 1) return false;
        // Max counts per piece type (uppercase = red, lowercase = black)
        if (counts['R'] > 2 || counts['r'] > 2) return false;
        if (counts['N'] > 2 || counts['n'] > 2) return false;
        if (counts['C'] > 2 || counts['c'] > 2) return false;
        if (counts['B'] > 2 || counts['b'] > 2) return false;
        if (counts['A'] > 2 || counts['a'] > 2) return false;
        if (counts['P'] > 5 || counts['p'] > 5) return false;
        return true;
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
