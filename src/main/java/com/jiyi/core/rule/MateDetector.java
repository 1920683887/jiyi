package com.jiyi.core.rule;

import com.jiyi.core.model.Board;

public class MateDetector {

    private final CheckDetector checkDetector = new CheckDetector();
    private final MoveGenerator generator = new MoveGenerator();

    public boolean isCheckmate(Board board, boolean isRed) {
        if (!checkDetector.isInCheck(board, isRed)) return false;
        return generator.generateLegal(board, isRed).isEmpty();
    }

    public boolean isStalemate(Board board, boolean isRed) {
        if (checkDetector.isInCheck(board, isRed)) return false;
        return generator.generateLegal(board, isRed).isEmpty();
    }
}
