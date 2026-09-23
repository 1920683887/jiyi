package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

import static com.jiyi.core.model.Piece.*;

public class MoveValidator {

    public boolean canGo(Board board, Move move, boolean isRed) {
        // 越界防护：from/to 任一越界直接返回 false，避免 pieceAt 抛异常冒泡到引擎线程
        if (!inBounds(move.fromRow(), move.fromCol()) || !inBounds(move.toRow(), move.toCol()))
            return false;
        char piece = board.pieceAt(move.fromRow(), move.fromCol());
        if (piece == ' ') return false;
        if (isRed != isRedChar(piece)) return false;

        char target = board.pieceAt(move.toRow(), move.toCol());
        if (target != ' ' && isRed == isRedChar(target)) return false;

        boolean ok = switch (Character.toLowerCase(piece)) {
            case 'k' -> canKingGo(board, move, isRed);
            case 'a' -> canAdvisorGo(move, isRed);
            case 'b' -> canBishopGo(board, move, isRed);
            case 'n' -> canKnightGo(board, move);
            case 'r' -> canRookGo(board, move);
            case 'c' -> canCannonGo(board, move);
            case 'p' -> canPawnGo(move, isRed);
            default -> false;
        };
        if (!ok) return false;

        // ★ 规则完整性：中国象棋将/帅不可被吃
        if (target != ' ' && Character.toLowerCase(target) == 'k') return false;
        // ★ 走后己方将不能处于被将军状态（拦截送将、将帅照面）
        return !new CheckDetector().isInCheck(board.apply(move), isRed);
    }

    private boolean inBounds(int row, int col) {
        return row >= 0 && row < 10 && col >= 0 && col < 9;
    }

    private boolean canKingGo(Board board, Move move, boolean isRed) {
        int dr = Math.abs(move.toRow() - move.fromRow());
        int dc = Math.abs(move.toCol() - move.fromCol());
        if (dr + dc != 1) return false;
        return inPalace(move.toRow(), move.toCol(), isRed);
    }

    private boolean canAdvisorGo(Move move, boolean isRed) {
        int dr = Math.abs(move.toRow() - move.fromRow());
        int dc = Math.abs(move.toCol() - move.fromCol());
        if (dr != 1 || dc != 1) return false;
        return inPalace(move.toRow(), move.toCol(), isRed);
    }

    private boolean canBishopGo(Board board, Move move, boolean isRed) {
        int dr = Math.abs(move.toRow() - move.fromRow());
        int dc = Math.abs(move.toCol() - move.fromCol());
        if (dr != 2 || dc != 2) return false;
        int blockRow = (move.fromRow() + move.toRow()) / 2;
        int blockCol = (move.fromCol() + move.toCol()) / 2;
        if (!board.isEmpty(blockRow, blockCol)) return false;
        return isRed ? move.toRow() >= 5 : move.toRow() <= 4;
    }

    private boolean canKnightGo(Board board, Move move) {
        int dr = move.toRow() - move.fromRow();
        int dc = move.toCol() - move.fromCol();
        int absDr = Math.abs(dr);
        int absDc = Math.abs(dc);
        if (!((absDr == 2 && absDc == 1) || (absDr == 1 && absDc == 2))) return false;

        int blockRow, blockCol;
        if (absDr == 2) {
            blockRow = move.fromRow() + (dr > 0 ? 1 : -1);
            blockCol = move.fromCol();
        } else {
            blockRow = move.fromRow();
            blockCol = move.fromCol() + (dc > 0 ? 1 : -1);
        }
        if (!inBounds(blockRow, blockCol)) return false;
        return board.isEmpty(blockRow, blockCol);
    }

    private boolean canRookGo(Board board, Move move) {
        return isLineClear(board, move.fromRow(), move.fromCol(), move.toRow(), move.toCol());
    }

    private boolean canCannonGo(Board board, Move move) {
        if (move.fromRow() != move.toRow() && move.fromCol() != move.toCol()) return false;
        char target = board.pieceAt(move.toRow(), move.toCol());
        if (target == ' ') {
            return isLineClear(board, move.fromRow(), move.fromCol(), move.toRow(), move.toCol());
        }
        int count = countBetween(board, move.fromRow(), move.fromCol(), move.toRow(), move.toCol());
        return count == 1;
    }

    private boolean canPawnGo(Move move, boolean isRed) {
        int dr = move.toRow() - move.fromRow();
        int dc = Math.abs(move.toCol() - move.fromCol());
        boolean forward = isRed ? dr == -1 : dr == 1;
        boolean crossedRiver = isRed ? move.fromRow() <= 4 : move.fromRow() >= 5;

        if (forward) return dc == 0;
        if (crossedRiver) return (dr == 0 && dc == 1);
        return false;
    }

    private boolean inPalace(int row, int col, boolean isRed) {
        if (col < 3 || col > 5) return false;
        return isRed ? (row >= 7 && row <= 9) : (row >= 0 && row <= 2);
    }

    private boolean isLineClear(Board board, int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++)
                if (!board.isEmpty(r1, c)) return false;
            return true;
        }
        if (c1 == c2) {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++)
                if (!board.isEmpty(r, c1)) return false;
            return true;
        }
        return false;
    }

    private int countBetween(Board board, int r1, int c1, int r2, int c2) {
        int count = 0;
        if (r1 == r2) {
            int minC = Math.min(c1, c2), maxC = Math.max(c1, c2);
            for (int c = minC + 1; c < maxC; c++)
                if (!board.isEmpty(r1, c)) count++;
        } else if (c1 == c2) {
            int minR = Math.min(r1, r2), maxR = Math.max(r1, r2);
            for (int r = minR + 1; r < maxR; r++)
                if (!board.isEmpty(r, c1)) count++;
        }
        return count;
    }
}
