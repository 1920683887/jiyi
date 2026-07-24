package com.jiyi.core.model;

import java.util.Arrays;
import java.util.Objects;

public record Board(String[] rows) {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    public Board {
        Objects.requireNonNull(rows);
        if (rows.length != ROWS) throw new IllegalArgumentException("Board must have 10 rows");
        for (String row : rows) {
            if (row == null || row.length() != COLS)
                throw new IllegalArgumentException("Each row must have 9 chars");
        }
        String[] copy = new String[ROWS];
        for (int i = 0; i < ROWS; i++) copy[i] = rows[i];
        rows = copy;
    }

    public static final Board STANDARD = Board.fromFen(
        "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
    );

    public char pieceAt(int row, int col) {
        return rows[row].charAt(col);
    }

    public boolean isEmpty(int row, int col) {
        return pieceAt(row, col) == ' ';
    }

    public Board apply(Move move) {
        char[][] chars = toCharMatrix();
        chars[move.toRow()][move.toCol()] = chars[move.fromRow()][move.fromCol()];
        chars[move.fromRow()][move.fromCol()] = ' ';
        return fromChars(chars);
    }

    public Board applyAndSwap(Move move) {
        return apply(move);
    }

    public Board mirrorVertical() {
        String[] mirrored = new String[ROWS];
        for (int i = 0; i < ROWS; i++) {
            mirrored[i] = new StringBuilder(rows[ROWS - 1 - i]).reverse().toString();
        }
        return new Board(mirrored);
    }

    public Board mirrorHorizontal() {
        String[] mirrored = new String[ROWS];
        for (int i = 0; i < ROWS; i++) {
            mirrored[i] = new StringBuilder(rows[i]).reverse().toString();
        }
        return new Board(mirrored);
    }

    public char[][] toCharMatrix() {
        char[][] chars = new char[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                chars[i][j] = rows[i].charAt(j);
            }
        }
        return chars;
    }

    public static Board fromChars(char[][] chars) {
        String[] rows = new String[ROWS];
        for (int i = 0; i < ROWS; i++) {
            rows[i] = new String(chars[i]);
        }
        return new Board(rows);
    }

    public static Board fromFen(String fen) {
        String boardPart = fen.split(" ")[0];
        String[] parts = boardPart.split("/");
        String[] rows = new String[ROWS];
        for (int i = 0; i < ROWS; i++) {
            StringBuilder sb = new StringBuilder();
            for (char c : parts[i].toCharArray()) {
                if (c >= '1' && c <= '9') {
                    sb.append(" ".repeat(c - '0'));
                } else {
                    sb.append(c);
                }
            }
            rows[i] = sb.toString();
        }
        return new Board(rows);
    }

    public String toFen(boolean redGo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROWS; i++) {
            int empty = 0;
            for (int j = 0; j < COLS; j++) {
                char c = rows[i].charAt(j);
                if (c == ' ') {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(c);
                }
            }
            if (empty > 0) sb.append(empty);
            if (i < ROWS - 1) sb.append('/');
        }
        sb.append(' ').append(redGo ? 'w' : 'b');
        sb.append(" - - 0 1");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Board board)) return false;
        return Arrays.equals(rows, board.rows);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rows);
    }
}
