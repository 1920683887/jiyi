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

    /**
     * 对比外部识别局面与引擎局面（完全对齐 C++ BoardComparator.cpp）。
     * @param linkBoard   外部平台当前识别局面
     * @param engineBoard 引擎当前局面（GameService，权威基准）
     * @param engineIsRed 引擎是否执红
     * @param analysisMode 观战模式
     * @return null=无变化；否则 action 对应 C++ flag（1=对手走子 2=引擎走子 3/4=新局/疑似新局→NEW_GAME）
     */
    public ComparisonResult compare(Board linkBoard, Board engineBoard,
                                     boolean engineIsRed, boolean analysisMode) {
        // 1. 收集 diff（同 C++：lc=link 棋子，ec=engine 棋子）
        List<int[]> diffs = new ArrayList<>();  // {r, c, lc, ec}
        int engToLinkCount = 0, linkToEngCount = 0, bothDiffCount = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char lc = linkBoard.pieceAt(r, c);
                char ec = engineBoard.pieceAt(r, c);
                if (lc == ec) continue;
                diffs.add(new int[]{r, c, lc, ec});
                if (lc == ' ' && ec != ' ') engToLinkCount++;
                else if (lc != ' ' && ec == ' ') linkToEngCount++;
                else bothDiffCount++;
            }
        }
        if (diffs.isEmpty()) return null;
        int total = diffs.size();

        // 2. 配对（同 C++）：link[i].lc == engine[j].ec（同一子：link 新位置 i，engine 旧位置 j）
        int matchedFromR = -1, matchedFromC = -1, matchedToR = -1, matchedToC = -1;
        char matchedPiece = ' ';
        int matchCount = 0;
        for (int i = 0; i < diffs.size(); i++) {
            for (int j = i + 1; j < diffs.size(); j++) {
                int[] di = diffs.get(i), dj = diffs.get(j);
                // 方向1: di(link) 有子 == dj(engine) 有子
                if (di[2] != ' ' && di[2] == dj[3]) {
                    int fromR = dj[0], fromC = dj[1];   // engine 旧位置
                    int toR = di[0], toC = di[1];       // link 新位置
                    // valid: from 格在 link 为空（移动），或吃子（to 格 engine 有子且同色）
                    boolean valid = (dj[2] == ' ')
                        || (di[3] != ' ' && Piece.isRedChar((char) di[2]) == Piece.isRedChar((char) di[3]));
                    if (valid && canGo(engineBoard, fromR, fromC, toR, toC, (char) di[2])) {
                        matchedFromR = fromR; matchedFromC = fromC;
                        matchedToR = toR; matchedToC = toC;
                        matchedPiece = (char) di[2];
                        matchCount++;
                    }
                }
                // 方向2: dj(link) 有子 == di(engine) 有子
                if (dj[2] != ' ' && dj[2] == di[3]) {
                    int fromR = di[0], fromC = di[1];
                    int toR = dj[0], toC = dj[1];
                    boolean valid = (di[2] == ' ')
                        || (dj[3] != ' ' && Piece.isRedChar((char) dj[2]) == Piece.isRedChar((char) dj[3]));
                    if (valid && canGo(engineBoard, fromR, fromC, toR, toC, (char) dj[2])) {
                        matchedFromR = fromR; matchedFromC = fromC;
                        matchedToR = toR; matchedToC = toC;
                        matchedPiece = (char) dj[2];
                        matchCount++;
                    }
                }
            }
        }

        // 3. 唯一配对成功（C++ linkToEngCount==2）
        if (matchCount == 1) {
            boolean pieceIsRed = Piece.isRedChar(matchedPiece);
            Action action = (pieceIsRed == engineIsRed) ? Action.ENGINE_MOVED : Action.OPPONENT_MOVED;
            return new ComparisonResult(action, new Diff(matchedFromR, matchedFromC, matchedToR, matchedToC));
        }

        // 4. 未配对（C++ flag 3/4）
        if (total > 4) return new ComparisonResult(Action.NEW_GAME, null);  // isNewGame
        if (total > 2 && engToLinkCount < 2 && linkToEngCount < 2)
            return new ComparisonResult(Action.NEW_GAME, null);             // isMaybeNewGame
        return new ComparisonResult(Action.UNCLEAR, null);                  // 未匹配（C++ flag 0）
    }

    private boolean canGo(Board board, int fromR, int fromC, int toR, int toC, char piece) {
        return validator.canGo(board, new Move(fromR, fromC, toR, toC), Piece.isRedChar(piece));
    }

    // needConfirm: 车/炮走子动画确认（对齐 C++：只对 flag1 对手走子触发；
    // 吃子不跳过——被吃子消失动画最需要确认，对齐 C++ BoardComparator.cpp:126-127 注释）
    public boolean needConfirm(Board linkBoard, Board engineBoard, ComparisonResult result) {
        if (result == null || result.diff() == null) return false;
        if (result.action() != Action.OPPONENT_MOVED) return false;
        var d = result.diff();
        char movedPiece = linkBoard.pieceAt(d.toRow(), d.toCol());
        if (movedPiece != 'R' && movedPiece != 'r'
            && movedPiece != 'C' && movedPiece != 'c') return false;

        // For rook: check that the path behind the destination is safe
        if (movedPiece == 'R' || movedPiece == 'r') {
            int dr = d.toRow() - d.fromRow();
            int dc = d.toCol() - d.fromCol();
            int checkR = d.toRow() + Integer.signum(dr);
            int checkC = d.toCol() + Integer.signum(dc);
            if (checkR >= 0 && checkR <= 9 && checkC >= 0 && checkC <= 8) {
                char behind = engineBoard.pieceAt(checkR, checkC);
                if (behind != ' ') {
                    // If there's a piece behind the destination that matches the mover's color,
                    // this might be a capture that needs confirmation
                    if (Character.isUpperCase(behind) == Character.isUpperCase(movedPiece)) {
                        return false; // Own piece behind → safe, no need to confirm
                    }
                }
            }
        }

        // For cannon: check if there's a screen piece that could be wrong
        if (movedPiece == 'C' || movedPiece == 'c') {
            int count = 0;
            if (d.fromRow() == d.toRow()) {
                int minC = Math.min(d.fromCol(), d.toCol());
                int maxC = Math.max(d.fromCol(), d.toCol());
                for (int c = minC + 1; c < maxC; c++) {
                    if (engineBoard.pieceAt(d.fromRow(), c) != ' ') count++;
                }
            } else if (d.fromCol() == d.toCol()) {
                int minR = Math.min(d.fromRow(), d.toRow());
                int maxR = Math.max(d.fromRow(), d.toRow());
                for (int r = minR + 1; r < maxR; r++) {
                    if (engineBoard.pieceAt(r, d.fromCol()) != ' ') count++;
                }
            }
            if (count > 1) return false; // Multiple screens → not a cannon capture move
        }

        return true; // Need confirmation scan
    }
}
