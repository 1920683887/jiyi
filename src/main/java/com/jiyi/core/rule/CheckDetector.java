package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

import static com.jiyi.core.model.Piece.*;

public class CheckDetector {

    private final MoveValidator validator = new MoveValidator();

    public boolean isInCheck(Board board, boolean isRed) {
        int[] kPos = findKing(board, isRed);
        if (kPos == null) return false;
        int kr = kPos[0], kc = kPos[1];

        return isAttackedByRookOrCannon(board, kr, kc, isRed)
            || isAttackedByKnight(board, kr, kc, isRed)
            || isAttackedByPawn(board, kr, kc, isRed)
            || isFlyingGeneral(board, kr, kc, isRed);
    }

    private int[] findKing(Board board, boolean isRed) {
        char king = isRed ? 'K' : 'k';
        for (int r = 0; r < 10; r++)
            for (int c = 0; c < 9; c++)
                if (board.pieceAt(r, c) == king) return new int[]{r, c};
        return null;
    }

    private boolean isAttackedByRookOrCannon(Board board, int kr, int kc, boolean isRed) {
        char enemyRook = isRed ? 'r' : 'R';
        char enemyCannon = isRed ? 'c' : 'C';
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};

        for (int[] d : dirs) {
            int r = kr + d[0], c = kc + d[1];
            int blockers = 0;
            while (r >= 0 && r < 10 && c >= 0 && c < 9) {
                char p = board.pieceAt(r, c);
                if (p != ' ') {
                    if (blockers == 0) {
                        if (p == enemyRook) return true;
                        blockers = 1;
                    } else {
                        if (p == enemyCannon) return true;
                        break;
                    }
                }
                r += d[0]; c += d[1];
            }
        }
        return false;
    }

    private boolean isAttackedByKnight(Board board, int kr, int kc, boolean isRed) {
        char enemyKnight = isRed ? 'n' : 'N';
        int[][] knightMoves = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        int[][] blocks = {{-1,0},{-1,0},{0,-1},{0,1},{0,-1},{0,1},{1,0},{1,0}};

        for (int i = 0; i < 8; i++) {
            int r = kr + knightMoves[i][0];
            int c = kc + knightMoves[i][1];
            if (r < 0 || r >= 10 || c < 0 || c >= 9) continue;
            if (board.pieceAt(r, c) == enemyKnight) {
                int br = kr + blocks[i][0];
                int bc = kc + blocks[i][1];
                if (board.isEmpty(br, bc)) return true;
            }
        }
        return false;
    }

    private boolean isAttackedByPawn(Board board, int kr, int kc, boolean isRed) {
        char enemyPawn = isRed ? 'p' : 'P';
        int[][] pawnAttacks = isRed
            ? new int[][]{{-1,0}, {0,-1}, {0,1}}
            : new int[][]{{1,0}, {0,-1}, {0,1}};

        boolean crossedRiver = isRed ? kr <= 4 : kr >= 5;
        int[][] attacks = crossedRiver ? pawnAttacks : new int[][]{pawnAttacks[0]};

        for (int[] a : attacks) {
            int r = kr + a[0], c = kc + a[1];
            if (r >= 0 && r < 10 && c >= 0 && c < 9 && board.pieceAt(r, c) == enemyPawn)
                return true;
        }
        return false;
    }

    private boolean isFlyingGeneral(Board board, int kr, int kc, boolean isRed) {
        int[] enemyKing = findKing(board, !isRed);
        if (enemyKing == null) return false;
        if (kc != enemyKing[1]) return false;
        int minR = Math.min(kr, enemyKing[0]);
        int maxR = Math.max(kr, enemyKing[0]);
        for (int r = minR + 1; r < maxR; r++)
            if (!board.isEmpty(r, kc)) return false;
        return true;
    }
}
