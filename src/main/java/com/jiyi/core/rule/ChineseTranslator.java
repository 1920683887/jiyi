package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

import java.util.*;

import static com.jiyi.core.model.Piece.*;

public class ChineseTranslator {

    private static final String[] RED_NUMS = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
    private static final String[] BLACK_NUMS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final Map<Character, String> PIECE_NAMES = new HashMap<>();

    static {
        PIECE_NAMES.put('K', "帅"); PIECE_NAMES.put('k', "将");
        PIECE_NAMES.put('A', "仕"); PIECE_NAMES.put('a', "士");
        PIECE_NAMES.put('B', "相"); PIECE_NAMES.put('b', "象");
        PIECE_NAMES.put('N', "马"); PIECE_NAMES.put('n', "马");
        PIECE_NAMES.put('R', "车"); PIECE_NAMES.put('r', "车");
        PIECE_NAMES.put('C', "炮"); PIECE_NAMES.put('c', "炮");
        PIECE_NAMES.put('P', "兵"); PIECE_NAMES.put('p', "卒");
    }

    public String toChinese(Board board, Move move) {
        boolean isRed = isRedChar(board.pieceAt(move.fromRow(), move.fromCol()));
        char piece = board.pieceAt(move.fromRow(), move.fromCol());
        String[] nums = isRed ? RED_NUMS : BLACK_NUMS;

        String prefix = resolvePrefix(board, move, isRed);
        String name = PIECE_NAMES.get(piece);
        String fromNum = nums[isRed ? 8 - move.fromCol() : move.fromCol()];
        String action;
        String toNum;

        if (move.fromRow() == move.toRow()) {
            action = "平";
            toNum = nums[isRed ? 8 - move.toCol() : move.toCol()];
        } else {
            boolean forward = isRed ? move.toRow() < move.fromRow() : move.toRow() > move.fromRow();
            action = forward ? "进" : "退";
            int distance = Math.abs(move.toRow() - move.fromRow());
            if (piece == 'N' || piece == 'n' || piece == 'A' || piece == 'a'
                || piece == 'B' || piece == 'b') {
                toNum = nums[isRed ? 8 - move.toCol() : move.toCol()];
            } else {
                toNum = String.valueOf(distance);
                if (!isRed) toNum = BLACK_NUMS[distance - 1];
            }
        }

        return prefix + name + fromNum + action + toNum;
    }

    private String resolvePrefix(Board board, Move move, boolean isRed) {
        char piece = board.pieceAt(move.fromRow(), move.fromCol());
        List<int[]> samePieces = new ArrayList<>();

        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                if (board.pieceAt(r, c) == piece && (r != move.fromRow() || c != move.fromCol())) {
                    samePieces.add(new int[]{r, c});
                }
            }
        }

        if (samePieces.isEmpty())
            return "";

        boolean sameFile = samePieces.stream().anyMatch(p -> p[1] == move.fromCol());
        if (!sameFile)
            return "";

        if (piece == 'P' || piece == 'p') {
            List<int[]> allPawns = new ArrayList<>(samePieces);
            allPawns.add(new int[]{move.fromRow(), move.fromCol()});
            if (allPawns.size() == 2) {
                // 两兵同列：红方 row 小=前、row 大=后；黑方 row 大=前、row 小=后（与 TCHESS 一致）
                int[] other = allPawns.get(0)[0] == move.fromRow() && allPawns.get(0)[1] == move.fromCol()
                    ? allPawns.get(1) : allPawns.get(0);
                boolean movingIsFront = isRed ? move.fromRow() < other[0] : move.fromRow() > other[0];
                return movingIsFront ? "前" : "后";
            }
            // ≥3 兵同列：数字编号。红方 row 升序（row 小=一）→ 一、二、三…；
            // 黑方 row 降序（row 大=1）→ 1、2、3…（与 TCHESS getSameFilePrefix 一致）
            allPawns.sort((a, b) -> isRed ? Integer.compare(a[0], b[0]) : Integer.compare(b[0], a[0]));
            int index = 0;
            for (int i = 0; i < allPawns.size(); i++) {
                if (allPawns.get(i)[0] == move.fromRow() && allPawns.get(i)[1] == move.fromCol()) {
                    index = i;
                    break;
                }
            }
            return isRed ? RED_NUMS[index] : BLACK_NUMS[index];
        }

        int fromRow = move.fromRow();
        for (int[] sp : samePieces) {
            if (sp[1] == move.fromCol()) {
                boolean movingPieceIsFront = isRed ? fromRow < sp[0] : fromRow > sp[0];
                return movingPieceIsFront ? "前" : "后";
            }
        }
        return "";
    }
}
