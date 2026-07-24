package com.jiyi.core.model;

public record Move(int fromRow, int fromCol, int toRow, int toCol) {
    public static Move fromUci(String uci) {
        if (uci == null || uci.length() < 4) throw new IllegalArgumentException("Invalid UCI: " + uci);
        int fc = uci.charAt(0) - 'a';
        int fr = 9 - (uci.charAt(1) - '0');
        int tc = uci.charAt(2) - 'a';
        int tr = 9 - (uci.charAt(3) - '0');
        return new Move(fr, fc, tr, tc);
    }

    public String toUci() {
        return "" + (char) ('a' + fromCol) + (9 - fromRow) + (char) ('a' + toCol) + (9 - toRow);
    }

    public boolean isCapture(Board board) {
        return board.pieceAt(toRow, toCol) != ' ';
    }

    public Move reverse() {
        return new Move(9 - fromRow, 8 - fromCol, 9 - toRow, 8 - toCol);
    }
}
