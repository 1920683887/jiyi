package com.jiyi.core.book;

import com.jiyi.core.model.Board;

import java.util.List;

public interface OpenBook {
    List<BookEntry> query(Board board, boolean redGo);
    List<BookEntry> query(String fenCode, boolean onlyFinalPhase);
    void close();
}
