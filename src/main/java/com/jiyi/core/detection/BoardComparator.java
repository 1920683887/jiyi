package com.jiyi.core.detection;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import com.jiyi.core.model.Piece;
import com.jiyi.core.rule.MoveValidator;

import java.util.ArrayList;
import java.util.List;

public class BoardComparator {
    private final MoveValidator validator = new MoveValidator();

    public record Diff(int fromRow, int fromCol, int toRow, int toCol) {
        public Move toMove() { return new Move(fromRow, fromCol, toRow, toCol); }
    }

    public enum Action { OPPONENT_MOVED, ENGINE_MOVED, NEW_GAME, UNCLEAR }

    public record ComparisonResult(Action action, Diff diff) {}

    public ComparisonResult compare(Board linkBoard, Board engineBoard,
                                     boolean engineIsRed, boolean analysisMode) {
        // Collect all diffs between link board and engine board
        int diff1 = 0, diff2 = 0, diff3 = 0;
        List<int[]> diffList = new ArrayList<>();

        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char a = linkBoard.pieceAt(r, c);
                char b = engineBoard.pieceAt(r, c);
                if (a != b) {
                    diffList.add(new int[]{r, c, a, b});
                    if (a != ' ' && b != ' ') diff1++;       // piece changed (capture)
                    else if (a != ' ' && b == ' ') diff2++;  // piece disappeared
                    else diff3++;                              // piece appeared
                }
            }
        }

        if (diffList.isEmpty()) return null;
        if (diff1 > 2 || (diff2 >= 2 && diff3 > 2))
            return new ComparisonResult(Action.NEW_GAME, null);

        // Try all direction combinations (TCHESS approach)
        // Direction 1: link[p1] == engine[p2], link[p1] != ' '
        // Direction 2: link[p2] == engine[p1], link[p2] != ' '
        ComparisonResult result = null;
        int sum = 0;

        for (int i = 0; i < diffList.size(); i++) {
            for (int j = i + 1; j < diffList.size(); j++) {
                int[] d1 = diffList.get(i), d2 = diffList.get(j);

                // Direction A: link[d1] moved to link[d2]
                var r1 = tryDirection(linkBoard, engineBoard, d1, d2, engineIsRed, analysisMode);
                if (r1 != null) { sum++; result = r1; }

                // Direction B: link[d2] moved to link[d1]
                var r2 = tryDirection(linkBoard, engineBoard, d2, d1, engineIsRed, analysisMode);
                if (r2 != null) { sum++; result = r2; }
            }
        }

        if (sum == 1) return result;
        if (diffList.size() > 2) return new ComparisonResult(Action.NEW_GAME, null);
        return new ComparisonResult(Action.UNCLEAR, null);
    }

    private ComparisonResult tryDirection(Board linkBoard, Board engineBoard,
                                           int[] from, int[] to,
                                           boolean engineIsRed, boolean analysisMode) {
        char linkPiece = linkBoard.pieceAt(from[0], from[1]);
        char enginePiece = engineBoard.pieceAt(to[0], to[1]);
        if (linkPiece == ' ' || linkPiece != enginePiece) return null;

        // Check that from position is now empty in link board
        // and to position was empty (or different color piece was there)
        if (linkBoard.pieceAt(to[0], to[1]) != ' '
            && engineBoard.pieceAt(from[0], from[1]) == ' ') return null;

        // Determine if this is opponent's move or engine's move
        boolean isRedMove = Piece.isRedChar(linkPiece);
        boolean isEngineMove = engineIsRed == isRedMove;

        // Validate the move with canGo
        if (isEngineMove && !validator.canGo(linkBoard,
                new Move(from[0], from[1], to[0], to[1]), isRedMove)) return null;
        if (!isEngineMove && !validator.canGo(engineBoard,
                new Move(from[0], from[1], to[0], to[1]), isRedMove)) return null;

        var diff = new Diff(from[0], from[1], to[0], to[1]);
        // In analysis mode, always flag as opponent move (no engine clicking)
        Action action = analysisMode ? Action.OPPONENT_MOVED
            : (isEngineMove ? Action.ENGINE_MOVED : Action.OPPONENT_MOVED);
        return new ComparisonResult(action, diff);
    }

    // needConfirm: for rook/cannon moves, do a second scan to verify
    public boolean needConfirm(Board linkBoard, Board engineBoard, ComparisonResult result) {
        if (result == null || result.diff() == null) return false;
        if (result.action() == Action.NEW_GAME) return true;
        var d = result.diff();
        char movedPiece = linkBoard.pieceAt(d.toRow(), d.toCol());
        if (movedPiece != 'R' && movedPiece != 'r' && movedPiece != 'C' && movedPiece != 'c')
            return false;
        // Check if the destination is clear in engine board (expecting confirmation)
        return engineBoard.pieceAt(d.toRow(), d.toCol()) == ' ';
    }
}
