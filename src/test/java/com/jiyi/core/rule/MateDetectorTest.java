package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MateDetectorTest {

    private final MateDetector detector = new MateDetector();

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
}
