package com.jiyi.core.book;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BookSelectorTest {

    private final BookSelector selector = new BookSelector();

    @Test
    void bestScore() {
        var entries = List.of(
            new BookEntry("h2e2", 50, 0.6, 0,0,0, "", ""),
            new BookEntry("h7e7", 100, 0.7, 0,0,0, "", ""),
            new BookEntry("b0c2", 30, 0.5, 0,0,0, "", "")
        );
        var result = selector.select(entries, BookSelector.Strategy.BEST_SCORE);
        assertEquals("h7e7", result.move());
    }

    @Test
    void bestWinrate() {
        var entries = List.of(
            new BookEntry("h2e2", 50, 0.6, 0,0,0, "", ""),
            new BookEntry("h7e7", 100, 0.8, 0,0,0, "", ""),
            new BookEntry("b0c2", 30, 0.5, 0,0,0, "", "")
        );
        var result = selector.select(entries, BookSelector.Strategy.BEST_WINRATE);
        assertEquals("h7e7", result.move());
        assertEquals(0.8, result.winRate());
    }

    @Test
    void random() {
        var entries = List.of(
            new BookEntry("h2e2", 50, 0.6, 0,0,0, "", ""),
            new BookEntry("h7e7", 100, 0.8, 0,0,0, "", "")
        );
        var result = selector.select(entries, BookSelector.Strategy.RANDOM);
        assertNotNull(result);
        assertTrue(result.move().equals("h2e2") || result.move().equals("h7e7"));
    }

    @Test
    void emptyReturnsNull() {
        assertNull(selector.select(List.of(), BookSelector.Strategy.BEST_SCORE));
    }
}
