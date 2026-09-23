package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MateDetectorTest {

    private final MateDetector detector = new MateDetector();
    private final CheckDetector checkDetector = new CheckDetector();

    @Test
    void standardNotMate() {
        assertFalse(detector.isCheckmate(Board.STANDARD, true));
    }

    @Test
    void notCheckmateWhenKingCanMove() {
        String fen = "4k4/9/9/9/9/9/9/9/4R4/5K3 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(detector.isCheckmate(b, false));
    }

    @Test
    void testIsCheckmate_true() {
        // 双车错绝杀：黑将 row0 col4 被红车 row1 col4 紧邻纵向将军。
        // 出口全被封：
        //   row0 col3 吃车 → 红帅 row9 col3 同列飞将 → 非法
        //   row0 col5 吃车 → 红车 row1 col5 同列纵向将军 → 非法
        //   row1 col4 吃车 → 红车 row2 col4 同列纵向将军 → 非法
        // 且当前被将军（row1 col4 红车）→ 将死
        String fen = "3RkR3/4RR3/4R4/9/9/9/9/9/9/3K5 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(checkDetector.isInCheck(b, false));
        assertTrue(detector.isNoLegalMove(b, false));
        assertTrue(detector.isCheckmate(b, false));
    }

    @Test
    void testIsNoLegalMove_stalematePosition_returnsTrue() {
        // 纯困毙（未被将军但无子可动）：
        // 黑将 row0 col4；红车 row1 col3（控制左出口 row0 col3——黑将走去即被车纵向将军）；
        // 红车 row1 col5（控制右出口 row0 col5）；红兵 row2 col4 正向控制下出口 row1 col4。
        // 控制子均在 row1/row2，不攻击黑将 row0 col4 → 当前未被将军，三出口全非法 → 困毙
        String fen = "4k4/3R1R3/4P4/9/9/9/9/9/9/3K5 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(checkDetector.isInCheck(b, false), "困毙局面不应处于被将军状态");
        assertTrue(detector.isNoLegalMove(b, false));
        assertFalse(detector.isCheckmate(b, false), "未被将军不算将死");
    }

    @Test
    void testIsNoLegalMove_checkmatePosition_returnsTrue() {
        String fen = "3RkR3/4RR3/4R4/9/9/9/9/9/9/3K5 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isNoLegalMove(b, false));
    }

    @Test
    void testIsNoLegalMove_activePosition_returnsFalse() {
        String fen = "4k4/9/9/9/9/9/9/9/9/3K5 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(detector.isNoLegalMove(b, false), "孤将可走 row1 col4，仍有着法");
    }
}
