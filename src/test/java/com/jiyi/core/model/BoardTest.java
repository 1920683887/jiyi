package com.jiyi.core.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    @Test
    void standardBoard() {
        Board b = Board.STANDARD;
        assertEquals('R', b.pieceAt(9, 0), "red rook at (9,0)");
        assertEquals('r', b.pieceAt(0, 0), "black rook at (0,0)");
        assertEquals('K', b.pieceAt(9, 4), "red king at (9,4)");
        assertEquals('k', b.pieceAt(0, 4), "black king at (0,4)");
    }

    @Test
    void applyMoveReturnsNewBoard() {
        Board b = Board.STANDARD;
        Board b2 = b.apply(new Move(0, 1, 2, 2));
        assertEquals('n', b.pieceAt(0, 1), "original unchanged");
        assertEquals(' ', b2.pieceAt(0, 1), "source cleared");
        assertEquals('n', b2.pieceAt(2, 2), "target set");
    }

    @Test
    void fenRoundTrip() {
        Board b = Board.STANDARD;
        Board b2 = Board.fromFen(b.toFen(true));
        assertEquals(b, b2);
    }

    @Test
    void moveFromUci() {
        Move m = Move.fromUci("a0a1");
        assertEquals(9, m.fromRow());
        assertEquals(0, m.fromCol());
        assertEquals(8, m.toRow());
        assertEquals(0, m.toCol());
    }

    @Test
    void moveToUci() {
        Move m = new Move(9, 0, 8, 0);
        assertEquals("a0a1", m.toUci());
    }

    @Test
    void uciRoundTrip() {
        String uci = "h2e2";
        Move m = Move.fromUci(uci);
        assertEquals(uci, m.toUci());
    }
}
