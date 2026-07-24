package com.jiyi.core.book;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class BookSelector {
    public enum Strategy {
        BEST_SCORE, BEST_WINRATE, RANDOM
    }

    private static final Random RNG = new Random();

    public BookEntry select(List<BookEntry> entries, Strategy strategy) {
        if (entries.isEmpty()) return null;
        return switch (strategy) {
            case BEST_SCORE -> entries.stream()
                .max(Comparator.comparingInt(BookEntry::score)).orElse(entries.get(0));
            case BEST_WINRATE -> entries.stream()
                .max(Comparator.comparingDouble(BookEntry::winRate)).orElse(entries.get(0));
            case RANDOM -> entries.get(RNG.nextInt(entries.size()));
        };
    }
}
