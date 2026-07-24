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
        String fen = "4k4/9/9/9/9/9/9/9/R3K4/9 w - - 0 1";
        Board b = Board.fromFen(fen);
        assertTrue(detector.isInCheck(b, false));
    }

}
