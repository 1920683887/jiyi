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

    /**
     * 当前方无任何合法着法（无论是否被将军）。
     * 中国象棋规则：无子可动即负——被将军时为将死，未被将军时为困毙，结果均为对方胜。
     */
    public boolean isNoLegalMove(Board board, boolean isRed) {
        return generator.generateLegal(board, isRed).isEmpty();
    }
}
