package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckDetectorTest {

    private final CheckDetector detector = new CheckDetector();

    @Test
    void standardNotCheck() {
        assertFalse(detector.isInCheck(Board.STANDARD, true));
        assertFalse(detector.isInCheck(Board.STANDARD, false));
    }

    @Test
    void flyingGeneral() {
        String fen = "4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isInCheck(b, true));
        assertTrue(detector.isInCheck(b, false));
    }

    @Test
    void rookCheck() {
        // 真实车将局面：黑将 row0 col4，红车 row2 col4 同列且 row1 空（车将成立）；红帅 row9 col3 避免飞将干扰
        String fen = "4k4/9/4R4/9/9/9/9/9/9/3K5 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isInCheck(b, false));
    }

    @Test
    void testIsInCheck_blackPawnSidewaysOnRedKing_returnsTrue() {
        // 黑兵 row9 col3 横向将军红将 row9 col4；黑将 row0 col3（与红将不同列，排除飞将干扰）
        String fen = "3k5/9/9/9/9/9/9/9/9/3pK4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isInCheck(b, true));
    }

    @Test
    void testIsInCheck_redPawnSidewaysOnBlackKing_returnsTrue() {
        // 红兵 row0 col3 横向将军黑将 row0 col4；红将 row9 col5（不同列）
        String fen = "3Pk4/9/9/9/9/9/9/9/9/5K3 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isInCheck(b, false));
    }

    @Test
    void testIsInCheck_pawnBehindKing_returnsFalse() {
        // 黑兵在红将正下方：黑兵前进方向向下，不能回头攻击上方的红将
        // 红将 row7 col4，黑兵 row8 col4（在将下方），黑将 row0 col3（不同列）
        String fen = "3k5/9/9/9/9/9/9/4K4/4p4/9 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(detector.isInCheck(b, true));
    }

    @Test
    void testIsInCheck_blackPawnFrontal_returnsTrue() {
        // 黑兵在红将正上方紧邻 row8 col4，黑兵向下走吃红将（正向将军）；黑将 row0 col3 不同列
        String fen = "3k5/9/9/9/9/9/9/9/4p4/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isInCheck(b, true));
    }

}
