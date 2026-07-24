package com.jiyi.core.book;

public record BookEntry(
    String move,
    int score,
    double winRate,
    int winNum,
    int drawNum,
    int loseNum,
    String note,
    String source
) {}
