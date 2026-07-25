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
        String fen = "4k4/9/r8/9/9/9/9/9/9/4K4 w - - 0 1";
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
        String fen = "4k4/9/9/9/9/9/9/9/4N4/4K4 w - - 0 1";
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
        String fen = "4k4/9/9/9/9/9/9/9/5P3/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("f1f2"), true));
    }

    @Test
    void redPawnForwardAfterRiver() {
        String fen = "4k4/9/9/9/5P3/9/9/9/9/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("f5f6"), true), "forward");
    }

    @Test
    void redPawnSidewaysAfterRiver() {
        String fen = "4k4/9/9/9/5P3/9/9/9/9/4K4 w - - 0 1";
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
        String fen = "4k4/9/9/9/9/9/9/9/4A4/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("e1d2"), true));
    }

    @Test
    void bishop() {
        String fen = "4k4/9/9/9/9/9/9/9/2B6/4K4 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(validator.canGo(b, Move.fromUci("c1a3"), true));
    }

    @Test
    void cannonCaptureWithScreen() {
        String fen = "4k4/9/9/2p6/9/9/2P6/2C6/9/4K4 w - - 0 1";
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
        String fen = "4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1";
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
}
