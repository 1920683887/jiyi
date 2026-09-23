package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

import java.util.ArrayList;
import java.util.List;

import static com.jiyi.core.model.Piece.*;

public class MoveGenerator {

    private final MoveValidator validator = new MoveValidator();

    public List<Move> generate(Board board, boolean isRed) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char piece = board.pieceAt(r, c);
                if (piece == ' ' || isRed != isRedChar(piece)) continue;
                addMovesFor(board, r, c, piece, moves);
            }
        }
        return moves;
    }

    public List<Move> generateLegal(Board board, boolean isRed) {
        CheckDetector checkDetector = new CheckDetector();
        return generate(board, isRed).stream()
            .filter(m -> !checkDetector.isInCheck(board.apply(m), isRed))
            .toList();
    }

    private void addMovesFor(Board board, int r, int c, char piece, List<Move> moves) {
        boolean isRed = isRedChar(piece);
        switch (Character.toLowerCase(piece)) {
            case 'k' -> addKingMoves(board, r, c, isRed, moves);
            case 'a' -> addAdvisorMoves(board, r, c, isRed, moves);
            case 'b' -> addBishopMoves(board, r, c, isRed, moves);
            case 'n' -> addKnightMoves(board, r, c, isRed, moves);
            case 'r' -> addSlidingMoves(board, r, c, isRed, moves);
            case 'c' -> addCannonMoves(board, r, c, isRed, moves);
            case 'p' -> addPawnMoves(board, r, c, isRed, moves);
        }
    }

    private void tryAdd(Board board, int fr, int fc, int tr, int tc, boolean isRed, List<Move> moves) {
        if (tr < 0 || tr >= 10 || tc < 0 || tc >= 9) return;
        char target = board.pieceAt(tr, tc);
        if (target != ' ' && isRed == isRedChar(target)) return;
        // 中国象棋禁止吃将/帅
        if (target != ' ' && Character.toLowerCase(target) == 'k') return;
        moves.add(new Move(fr, fc, tr, tc));
    }

    private void addKingMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            int nr = r + d[0], nc = c + d[1];
            if (isRed ? (nr >= 7 && nr <= 9 && nc >= 3 && nc <= 5)
                      : (nr >= 0 && nr <= 2 && nc >= 3 && nc <= 5))
                tryAdd(board, r, c, nr, nc, isRed, moves);
        }
    }

    private void addAdvisorMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        for (int[] d : new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}) {
            int nr = r + d[0], nc = c + d[1];
            if (isRed ? (nr >= 7 && nr <= 9 && nc >= 3 && nc <= 5)
                      : (nr >= 0 && nr <= 2 && nc >= 3 && nc <= 5))
                tryAdd(board, r, c, nr, nc, isRed, moves);
        }
    }

    private void addBishopMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        for (int[] d : new int[][]{{2,2},{2,-2},{-2,2},{-2,-2}}) {
            int nr = r + d[0], nc = c + d[1];
            int br = r + d[0]/2, bc = c + d[1]/2;
            if (nr < 0 || nr >= 10 || nc < 0 || nc >= 9) continue;
            if (!board.isEmpty(br, bc)) continue;
            if (isRed ? nr >= 5 : nr <= 4) continue;
            tryAdd(board, r, c, nr, nc, isRed, moves);
        }
    }

    private void addKnightMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        int[][] moves_list = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        int[][] blocks = {{-1,0},{-1,0},{0,-1},{0,1},{0,-1},{0,1},{1,0},{1,0}};
        for (int i = 0; i < 8; i++) {
            int nr = r + moves_list[i][0], nc = c + moves_list[i][1];
            int br = r + blocks[i][0], bc = c + blocks[i][1];
            if (nr < 0 || nr >= 10 || nc < 0 || nc >= 9) continue;
            if (!board.isEmpty(br, bc)) continue;
            tryAdd(board, r, c, nr, nc, isRed, moves);
        }
    }

    private void addSlidingMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (nr >= 0 && nr < 10 && nc >= 0 && nc < 9) {
                char target = board.pieceAt(nr, nc);
                if (target == ' ') {
                    moves.add(new Move(r, c, nr, nc));
                } else {
                    if (isRed != isRedChar(target))
                        moves.add(new Move(r, c, nr, nc));
                    break;
                }
                nr += d[0]; nc += d[1];
            }
        }
    }

    private void addCannonMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            boolean foundScreen = false;
            while (nr >= 0 && nr < 10 && nc >= 0 && nc < 9) {
                char target = board.pieceAt(nr, nc);
                if (!foundScreen) {
                    if (target == ' ') {
                        moves.add(new Move(r, c, nr, nc));
                    } else {
                        foundScreen = true;
                    }
                } else {
                    if (target != ' ') {
                        if (isRed != isRedChar(target))
                            moves.add(new Move(r, c, nr, nc));
                        break;
                    }
                }
                nr += d[0]; nc += d[1];
            }
        }
    }

    private void addPawnMoves(Board board, int r, int c, boolean isRed, List<Move> moves) {
        int forward = isRed ? -1 : 1;
        boolean crossed = isRed ? r <= 4 : r >= 5;
        tryAdd(board, r, c, r + forward, c, isRed, moves);
        if (crossed) {
            tryAdd(board, r, c, r, c - 1, isRed, moves);
            tryAdd(board, r, c, r, c + 1, isRed, moves);
        }
    }
}
