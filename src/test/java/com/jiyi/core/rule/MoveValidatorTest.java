package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoveValidatorTest {

    private final MoveValidator validator = new MoveValidator();

    // FEN parts[N] = rows[N] = UCI row = 9-N
    // rows[0] (top) = black back rank = UCI row 9
    // rows[9] (bottom) = red back rank = UCI row 0

    @Test
    void rookForward() {
        String fen = "3k5/9/r8/9/9/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("a7a6"), false));
    }

    @Test
    void rookBlocked() {
        String fen = "4k4/9/r8/p8/P8/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, Move.fromUci("a7a4"), false));
    }

    @Test
    void knight() {
        String fen = "3k5/9/9/9/9/9/9/9/4N4/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("e1c2"), true));
    }

    @Test
    void cannon() {
        String fen = "5k3/9/9/9/9/9/9/9/c3K4/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("a1a2"), false));
    }

    @Test
    void redPawnForward() {
        String fen = "5k3/9/9/9/9/9/9/9/5P3/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("f1f2"), true));
    }

    @Test
    void redPawnForwardAfterRiver() {
        String fen = "3k5/9/9/9/5P3/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("f5f6"), true), "forward");
    }

    @Test
    void redPawnSidewaysAfterRiver() {
        String fen = "3k5/9/9/9/5P3/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("f5e5"), true), "left");
        assertTrue(validator.canGo(b, Move.fromUci("f5g5"), true), "right");
    }

    @Test
    void redPawnNoSidewaysBeforeRiver() {
        String fen = "4k4/9/9/9/9/9/5P3/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, Move.fromUci("f3e3"), true), "not crossed");
    }

    @Test
    void advisor() {
        String fen = "3k5/9/9/9/9/9/9/9/4A4/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("e1d2"), true));
    }

    @Test
    void bishop() {
        String fen = "3k5/9/9/9/9/9/9/9/2B6/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("c1a3"), true));
    }

    @Test
    void cannonCaptureWithScreen() {
        String fen = "3k5/9/9/2p6/9/9/2P6/2C6/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("c2c6"), true));
    }

    @Test
    void cannonCannotCaptureWithoutScreen() {
        String fen = "4k4/9/9/2p6/9/9/9/2C6/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, Move.fromUci("c2c6"), true));
    }

    @Test
    void kingMove() {
        String fen = "1k7/9/9/9/9/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("e0e1"), true));
        assertTrue(validator.canGo(b, Move.fromUci("e0d0"), true));
        assertFalse(validator.canGo(b, Move.fromUci("e0c0"), true));
    }

    @Test
    void cannotCaptureOwn() {
        Board b = Board.STANDARD;
        assertFalse(validator.canGo(b, Move.fromUci("a9b9"), false));
    }

    @Test
    void testCanGo_outOfBoundsTarget_returnsFalse() {
        Board b = Board.STANDARD;
        // h2e2 合法着法，但构造越界目标（toRow=-1 / toCol=9）必须安全返回 false 而非抛异常
        assertFalse(validator.canGo(b, new Move(7, 7, -1, 7), true));
        assertFalse(validator.canGo(b, new Move(7, 7, 7, 9), true));
        // from 越界同样安全
        assertFalse(validator.canGo(b, new Move(-1, 0, 0, 0), true));
    }

    @Test
    void testCanKnightGo_blockedByEdge_returnsFalse() {
        // 黑马在 row1 col4，向上两格到 row-1 越界——必须安全 false
        String fen = "4k4/4n4/9/9/9/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, new Move(1, 4, -1, 3), false));
        assertFalse(validator.canGo(b, new Move(1, 4, -1, 5), false));
    }

    @Test
    void testCanKnightGo_legBlocked_returnsFalse() {
        // 红马 row1 col4，目标 (3,5)；马腿在 (2,4) 放一红兵挡住
        String fen = "4k4/9/9/9/9/9/2P6/9/4N4/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        // 马在 e1(row1)，目标 g2(row3)；马腿 f1(row2) 被兵占 → 蹩马腿
        assertFalse(validator.canGo(b, new Move(1, 4, 3, 5), true));
    }

    @Test
    void testCanGo_underCheck_mustEscape() {
        // 黑车 e7 正瞄红将 e0（e 列无遮挡）：红被将军，走无关子拒绝、走将逃出允许
        String fen = "4k4/9/4r4/9/9/9/9/9/9/R3K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, Move.fromUci("a0a1"), true), "must not ignore check");
        assertFalse(validator.canGo(b, Move.fromUci("e0e1"), true), "still under check after move");
        assertTrue(validator.canGo(b, Move.fromUci("e0d0"), true), "escape check sideways");
    }

    @Test
    void testCanGo_flyingGeneral_returnsFalse() {
        // 将帅照面：红将 e0 与黑将 e9 同列无遮挡，红将 e0e1 后仍照面 → 拒绝
        String fen = "4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, Move.fromUci("e0e1"), true), "flying general forbidden");
        // 黑方同理由红方视角同样拒绝
        assertFalse(validator.canGo(b, Move.fromUci("e9e8"), false), "flying general forbidden (black)");
    }

    @Test
    void testCanGo_captureKing_returnsFalse() {
        // 红车 e5 可直线吃到黑将 e9 → 禁吃将
        String fen = "4k4/9/9/9/4r4/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertFalse(validator.canGo(b, Move.fromUci("e5e9"), true), "capturing king forbidden");
    }

    @Test
    void testGenerateLegal_noKingCapture() {
        // 生成着法列表不应包含吃将着法
        String fen = "4k4/9/9/9/4r4/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        var moves = new MoveGenerator().generateLegal(b, true);
        assertTrue(moves.stream().noneMatch(m -> m.toRow() == 9 && m.toCol() == 4), "no king capture move");
    }
}
